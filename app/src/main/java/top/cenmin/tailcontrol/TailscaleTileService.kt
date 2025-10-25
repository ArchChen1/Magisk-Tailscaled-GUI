package top.cenmin.tailcontrol

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class TailscaleTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()

        updateTileState()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {

        // 关闭控制中心
        val intent = Intent(this, DummyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ 官方推荐写法
            startActivityAndCollapse(pendingIntent)
        } else {
            // Android 13 及以下仍可使用旧方法
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }

        Thread {
            val output = executeRootCommand("tailscale status --json")
            val backendState = parseBackendState(output)

            val toastContent: String
            val command: String

            when (backendState) {
                "Running" -> {
                    command = "tailscale down"
                    toastContent = "Tailscale: ${getString(R.string.status_service_stopped)}"
                }
                "Stopped" -> {
                    command = "tailscale up"
                    toastContent = "Tailscale: ${getString(R.string.status_service_starting)}"
                }
                "Starting" -> {
                    command = "tailscale up"
                    toastContent = "Tailscale: ${getString(R.string.status_service_starting)}"
                }
                "NeedsLogin" -> {
                    command = ""
                    toastContent = "Tailscale: ${getString(R.string.status_service_needslogin)}"
                }
                else -> {
                    command = "tailscaled.service restart && tailscale up"
                    toastContent = "Tailscale: ${getString(R.string.status_service_starting)}"
                }
            }

            // 更新 Tile
            mainHandler.post {
                qsTile.state = if (backendState == "Running" || backendState == "Starting") {
                    Tile.STATE_ACTIVE
                } else {
                    Tile.STATE_INACTIVE
                }
                qsTile.icon = Icon.createWithResource(this, R.drawable.ic_tailscale)
                qsTile.updateTile()

                // Toast 反馈
                Toast.makeText(applicationContext, toastContent, Toast.LENGTH_SHORT).show()
            }

            // 执行命令
            executeRootCommand(command)

        }.start()
    }

    private fun updateTileState() {
        Thread {
            val output = executeRootCommand("tailscale status --json")
            val backendState = parseBackendState(output)
            val newState = if (backendState == "Running" || backendState == "Starting") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

            mainHandler.post {
                qsTile.state = newState
                qsTile.icon = Icon.createWithResource(this, R.drawable.ic_tailscale)
                qsTile.updateTile()
            }
        }.start()
    }

    override fun onTileAdded() {
        super.onTileAdded()

        mainHandler.post {
            qsTile.label = "Tailscale"
            qsTile.icon = Icon.createWithResource(this, R.drawable.ic_tailscale)
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.updateTile()
        }
    }

    private fun parseBackendState(jsonStr: String): String {
        return try {
            if (jsonStr.contains("failed to")) {
                "Stopped"
            } else {
                JSONObject(jsonStr).optString("BackendState", "未知")
            }
        } catch (e: Exception) {
            Log.e("TailscaleTileService", "解析 BackendState 出错: $e")
            "未知"
        }
    }

    private fun executeRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            BufferedReader(InputStreamReader(process.inputStream)).use { br ->
                var line = br.readLine()
                while (line != null) {
                    stdout.append(line).append('\n')
                    line = br.readLine()
                }
            }

            BufferedReader(InputStreamReader(process.errorStream)).use { br ->
                var line = br.readLine()
                while (line != null) {
                    stderr.append(line).append('\n')
                    line = br.readLine()
                }
            }

            process.waitFor()
            val result = (stdout.toString() + stderr.toString()).trim()
            result.ifEmpty { "【无输出】" }
        } catch (e: Exception) {
            "执行异常: ${e.message}"
        }
    }
}
