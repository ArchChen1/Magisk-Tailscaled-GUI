package top.cenmin.tailcontrol.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.cenmin.tailcontrol.core.model.ConflictBehavior
import top.cenmin.tailcontrol.core.shell.RootShell
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropRepository @Inject constructor(
    private val shell: RootShell,
    private val prefs: PreferencesRepository,
) {
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    fun appendLog(line: String) {
        val cur = _output.value
        _output.value = if (cur.isBlank()) line else "$cur\n$line"
    }

    fun resetLog() {
        _output.value = ""
    }

    suspend fun isProcessAlive(pid: Int): Boolean {
        return shell.exec("kill -0 $pid").ok
    }

    suspend fun startFileGet(path: String, behavior: ConflictBehavior): Int? {
        // 先确保不会有过多 tailscale 进程
        val count = shell.exec("pgrep -x tailscale").out.count { it.isNotBlank() }
        if (count >= 5) shell.exec("pkill -9 -x tailscale")

        val cmd = "nohup tailscale file get --conflict=${behavior.cliValue} --loop $path >/dev/null 2>&1 & echo \$!"
        val ppidLine = shell.execText(cmd).trim()
        val ppid = ppidLine.toIntOrNull() ?: return null

        // 找到实际子进程 PID
        val ps = shell.execText("ps -A | grep $ppid")
        val regex = Regex("\\s*(\\d+)\\s+$ppid\\s+.*tailscale")
        val realPid = regex.find(ps)?.groupValues?.get(1)?.toIntOrNull()
        prefs.rememberDropRuntime(path, behavior, realPid)
        return realPid ?: ppid
    }

    suspend fun stopFileGet() {
        val (_, _, pid) = prefs.loadDropRuntime()
        if (pid != null && pid > 0) {
            shell.exec("kill $pid")
        }
        prefs.clearDropPid()
    }
}
