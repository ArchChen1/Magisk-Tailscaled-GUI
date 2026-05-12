package top.cenmin.tailcontrol.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.share.FileShareActivity

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
                val text = intent.getStringExtra(EXTRA_CONTENT_TEXT) ?: getString(R.string.waiting)
                builder.setContentText(text).setProgress(100, progress, false)

                val openIntent = Intent(this, FileShareActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                builder.setContentIntent(
                    PendingIntent.getActivity(
                        this, 0, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    ),
                )

                val canPost = Build.VERSION.SDK_INT < 33 ||
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

                if (canPost) {
                    if (!isForegroundStarted) {
                        startForeground(NOTIFICATION_ID, builder.build())
                        isForegroundStarted = true
                    } else {
                        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
                    }
                }

                if (progress >= 100 || text.contains(getString(R.string.transfer_complete), true)) {
                    builder.setOngoing(false).setProgress(0, 0, false).setContentIntent(null)
                    if (canPost) {
                        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
                        @Suppress("DEPRECATION") stopForeground(true)
                    } else {
                        Toast.makeText(this, getString(R.string.transfer_complete), Toast.LENGTH_SHORT).show()
                    }
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                @Suppress("DEPRECATION") stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Tailscale ${getString(R.string.file_transfer)}",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
