package top.cenmin.tailcontrol.core.data

import kotlinx.coroutines.flow.Flow
import top.cenmin.tailcontrol.core.shell.RootShell
import javax.inject.Inject
import javax.inject.Singleton

enum class LogSource(val path: String) {
    Tailscaled("/data/adb/tailscale/run/tailscaled.log"),
    Runs("/data/adb/tailscale/run/runs.log"),
}

@Singleton
class LogRepository @Inject constructor(
    private val shell: RootShell,
) {
    fun tail(source: LogSource, lines: Int = 200): Flow<String> {
        val path = source.path
        // toybox 的 `tail -n 0` 不识别为"零行历史"，仍会输出默认 10 行，
        // 所以 lines == 0 时改用 wc -l + tail -n +X 把游标定位到当前末尾后再 follow。
        val cmd = if (lines <= 0) {
            "L=\$(wc -l < $path 2>/dev/null || echo 0); tail -n +\$((L+1)) -F $path 2>/dev/null"
        } else {
            "tail -n $lines -F $path 2>/dev/null"
        }
        return shell.stream(cmd)
    }
}
