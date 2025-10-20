package top.cenmin.tailcontrol

import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.io.BufferedReader
import java.io.InputStreamReader

class TailscaleTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        Thread {
            val serviceStatus = executeRootCommand("tailscale status")
            val newLabel: String
            if (serviceStatus.contains("Health", ignoreCase = true)) {
                // 已运行 → 停止
                executeRootCommand("tailscale down")
                newLabel = "Tailscale: 已停止"
            } else if (serviceStatus.contains("100.", ignoreCase = true)) {
                // 已运行 → 停止
                executeRootCommand("tailscale down")
                newLabel = "Tailscale: 已停止"
            } else if (serviceStatus.contains("is stopped", ignoreCase = true)) {
                // 未运行 → 启动并 up
                executeRootCommand("tailscale up")
                newLabel = "Tailscale: 已启动"
            } else if (serviceStatus.contains("failed", ignoreCase = true)) {
                executeRootCommand("tailscaled.service start && tailscale up")
                newLabel = "Tailscale: 已启动"
            } else {
                executeRootCommand("tailscaled.service restart && tailscale up")
                newLabel = "Tailscale: 尝试重启"
            }

            // 更新 Tile
            mainHandler.post {
                qsTile.label = newLabel
                qsTile.state = if (newLabel.contains("启动") || newLabel.contains("Health")) {
                    Tile.STATE_ACTIVE
                } else {
                    Tile.STATE_INACTIVE
                }
                qsTile.icon = Icon.createWithResource(this, R.drawable.ic_tailscale)
                qsTile.updateTile()
            }
        }.start()
    }
    private fun updateTileState() {
        Thread {
            val serviceStatus = executeRootCommand("tailscale status")
            val newLabel = "Tailcontrol"
            val newState: Int = if (serviceStatus.contains("Health", ignoreCase = true)) {

                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }

            mainHandler.post {
                qsTile.label = newLabel
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


    private fun executeRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            // 读标准输出
            BufferedReader(InputStreamReader(process.inputStream)).use { br ->
                var line = br.readLine()
                while (line != null) {
                    stdout.append(line).append('\n')
                    line = br.readLine()
                }
            }

            // 读错误输出
            BufferedReader(InputStreamReader(process.errorStream)).use { br ->
                var line = br.readLine()
                while (line != null) {
                    stderr.append(line).append('\n')
                    line = br.readLine()
                }
            }

            process.waitFor()

            // 如果 stdout 为空，就返回 stderr
            val result = (stdout.toString() + stderr.toString()).trim()
            result.ifEmpty { "【无输出】" }
        } catch (e: Exception) {
            "执行异常: ${e.message}"
        }
    }



}
