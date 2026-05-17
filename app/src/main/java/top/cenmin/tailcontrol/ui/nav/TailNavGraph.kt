package top.cenmin.tailcontrol.ui.nav

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import top.cenmin.tailcontrol.ui.screen.accounts.AccountsScreen
import top.cenmin.tailcontrol.ui.screen.drop.DropScreen
import top.cenmin.tailcontrol.ui.screen.home.HomeScreen
import top.cenmin.tailcontrol.ui.screen.log.LogScreen
import top.cenmin.tailcontrol.ui.screen.netcheck.NetcheckScreen
import top.cenmin.tailcontrol.ui.screen.peer.PeerDetailScreen
import top.cenmin.tailcontrol.ui.screen.settings.SettingsScreen
import top.cenmin.tailcontrol.ui.screen.settings.exit.ExitNodePickerScreen
import top.cenmin.tailcontrol.ui.screen.settings.experimental.ExperimentalScreen
import top.cenmin.tailcontrol.ui.screen.settings.subnet.SubnetEditorScreen
import top.cenmin.tailcontrol.ui.screen.traffic.TrafficScreen

@Composable
fun TailNavGraph(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val currentTop: TopLevelDestination? = TopLevelDestination.entries.firstOrNull { dest ->
        currentRoute?.contains(dest.routeKeyword()) == true
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.filter { it.showInBar }.forEach { dest ->
                item(
                    selected = currentTop == dest,
                    onClick = { navController.navigateTopLevel(dest) },
                    icon = { Icon(dest.icon, contentDescription = null) },
                    label = { Text(stringResource(dest.labelRes)) },
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Destinations.Home,
        ) {
            composable<Destinations.Home> {
                HomeScreen(
                    onPeerClick = { device -> navController.navigate(Destinations.PeerDetail(device.name)) },
                    onOpenAccounts = { navController.navigate(Destinations.Accounts) },
                    onOpenLogs = { navController.navigate(Destinations.Logs) },
                )
            }
            composable<Destinations.Drop> { DropScreen() }
            composable<Destinations.Settings> {
                SettingsScreen(
                    onOpenAccounts = { navController.navigate(Destinations.Accounts) },
                    onOpenExitNode = { navController.navigate(Destinations.ExitNodePicker) },
                    onOpenSubnet = { navController.navigate(Destinations.SubnetEditor) },
                    onOpenExperimental = { navController.navigate(Destinations.Experimental) },
                    onOpenLogs = { navController.navigate(Destinations.Logs) },
                )
            }
            composable<Destinations.Accounts> {
                AccountsScreen(onBack = { navController.popBackStack() })
            }
            composable<Destinations.Netcheck> { NetcheckScreen() }
            composable<Destinations.Traffic> { TrafficScreen() }
            composable<Destinations.Logs> {
                LogScreen(onBack = { navController.popBackStack() })
            }
            composable<Destinations.ExitNodePicker> {
                ExitNodePickerScreen(onBack = { navController.popBackStack() })
            }
            composable<Destinations.SubnetEditor> {
                SubnetEditorScreen(onBack = { navController.popBackStack() })
            }
            composable<Destinations.Experimental> {
                ExperimentalScreen(onBack = { navController.popBackStack() })
            }
            composable<Destinations.PeerDetail> { entry ->
                val args = entry.toRoute<Destinations.PeerDetail>()
                PeerDetailScreen(
                    peerName = args.name,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(dest: TopLevelDestination) {
    val route = when (dest) {
        TopLevelDestination.Home -> Destinations.Home
        TopLevelDestination.Drop -> Destinations.Drop
        TopLevelDestination.Settings -> Destinations.Settings
        TopLevelDestination.Accounts -> Destinations.Accounts
        TopLevelDestination.Netcheck -> Destinations.Netcheck
        TopLevelDestination.Traffic -> Destinations.Traffic
        TopLevelDestination.Logs -> Destinations.Logs
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun TopLevelDestination.routeKeyword(): String = when (this) {
    TopLevelDestination.Home -> "Home"
    TopLevelDestination.Drop -> "Drop"
    TopLevelDestination.Settings -> "Settings"
    TopLevelDestination.Accounts -> "Accounts"
    TopLevelDestination.Netcheck -> "Netcheck"
    TopLevelDestination.Traffic -> "Traffic"
    TopLevelDestination.Logs -> "Logs"
}