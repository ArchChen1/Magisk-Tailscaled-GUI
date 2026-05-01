package top.cenmin.tailcontrol.core.model

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class TailscaleDevice(
    val name: String,
    val rawHostName: String,
    val dnsName: String,
    val os: String,
    val userId: Long?,
    val ips: List<String>,
    val ipv4: String?,
    val ipv6: String?,
    val relay: String?,
    val lastSeen: String?,
    val rawLastSeen: String?,
    val online: Boolean,
    val active: Boolean,
    val primaryRoutes: List<String>,
    val allowedIps: List<String>,
    val exitNodeOption: Boolean,
    val isExitNode: Boolean,
    val rxBytes: Long?,
    val txBytes: Long?,
    val curAddr: String?,
    val isSelf: Boolean,
)

fun PeerJson.toDevice(isSelf: Boolean = false): TailscaleDevice {
    val raw = hostName.orEmpty().ifBlank { "Unknown" }
    val dns = dnsName.orEmpty()
    val displayName = if (dns.isNotBlank()) {
        dns.trimEnd('.').split(".").firstOrNull().orEmpty().ifBlank { raw }
    } else raw
    val ipList = tailscaleIps.orEmpty()
    val v4 = ipList.firstOrNull { it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) }
    val v6 = ipList.firstOrNull { it.contains(":") }
    val parsedLast = lastSeen
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching {
                OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }.getOrNull() ?: it
        }
    return TailscaleDevice(
        name = displayName,
        rawHostName = raw,
        dnsName = dns,
        os = os.orEmpty().ifBlank { "Unknown" },
        userId = userId,
        ips = ipList,
        ipv4 = v4,
        ipv6 = v6,
        relay = relay,
        lastSeen = parsedLast,
        rawLastSeen = lastSeen,
        online = online,
        active = active,
        primaryRoutes = primaryRoutes.orEmpty(),
        allowedIps = allowedIps.orEmpty(),
        exitNodeOption = exitNodeOption,
        isExitNode = exitNode,
        rxBytes = rxBytes,
        txBytes = txBytes,
        curAddr = curAddr,
        isSelf = isSelf,
    )
}
