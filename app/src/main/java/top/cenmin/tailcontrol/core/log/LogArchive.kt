package top.cenmin.tailcontrol.core.log

/**
 * 归档下拉的一项；可能是 "Live"（当前文件、跟随 tail）或某个轮转后的旧文件。
 *
 *  - id:  "live" 表示当前；其他取文件名 (e.g. "app.log.1", "tailscaled.log.1")
 *  - label: 给 UI 直接显示的字符串（已包含大小，如 "app.log.1 (123 KB)"）
 *  - sizeBytes: 文件大小，便于 UI 拒绝加载过大文件
 *  - isCurrent: true 表示这是 "Live"
 *  - path: 仅对非 Live 有意义；App 是绝对本地路径，Tailscaled/Runs 是 /data/adb/tailscale/run/ 下的绝对路径
 */
data class LogArchive(
    val id: String,
    val label: String,
    val sizeBytes: Long,
    val isCurrent: Boolean,
    val path: String? = null,
) {
    companion object {
        const val LIVE_ID: String = "live"
    }
}
