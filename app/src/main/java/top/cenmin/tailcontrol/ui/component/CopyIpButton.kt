package top.cenmin.tailcontrol.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.cenmin.tailcontrol.R

@Composable
fun CopyIpButton(
    ip: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toastTpl = stringResource(R.string.ip_copied)
    IconButton(
        onClick = { doCopy(context, ip, label = "Tailscale IP", toast = ip?.let { "$toastTpl $it" }) },
        enabled = !ip.isNullOrBlank(),
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(),
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = stringResource(R.string.copy),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun CopyTextButton(
    text: String?,
    label: String = "TailControl",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copiedToast = stringResource(R.string.url_copied)
    IconButton(
        onClick = { doCopy(context, text, label = label, toast = copiedToast) },
        enabled = !text.isNullOrBlank(),
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(),
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = stringResource(R.string.copy),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun doCopy(context: Context, value: String?, label: String, toast: String?) {
    if (value.isNullOrBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    if (!toast.isNullOrBlank()) {
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }
}
