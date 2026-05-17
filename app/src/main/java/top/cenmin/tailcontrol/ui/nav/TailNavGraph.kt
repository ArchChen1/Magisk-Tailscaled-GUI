package top.cenmin.tailcontrol.ui.nav

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import top.cenmin.tailcontrol.ui.screen.settings.experimental.NavBarCustomizerScreen
import top.cenmin.tailcontrol.ui.screen.settings.experimental.NavBarCustomizerViewModel
import top.cenmin.tailcontrol.ui.screen.settings.subnet.SubnetEditorScreen
import top.cenmin.tailcontrol.ui.screen.traffic.TrafficScreen

@Composable
fun TailNavGraph(navController: NavHostController = rememberNavController()) {
    val navBarViewModel: NavBarCustomizerViewModel = hiltViewModel()
    val navBarUi by navBarViewModel.ui.collectAsStateWithLifecycle()

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentTop: TopLevelDestination? = TopLevelDestination.entries.firstOrNull { dest ->
        currentRoute?.contains(dest.routeKeyword()) == true
    }

    val barDestinations = TopLevelDestination.entries.filter { it.showInBar }

    data class NavItemAnimState(
        val targetVisible: Boolean,
        val animatedValue: Float,
        val shouldRender: Boolean,
    )

    val animatedStates: Map<TopLevelDestination, NavItemAnimState> =
        barDestinations.associateWith { dest ->
            val targetVisible = dest.name !in navBarUi.hiddenItems

            val animatedValue by animateFloatAsState(
                targetValue = if (targetVisible) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "nav_anim_${dest.name}",
            )

            val shouldRender = targetVisible || animatedValue > 0.01f

            NavItemAnimState(
                targetVisible = targetVisible,
                animatedValue = animatedValue,
                shouldRender = shouldRender,
            )
        }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            barDestinations.forEach { dest ->
                val state = animatedStates[dest] ?: return@forEach

                if (!state.shouldRender) return@forEach

                item(
                    selected = currentTop == dest,
                    onClick = { navController.navigateTopLevel(dest) },
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.8f + 0.2f * state.animatedValue
                        scaleY = 0.8f + 0.2f * state.animatedValue
                        alpha = state.animatedValue
                    },
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
                ExperimentalScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNavBarCustomizer = { navController.navigate(Destinations.NavBarCustomizer) },
                )
            }
            composable<Destinations.NavBarCustomizer> {
                NavBarCustomizerScreen(onBack = { navController.popBackStack() })
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
        TopLevelDestination.Home     -> Destinations.Home
        TopLevelDestination.Drop     -> Destinations.Drop
        TopLevelDestination.Settings -> Destinations.Settings
        TopLevelDestination.Accounts -> Destinations.Accounts
        TopLevelDestination.Netcheck -> Destinations.Netcheck
        TopLevelDestination.Traffic  -> Destinations.Traffic
        TopLevelDestination.Logs     -> Destinations.Logs
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun TopLevelDestination.routeKeyword(): String = when (this) {
    TopLevelDestination.Home     -> "Home"
    TopLevelDestination.Drop     -> "Drop"
    TopLevelDestination.Settings -> "Settings"
    TopLevelDestination.Accounts -> "Accounts"
    TopLevelDestination.Netcheck -> "Netcheck"
    TopLevelDestination.Traffic  -> "Traffic"
    TopLevelDestination.Logs     -> "Logs"
}