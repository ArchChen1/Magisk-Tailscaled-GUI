package top.cenmin.tailcontrol.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.core.model.TailscaleDevice
import top.cenmin.tailcontrol.ui.component.DeviceCard
import top.cenmin.tailcontrol.ui.component.HealthBanner
import top.cenmin.tailcontrol.ui.component.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPeerClick: (TailscaleDevice) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val status = ui.status
    val self = status.self
    val isRunning = status.backendState is BackendState.Running
    val undismissedHealthCheck = viewModel.getUndismissedHealthCheck()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenAccounts) {
                        Icon(Icons.Filled.PeopleAlt, contentDescription = stringResource(R.string.nav_accounts))
                    }
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = stringResource(R.string.nav_logs))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.isRefreshing,
            onRefresh = viewModel::manualRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                if (undismissedHealthCheck.isNotEmpty()) {
                    HealthBanner(
                        healthCheck = undismissedHealthCheck,
                        onDismiss = viewModel::dismissAllHealthCheck,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                StatusCard(
                    state = status.backendState,
                    online = self?.online == true,
                    hostName = self?.name ?: stringResource(R.string.unknown),
                    ip = self?.ipv4 ?: self?.ipv6 ?: "—",
                    modifier = Modifier.padding(top = 12.dp),
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = viewModel::toggleTailscale,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (isRunning || status.backendState is BackendState.NeedsLogin)
                            stringResource(R.string.stop_tailscale)
                        else
                            stringResource(R.string.start_tailscale),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.device_list_autofresh, ui.countdownSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::manualRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(status.peers, key = { it.name + it.rawHostName }) { peer ->
                        DeviceCard(device = peer, onClick = { onPeerClick(peer) })
                    }
                }
            }
        }
    }
}
