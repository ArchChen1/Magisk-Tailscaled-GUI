package top.cenmin.tailcontrol.ui.screen.settings.subnet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
fun SubnetEditorScreen(
    onBack: () -> Unit,
    viewModel: SubnetViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subnet_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onBack) }) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::add) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.subnet_add))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(ui.rows) { idx, value ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { viewModel.update(idx, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = !viewModel.isValid(value),
                        placeholder = { Text("10.0.0.0/24") },
                        supportingText = {
                            if (!viewModel.isValid(value)) Text(stringResource(R.string.subnet_invalid))
                        },
                    )
                    IconButton(onClick = { viewModel.remove(idx) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.advertise_subnets_routes_descripe))
                }
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
