package top.cenmin.tailcontrol.core.data

import kotlinx.coroutines.flow.Flow
import top.cenmin.tailcontrol.core.model.AccountItem
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.core.model.DnsStatus
import top.cenmin.tailcontrol.core.model.TailscaleDevice
import top.cenmin.tailcontrol.core.model.TailscaleJson
import top.cenmin.tailcontrol.core.model.TailscaleStatus
import top.cenmin.tailcontrol.core.model.TailscaleStatusJson
import top.cenmin.tailcontrol.core.model.UserJson
import top.cenmin.tailcontrol.core.model.WhoisInfo
import top.cenmin.tailcontrol.core.model.toDevice
import top.cenmin.tailcontrol.core.shell.CommandResult
import top.cenmin.tailcontrol.core.shell.RootShell
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TailscaleRepository @Inject constructor(
    private val shell: RootShell,
) {

    suspend fun fetchStatus(): TailscaleStatus {
        val result = shell.exec("tailscale status --json")
        val raw = result.text
        if (!result.ok && (raw.isBlank() || raw.contains("failed to") || raw.contains("connect"))) {
            return TailscaleStatus(
                backendState = BackendState.DaemonOffline,
                rawJson = raw,
            )
        }
        return decodeStatus(raw)
    }

    fun decodeStatus(raw: String): TailscaleStatus {
        if (raw.isBlank()) return TailscaleStatus(backendState = BackendState.DaemonOffline, rawJson = raw)
        if (raw.contains("failed to connect", ignoreCase = true)) {
            return TailscaleStatus(backendState = BackendState.DaemonOffline, rawJson = raw)
        }
        return runCatching {
            val parsed = TailscaleJson.decodeFromString(TailscaleStatusJson.serializer(), raw)
            val state = BackendState.fromCli(parsed.backendState)
            val self = parsed.self?.toDevice(isSelf = true)
            val peers = parsed.peer?.values
                ?.map { it.toDevice(isSelf = false) }
                ?.sortedWith(
                    compareByDescending<TailscaleDevice> { it.online }
                        .thenByDescending { it.rawLastSeen ?: "" }
                        .thenBy { it.name.lowercase() }
                ).orEmpty()
            val users = parsed.user?.entries?.associate { (k, v) -> (k.toLongOrNull() ?: -1L) to v }.orEmpty()
            TailscaleStatus(
                backendState = state,
                self = self,
                peers = peers,
                users = users,
                rawJson = raw,
            )
        }.getOrElse {
            TailscaleStatus(backendState = BackendState.Unknown, rawJson = raw)
        }
    }

    suspend fun up(args: String = ""): CommandResult =
        shell.exec("tailscale up ${args.trim()}".trim())

    suspend fun down(): CommandResult = shell.exec("tailscale down")

    suspend fun set(args: String): CommandResult = shell.exec("tailscale set ${args.trim()}".trim())

    suspend fun daemonStart(): CommandResult = shell.exec("tailscaled.service start")
    suspend fun daemonStop(): CommandResult = shell.exec("tailscaled.service stop")
    suspend fun daemonRestart(): CommandResult = shell.exec("tailscaled.service restart")

    suspend fun listAccounts(): List<AccountItem> {
        val raw = shell.execText("tailscale switch --list")
        return raw.lineSequence().drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 3) return@mapNotNull null
            val raw3 = parts[2]
            AccountItem(
                id = parts[0],
                account = raw3.removeSuffix("*"),
                isCurrent = raw3.endsWith("*"),
            )
        }.toList()
    }

    suspend fun switchAccount(id: String): CommandResult = shell.exec("tailscale switch $id")

    suspend fun logout(): CommandResult = shell.exec("tailscale logout")

    /** 流式 login，每行 stderr 都会 emit；调用方负责解析 https:// 链接。 */
    fun login(args: String): Flow<String> = shell.stream("tailscale login ${args.trim()}".trim())

    /**
     * 实时 ping。tailscale ping 默认在拿到直连后自动停止；
     * 加 -c 30 限定最大 30 次，避免一直走 DERP 中继时无止境地刷。
     */
    fun ping(target: String): Flow<String> =
        shell.stream("tailscale ping --timeout=2s -c 30 $target")

    /** 实时 netcheck。 */
    fun netcheck(): Flow<String> = shell.stream("tailscale netcheck")

    suspend fun pidofTailscaled(): Int? =
        shell.execText("pidof tailscaled").trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()

    suspend fun killAllTailscale(): CommandResult = shell.exec("pkill -9 -x tailscale")

    suspend fun pgrepTailscaleCount(): Int {
        val out = shell.execText("pgrep -x tailscale")
        return out.lineSequence().count { it.isNotBlank() }
    }

    /** 探测当前 tailscale 是否还支持 file 子命令（1.90+ 已删除）。 */
    suspend fun isFileCommandSupported(): Boolean {
        val r = shell.exec("tailscale file --help")
        // 不存在时 stderr 含 "unknown subcommand: file"
        return r.text.contains("unknown subcommand: file").not()
    }

    suspend fun exitNodeSuggest(): String? {
        val text = shell.execText("tailscale exit-node suggest")
        // 输出: "Suggested exit node: hostname.qilin-char.ts.net."
        val m = Regex("Suggested exit node:\\s*([^.\\s]+(?:\\.[^.\\s]+)*?)\\.?\\s*$", RegexOption.MULTILINE).find(text)
        return m?.groupValues?.get(1)?.trim()
    }

    suspend fun whois(ip: String): WhoisInfo? {
        if (ip.isBlank()) return null
        val text = shell.execText("tailscale whois $ip")
        if (text.isBlank() || text.contains("not found", ignoreCase = true)) return null
        // 解析两块: Machine / User
        val mName = Regex("Machine:\\s*\\n\\s*Name:\\s*(\\S+)").find(text)?.groupValues?.get(1)
        val mId = Regex("Machine:[\\s\\S]*?ID:\\s*(\\S+)").find(text)?.groupValues?.get(1)
        val mAddr = Regex("Addresses:\\s*\\[([^\\]]+)\\]").find(text)?.groupValues?.get(1)
        val uName = Regex("User:\\s*\\n\\s*Name:\\s*(\\S+)").find(text)?.groupValues?.get(1)
        val uId = Regex("User:[\\s\\S]*?ID:\\s*(\\S+)").find(text)?.groupValues?.get(1)
        if (mName == null && uName == null) return null
        return WhoisInfo(mName, mId, mAddr, uName, uId)
    }

    suspend fun dnsStatus(): DnsStatus {
        val text = shell.execText("tailscale dns status")
        if (text.isBlank()) return DnsStatus()
        val tsDnsEnabled = text.contains("Tailscale DNS: enabled", ignoreCase = true)
        val magicDnsEnabled = Regex("MagicDNS:\\s*enabled", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val magicSuffix = Regex("suffix\\s*=\\s*([^)\\s]+)").find(text)?.groupValues?.get(1)
        val deviceName = Regex("can reach this device at\\s+(\\S+?)\\.?\\s*$", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
        return DnsStatus(
            tailscaleDnsEnabled = tsDnsEnabled,
            magicDnsEnabled = magicDnsEnabled,
            magicDnsSuffix = magicSuffix,
            deviceDnsName = deviceName,
        )
    }

    /** Advertise 本机为 Tailscale SSH server（让其他 tailnet 节点可 ssh 进来）。 */
    suspend fun setSshServer(enabled: Boolean): CommandResult =
        shell.exec("tailscale set --ssh=$enabled")

    /**
     * 解析当前 shell 实际会调用的 tailscale / tailscaled / tailscaled.service 路径。
     * 走 RootShell 的兜底 PATH，能反映出二进制到底是 magic mount 的系统位置，
     * 还是 fallback 到 /data/adb/tailscale/bin。
     */
    suspend fun resolveBinaries(): BinaryPaths = BinaryPaths(
        tailscale = shell.execText("command -v tailscale").trim().ifBlank { null },
        tailscaled = shell.execText("command -v tailscaled").trim().ifBlank { null },
        service = shell.execText("command -v tailscaled.service").trim().ifBlank { null },
    )
}

data class BinaryPaths(
    val tailscale: String?,
    val tailscaled: String?,
    val service: String?,
)
