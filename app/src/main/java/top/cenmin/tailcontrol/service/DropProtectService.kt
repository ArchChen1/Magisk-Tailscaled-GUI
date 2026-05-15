package top.cenmin.tailcontrol.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import top.cenmin.tailcontrol.MainActivity
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.data.DropRepository
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.core.model.ConflictBehavior
import javax.inject.Inject

@AndroidEntryPoint
class DropProtectService : Service() {

    companion object {
        const val CHANNEL_ID = "TAILCONTROL_DROP_PROTECT"
        const val NOTIFICATION_ID = 3001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PATH = "EXTRA_PATH"
        const val EXTRA_BEHAVIOR = "EXTRA_BEHAVIOR"
    }

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var dropRepo: DropRepository
    @Inject lateinit var tailRepo: TailscaleRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val path = intent.getStringExtra(EXTRA_PATH)
                val behavior = ConflictBehavior.fromCli(intent.getStringExtra(EXTRA_BEHAVIOR))
                startProtect(path, behavior)
            }
            ACTION_STOP -> stopProtect()
            else -> {
                // Restart from boot or system: read prefs.
                scope.launch {
                    val cfg = prefs.dropConfig.first()
                    if (cfg.enabled) startProtect(cfg.path, cfg.conflict)
                    else stopProtect()
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("SdCardPath")
    private fun startProtect(pathArg: String?, behavior: ConflictBehavior) {
        val path = pathArg ?: "/sdcard/Download/TailDrop/"
        // 检查目录是否存在
        if (!ensureDirectoryExists(path)) {
            dropRepo.appendLog(getString(R.string.drop_start_failed_dir_invalid, path))
            updateNotification(getString(R.string.drop_failed_dir_invalid))
            stopProtect()
            return
        }
        loopJob?.cancel()

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.loading)))

        loopJob = scope.launch {
            val status = tailRepo.fetchStatus()
            val running = status.backendState is BackendState.Running
            dropRepo.appendLog(getString(R.string.status) + ": " + getString(status.backendState.labelRes))
            if (!running) {
                dropRepo.appendLog(getString(R.string.drop_stop_due_to_tail))
                dropRepo.stopFileGet()
                @Suppress("DEPRECATION") stopForeground(true)
                return@launch
            }
            updateNotification(getString(R.string.Daemon_running))

            // Sanity: kill bloated tailscale forks
            val n = tailRepo.pgrepTailscaleCount()
            if (n >= 5) {
                tailRepo.killAllTailscale()
                Timber.d("[drop] too many tailscale forks (%d), pkill", n)
            }

            while (isActive) {
                runCatching {
                    val (storedPath, storedBehavior, pid) = prefs.loadDropRuntime()
                    val needRestart = storedPath != path || storedBehavior != behavior
                    if (needRestart) dropRepo.stopFileGet()
                    val alive = pid != null && pid > 0 && dropRepo.isProcessAlive(pid)
                    if (!alive) {
                        val realPid = dropRepo.startFileGet(path, behavior)
                        if (realPid != null) {
                            dropRepo.appendLog(
                                getString(R.string.msg_file_get_success, realPid, realPid),
                            )
                        } else {
                            dropRepo.appendLog(getString(R.string.msg_file_get_failed_no_ppid))
                        }
                    }
                }.onFailure {
                    dropRepo.appendLog(getString(R.string.msg_file_get_unexpected, it.message.orEmpty()))
                    Timber.e(it, "drop loop failure")
                }
                delay(3000)
            }
        }
    }

    private fun stopProtect() {
        loopJob?.cancel()
        loopJob = null
        scope.launch { dropRepo.stopFileGet() }
        @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 应用主任务被滑掉时，如果用户开启了 drop，让前台服务继续；否则直接结束。
        runBlocking {
            val enabled = prefs.dropConfig.first().enabled
            if (!enabled) stopProtect()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "TailControl ${getString(R.string.drop_label)}",
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_drop_screen", true)
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("TailControl Drop")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun ensureDirectoryExists(path: String): Boolean {
        return try {
            val dir = java.io.File(path)
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (created) {
                    dropRepo.appendLog(getString(R.string.dir_created, path))
                    Timber.d("Directory created: $path")
                    runBlocking {
                        delay(500)
                    }
                } else {
                    dropRepo.appendLog(getString(R.string.dir_create_failed, path))
                    Timber.e("Failed to create directory: $path")
                }
                created
            } else {
                true
            }
        } catch (e: SecurityException) {
            dropRepo.appendLog(getString(R.string.dir_create_permission_denied, path, e.message.orEmpty()))
            Timber.e(e, "Permission denied to create directory: $path")
            false
        } catch (e: Exception) {
            dropRepo.appendLog(getString(R.string.dir_create_error, path, e.message.orEmpty()))
            Timber.e(e, "Error creating directory: $path")
            false
        }
    }
}
