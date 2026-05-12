package top.cenmin.tailcontrol.ui.screen.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.model.AccountItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tailscale_accounts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ui.accounts, key = { it.id }) { item ->
                AccountCard(item) {
                    if (!item.isCurrent) {
                        Toast.makeText(context, context.getString(R.string.switching), Toast.LENGTH_SHORT).show()
                        viewModel.switchAccount(item.id)
                    }
                }
            }
            item {
                ActionCard(text = stringResource(R.string.admin_console)) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://tailscale.com/admin".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure {
                        Toast.makeText(context, R.string.unable_browser, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            item {
                val waiting = stringResource(R.string.waiting_link)
                ActionCard(text = stringResource(R.string.add_new_acc)) {
                    viewModel.startLoginWithSavedSettings(waiting)
                }
            }
            if (ui.accounts.any { it.isCurrent }) {
                item {
                    ActionCard(
                        text = stringResource(R.string.logout),
                        color = MaterialTheme.colorScheme.error,
                        onClick = viewModel::openLogoutDialog,
                    )
                }
            }
        }
    }

    if (ui.loginDialogOpen) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Tailscale ${stringResource(R.string.login)}") },
            text = {
                SelectionContainer { Text(ui.loginDialogText) }
            },
            confirmButton = {
                if (ui.loginSuccess) {
                    TextButton(onClick = viewModel::closeLoginDialog) { Text(stringResource(R.string.done)) }
                } else {
                    Row {
                        TextButton(onClick = {
                            if (ui.loginUrl.isNotEmpty()) {
                                copyAndOpen(context, ui.loginUrl)
                            }
                        }) { Text(stringResource(R.string.copy_open)) }
                        TextButton(onClick = viewModel::closeLoginDialog) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            },
        )
    }

    if (ui.logoutDialogOpen) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLogoutDialog,
            title = { Text(stringResource(R.string.confirm_sign_out)) },
            text = { Text(stringResource(R.string.confirm_sign_out_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLogout) { Text(stringResource(R.string.logout)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLogoutDialog) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun AccountCard(item: AccountItem, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !item.isCurrent, onClick = onClick),
        colors = if (item.isCurrent)
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.elevatedCardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.account, style = MaterialTheme.typography.titleMedium)
            if (item.isCurrent) {
                Text(stringResource(R.string.current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ActionCard(
    text: String,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(text, color = color, modifier = Modifier.padding(16.dp))
    }
}

private fun copyAndOpen(ctx: Context, url: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Tailscale Login", url))
    Toast.makeText(ctx, R.string.url_copied, Toast.LENGTH_SHORT).show()
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(ctx, "${ctx.getString(R.string.unable_browser)}: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}
