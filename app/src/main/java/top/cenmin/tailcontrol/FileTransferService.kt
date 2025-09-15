package top.cenmin.tailcontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.pm.PackageManager

class FileTransferService : Service() {

    companion object {
        const val CHANNEL_ID = "TAILSCALE_FILE_CHANNEL"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PROGRESS = "EXTRA_PROGRESS"
        const val EXTRA_CONTENT_TEXT = "EXTRA_CONTENT_TEXT"
    }

    private var isForegroundStarted = false
    private lateinit var builder: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Tailscale ${getString(R.string.file_transfer)}")
            .setProgress(100, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val contentText = intent.getStringExtra(EXTRA_CONTENT_TEXT) ?: getString(R.string.waiting)
                builder.setContentText(contentText).setProgress(100, progress, false)

                // 添加点击通知回到 FileShareActivity
                val intentToActivity = Intent(this, FileShareActivity::class.java)
                intentToActivity.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.getActivity(this, 0, intentToActivity, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                } else {
                    PendingIntent.getActivity(this, 0, intentToActivity, PendingIntent.FLAG_UPDATE_CURRENT)
                }
                builder.setContentIntent(pendingIntent)

                // Android 13+ 没权限时安全处理
                val canPostNotification = Build.VERSION.SDK_INT < 33 ||
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

                if (canPostNotification) {
                    val nm = NotificationManagerCompat.from(this)
                    if (!isForegroundStarted) {
                        startForeground(NOTIFICATION_ID, builder.build())
                        isForegroundStarted = true
                    } else {
                        nm.notify(NOTIFICATION_ID, builder.build())
                    }
                }

                // 完成 100%，允许滑掉通知
                if (progress >= 100 || contentText.contains("完成") || contentText.contains("complete")) {
                    // 完成时，不再调用 notify() 或 startForeground/stopForeground，如果没有权限
                    builder.setOngoing(false).setProgress(0, 0, false).setContentIntent(null)

                    if (canPostNotification) {
                        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    } else {
                        // 没权限，用 Toast 提示，保证不闪退
                        Toast.makeText(this, getString(R.string.transfer_complete), Toast.LENGTH_SHORT).show()
                    }

                    stopSelf()
                }
            }

            ACTION_STOP -> {
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tailscale ${getString(R.string.file_transfer)}",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
