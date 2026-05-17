package top.cenmin.tailcontrol.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import top.cenmin.tailcontrol.R

object Destinations {

    @Serializable data object Home
    @Serializable data object Drop
    @Serializable data object Settings
    @Serializable data object Accounts
    @Serializable data object Netcheck
    @Serializable data object Traffic
    @Serializable data object Logs
    @Serializable data object ExitNodePicker
    @Serializable data object SubnetEditor
    @Serializable data object Experimental
    @Serializable data object NavBarCustomizer
    @Serializable data class PeerDetail(val name: String)
}

enum class TopLevelDestination(
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    val showInBar: Boolean = true,
) {
    Home(Icons.Filled.Home, R.string.nav_home, true),
    Drop(Icons.AutoMirrored.Filled.Send, R.string.nav_drop, true),
    Netcheck(Icons.Filled.NetworkCheck, R.string.nav_netcheck, true),
    Traffic(Icons.Filled.Analytics, R.string.nav_traffic, true),
    Settings(Icons.Filled.Settings, R.string.nav_settings, true),
    Accounts(Icons.Filled.PeopleAlt, R.string.nav_accounts, false),
    Logs(Icons.AutoMirrored.Filled.Article, R.string.nav_logs, false),
}