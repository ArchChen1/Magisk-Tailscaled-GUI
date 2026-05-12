package top.cenmin.tailcontrol.ui.screen.drop

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.core.model.ConflictBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropScreen(viewModel: DropViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val daemonOutput by viewModel.daemonOutput.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.errorToast) {
        ui.errorToast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val path = uri.path?.replace("/tree/primary:", "/sdcard/") ?: return@rememberLauncherForActivityResult
            viewModel.setPath(path)
            Toast.makeText(context, "${context.getString(R.string.path_selected)}: $path", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tailscale Drop") }) },
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

            if (!ui.fileCommandSupported) {
                androidx.compose.material3.ElevatedCard(
                    Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.drop_unavailable_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.drop_unavailable_msg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // 1. Receive daemon
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.file_receiver_daemon),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = ui.config.enabled,
                            onCheckedChange = viewModel::setEnabled,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { pickFolder.launch(null) }, modifier = Modifier.widthIn(min = 100.dp)) {
                            Text(stringResource(R.string.select_path))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${stringResource(R.string.current_path)}: ${ui.config.path}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 2. Conflict behavior
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.conflict_behavior),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(Modifier.weight(1f))
                        ConflictDropdown(
                            current = ui.config.conflict,
                            onSelect = viewModel::setConflict,
                        )
                    }
                }
            }

            // 3. Ping
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Ping",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = ui.pingAddress,
                            onValueChange = viewModel::setPingAddress,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.ping_placeholder)) },
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = viewModel::startPing,
                            enabled = ui.tailscaleState is BackendState.Running,
                            modifier = Modifier.widthIn(min = 80.dp),
                        ) { Text(stringResource(R.string.test)) }
                    }
                }
            }

            // 4. Fix duplicate processes
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.fix_duplicate_file),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.weight(1f))
                    Button(onClick = viewModel::openKillDialog) {
                        Text(stringResource(R.string.execute))
                    }
                }
            }

            // 5. Daemon output
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("${stringResource(R.string.daemon_output)}:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(daemonOutput.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text(
                stringResource(R.string.tip),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }

    if (ui.pingDialogOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closePingDialog,
            title = { Text("Ping ${stringResource(R.string.test)}") },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(rememberScrollState()),
                ) { Text(ui.pingOutput) }
            },
            confirmButton = {
                Button(onClick = if (ui.isPinging) viewModel::stopPing else viewModel::closePingDialog) {
                    Text(if (ui.isPinging) stringResource(R.string.cancel) else stringResource(R.string.done))
                }
            },
        )
    }

    if (ui.killDialogOpen) {
        val (titleRes, textRes) = when {
            ui.processCount == 0 -> R.string.oops to R.string.no_tail_process
            ui.processCount in 1..2 -> R.string.kill_process to R.string.only_one_process
            else -> R.string.kill_process to R.string.multi_process
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissKillDialog,
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(textRes)) },
            confirmButton = {
                TextButton(
                    onClick = if (ui.processCount == 0) viewModel::dismissKillDialog else viewModel::confirmKillProcesses,
                ) { Text(stringResource(if (ui.processCount == 0) R.string.done else R.string.confirm)) }
            },
            dismissButton = if (ui.processCount > 0) {
                { TextButton(onClick = viewModel::dismissKillDialog) { Text(stringResource(R.string.cancel)) } }
            } else null,
        )
    }
}

@Composable
private fun ConflictDropdown(
    current: ConflictBehavior,
    onSelect: (ConflictBehavior) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        Button(onClick = { expanded = true }, modifier = Modifier.widthIn(min = 100.dp)) {
            Text(
                when (current) {
                    ConflictBehavior.Rename -> stringResource(R.string.rename)
                    ConflictBehavior.Skip -> stringResource(R.string.skip)
                    ConflictBehavior.Overwrite -> stringResource(R.string.overwrite)
                },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ConflictBehavior.entries.forEach { value ->
                val label = when (value) {
                    ConflictBehavior.Rename -> stringResource(R.string.rename)
                    ConflictBehavior.Skip -> stringResource(R.string.skip)
                    ConflictBehavior.Overwrite -> stringResource(R.string.overwrite)
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                        Toast.makeText(
                            context,
                            "${context.getString(R.string.conflict_behavior_set_to)}: $label",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}
