package top.cenmin.tailcontrol.service.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.BackendState
import javax.inject.Inject

@AndroidEntryPoint
class TailscaleTileService : TileService() {

    @Inject lateinit var tailRepo: TailscaleRepository

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val state = tailRepo.fetchStatus().backendState
            main.post { updateTile(state) }
        }
    }

    @Suppress("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        // 折叠快捷设置
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
            val state = tailRepo.fetchStatus().backendState
            val (toastRes, action) = when (state) {
                BackendState.Running -> R.string.status_service_stopped to suspend { tailRepo.down() }
                BackendState.Stopped, BackendState.Starting -> R.string.status_service_starting to suspend { tailRepo.up(); Unit }
                BackendState.NeedsLogin -> R.string.status_service_needslogin to suspend { /* no-op */ }
                else -> R.string.status_service_starting to suspend { tailRepo.daemonRestart(); tailRepo.up(); Unit }
            }
            main.post {
                Toast.makeText(applicationContext, "Tailscale: ${getString(toastRes)}", Toast.LENGTH_SHORT).show()
            }
            action()
            val newState = tailRepo.fetchStatus().backendState
            main.post { updateTile(newState) }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        main.post { updateTile(BackendState.Unknown) }
    }

    private fun updateTile(state: BackendState) {
        val tile = qsTile ?: return
        tile.label = "Tailscale"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tailscale)
        tile.state = if (state is BackendState.Running || state is BackendState.Starting)
            Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
