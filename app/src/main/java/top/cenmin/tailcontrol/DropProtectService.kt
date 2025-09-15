package top.cenmin.tailcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import androidx.core.content.edit

class DropProtectService : Service() {


    companion object {
        const val CHANNEL_ID = "TAILCONTROL_DROP_PROTECT"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PATH = "EXTRA_PATH"
        const val EXTRA_BEHAVIOR = "EXTRA_BEHAVIOR"
        const val NOTIFICATION_ID = 3001
        const val TAG = "DropProtectService"
        const val PREFS_NAME = "drop_protect"
        const val KEY_PID = "fileGetPid"
    }

    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @SuppressLint("SdCardPath")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val path = intent?.getStringExtra(EXTRA_PATH) ?: "/sdcard/Download/TailDrop/"
        val behavior = intent?.getStringExtra(EXTRA_BEHAVIOR) ?: "rename"
        Log.d(TAG, "onStartCommand action=$action, path=$path, behavior=$behavior")

        when (action) {
            ACTION_START -> {
                // 检查路径是否改变
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val currentPath = prefs.getString("current_path", null)
                val currentBehavior = prefs.getString("current_behavior", "rename")

                // 如果路径或行为变了，重启 file get
                if (currentPath != path || currentBehavior != behavior) {
                    stopFileGet()
                    prefs.edit {
                        putString("current_path", path)
                            .putString("current_behavior", behavior)
                    }
                }
                startProtectLoop(path, behavior)
            }
            ACTION_STOP -> stopProtectLoop()
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val enabled = prefs.getBoolean("drop_enabled", false)
        return if (enabled) START_STICKY else START_NOT_STICKY
    }


