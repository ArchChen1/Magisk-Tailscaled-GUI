package top.cenmin.tailcontrol.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.ui.theme.LocalStatusColors
import top.cenmin.tailcontrol.ui.theme.colorFor

@Composable
fun StatusCard(
    state: BackendState,
    online: Boolean,
    hostName: String,
    ip: String,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalStatusColors.current
    val effectiveTone = when {
        state == BackendState.Running && online -> top.cenmin.tailcontrol.core.model.StatusTone.ONLINE
        state == BackendState.Running -> top.cenmin.tailcontrol.core.model.StatusTone.WARNING
        else -> state.tone
    }
    val dotColor = statusColors.colorFor(effectiveTone)

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(14.dp)) { drawCircle(dotColor) }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val stateText = stringResource(state.labelRes)
                val connText = if (online) stringResource(R.string.status_online)
                else stringResource(R.string.status_offline)
                Text(
                    text = "${stringResource(R.string.status)}: $stateText · $connText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${stringResource(R.string.hostname)}: $hostName",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "IP: $ip",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(4.dp))
                    val copyTarget = ip.takeIf { it.isNotBlank() && it != "—" }
                    CopyIpButton(ip = copyTarget)
                }
            }
        }
    }
}
