package top.cenmin.tailcontrol.ui.screen.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.data.LogSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(ui.lines.size, ui.paused) {
        if (!ui.paused && ui.lines.isNotEmpty()) {
            listState.animateScrollToItem(ui.lines.lastIndex.coerceAtLeast(0))
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
                    IconButton(onClick = viewModel::togglePause) {
                        Icon(
                            if (ui.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = stringResource(if (ui.paused) R.string.log_resume else R.string.log_pause),
                        )
                    }
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.log_clear))
                    }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
