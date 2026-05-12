package top.cenmin.tailcontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
            )
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val cfg = prefs.dropConfig.first()
                if (cfg.enabled) {
                    val svc = Intent(context, DropProtectService::class.java).apply {
                        action = DropProtectService.ACTION_START
                        putExtra(DropProtectService.EXTRA_PATH, cfg.path)
                        putExtra(DropProtectService.EXTRA_BEHAVIOR, cfg.conflict.cliValue)
                    }
                    context.startForegroundService(svc)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