    private fun startProtectLoop(path: String, behavior: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_drop_screen", true) // 告诉 MainActivity 打开 ADropScreen
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1️⃣ 立即显示启动中通知
        val startNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("TailControl Drop启动中…")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(NOTIFICATION_ID, startNotification)

        job = CoroutineScope(Dispatchers.IO).launch {
            // 2️⃣ 检查 Tailscale 状态
            val tsStatus = executeRootCommand("tailscale status")
            val status = when {
                tsStatus.contains("Health") || tsStatus.contains("100.") -> "服务运行中"
                else -> "未运行"
            }

            val displaystatus = when (status) {
                "服务运行中" -> getString(R.string.status_service_running)
                else -> getString(R.string.status_service_stopped)
            }
            DropOutput.outputFlow.value = "Tailscale ${getString(R.string.status)}: $displaystatus"

            if (status != "服务运行中") {
                DropOutput.outputFlow.value += "\n${getString(R.string.drop_stop_due_to_tail)}"
                stopFileGet()
                @Suppress("DEPRECATION")
                stopForeground(true) // 移除通知
                return@launch
            }

            // 3️⃣ 状态正常，更新通知为“保护运行中”
            val runningNotification = NotificationCompat.Builder(this@DropProtectService, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("TailControl Drop ${getString(R.string.Daemon_running)}")
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()

            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, runningNotification)
            // 第零步：检测当前启动了几个tailscale
            CoroutineScope(Dispatchers.IO).launch {
                var output = ""
                try {
                    output = Runtime.getRuntime()
                        .exec(arrayOf("su", "-c", "pgrep -x tailscale"))
                        .inputStream.bufferedReader().readText()
                } catch (_: Exception) {
                }

                val lines = output.lines().filter { it.isNotBlank() }
                val tooManyTailscaleRunning = lines.size >= 5
                if(tooManyTailscaleRunning){
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -9 -x tailscale")).waitFor()
                            Log.d("TailControl", "[drop] too many tailscale, kill all")
                        } catch (e: Exception) {
                            Log.e("TailControl", "[drop] kill tailscale failed: $e")
                        }
                    }
                }else{
                    Log.d("TailControl", "[drop] the number of tailscale is ok, num: ${lines.size/3}")
                }
            }
            // 4️⃣ 循环启动 file get
            while (isActive) {
                try {
                    startFileGet(path, behavior)
                } catch (e: Exception) {
                    DropOutput.outputFlow.value += "\nERROR: ${e.message}"
                    e.printStackTrace()
                }
                delay(3000)
            }
        }
    }

    /** 启动 file get，并持久化 PID */
    private fun startFileGet(path: String, behavior: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val pid = prefs.getInt(KEY_PID, -1)

        // 检查 PID 是否存在
        if (pid != -1 && isProcessAlive(pid)) {
            //Log.d(TAG, "DPS: file get 已经在运行，PID=$pid")
            return
        }

        try {
            // 第零步：检测当前启动了几个tailscale
            CoroutineScope(Dispatchers.IO).launch {
                var output = ""
                try {
                    output = Runtime.getRuntime()
                        .exec(arrayOf("su", "-c", "pgrep -x tailscale"))
                        .inputStream.bufferedReader().readText()
                } catch (_: Exception) {
                }

                val lines = output.lines().filter { it.isNotBlank() }
                val tooManyTailscaleRunning = lines.size >= 5
                if(tooManyTailscaleRunning){
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -9 -x tailscale"))
                                .waitFor()

                        } catch (e: Exception) {
                            Log.e("TailControl", "kill tailscale failed: $e")
                        }
                    }
                }
            }
            // 第一步：启动命令，拿到 ppid
            val startCmd = "nohup tailscale file get --conflict=$behavior --loop $path >/dev/null 2>&1 & echo $!"
            DropOutput.outputFlow.value += "\n${getString(R.string.execute)}: $startCmd"
            val ppidOutput = executeRootCommand(startCmd).trim()
            val ppid = ppidOutput.toIntOrNull()

            if (ppid == null) {
                DropOutput.outputFlow.value += "\n启动 file get 失败，无法获取 PPID"
                return
            }

            // 第二步：根据 ppid 找到实际子进程 PID
            val psOutput = executeRootCommand("ps -A | grep $ppid")

            val pidRegex = Regex("\\s*(\\d+)\\s+$ppid\\s+.*tailscale")
            val match = pidRegex.find(psOutput)

            val realPid = match?.groupValues?.get(1)?.toIntOrNull()

            if (realPid != null) {
                prefs.edit {
                    putInt(KEY_PID, realPid)
                        .putString("current_path", path) // 保存路径
                        .putString("current_behavior", behavior)
                }
                DropOutput.outputFlow.value += "\n" + getString(R.string.msg_file_get_success, ppid, realPid)
            } else {
                DropOutput.outputFlow.value += "\n" + getString(R.string.msg_file_get_success_but_pid_failed, ppid)
            }

        } catch (e: Exception) {
            DropOutput.outputFlow.value += "\n" + getString(R.string.msg_file_get_unexpected,  e.message)
            e.printStackTrace()
        }
    }


    /** 停止 file get 并清理 PID */
    private fun stopFileGet() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val pid = prefs.getInt(KEY_PID, -1)
        if (pid != -1) {
            executeRootCommand("kill $pid")
            prefs.edit { remove(KEY_PID) }
            DropOutput.outputFlow.value += "\n" + getString(R.string.msg_stop_file_get, pid)
        }
    }

    private fun stopProtectLoop() {
        job?.cancel()
        job = null
        stopFileGet()
        @Suppress("DEPRECATION")
        stopForeground(true)
    }

    /** 执行 root 命令并返回 stdout+stderr */
    private fun executeRootCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            (stdout + "\n" + stderr).trim()
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "执行异常"
        }
    }

    /** 判断 PID 是否存活 */
    private fun isProcessAlive(pid: Int): Boolean {
        return try {
            val output = executeRootCommand("kill -0 $pid")
            output.isEmpty() // kill -0 不输出，返回空说明存在
        } catch (e: Exception) {
            Log.e("TailControl", "$e")
            false
        }
    }
    @SuppressLint("SdCardPath")
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onTaskRemoved(rootIntent: Intent?) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // 👉 读取开关状态（默认 false）
        val enabled = prefs.getBoolean("drop_enabled", false)

        if (enabled) {
            val path = prefs.getString("current_path", "/sdcard/Download/TailDrop/")
            val behavior = prefs.getString("current_behavior", "rename")

            val restartServiceIntent = Intent(applicationContext, DropProtectService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PATH, path)
                putExtra(EXTRA_BEHAVIOR, behavior)
            }

            val restartServicePendingIntent = PendingIntent.getService(
                applicationContext,
                1,
                restartServiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000, // 延迟 1 秒重启
                restartServicePendingIntent
            )

            Log.d(TAG, "onTaskRemoved: 服务已被移除，将在 1 秒后重启")
        } else {
            Log.d(TAG, "onTaskRemoved: 开关关闭，不重启服务")
        }

        super.onTaskRemoved(rootIntent)
    }



    override fun onDestroy() {
        stopProtectLoop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TailControl ${getString(R.string.drop_label)}",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
