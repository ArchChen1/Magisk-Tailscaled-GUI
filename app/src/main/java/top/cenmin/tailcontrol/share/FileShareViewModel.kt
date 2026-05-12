package top.cenmin.tailcontrol.share

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
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

                // 进度从 /proc/<pidof tailscaled>/net/dev tailscale0 字段拉
                val pid = tailRepo.pidofTailscaled()
                val trafficJob = if (pid != null && total > 0) viewModelScope.launch(Dispatchers.IO) {
                    var lastTx: Long = -1
                    while (isActive && process.isAlive) {
                        runCatching {
                            val raw = Shell.cmd("cat /proc/$pid/net/dev").exec().out.joinToString("\n")
                            raw.lineSequence().firstOrNull { it.contains("tailscale0:") }?.let { line ->
                                val tokens = line.substringAfter(":").trim().split(Regex("\\s+"))
                                val tx = tokens.getOrNull(8)?.toLongOrNull() ?: 0L
                                if (lastTx < 0) lastTx = tx
                                val pct = (((tx - lastTx).toDouble() / total) * 100).toInt().coerceIn(0, 99)
                                _ui.value = _ui.value.copy(progressPercent = pct, progressText = "$pct%")
                                pushNotification(pct, "$pct%")
                            }
                        }
                        delay(150)
                    }
                } else null

                process.errorStream.bufferedReader().forEachLine { line -> stderr.add(line) }
                val exit = process.waitFor()
                stdinJob.cancel()
                trafficJob?.cancel()

                if (exit == 0) {
                    _ui.value = _ui.value.copy(transferring = false, transferFinished = true, progressPercent = 100, progressText = "100%")
                    pushNotification(100, app.getString(top.cenmin.tailcontrol.R.string.transfer_complete))
                } else {
                    val errText = stderr.joinToString("\n").trim()
                    _ui.value = _ui.value.copy(
                        transferring = false,
                        transferFinished = true,
                        progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_failed, errText),
                    )
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    transferring = false,
                    progressText = app.getString(top.cenmin.tailcontrol.R.string.transfer_failed, t.message.orEmpty()),
                )
            }
        }
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
