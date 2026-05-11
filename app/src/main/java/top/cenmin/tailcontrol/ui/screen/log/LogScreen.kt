package top.cenmin.tailcontrol.ui.screen.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.log.LogArchive
import top.cenmin.tailcontrol.core.log.LogSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(ui.lines.size, ui.paused, ui.isLive) {
        if (ui.isLive && !ui.paused && ui.lines.isNotEmpty()) {
            listState.animateScrollToItem(ui.lines.lastIndex.coerceAtLeast(0))
        }
    }

    val clearedOk = stringResource(R.string.log_clear_done)
    val noRoot = stringResource(R.string.log_clear_no_root)
    val clearFailed = stringResource(R.string.log_clear_failed)
    val tooLarge = stringResource(R.string.log_archive_too_large)
    val readFailed = stringResource(R.string.log_read_failed)
    LaunchedEffect(ui.transient) {
        val msg = when (ui.transient) {
            TransientKind.ClearedOk -> clearedOk
            TransientKind.NoRoot -> noRoot
            TransientKind.ClearFailed -> clearFailed
            TransientKind.ArchiveTooLarge -> tooLarge
            TransientKind.ReadFailed -> readFailed
            null -> null
        }
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissTransient()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_logs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (ui.isLive) {
                        IconButton(onClick = viewModel::togglePause) {
                            Icon(
                                if (ui.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = stringResource(if (ui.paused) R.string.log_resume else R.string.log_pause),
                            )
                        }
                    }
                    IconButton(onClick = viewModel::requestClear) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.log_clear))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ui.source == LogSource.App,
                    onClick = { viewModel.switchSource(LogSource.App) },
                    label = { Text(stringResource(R.string.log_source_app)) },
                )
                FilterChip(
                    selected = ui.source == LogSource.Tailscaled,
                    onClick = { viewModel.switchSource(LogSource.Tailscaled) },
                    label = { Text(stringResource(R.string.log_source_tailscaled)) },
                )
                FilterChip(
                    selected = ui.source == LogSource.Runs,
                    onClick = { viewModel.switchSource(LogSource.Runs) },
                    label = { Text(stringResource(R.string.log_source_runs)) },
                )
            }
            ArchivePicker(
                archives = ui.archives,
                selectedId = ui.selectedArchiveId,
                onSelect = viewModel::selectArchive,
            )
            if (ui.isLive && ui.initialLines < 5000) {
                TextButton(onClick = viewModel::loadEarlier) {
                    Text(stringResource(R.string.log_load_earlier))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(ui.lines) { line ->
                    Text(
                        line,
                        color = colorFor(line),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }

    if (ui.confirmClear) {
        val targetingArchive = !ui.isLive
        AlertDialog(
            onDismissRequest = viewModel::dismissClear,
            title = {
                Text(
                    stringResource(
                        if (targetingArchive) R.string.log_archive_delete_confirm_title
                        else R.string.log_clear_confirm_title
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (targetingArchive) R.string.log_archive_delete_confirm_msg
                        else R.string.log_clear_confirm_msg
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClear) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClear) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ArchivePicker(
    archives: List<LogArchive>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    if (archives.size <= 1) return // 只有 Live，没必要给下拉
    var expanded by remember { mutableStateOf(false) }
    val selected = archives.firstOrNull { it.id == selectedId } ?: archives.first()
    val liveLabel = stringResource(R.string.log_archive_live)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.log_archive_select),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(if (selected.isCurrent) liveLabel else selected.label)
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            archives.forEach { item ->
                DropdownMenuItem(
                    text = { Text(if (item.isCurrent) liveLabel else item.label) },
                    onClick = {
                        expanded = false
                        onSelect(item.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun colorFor(line: String): Color = when {
    line.contains("[Error]", ignoreCase = true) || line.contains("error", ignoreCase = true) ->
        MaterialTheme.colorScheme.error
    line.contains("[Warning]", ignoreCase = true) || line.contains("warn", ignoreCase = true) ->
        MaterialTheme.colorScheme.tertiary
    line.contains("[Success]", ignoreCase = true) ->
        top.cenmin.tailcontrol.ui.theme.LocalStatusColors.current.online
    else -> MaterialTheme.colorScheme.onSurface
}

