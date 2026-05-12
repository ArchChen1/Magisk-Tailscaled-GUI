package top.cenmin.tailcontrol.service.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.service.DropProtectService
import javax.inject.Inject

@AndroidEntryPoint
class DropTileService : TileService() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var tailRepo: TailscaleRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartListening() {
        scope.launch {
            val enabled = prefs.dropConfig.first().enabled
            withContext(Dispatchers.Main) { updateTile(enabled) }
        }
    }

    @Suppress("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = Intent(this, DummyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }

        scope.launch {
            val cfg = prefs.dropConfig.first()
            val target = !cfg.enabled
            if (target) {
                val state = tailRepo.fetchStatus().backendState
                if (state !is BackendState.Running) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DropTileService, "Tailscale ${getString(R.string.offline_now)}", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            }
            prefs.setDropEnabled(target)
            val svc = Intent(this@DropTileService, DropProtectService::class.java).apply {
                action = if (target) DropProtectService.ACTION_START else DropProtectService.ACTION_STOP
                if (target) {
                    putExtra(DropProtectService.EXTRA_PATH, cfg.path)
                    putExtra(DropProtectService.EXTRA_BEHAVIOR, cfg.conflict.cliValue)
                }
            }
            if (target) startForegroundService(svc) else startService(svc)
            withContext(Dispatchers.Main) {
                updateTile(target)
                Toast.makeText(
                    this@DropTileService,
                    getString(if (target) R.string.drop_service_started else R.string.drop_service_stopped),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun updateTile(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.drop_label)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
