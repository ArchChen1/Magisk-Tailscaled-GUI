package top.cenmin.tailcontrol.core.log

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 Timber 调用持久化到 filesDir/logs/app.log，按大小轮转，保留 app.log.1 / app.log.2。
 *
 * 线程安全：Timber 可能从任何线程调用 log()，本类用 synchronized(lock) 保护写入和轮转。
 * 文件 I/O 走在调用方线程（Timber 是同步的），单条写入开销很小，可接受。
 */
@Singleton
class AppLogTree @Inject constructor(
    @ApplicationContext context: Context,
) : Timber.Tree() {

    private val logsDir: File = File(context.filesDir, LOGS_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }
    private val currentFile: File = File(logsDir, CURRENT_NAME)

    private val lock = Any()
    private var writer: BufferedWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        synchronized(lock) {
            try {
                val w = writer ?: openWriter().also { writer = it }
                val line = buildString {
                    append(dateFormat.format(Date()))
                    append(' ')
                    append(priorityChar(priority))
                    append('/')
                    append(tag ?: "App")
                    append(": ")
                    append(message)
                    append('\n')
                    if (t != null) {
                        append(Log.getStackTraceString(t))
                        if (!endsWith('\n')) append('\n')
                    }
                }
                w.write(line)
                w.flush()
                if (currentFile.length() > MAX_BYTES) rotate()
            } catch (_: Throwable) {
                // 避免日志写入失败再次抛异常造成回环；下次再试。
                runCatching { writer?.close() }
                writer = null
            }
        }
    }

    /** 删除所有历史文件，并重新打开 writer。 */
    fun clearAll() {
        synchronized(lock) {
            runCatching { writer?.close() }
            writer = null
            logsDir.listFiles()?.forEach { f ->
                if (f.name == CURRENT_NAME || f.name.startsWith("$CURRENT_NAME.")) {
                    f.delete()
                }
            }
        }
    }

    /** 当前 + 已存在的轮转件，按从新到旧排列：app.log, app.log.1, app.log.2。 */
    fun listFiles(): List<File> {
        synchronized(lock) {
            // 在 list 前 flush 一次，避免 size 不准。
            runCatching { writer?.flush() }
            val out = mutableListOf<File>()
            if (currentFile.exists()) out += currentFile
            for (i in 1..KEEP) {
                val f = File(logsDir, "$CURRENT_NAME.$i")
                if (f.exists()) out += f
            }
            return out
        }
    }

    /** 直接暴露当前文件路径，供 reader 在不持锁的情况下做随机读。 */
    fun currentFile(): File = currentFile

    fun logsDir(): File = logsDir

    private fun openWriter(): BufferedWriter {
        if (!logsDir.exists()) logsDir.mkdirs()
        return BufferedWriter(FileWriter(currentFile, /* append = */ true))
    }

    private fun rotate() {
        runCatching { writer?.close() }
        writer = null
        // 从最旧往新滑动：app.log.1 -> app.log.2，新出现的 app.log 变成 app.log.1
        for (i in KEEP downTo 2) {
            val src = File(logsDir, "$CURRENT_NAME.${i - 1}")
            val dst = File(logsDir, "$CURRENT_NAME.$i")
            if (src.exists()) {
                if (dst.exists()) dst.delete()
                src.renameTo(dst)
            }
        }
        if (currentFile.exists()) {
            val first = File(logsDir, "$CURRENT_NAME.1")
            if (first.exists()) first.delete()
            currentFile.renameTo(first)
        }
        writer = openWriter()
    }

    private fun priorityChar(priority: Int): Char = when (priority) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        Log.ASSERT -> 'A'
        else -> '?'
    }

    companion object {
        const val LOGS_DIR_NAME = "logs"
        const val CURRENT_NAME = "app.log"
        const val MAX_BYTES: Long = 1_000_000L
        const val KEEP: Int = 2
    }
}
