package top.cenmin.tailcontrol.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccounts: () -> Unit,
    onOpenExitNode: () -> Unit,
    onOpenSubnet: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tailscale_settings)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::save) {
                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
            }
        },
        floatingActionButtonPosition = FabPosition.End,
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

            // User info card
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${stringResource(R.string.user)}:",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Spacer(Modifier.height(0.dp))
                            Text(
                                text = ui.username.ifBlank { stringResource(if (ui.isLoggedIn) R.string.unknown else R.string.status_service_needslogin) },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp).horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                    Button(onClick = onOpenAccounts) { Text(stringResource(R.string.manage)) }
                }
            }

            // Toggle settings
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    CheckRow(
                        label = "${stringResource(R.string.subnet)} (--accept-routes)",
                        checked = ui.settings.acceptRoutes,
                    ) { v -> viewModel.update { it.copy(acceptRoutes = v) } }
                    CheckRow(
                        label = "${stringResource(R.string.accept_dns)} (--accept-dns)",
                        checked = ui.settings.acceptDns,
                    ) { v -> viewModel.update { it.copy(acceptDns = v) } }
                    CheckRow(
                        label = "${stringResource(R.string.advertise_exit_node)} (--advertise-exit-node)",
                        checked = ui.settings.advertiseExitNode,
                    ) { v -> viewModel.update { it.copy(advertiseExitNode = v) } }
                }
            }

            // Pickers
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column {
                    PickerRow(
                        label = stringResource(R.string.exit_node),
                        valuePreview = ui.settings.exitNode.ifBlank { stringResource(R.string.exit_node_none) },
                        onClick = onOpenExitNode,
                    )
                    PickerRow(
                        label = stringResource(R.string.advertise_subnets_routes),
                        valuePreview = ui.settings.advertiseRoutes.ifBlank { stringResource(R.string.none) },
                        onClick = onOpenSubnet,
                    )
                }
            }

            // Free-form fields
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ui.settings.customName,
                        onValueChange = { v -> viewModel.update { it.copy(customName = v) } },
                        label = { Text("${stringResource(R.string.hostname_set)} (--hostname)") },
                        placeholder = { Text(stringResource(R.string.hostname_set_descripe)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.settings.customParams,
                        onValueChange = { v -> viewModel.update { it.copy(customParams = v) } },
                        label = { Text(stringResource(R.string.custom_parameters)) },
                        placeholder = { Text("--webclient --update-check=false") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // SSH server (advertise self as Tailscale SSH server)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.advertise_ssh), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.advertise_ssh_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = ui.sshServerEnabled,
                        onCheckedChange = viewModel::setSshServer,
                        enabled = !ui.sshUpdating,
                    )
                }
            }

            // DNS status (read-only summary)
            ui.dnsStatus?.let { dns ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("DNS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "${stringResource(R.string.dns_tailscale_dns)}: " +
                                if (dns.tailscaleDnsEnabled) stringResource(R.string.dns_enabled) else stringResource(R.string.dns_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "MagicDNS: " +
                                if (dns.magicDnsEnabled) stringResource(R.string.dns_enabled) else stringResource(R.string.dns_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!dns.magicDnsSuffix.isNullOrBlank())
                            Text("Suffix: ${dns.magicDnsSuffix}", style = MaterialTheme.typography.bodySmall)
                        if (!dns.deviceDnsName.isNullOrBlank())
                            Text("This device: ${dns.deviceDnsName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Logs shortcut
            ElevatedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.nav_logs)) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLogs),
                )
            }

            // Binary paths (which tailscale / tailscaled / tailscaled.service actually run)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.binary_path_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        stringResource(R.string.binary_path_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    val missing = stringResource(R.string.binary_path_missing)
                    BinaryRow("tailscale", ui.binaries?.tailscale ?: missing)
                    BinaryRow("tailscaled", ui.binaries?.tailscaled ?: missing)
                    BinaryRow("tailscaled.service", ui.binaries?.service ?: missing)
                }
            }

            // Dynamic color
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.dynamic_color_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = ui.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (ui.saveError != null) {
        AlertDialog(
            onDismissRequest = viewModel::consumeSaveError,
            title = { Text(stringResource(R.string.save_failed)) },
            text = { Text("${stringResource(R.string.save_failed_text)}:\n${ui.saveError}") },
            confirmButton = {
                TextButton(onClick = viewModel::consumeSaveError) { Text(stringResource(R.string.done)) }
            },
        )
    }
    if (ui.saveSuccess) {
        AlertDialog(
            onDismissRequest = viewModel::consumeSaveSuccess,
            title = { Text(stringResource(R.string.save_success)) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeSaveSuccess) { Text(stringResource(R.string.done)) }
            },
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BinaryRow(name: String, path: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$name:",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            path,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun PickerRow(label: String, valuePreview: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                valuePreview,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
