package top.cenmin.tailcontrol.core.manager

import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.shell.RootShell
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 AltRepo 版本体验优化中的路由注入功能。
 *
 * 负责将 tailscale peer 的 primaryRoutes 写入
 * /data/adb/tailscale/scripts/tailscaled.tun.up 和 tailscaled.tun.down，
 * 并在首次操作前自动备份原文件，支持一键还原。
 *
 * 编辑策略：使用固定起止注释块标记托管区域，安全地替换而不破坏其余内容。
 */
@Singleton
class AltRepoRouteManager @Inject constructor(
    private val shell: RootShell,
    private val tailscaleRepository: TailscaleRepository,
) {

    companion object {
        private const val SCRIPTS_DIR = "/data/adb/tailscale/scripts"
        private const val TUN_UP   = "$SCRIPTS_DIR/tailscaled.tun.up"
        private const val TUN_DOWN = "$SCRIPTS_DIR/tailscaled.tun.down"
        private const val TUN_UP_BAK   = "$SCRIPTS_DIR/tailscaled.tun.up.bak"
        private const val TUN_DOWN_BAK = "$SCRIPTS_DIR/tailscaled.tun.down.bak"

        private const val BLOCK_BEGIN = "# --- TailControl managed routes begin ---"
        private const val BLOCK_END   = "# --- TailControl managed routes end ---"
    }

    // -------------------------------------------------------------------------
    // 公开 API
    // -------------------------------------------------------------------------

    /**
     * 同步当前 tailscale primary routes 到 tun.up / tun.down。
     * - 首次调用时自动备份原文件（.bak 不存在则备份）。
     * - 之后每次都重写托管区域，幂等操作。
     * - 最终执行 `tailscaled.tun restart`。
     *
     * @return 操作摘要（成功/失败信息）
     */
    suspend fun syncRoutes(): SyncResult {
        // 1. 收集所有 peer 的 primaryRoutes（去重排序）
        val status = tailscaleRepository.fetchStatus()
        val routes = (status.peers.flatMap { it.primaryRoutes } +
                (status.self?.primaryRoutes ?: emptyList()))
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        if (routes.isEmpty()) {
            return SyncResult(ok = false, message = "No primary routes found in tailscale status.")
        }

        // 2. 确保文件存在（不存在则创建最小骨架）
        ensureFileExists(TUN_UP,   "#!/system/bin/sh\n")
        ensureFileExists(TUN_DOWN, "#!/system/bin/sh\n")

        // 3. 首次备份（.bak 不存在时才备份，保留最原始的版本）
        backupIfAbsent(TUN_UP,   TUN_UP_BAK)
        backupIfAbsent(TUN_DOWN, TUN_DOWN_BAK)

        // 4. 构建托管块内容
        val upBlock   = buildUpBlock(routes)
        val downBlock = buildDownBlock(routes)

        // 5. 先停止 tun，再写入文件，最后启动
        var msg = "Routes synced: ${routes.joinToString(", ")}\n"

        val stopResult = shell.exec("tailscaled.tun stop")
        msg += "tun stop: ${if (stopResult.ok) "OK" else stopResult.text}\n"

        val upResult   = injectBlock(TUN_UP,   upBlock)
        val downResult = injectBlock(TUN_DOWN, downBlock)

        if (!upResult.ok)   return SyncResult(ok = false, message = "${msg}Failed to write tun.up: ${upResult.message}")
        if (!downResult.ok) return SyncResult(ok = false, message = "${msg}Failed to write tun.down: ${downResult.message}")

        val startResult = shell.exec("tailscaled.tun start")
        msg += "tun start: ${if (startResult.ok) "OK" else startResult.text}"

        return SyncResult(
            ok = startResult.ok,
            message = msg.trim(),
            routes = routes,
        )
    }

    /**
     * 还原备份文件（.bak → 原文件）。
     * 顺序：tailscaled.tun stop → 恢复文件 → tailscaled.tun start
     */
    suspend fun restoreBackup(): SyncResult {
        val upBakExists   = fileExists(TUN_UP_BAK)
        val downBakExists = fileExists(TUN_DOWN_BAK)

        if (!upBakExists && !downBakExists) {
            return SyncResult(ok = false, message = "No backup files found. Nothing to restore.")
        }

        var msg = ""

        // 1. 先停止 tun，避免脚本替换过程中状态不一致
        val stopResult = shell.exec("tailscaled.tun stop")
        msg += "tun stop: ${if (stopResult.ok) "OK" else stopResult.text}\n"

        // 2. 恢复文件
        if (upBakExists) {
            val r = shell.exec("cp -f $TUN_UP_BAK $TUN_UP")
            msg += "tun.up restore: ${if (r.ok) "OK" else r.text}\n"
        }
        if (downBakExists) {
            val r = shell.exec("cp -f $TUN_DOWN_BAK $TUN_DOWN")
            msg += "tun.down restore: ${if (r.ok) "OK" else r.text}\n"
        }

        // 3. 文件就位后再启动
        val startResult = shell.exec("tailscaled.tun start")
        msg += "tun start: ${if (startResult.ok) "OK" else startResult.text}"

        return SyncResult(ok = true, message = msg.trim())
    }

    /**
     * 从 tun.up / tun.down 中删除托管区域。
     * 顺序：tailscaled.tun stop → 删除代码块 → tailscaled.tun start
     * 关闭 AltRepo 优化开关时调用，保证文件干净。
     */
    suspend fun removeRoutes(): SyncResult {
        var msg = ""

        // 1. 先停止 tun
        val stopResult = shell.exec("tailscaled.tun stop")
        msg += "tun stop: ${if (stopResult.ok) "OK" else stopResult.text}\n"

        // 2. 删除托管区域
        val upResult   = removeBlock(TUN_UP)
        val downResult = removeBlock(TUN_DOWN)

        if (!upResult.ok)   return SyncResult(ok = false, message = "$msg\nFailed to clean tun.up: ${upResult.message}")
        if (!downResult.ok) return SyncResult(ok = false, message = "$msg\nFailed to clean tun.down: ${downResult.message}")
        msg += "Managed block removed.\n"

        // 3. 文件干净后再启动
        val startResult = shell.exec("tailscaled.tun start")
        msg += "tun start: ${if (startResult.ok) "OK" else startResult.text}"

        return SyncResult(ok = startResult.ok, message = msg.trim())
    }

    /** 检查备份文件是否存在（用于 UI 决定是否显示"还原"按钮）。 */
    suspend fun hasBackup(): Boolean = fileExists(TUN_UP_BAK) || fileExists(TUN_DOWN_BAK)

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    private suspend fun fileExists(path: String): Boolean {
        val r = shell.exec("[ -f $path ] && echo yes || echo no")
        return r.text.trim() == "yes"
    }

    private suspend fun ensureFileExists(path: String, skeleton: String) {
        if (!fileExists(path)) {
            // 用 printf 写入骨架内容并赋予可执行权限
            shell.exec("printf '%s' ${shellEscape(skeleton)} > $path && chmod 755 $path")
        }
    }

    private suspend fun backupIfAbsent(src: String, bak: String) {
        if (!fileExists(bak)) {
            shell.exec("cp $src $bak")
        }
    }

    /**
     * 从文件中删除托管区域（begin…end 整块），其余内容保留。
     * 文件不存在或不含托管区域时直接返回成功（幂等）。
     */
    private suspend fun removeBlock(filePath: String): InjectResult {
        if (!fileExists(filePath)) return InjectResult(ok = true)

        val suffix = if (filePath.contains("up")) "up" else "down"
        val tmpOut = "/data/local/tmp/tc_out_${suffix}.tmp"

        // awk：遇到 begin 标记开始跳过，遇到 end 停止跳过，其余正常打印
        val awkCmd = """awk '/^# --- TailControl managed routes begin ---/{skip=1;next} /^# --- TailControl managed routes end ---/{skip=0;next} !skip{print}' $filePath > $tmpOut && mv $tmpOut $filePath && chmod 755 $filePath"""
        val r = shell.exec(awkCmd)
        return if (r.ok) InjectResult(ok = true)
        else InjectResult(ok = false, message = r.text)
    }

    /** 将 [block] 注入到 [filePath] 中的托管区域。
     * - 若托管区域已存在 → 替换。
     * - 若不存在 → 追加到文件末尾。
     *
     * 实现原理：用 awk 在 shell 侧完成替换，避免复杂的转义问题。
     */
    private suspend fun injectBlock(filePath: String, block: String): InjectResult {
        // 把 block 写到临时文件，再用 awk 拼接，规避 shell 引号地狱
        val tmpBlock = "/data/local/tmp/tc_block_${ if (filePath.contains("up")) "up" else "down" }.tmp"
        val escapedBlock = shellEscape(block)

        // 写临时块文件
        val writeResult = shell.exec("printf '%s\\n' $escapedBlock > $tmpBlock")
        if (!writeResult.ok) return InjectResult(ok = false, message = writeResult.text)

        // awk 脚本：跳过旧托管区域，在 END 或文件末尾插入新块
        val awkScript = """
            BEGIN { skip=0; printed=0 }
            /^# --- TailControl managed routes begin ---/ { skip=1; next }
            /^# --- TailControl managed routes end ---/   { skip=0; next }
            !skip { print }
            END {
                while ((getline line < ARGV[2]) > 0) print line
                printed=1
            }
        """.trimIndent()

        val tmpAwk = "/data/local/tmp/tc_awk.awk"
        val tmpOut  = "/data/local/tmp/tc_out_${ if (filePath.contains("up")) "up" else "down" }.tmp"

        shell.exec("printf '%s\\n' ${shellEscape(awkScript)} > $tmpAwk")
        val awkResult = shell.exec("awk -f $tmpAwk $filePath $tmpBlock > $tmpOut && mv $tmpOut $filePath && chmod 755 $filePath")

        // 清理临时文件
        shell.exec("rm -f $tmpBlock $tmpAwk $tmpOut")

        return if (awkResult.ok) InjectResult(ok = true)
        else InjectResult(ok = false, message = awkResult.text)
    }

    /** 构建 tun.up 托管块：添加路由 + iptables MARK */
    private fun buildUpBlock(routes: List<String>): String = buildString {
        appendLine(BLOCK_BEGIN)
        for (route in routes) {
            appendLine("ip route add $route dev tailscale0 metric 1 2>/dev/null || true")
            appendLine("iptables -t mangle -A OUTPUT -d $route -j MARK --set-mark 1099 2>/dev/null || true")
            // IPv6 子网也加 ip6tables（如果是 v6 路由）
            if (route.contains(":")) {
                appendLine("ip6tables -t mangle -A OUTPUT -d $route -j MARK --set-mark 1099 2>/dev/null || true")
            }
        }
        append(BLOCK_END)
    }

    /** 构建 tun.down 托管块：删除路由 + 清理 iptables */
    private fun buildDownBlock(routes: List<String>): String = buildString {
        appendLine(BLOCK_BEGIN)
        for (route in routes) {
            appendLine("ip route del $route dev tailscale0 2>/dev/null || true")
            appendLine("iptables -t mangle -D OUTPUT -d $route -j MARK --set-mark 1099 2>/dev/null || true")
            if (route.contains(":")) {
                appendLine("ip6tables -t mangle -D OUTPUT -d $route -j MARK --set-mark 1099 2>/dev/null || true")
            }
        }
        append(BLOCK_END)
    }

    /** 对 shell 单引号转义：将 ' 替换为 '\'' */
    private fun shellEscape(s: String): String = "'${s.replace("'", "'\\''")}'"

    // -------------------------------------------------------------------------
    // 结果数据类
    // -------------------------------------------------------------------------

    data class SyncResult(
        val ok: Boolean,
        val message: String = "",
        val routes: List<String> = emptyList(),
    )

    private data class InjectResult(
        val ok: Boolean,
        val message: String = "",
    )
}