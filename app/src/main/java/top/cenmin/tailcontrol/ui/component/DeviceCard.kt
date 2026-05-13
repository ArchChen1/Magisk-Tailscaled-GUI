package top.cenmin.tailcontrol.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.core.model.TailscaleDevice
import top.cenmin.tailcontrol.ui.screen.peer.toLocalTime
import top.cenmin.tailcontrol.ui.theme.LocalStatusColors
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


fun String.toLocalTime(): String {
    return try {
        LocalDateTime.parse(trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    } catch (e: Exception) {
        "$this (UTC)"
    }
}
@Composable
fun DeviceCard(
    device: TailscaleDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalStatusColors.current
    val ip = device.ipv4 ?: device.ipv6
    val ipText = ip ?: "—"

    ElevatedCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(10.dp)) {
                drawCircle(if (device.online) statusColors.online else statusColors.offline)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("IP: $ipText", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(2.dp))
                    CopyIpButton(ip = ip)
                    Spacer(Modifier.width(8.dp))
                    Text("· ${device.os}", style = MaterialTheme.typography.bodyMedium)
                }
                if (!device.online && !device.lastSeen.isNullOrBlank()) {
                    Text(
                        "${stringResource(R.string.last_seen)}: ${device.lastSeen.toLocalTime()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}
