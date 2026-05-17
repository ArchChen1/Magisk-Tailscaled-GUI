package top.cenmin.tailcontrol.share

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.core.model.TailscaleDevice
import top.cenmin.tailcontrol.service.FileTransferService
import javax.inject.Inject
import com.topjohnwu.superuser.Shell

data class FileShareUiState(
    val fileUri: Uri? = null,
    val fileName: String = "",
    val totalBytes: Long = -1,
    val peers: List<TailscaleDevice> = emptyList(),
    val countdown: Int = 0,
    val transferring: Boolean = false,
    val transferFinished: Boolean = false,
    val progressPercent: Int = 0,
    val progressText: String = "",
    val showNotRunningDialog: Boolean = false,
    val showOfflineDialog: Boolean = false,
    val cancelDialogOpen: Boolean = false,
    val processingDialog: Boolean = false,
    val doneDialog: Boolean = false,
    val tailscaleState: BackendState = BackendState.Unknown,
)

@HiltViewModel
class FileShareViewModel @Inject constructor(
    private val app: Application,
    private val tailRepo: TailscaleRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FileShareUiState())
    val ui: StateFlow<FileShareUiState> = _ui.asStateFlow()

    private var transferJob: Job? = null
    private var refreshJob: Job? = null

    fun bind(uri: Uri) {
        val name = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            c.moveToFirst(); c.getString(idx)
        } ?: "shared_file"
        val total = runCatching { app.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1 }.getOrDefault(-1)
        _ui.value = _ui.value.copy(fileUri = uri, fileName = name, totalBytes = total)
        startAutoRefresh()
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                for (i in 5 downTo 1) {
                    _ui.value = _ui.value.copy(countdown = i)
                    delay(1000)
                }
            }
        }
    }

    fun refreshOnce() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = tailRepo.fetchStatus()
            val state = status.backendState
            _ui.value = _ui.value.copy(tailscaleState = state)
            if (state is BackendState.Running) {
                val online = status.self?.online == true
                if (!online) {
                    _ui.value = _ui.value.copy(showOfflineDialog = true)
                } else {
                    _ui.value = _ui.value.copy(peers = status.peers)
                }
            } else {
                _ui.value = _ui.value.copy(showNotRunningDialog = true)
            }
        }
    }

    fun startTailscale() {
        viewModelScope.launch(Dispatchers.IO) {
            tailRepo.up()
            refreshOnce()
        }
    }

    fun dismissNotRunning() { _ui.value = _ui.value.copy(showNotRunningDialog = false) }
    fun dismissOffline() { _ui.value = _ui.value.copy(showOfflineDialog = false) }

    fun send(peer: TailscaleDevice) {
        val uri = _ui.value.fileUri ?: return
        val fileName = _ui.value.fileName
        val total = _ui.value.totalBytes

        if (total <= 0) {
            // 如果无法获取文件大小，则使用原来的网卡监控方式
            sendWithTrafficMonitoring(peer, uri, fileName)
            return
        }

        _ui.value = _ui.value.copy(transferring = true, progressPercent = 0, progressText = "0%")

        transferJob = viewModelScope.launch(Dispatchers.IO) {
            val cmd = "tailscale file cp --verbose --name \"$fileName\" - ${peer.name}:"
            val stderr = mutableListOf<String>()

            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))

                // 分块写入 stdin 并实时更新进度
                val writeJob = viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            process.outputStream.use { output ->
                                val buffer = ByteArray(64 * 1024) // 64KB 缓冲区
                                var totalWritten = 0L
                                var lastProgressPercent = 0

                                while (isActive) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break

                                    output.write(buffer, 0, read)
                                    output.flush()

                                    totalWritten += read
                                    // 限制最大进度为 99%，避免未完成就显示 100%
                                    var currentPercent = ((totalWritten.toDouble() / total) * 100).toInt()
                                    if (currentPercent >= 100) currentPercent = 99

                                    // 每变化 1% 或每 1MB 更新一次 UI，避免过于频繁
                                    if (currentPercent > lastProgressPercent || totalWritten % (1024 * 1024) < buffer.size) {
                                        lastProgressPercent = currentPercent
                                        withContext(Dispatchers.Main) {
                                            _ui.value = _ui.value.copy(
                                                progressPercent = currentPercent,
                                                progressText = "$currentPercent%"
                                            )
                                        }
                                        pushNotification(currentPercent, "$currentPercent%")
                                    }
                                }
                            }
                        }
                    }
                }

                // 同时读取错误流
                val errorJob = viewModelScope.launch(Dispatchers.IO) {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        synchronized(stderr) { stderr.add(line) }
                    }
                }

                val exit = process.waitFor()
                writeJob.cancel()
                errorJob.cancel()

                withContext(Dispatchers.Main) {
                    if (exit == 0) {
                        _ui.value = _ui.value.copy(
                            transferring = false,
                            transferFinished = true,
                            progressPercent = 100,
                            progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_complete_progress)
                        )
                        pushNotification(100, app.getString(top.cenmin.tailcontrol.R.string.transfer_complete))
                    } else {
                        val errText = synchronized(stderr) { stderr.joinToString("\n").trim() }
                        _ui.value = _ui.value.copy(
                            transferring = false,
                            transferFinished = true,
                            progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_failed, errText.ifEmpty { "Unknown error" }),
                        )
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(
                        transferring = false,
                        progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_failed, t.message.orEmpty()),
                    )
                }
            }
        }
    }

    // 原有的网卡监控方式作为备用（当无法获取文件大小时使用）
    private fun sendWithTrafficMonitoring(peer: TailscaleDevice, uri: Uri, fileName: String) {
        val total = _ui.value.totalBytes
        _ui.value = _ui.value.copy(transferring = true, progressPercent = 0, progressText = "0%")

        transferJob = viewModelScope.launch(Dispatchers.IO) {
            val cmd = "tailscale file cp --verbose --name \"$fileName\" - ${peer.name}:"
            val stderr = mutableListOf<String>()
            try {
                // libsu 不暴露 stdin 写入，这里用 Runtime.exec 直接拿到 outputStream 把 content 流写过去
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))

                // stdin
                val stdinJob = viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            process.outputStream.use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                    }
                }

                // 通过 /proc 监控网卡流量来估算进度
                val pid = tailRepo.pidofTailscaled()
                val trafficJob = if (pid != null && total > 0) viewModelScope.launch(Dispatchers.IO) {
                    var lastTotalTx: Long = -1
                    var lastTailscaleTx: Long = -1
                    var activeInterface: String? = null

                    while (isActive && process.isAlive) {
                        runCatching {
                            val raw = Shell.cmd("cat /proc/$pid/net/dev").exec().out.joinToString("\n")

                            // 优先尝试 tailscale0
                            val tailscaleLine = raw.lineSequence().firstOrNull { it.contains("tailscale0:") }
                            var currentTx = tailscaleLine?.let { parseTxBytes(it) } ?: 0L
                            var useFallback = false

                            if (activeInterface == null || activeInterface == "tailscale0") {
                                if (lastTailscaleTx >= 0) {
                                    val delta = currentTx - lastTailscaleTx
                                    if (delta <= 300) {
                                        useFallback = true
                                    } else {
                                        activeInterface = "tailscale0"
                                    }
                                } else {
                                    lastTailscaleTx = currentTx
                                    delay(1000)
                                    return@runCatching
                                }
                            }

                            // 降级到 wlan* 接口
                            if (useFallback || activeInterface == "wlan") {
                                val wlanTotal = raw.lineSequence()
                                    .filter { it.contains("wlan") && it.contains(":") }
                                    .sumOf { parseTxBytes(it) }

                                if (lastTotalTx >= 0 && activeInterface != "wlan") {
                                    val delta = wlanTotal - lastTotalTx
                                    if (delta > 300) {
                                        activeInterface = "wlan"
                                        currentTx = wlanTotal
                                        useFallback = false
                                    }
                                }
                                lastTotalTx = wlanTotal
                                if (useFallback) currentTx = wlanTotal
                            }

                            // 降级到 rmnet_* 接口
                            if (useFallback || activeInterface == "rmnet") {
                                val rmnetTotal = raw.lineSequence()
                                    .filter { it.contains("rmnet_") && it.contains(":") }
                                    .sumOf { parseTxBytes(it) }

                                if (lastTotalTx >= 0 && activeInterface != "rmnet") {
                                    val delta = rmnetTotal - lastTotalTx
                                    if (delta > 300) {
                                        activeInterface = "rmnet"
                                        currentTx = rmnetTotal
                                        useFallback = false
                                    }
                                }
                                lastTotalTx = rmnetTotal
                                if (useFallback) currentTx = rmnetTotal
                            }

                            // 最后降级到 p2p0
                            if (useFallback || activeInterface == "p2p") {
                                val p2pTx = raw.lineSequence()
                                    .firstOrNull { it.contains("p2p0:") }
                                    ?.let { parseTxBytes(it) } ?: 0L

                                if (lastTotalTx >= 0 && activeInterface != "p2p") {
                                    val delta = p2pTx - lastTotalTx
                                    if (delta > 300) {
                                        activeInterface = "p2p"
                                        currentTx = p2pTx
                                    }
                                }
                                lastTotalTx = p2pTx
                                if (useFallback) currentTx = p2pTx
                            }

                            // 计算进度
                            if (lastTailscaleTx >= 0 && lastTotalTx >= 0) {
                                val lastTx = when (activeInterface) {
                                    "tailscale0" -> lastTailscaleTx
                                    else -> lastTotalTx
                                }
                                val delta = currentTx - lastTx
                                var pct = ((delta.toDouble() / total) * 100).toInt().coerceIn(0, 99)
                                if (pct >= 100) pct = 99
                                withContext(Dispatchers.Main) {
                                    _ui.value = _ui.value.copy(progressPercent = pct, progressText = "$pct%")
                                }
                                pushNotification(pct, "$pct%")
                            }

                            if (activeInterface == "tailscale0") {
                                lastTailscaleTx = currentTx
                            } else {
                                lastTotalTx = currentTx
                            }
                        }
                        delay(150)
                    }
                } else null

                process.errorStream.bufferedReader().forEachLine { line -> stderr.add(line) }
                val exit = process.waitFor()
                stdinJob.cancel()
                trafficJob?.cancel()

                withContext(Dispatchers.Main) {
                    if (exit == 0) {
                        _ui.value = _ui.value.copy(
                            transferring = false,
                            transferFinished = true,
                            progressPercent = 100,
                            progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_complete_progress)
                        )
                        pushNotification(100, app.getString(top.cenmin.tailcontrol.R.string.transfer_complete))
                    } else {
                        val errText = stderr.joinToString("\n").trim()
                        _ui.value = _ui.value.copy(
                            transferring = false,
                            transferFinished = true,
                            progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_failed, errText.ifEmpty { "Unknown error" }),
                        )
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(
                        transferring = false,
                        progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_failed, t.message.orEmpty()),
                    )
                }
            }
        }
    }

    // 辅助函数：解析网卡行中的 TX bytes（第10列，索引9）
    private fun parseTxBytes(line: String): Long {
        val afterColon = line.substringAfter(":").trim()
        val tokens = afterColon.split(Regex("\\s+"))
        return tokens.getOrNull(8)?.toLongOrNull() ?: 0L
    }

    fun openCancelDialog() { _ui.value = _ui.value.copy(cancelDialogOpen = true) }
    fun dismissCancelDialog() { _ui.value = _ui.value.copy(cancelDialogOpen = false) }

    fun confirmCancel(onComplete: () -> Unit) {
        _ui.value = _ui.value.copy(cancelDialogOpen = false, processingDialog = true)
        viewModelScope.launch(Dispatchers.IO) {
            tailRepo.killAllTailscale()
            tailRepo.up()
            withContext(Dispatchers.Main) {
                _ui.value = _ui.value.copy(
                    processingDialog = false,
                    transferring = false,
                    progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_canceled),
                    progressPercent = 0,
                    doneDialog = true,
                )
                stopNotification()
                onComplete()
            }
        }
    }

    fun dismissDoneDialog() { _ui.value = _ui.value.copy(doneDialog = false) }

    fun stopNotification() {
        val intent = Intent(app, FileTransferService::class.java).apply { action = FileTransferService.ACTION_STOP }
        app.startService(intent)
    }

    private fun pushNotification(progress: Int, text: String) {
        val intent = Intent(app, FileTransferService::class.java).apply {
            action = FileTransferService.ACTION_START
            putExtra(FileTransferService.EXTRA_PROGRESS, progress)
            putExtra(FileTransferService.EXTRA_CONTENT_TEXT, text)
        }
        app.startForegroundService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        transferJob?.cancel()
        refreshJob?.cancel()
    }
}