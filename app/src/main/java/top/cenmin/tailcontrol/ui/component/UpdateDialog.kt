package top.cenmin.tailcontrol.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.network.UpdateChecker

@Composable
fun UpdateDialog(
    result: UpdateChecker.CheckUpdateResult?,
    onDismiss: () -> Unit,
    onOpenDownloadPage: () -> Unit
) {
    result?.let { updateResult ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(stringResource(R.string.update_found_title, updateResult.updateInfo.versionNumber))
            },
            text = {
                Column {
                    Text(stringResource(R.string.update_current_version, updateResult.currentVersion))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_release_notes),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateResult.updateInfo.body.take(300),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onOpenDownloadPage()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }
}