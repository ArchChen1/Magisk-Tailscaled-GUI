package top.cenmin.tailcontrol

import android.annotation.SuppressLint
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

class DropTileService : TileService(), CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + job

    private val prefs by lazy { getSharedPreferences("drop_prefs", MODE_PRIVATE) }

    override fun onStartListening() = updateTileState()

    @SuppressLint("SdCardPath")
    override fun onClick() {
        launch {
            val enabled = prefs.getBoolean("drop_enabled", false)

            // 1. 想打开 → 必须验证
            if (!enabled) {
                val (backendOk, selfOk) = checkTailscaleStatus()
                if (!(backendOk && selfOk)) {
                    Toast.makeText(this@DropTileService,
                        "Tailscale ${getString(R.string.offline_now)}", Toast.LENGTH_SHORT).show()
                    return@launch        // 验证失败，什么都不做
                }
            }

            // 2. 通过验证 or 原本就是开 → 直接翻转
            val newEnabled = !enabled
            prefs.edit { putBoolean("drop_enabled", newEnabled) }

            val path  = prefs.getString("drop_path", "/sdcard/Download/TailDrop/")!!
            val behavior = prefs.getString("conflict_behavior", "rename")!!

            val intent = Intent(this@DropTileService, DropProtectService::class.java).apply {
                action = if (newEnabled) DropProtectService.ACTION_START
                else DropProtectService.ACTION_STOP
                if (newEnabled) {
                    putExtra(DropProtectService.EXTRA_PATH, path)
                    putExtra(DropProtectService.EXTRA_BEHAVIOR, behavior)
                }
            }
            if (newEnabled) startForegroundService(intent)
            else startService(intent)

            updateTileState()
            collapseQuickSettings()
        }
    }
    private fun collapseQuickSettings() {
        try {
            val statusBar = getSystemService("statusbar")
            val method = statusBar?.javaClass?.getMethod("collapsePanels")
            method?.invoke(statusBar)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    /* 返回 Pair：first = BackendState 是否 Running，second = Self 是否 Online */
    private suspend fun checkTailscaleStatus(): Pair<Boolean, Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "tailscale status --json"))
                    .inputStream.bufferedReader().readText()

                val root = JSONObject(json)
                val backendOk = root.optString("BackendState") == "Running"
                val selfOk = root.optJSONObject("Self")?.optBoolean("Online") == true
                backendOk to selfOk
            }.getOrElse {
                Log.d("TailControl", "checkTailscaleStatus: $it")
                false to false
            }
        }

    private fun updateTileState() {
        val enabled = prefs.getBoolean("drop_enabled", false)
        qsTile?.let { tile ->
            tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.drop_label)
            tile.updateTile()
        }
    }
}