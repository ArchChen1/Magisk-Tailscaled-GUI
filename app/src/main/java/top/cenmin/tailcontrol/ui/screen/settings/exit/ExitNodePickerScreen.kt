package top.cenmin.tailcontrol.ui.screen.settings.exit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitNodePickerScreen(
    onBack: () -> Unit,
    viewModel: ExitNodeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exit_node_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.save(onBack) }) {
                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ui.suggestion?.let { sug ->
                item {
                    androidx.compose.material3.AssistChip(
                        onClick = viewModel::applySuggestion,
                        label = { Text(stringResource(R.string.exit_node_suggest_use, sug)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                ChoiceRow(
                    label = stringResource(R.string.exit_node_none),
                    selected = ui.selected.isBlank(),
                ) { viewModel.select("") }
            }
            items(ui.candidates, key = { it.name }) { peer ->
                val identity = peer.ipv4 ?: peer.name
                ChoiceRow(
                    label = "${peer.name} (${peer.os})",
                    sub = identity,
                    selected = ui.selected == identity || ui.selected == peer.name,
                ) { viewModel.select(identity) }
            }
        }
    }

    if (ui.saveError != null) {
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text(stringResource(R.string.save_failed)) },
            text = { Text(ui.saveError.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::consumeError) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun ChoiceRow(label: String, sub: String? = null, selected: Boolean, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.padding(start = 4.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
