package top.cenmin.tailcontrol.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import top.cenmin.tailcontrol.core.log.AppLogReader
import top.cenmin.tailcontrol.core.log.LogArchive
import top.cenmin.tailcontrol.core.log.LogSource
import top.cenmin.tailcontrol.core.log.LogTooLargeException
import top.cenmin.tailcontrol.core.shell.RootShell
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val shell: RootShell,
    private val appLogReader: AppLogReader,
) {
    /** 实时 tail 当前活动文件。lines <= 0 表示不读历史，只看新增。 */
    fun tail(source: LogSource, lines: Int = 200): Flow<String> = when (source) {
        LogSource.App -> appLogReader.tailFlow(lines)
        LogSource.Tailscaled, LogSource.Runs -> tailRoot(source.path(), lines)
    }

    private fun tailRoot(path: String, lines: Int): Flow<String> {
        // toybox 的 `tail -n 0` 不识别为"零行历史"，仍会输出默认 10 行，
        // 所以 lines == 0 时改用 wc -l + tail -n +X 把游标定位到当前末尾后再 follow。
        val cmd = if (lines <= 0) {
            "L=\$(wc -l < $path 2>/dev/null || echo 0); tail -n +\$((L+1)) -F $path 2>/dev/null"
        } else {
            "tail -n $lines -F $path 2>/dev/null"
        }
        return shell.stream(cmd)
    }

    /**
     * 真清空。
     *  - target == null 或 target.isCurrent: 截断 live 文件（App 等价于把 logs 目录下所有 app.log* 删掉重开）
     *  - target 是某个归档: 删除该具体归档文件
     * 失败原因放在 Result.exceptionOrNull()?.message。
     */
    suspend fun clear(source: LogSource, target: LogArchive? = null): Result<Unit> {
        if (target != null && !target.isCurrent) {
            return clearArchive(source, target)
        }
        return clearLive(source)
    }

    private suspend fun clearLive(source: LogSource): Result<Unit> = when (source) {
        LogSource.App -> runCatching { appLogReader.clear() }
        LogSource.Tailscaled, LogSource.Runs -> {
            if (!shell.isRoot()) {
                Result.failure(IllegalStateException("no_root"))
            } else {
                val r = shell.exec(": > ${source.path()}")
                if (r.ok) Result.success(Unit) else Result.failure(IllegalStateException(r.text))
            }
        }
    }

    private suspend fun clearArchive(source: LogSource, archive: LogArchive): Result<Unit> {
        val path = archive.path ?: return Result.failure(IllegalStateException("no_path"))
        return when (source) {
            LogSource.App -> runCatching {
                val ok = java.io.File(path).delete()
                if (!ok) throw IllegalStateException("delete_failed")
            }
            LogSource.Tailscaled, LogSource.Runs -> {
                if (!shell.isRoot()) {
                    Result.failure(IllegalStateException("no_root"))
                } else {
                    val r = shell.exec("rm -f '$path'")
                    if (r.ok) Result.success(Unit) else Result.failure(IllegalStateException(r.text))
                }
            }
        }
    }

    /** 列出所有可选归档：[0] 永远是 "Live"，其余为已存在的轮转件。 */
    suspend fun listArchives(source: LogSource): List<LogArchive> = when (source) {
        LogSource.App -> appLogReader.archives()
        LogSource.Tailscaled, LogSource.Runs -> rootArchives(source)
    }

    private suspend fun rootArchives(source: LogSource): List<LogArchive> {
        val basename = source.basename()
        val dir = source.dir()
        // stat -c '%s %n'：每行 "<size> <fullpath>"；toybox 支持。
        val cmd = "stat -c '%s %n' $dir/$basename* 2>/dev/null"
        val raw = shell.execText(cmd)
        val out = mutableListOf<LogArchive>()
        var liveSize = 0L
        var livePath: String? = null
        val rotated = mutableListOf<LogArchive>()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val sp = line.indexOf(' ')
                if (sp <= 0) return@forEach
                val size = line.substring(0, sp).toLongOrNull() ?: return@forEach
                val path = line.substring(sp + 1)
                val name = path.substringAfterLast('/')
                if (name == basename) {
                    liveSize = size
                    livePath = path
                } else if (name.startsWith("$basename.")) {
                    rotated += LogArchive(
                        id = name,
                        label = "$name (${humanSize(size)})",
                        sizeBytes = size,
                        isCurrent = false,
                        path = path,
                    )
                }
            }
        out += LogArchive(LogArchive.LIVE_ID, "Live", liveSize, isCurrent = true, path = livePath)
        out += rotated.sortedBy { it.id }
        return out
    }

    /**
     * 读取归档文件（一次性，不 follow）。命令退出后 Flow 自然结束。
     * 调用方应在 VM 层根据 [LogArchive.sizeBytes] 自行决定是否要拒绝过大文件。
     * App 归档若超过 [AppLogReader.MAX_ARCHIVE_BYTES] 会在 collect 时抛 [LogTooLargeException]。
     */
    fun readArchive(source: LogSource, archive: LogArchive): Flow<String> {
        val path = archive.path ?: return emptyFlow()
        return when (source) {
            LogSource.App -> appLogReader.readArchive(java.io.File(path)).asFlow()
            LogSource.Tailscaled, LogSource.Runs -> shell.stream("cat $path")
        }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    private fun LogSource.path(): String = when (this) {
        LogSource.App -> "" // unused
        LogSource.Tailscaled -> LogSource.Tailscaled.PATH
        LogSource.Runs -> LogSource.Runs.PATH
    }

    private fun LogSource.basename(): String = when (this) {
        LogSource.App -> ""
        LogSource.Tailscaled -> LogSource.Tailscaled.BASENAME
        LogSource.Runs -> LogSource.Runs.BASENAME
    }

    private fun LogSource.dir(): String = when (this) {
        LogSource.App -> ""
        LogSource.Tailscaled -> LogSource.Tailscaled.DIR
        LogSource.Runs -> LogSource.Runs.DIR
    }
}
