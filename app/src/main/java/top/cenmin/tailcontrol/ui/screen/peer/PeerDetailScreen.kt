package top.cenmin.tailcontrol.ui.screen.peer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.ui.component.SpeedChart
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


fun String.toLocalTime(): String {
    return try {
        LocalDateTime.parse(trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    } catch (e: Exception) {
        "$this (UTC)"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerDetailScreen(
    peerName: String,
    onBack: () -> Unit,
    viewModel: PeerDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peerName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            val peer = ui.peer
            if (ui.notFound || peer == null) {
                Text(stringResource(R.string.unknown))
                return@Column
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(peer.name, style = MaterialTheme.typography.titleLarge)
                        Text("${stringResource(R.string.peer_os)}: ${peer.os}")
                        if (!peer.relay.isNullOrBlank())
                            Text("${stringResource(R.string.peer_relay)}: ${peer.relay}")
                        Text("${stringResource(R.string.peer_active)}: ${peer.active}")
                        Text("${stringResource(R.string.status)}: " + if (peer.online) stringResource(R.string.status_online) else stringResource(R.string.status_offline))
                        if (peer.exitNodeOption) Text(stringResource(R.string.peer_exit_node_option))
                        if (peer.isExitNode) Text(stringResource(R.string.peer_is_exit_node))
                        if (!peer.online && !peer.lastSeen.isNullOrBlank())
                            Text("${stringResource(R.string.last_seen)}: ${peer.lastSeen.toLocalTime()}")
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.peer_addresses), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        peer.ips.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            ui.whois?.let { w ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.peer_owner), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            if (!w.userName.isNullOrBlank())
                                Text("${stringResource(R.string.peer_user)}: ${w.userName}")
                            if (!w.machineName.isNullOrBlank())
                                Text("${stringResource(R.string.peer_machine)}: ${w.machineName}")
                            if (!w.machineId.isNullOrBlank())
                                Text("ID: ${w.machineId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // SSH 提示卡：让用户复制命令到 Termux 等
            viewModel.sshCommand?.let { cmd ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.peer_ssh_command), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(Modifier.weight(1f))
                            top.cenmin.tailcontrol.ui.component.CopyTextButton(text = cmd)
                        }
                        androidx.compose.material3.Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    cmd,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.peer_ssh_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (peer.primaryRoutes.isNotEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.peer_routes), style = MaterialTheme.typography.titleMedium)
                            peer.primaryRoutes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            if (peer.allowedIps.isNotEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.peer_allowed_ips), style = MaterialTheme.typography.titleMedium)
                            peer.allowedIps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            // Ping panel
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Ping", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = if (ui.pingActive) viewModel::stopPing else viewModel::startPing) {
                            Text(stringResource(if (ui.pingActive) R.string.peer_stop_ping else R.string.peer_start_ping))
                        }
                    }
                    if (ui.rttSamples.isNotEmpty()) {
                        SpeedChart(
                            rxValues = ui.rttSamples,
                            txValues = emptyList(),
                            rxLabel = "RTT (ms)",
                            txLabel = "",
                            height = 160.dp,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${stringResource(R.string.peer_ping_avg)}: ${"%.1f".format(viewModel.avgRtt)} ms")
                            Text("${stringResource(R.string.peer_ping_p95)}: ${"%.1f".format(viewModel.p95Rtt)} ms")
                            Text("${stringResource(R.string.peer_ping_count)}: ${ui.rttSamples.size}")
                        }
                    }
                    if (ui.pingLines.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().height(160.dp).verticalScroll(rememberScrollState())) {
                            Text(ui.pingLines.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
