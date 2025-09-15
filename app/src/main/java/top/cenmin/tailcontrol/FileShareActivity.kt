package top.cenmin.tailcontrol

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import java.io.IOException
import android.Manifest
import android.app.AlertDialog
import android.os.Build
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class FileShareActivity : ComponentActivity() {

    private var transferProcess: Process? = null
    private var isActivityForeground = false
    private var transferFinished = false
    private var transferring = false  // Activity 级别状态
    // 请求通知权限的 launcher
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("Tailscale", "通知权限已授予")
            } else {
                Log.d("Tailscale", "通知权限未授予，继续运行")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!checkRootAccess()) {
            showRootDialog()
            return
        }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri == null) {
            Log.e("FileShare", "[transfer] 未获取到文件 Uri")
            finish()
            return
        }

        // 获取文件名
        val fileUri = uri
        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: "shared_file"

        Log.i("FileShare", "[transfer] 准备发送文件: $fileName, Uri=$fileUri")

        setContent {
            var autoRefresh by remember { mutableStateOf(true) }
            var countDown by remember { mutableIntStateOf(0) }
            val scope = rememberCoroutineScope()
            var peers by remember { mutableStateOf(listOf<TailscaleDevice>()) }
            var rawLog by remember { mutableStateOf("") }
            var selectedPeer by remember { mutableStateOf<TailscaleDevice?>(null) }
            var progressText by remember { mutableStateOf(getString(R.string.waiting)) }
            var progressPercent by remember { mutableIntStateOf(0) }
            var showNotRunningDialog by remember { mutableStateOf(false) }
            var showOfflineDialog by remember { mutableStateOf(false) }
            val animatedProgress by animateFloatAsState(
                targetValue = progressPercent / 100f,
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            )

            fun refreshPeers() {
                scope.launch(Dispatchers.IO) {
                    val (backendState, selfOnline) = checkTailscaleStatus()
                    if (backendState == "Running"){
                        if (selfOnline){
                            try {
                                val output = executeRootCommand("tailscale status --json")
                                val newPeers = parseDevicesFromJson(output)
                                val sortedPeers = newPeers.sortedWith(
                                    compareBy<TailscaleDevice> { !it.online }
                                        .thenByDescending { it.lastSeen ?: "9999-99-99 99:99:99" }
                                )
                                withContext(Dispatchers.Main) { peers = sortedPeers }
                            } catch (e: Exception) {
                                Log.e("FileShare", "[transfer] 刷新设备失败: ${e.message}")
                            }
                        }else{
                            showOfflineDialog  = true
                        }
                    }else{
                        showNotRunningDialog = true // 触发对话框
                    }
                }
            }
            // 在 Composable 层里渲染对话框
            if (showNotRunningDialog) {
                AlertDialog(
                    onDismissRequest = { showNotRunningDialog = false },
                    title = { Text("Tailscale ${getString(R.string.status_service_stopped) }") },
                    text = { Text(getString(R.string.tailscale_not_running) ) },
                    confirmButton = {
                        TextButton(onClick = {
                            showNotRunningDialog = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    Runtime.getRuntime().exec(arrayOf("su", "-c", "tailscale up")).waitFor()
                                    Log.i("FileShare", "[transfer] tailscale 已启动")
                                } catch (e: Exception) {
                                    Log.e("FileShare", "[transfer] 启动 tailscale 失败: ${e.message}")
                                }
                            }
                        }) {
                            Text(getString(R.string.start_tailscale) )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNotRunningDialog = false }) {
                            Text(getString(R.string.cancel) )
                        }
                    }
                )
            }

            if (showOfflineDialog) {
                AlertDialog(
                    onDismissRequest = { showOfflineDialog = false },
                    title = { Text("Tailscale ${getString(R.string.offline_now) }") },
                    text = { Text(getString(R.string.tailnet_unreachable) ) },
                    confirmButton = { TextButton(onClick = { showOfflineDialog = false }) { Text(getString(R.string.done) ) }
                    }
                )
            }
            LaunchedEffect(Unit) {
                refreshPeers()
                while (autoRefresh) {
                    for (i in 5 downTo 1) { countDown = i; delay(1000) }
                    refreshPeers()
                }
            }

            Column(Modifier.fillMaxSize()) {
                // 进度条 + 控制区域（保持原样）
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(getString(R.string.transfer_status, progressText), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))

                        var showCancelDialog by remember { mutableStateOf(false) }
                        var showProcessingDialog by remember { mutableStateOf(false) }
                        var showDoneDialog by remember { mutableStateOf(false) }

                        if (transferring) {
                            Button(onClick = { showCancelDialog = true }) { Text(getString(R.string.force_cancel_transfer)) }
                        }

                        if (showCancelDialog) {
                            AlertDialog(
                                onDismissRequest = { showCancelDialog = false },
                                title = { Text(getString(R.string.force_cancel_title)) },
                                text = { Text(getString(R.string.force_cancel_warn)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showCancelDialog = false
                                        showProcessingDialog = true
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                executeRootCommand("kill -2 $(pidof tailscale) && tailscale up")
                                                withContext(Dispatchers.Main) {
                                                    showProcessingDialog = false
                                                    showDoneDialog = true
                                                    transferring = false
                                                    progressText = getString(R.string.transfer_canceled)
                                                    progressPercent = 0
                                                    stopNotification()
                                                }
                                            } catch (e: Exception) {
                                                Log.e("FileShare", "[transfer] 取消失败: ${e.message}")
                                                withContext(Dispatchers.Main) { showProcessingDialog = false }
                                            }
                                        }
                                    }) { Text(getString(R.string.confirm)) }
                                },
                                dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text(getString(R.string.cancel)) } }
                            )
                        }

                        if (showProcessingDialog) {
                            AlertDialog(
                                onDismissRequest = {},
                                title = { Text(getString(R.string.drop_canceling)) },
                                text = { Text(getString(R.string.cancel_please_wait)) },
                                confirmButton = {}
                            )
                        }

                        if (showDoneDialog) {
                            val activity = LocalContext.current as? Activity
                            AlertDialog(
                                onDismissRequest = { showDoneDialog = false; activity?.finishAffinity() },
                                title = { Text(getString(R.string.operation_complete)) },
                                text = { Text(getString(R.string.tailreboot)) },
                                confirmButton = { TextButton(onClick = { showDoneDialog = false; activity?.finishAffinity() }) { Text(getString(R.string.done)) } }
                            )
                        }
                    }
                }

                // 设备列表
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Text(getString(R.string.device_list_autofresh, countDown))
                    Button(onClick = { refreshPeers() }) { Text(getString(R.string.refresh)) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                ) {
                    items(peers) { peer ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val dotColor = if (peer.online) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    Canvas(modifier = Modifier.size(12.dp)) { drawCircle(dotColor) }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("${getString(R.string.name)}: ${peer.name}")
                                        Text("IP: ${peer.ip}", style = MaterialTheme.typography.bodySmall)
                                        if (!peer.online) Text("${getString(R.string.last_seen)}: ${peer.lastSeen}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                // 在 setContent {} 的 Composable 里定义一个状态

                                val scope = rememberCoroutineScope()
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val (backendState, selfOnline) = checkTailscaleStatus()
                                            Log.d("FileShare", "[transfer] 点击发送按钮，peer=${peer.name}, 保护程序运行=$backendState, 状态在线=$selfOnline")
                                            if (backendState == "Running"){
                                                if (selfOnline){
                                                    selectedPeer = peer
                                                    transferring = true
                                                    peers = peers.toList()
                                                }else{
                                                    showOfflineDialog  = true
                                                }
                                            }else{
                                                showNotRunningDialog = true // 触发对话框
                                            }

                                        }
                                    },
                                    enabled = !transferring && peer.online
                                ) { Text(getString(R.string.send)) }
                            }
                        }
                    }
                }
            }

            if (selectedPeer != null) {
                LaunchedEffect(selectedPeer) {
                    Log.d("FileShare", "[transfer] LaunchedEffect($selectedPeer)")
                    startFileTransfer(fileUri, fileName, selectedPeer!!.name,
                        onUpdate = { update ->
                            progressText = update
                            val percentMatch = Regex("(\\d+(?:\\.\\d+)?)%").find(update)
                            progressPercent = percentMatch?.groupValues?.get(1)?.toFloat()?.toInt() ?: progressPercent
                            updateNotification(progressPercent, update)
                        },
                        onLog = { logLine ->
                            rawLog += logLine
                            Log.d("FileShare", "[transfer] ${logLine.trim()}")
                        },
                        onFinish = { exitCode ->
                            transferring = false
                            selectedPeer = null
                            peers = peers.toList()
                            progressPercent = 100
                            if (exitCode==0)progressText = getString(R.string.transfer_complete)

                            transferFinished = true

                            if (isActivityForeground) {
                                stopNotification()
                            } else {
                                updateNotification(progressPercent, progressText)
                            }
                        }
                    )
                }
            }
        }
    }
    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            output.contains("uid=0")
        } catch (e: Exception) {
            Log.d("Tailscale", "checkRootAccess: $e")
            false
        }
    }
    private fun showRootDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.get_root_failed_title))
            .setMessage(getString(R.string.get_root_failed_msg))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.exit)) { _, _ -> finish() }
            .show()
    }
    private fun startFileTransfer(
        uri: Uri,
        fileName: String,
        peer: String,
        onUpdate: (String) -> Unit,
        onLog: (String) -> Unit,
        onFinish: (Int) -> Unit
    ) {
        Log.d("FileShare", "[transfer] startFileTransfer调用")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val totalBytes = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
                Log.i("FileShare", "[transfer] 文件大小: $totalBytes bytes")
                var lastTxBytes: Long? = null // 初始为 null
                val command = arrayOf("su", "-c", "tailscale file cp --verbose --name \"$fileName\" - $peer:")
                Log.i("FileShare", "[transfer] 执行命令: ${command.joinToString(" ")}")
                transferProcess = Runtime.getRuntime().exec(command)

                // 写入 stdin
                launch {
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            transferProcess!!.outputStream.use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                        Log.i("FileShare", "[transfer] 文件流写入完成")
                    } catch (e: IOException) {
                        Log.e("FileShare", "[transfer] 写入 stdin 出错: ${e.message}")
                    }
                }
                val tailscalePid = Runtime.getRuntime().exec(arrayOf("su", "-c", "pidof tailscaled")).inputStream.bufferedReader().readText().trim().toIntOrNull()
                val trafficJob = tailscalePid?.let { pid ->
                    launch(Dispatchers.IO) {
                        while (transferProcess?.isAlive == true) {
                            try {
                                val lines = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/net/dev"))
                                    .inputStream.bufferedReader().readLines()
                                val line = lines.firstOrNull { it.contains("tailscale0") }
                                Log.d("TailControl", "[transfer] lastTxBytes: $line")
                                line?.trim()?.split(Regex("\\s+"))?.getOrNull(9)?.toLongOrNull()?.let { txBytes ->
                                    val last = lastTxBytes ?: txBytes // 如果为 null，就初始化
                                    Log.d("TailControl", "[transfer] lastTxBytes: $lastTxBytes")
                                    val progress = ((txBytes - last).toDouble() / totalBytes * 100)
                                        .toInt()
                                        .coerceAtMost(99)
                                    lastTxBytes = last // 更新 lastTxBytes
                                    onUpdate("${getString(R.string.transferring)} $progress%")
                                }
                            } catch (_: Exception) {}
                            delay(100)
                        }
                    }
                }

                val stderrBuilder = StringBuilder()
                val stderrReader = transferProcess!!.errorStream.bufferedReader()
                val stderrJob = launch(Dispatchers.IO) {
                    stderrReader.forEachLine { line ->
                        stderrBuilder.appendLine(line)
                        onLog("[STDERR] $line")
                        // 不在这里调用 onUpdate("传输完成") 或 onFinish()
                    }
                }

                val exitCode = transferProcess!!.waitFor()
                stderrJob.cancelAndJoin()  // 确保协程结束
                trafficJob?.cancelAndJoin()
                val stderrText = stderrBuilder.toString().trim()

                Log.i("FileShare", "[transfer] 进程退出: $exitCode, STDERR=$stderrText")

                if (exitCode == 0) {
                    Log.i("FileShare", "[transfer] ui 传输完成")
                    onUpdate("传输完成")
                } else {
                    Log.i("FileShare", "[transfer] ui 失败: $stderrText")
                    onUpdate("失败: $stderrText") // UI 会显示 "can't send to laptop: not connected to the tailnet"
                }
                onFinish(exitCode)


            } catch (e: Exception) {
                Log.e("FileShare", "[transfer] 传输失败: ${e.message}", e)
                onUpdate("失败: ${e.message}")
            }
        }


    }

    private fun updateNotification(progress: Int, contentText: String) {

        val intent = Intent(this, FileTransferService::class.java).apply {
            action = FileTransferService.ACTION_START
            putExtra(FileTransferService.EXTRA_PROGRESS, progress)
            putExtra(FileTransferService.EXTRA_CONTENT_TEXT, contentText)
        }
        startForegroundService(intent)
    }

    /* 返回 Pair：first = BackendState 是否 Running，second = Self 是否 Online */
    private suspend fun checkTailscaleStatus(): Pair<String, Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "tailscale status --json"))
                    .inputStream.bufferedReader().readText()

                val root = JSONObject(json)
                val backendState = root.optString("BackendState")  // 直接拿字符串
                val selfOk = root.optJSONObject("Self")?.optBoolean("Online") == true
                backendState to selfOk
            }.getOrElse {
                Log.d("TailControl", "checkTailscaleStatus: $it")
                "Unknown" to false
            }
        }


    private fun stopNotification() {
        val intent = Intent(this, FileTransferService::class.java).apply { action = FileTransferService.ACTION_STOP }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNotification()
    }

    override fun onResume() {
        super.onResume()
        isActivityForeground = true
        if (transferFinished) {
            stopNotification()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityForeground = false
        when {
            !transferring && !transferFinished -> {
                stopNotification()
                finish()
            }
            !transferring && transferFinished -> {
                finish()
            }
        }
    }

    @Deprecated("Deprecated")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!transferring) {
            stopNotification()
            finish()
        } else {
            super.onBackPressed()
        }
    }
}
