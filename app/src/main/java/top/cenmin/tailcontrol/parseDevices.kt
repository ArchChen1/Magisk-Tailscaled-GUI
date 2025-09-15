@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package top.cenmin.tailcontrol

import android.util.Log
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// ----------------- 数据模型 -----------------
data class TailscaleDevice(
    val name: String,
    val os: String,
    val ips: List<String>,
    val ip: String?,
    val relay: String?,
    val lastSeen: String?,
    val online: Boolean,
    val primaryRoutes: List<String>?,
    val exitNodeOption: Boolean,
    val rawLastSeen: String?

)

// ----------------- JSON 设备解析 -----------------
fun parseDevicesFromJson(output: String): List<TailscaleDevice> {
    val devices = mutableListOf<TailscaleDevice>()
    val root = JSONObject(output)

    // 取 Peer 节点
    if (root.has("Peer")) {
        val peers = root.getJSONObject("Peer")
        for (key in peers.keys()) {
            val peer = peers.getJSONObject(key)
            val rawHostName = peer.optString("HostName", "Unknown")
            val dnsName = peer.optString("DNSName", "")

            // 如果 HostName 是 localhost，就解析 DNSName 的第一个片段
            val name = if (dnsName.isNotBlank()) {
                dnsName.trimEnd('.').split(".").firstOrNull() ?: "Unknown"
            } else {
                rawHostName
            }
            val os = peer.optString("OS", "Unknown")
            val ips = peer.optJSONArray("TailscaleIPs")?.let { arr ->
                List(arr.length()) { arr.getString(it) }
            } ?: emptyList()

            // 找第一个 IPv4
            val ipv4 = ips.firstOrNull { it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) } ?: "Unknown"
            val relay = peer.optString("Relay", null) ?: ""
            val rawLastSeen  = peer.optString("LastSeen", null)  ?: "Unknown"
            val lastSeen = rawLastSeen.takeIf { it.isNotBlank() }?.let {
                try {
                    // 解析 ISO8601
                    val odt = OffsetDateTime.parse(it)
                    // 格式化成你要的样子
                    odt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } catch (e: Exception) {
                    Log.e("FileShare", "[transfer] 刷新设备失败: ${e.message}")
                    it // 解析失败就返回原始值
                }
            } ?: "Unknown"
            val online = peer.optBoolean("Online", false)
            val primaryRoutes = peer.optJSONArray("PrimaryRoutes")?.let { arr ->
                List(arr.length()) { arr.getString(it) }
            }
            val exitNodeOption = peer.optBoolean("ExitNodeOption", false)

            devices.add(
                TailscaleDevice(
                    name = name,
                    os = os,
                    ips = ips,
                    ip = ipv4,
                    relay = relay,
                    lastSeen = lastSeen,
                    online = online,
                    primaryRoutes = primaryRoutes,
                    exitNodeOption = exitNodeOption,
                    rawLastSeen = rawLastSeen
                )
            )
        }
    }

    // 在线优先
    return devices.sortedWith(
        compareByDescending<TailscaleDevice> { it.online }
            .thenBy { it.name }
    )
}




// ----------------- Root 命令执行 -----------------
fun executeRootCommand(command: String): String {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        process.waitFor()
        (stdout + "\n" + stderr).trim()
    } catch (e: Exception) {
        e.toString()
    }
}
