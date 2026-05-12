package top.cenmin.tailcontrol.core.log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

class LogTooLargeException(val sizeBytes: Long) : RuntimeException("log too large: $sizeBytes bytes")

@Singleton
class AppLogReader @Inject constructor(
    private val tree: AppLogTree,
) {

    /**
     * 实时 tail：先发出末 [initialLines] 行，再每秒轮询新增内容。
     * 检测到文件被截断 / 变小（例如 clearAll 或轮转后未及时切换）时，把游标重置回 0。
     */
    fun tailFlow(initialLines: Int): Flow<String> = flow {
        val file = tree.currentFile()
        var offset: Long = if (initialLines > 0) {
            emitTailFrom(file, initialLines)
        } else {
            // 不读历史，把游标定位到当前末尾。
            if (file.exists()) file.length() else 0L
        }

        while (true) {
            delay(POLL_MS)
            if (!file.exists()) {
                offset = 0L
                continue
            }
            val len = file.length()
            if (len < offset) {
                // truncate / rotate / clear → 从头读
                offset = 0L
            }
            if (len > offset) {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(offset)
                    while (true) {
                        val line = raf.readLine() ?: break
                        emit(line)
                    }
                    offset = raf.filePointer
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 一次性读取一个归档文件；> [MAX_ARCHIVE_BYTES] 直接拒绝。 */
    fun readArchive(file: File): List<String> {
        val len = file.length()
        if (len > MAX_ARCHIVE_BYTES) throw LogTooLargeException(len)
        return file.useLines { it.toList() }
    }

    /** 当前 + 已存在的轮转件，"Live" 排第一位。 */
    fun archives(): List<LogArchive> {
        val files = tree.listFiles()
        if (files.isEmpty()) {
            return listOf(LogArchive(LogArchive.LIVE_ID, "Live", 0L, isCurrent = true))
        }
        val out = mutableListOf<LogArchive>()
        val current = files.firstOrNull { it.name == AppLogTree.CURRENT_NAME }
        out += LogArchive(
            id = LogArchive.LIVE_ID,
            label = "Live",
            sizeBytes = current?.length() ?: 0L,
            isCurrent = true,
            path = current?.absolutePath,
        )
        files.filter { it.name != AppLogTree.CURRENT_NAME }
            .sortedBy { it.name }
            .forEach { f ->
                out += LogArchive(
                    id = f.name,
                    label = "${f.name} (${humanSize(f.length())})",
                    sizeBytes = f.length(),
                    isCurrent = false,
                    path = f.absolutePath,
                )
            }
        return out
    }

    fun clear() {
        tree.clearAll()
    }

    /**
     * 反向定位末 [n] 行的起点 offset，从该点开始把内容逐行发出，返回结束时的 offset。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitTailFrom(
        file: File,
        n: Int,
    ): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            // 反向扫描换行；为简单起见一字节一字节读，缓冲区开销可忽略。
            var pos = len - 1
            var newlines = 0
            while (pos >= 0) {
                raf.seek(pos)
                val b = raf.read()
                if (b == '\n'.code) {
                    newlines++
                    if (newlines > n) {
                        pos++
                        break
                    }
                }
                pos--
            }
            val start = (pos + 1).coerceAtLeast(0)
            raf.seek(start)
            while (true) {
                val line = raf.readLine() ?: break
                emit(line)
            }
            return raf.filePointer
        }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    companion object {
        const val POLL_MS: Long = 1000L
        const val MAX_ARCHIVE_BYTES: Long = 5L * 1024L * 1024L
    }
}
