package top.cenmin.tailcontrol.core.log

/**
 * 三种日志源：
 *  - App: 进程内 Timber 落盘到 filesDir/logs/app.log（无需 root）
 *  - Tailscaled / Runs: Magisk 模块在 /data/adb/tailscale/run/ 下产出的文件（需 root tail）
 *
 * id 用于持久化和归档下拉选择项的标识。
 */
sealed class LogSource(val id: String) {
    data object App : LogSource("app")
    data object Tailscaled : LogSource("tailscaled") {
        const val PATH: String = "/data/adb/tailscale/run/tailscaled.log"
        const val DIR: String = "/data/adb/tailscale/run"
        const val BASENAME: String = "tailscaled.log"
    }
    data object Runs : LogSource("runs") {
        const val PATH: String = "/data/adb/tailscale/run/runs.log"
        const val DIR: String = "/data/adb/tailscale/run"
        const val BASENAME: String = "runs.log"
    }

    companion object {
        fun fromId(id: String): LogSource = when (id) {
            App.id -> App
            Tailscaled.id -> Tailscaled
            Runs.id -> Runs
            else -> App
        }
    }
}
