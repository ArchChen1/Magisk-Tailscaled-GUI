package top.cenmin.tailcontrol.core.shell

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

data class CommandResult(
    val ok: Boolean,
    val code: Int,
    val out: List<String>,
    val err: List<String>,
) {
    val text: String get() = (out + err).joinToString("\n").trim()
}

@Singleton
class RootShell @Inject constructor() {

    suspend fun isRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    suspend fun exec(cmd: String): CommandResult = withContext(Dispatchers.IO) {
        runCatching {
            val r = Shell.cmd(cmd).exec()
            CommandResult(r.isSuccess, r.code, r.out.orEmpty(), r.err.orEmpty())
        }.getOrElse {
            CommandResult(false, -1, emptyList(), listOf(it.message.orEmpty()))
        }
    }

    suspend fun execText(cmd: String): String = exec(cmd).text

    /**
     * 长流式命令使用专用 Shell 实例，不阻塞共享主 Shell。
     * Flow 在以下情况会自然结束：命令自然退出、collector 取消、或 shell 异常。
     */
    fun stream(cmd: String): Flow<String> = callbackFlow {
        val shell = Shell.Builder.create().build()
        val outCb = object : CallbackList<String>() {
            override fun onAddElement(s: String?) {
                if (s != null) trySend(s)
            }
        }
        val errCb = object : CallbackList<String>() {
            override fun onAddElement(s: String?) {
                if (s != null) trySend(s)
            }
        }
        // 用 callback 形式启动；命令退出时主动 close 以让 collect 返回。
        val executor = Executors.newSingleThreadExecutor()
        shell.newJob()
            .add(cmd)
            .to(outCb, errCb)
            .submit(executor) { _ ->
                close()
            }
        awaitClose {
            runCatching { shell.close() }       // kill su 进程
            runCatching { executor.shutdownNow() }
        }
    }.flowOn(Dispatchers.IO)
}
