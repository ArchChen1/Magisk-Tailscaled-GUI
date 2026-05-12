package top.cenmin.tailcontrol

import android.Manifest
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.ui.nav.TailNavGraph
import top.cenmin.tailcontrol.ui.theme.TailControlTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: PreferencesRepository

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!Shell.getShell().isRoot) {
            AlertDialog.Builder(this)
                .setTitle(R.string.get_root_failed_title)
                .setMessage(R.string.get_root_failed_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.exit) { _, _ -> finish() }
                .show()
            return
        }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val dynamic by prefs.dynamicColorEnabled.collectAsState(initial = true)
            TailControlTheme(dynamicColor = dynamic) {
                TailNavGraph()
            }
        }
    }
}
