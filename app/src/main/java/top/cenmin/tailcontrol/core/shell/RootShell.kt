package top.cenmin.tailcontrol.core.shell

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
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
            val r = Shell.cmd(withFallbackPath(cmd)).exec()
            CommandResult(r.isSuccess, r.code, r.out.orEmpty(), r.err.orEmpty())
        }.getOrElse {
            CommandResult(false, -1, emptyList(), listOf(it.message.orEmpty()))
        }
    }

    suspend fun execText(cmd: String): String = exec(cmd).text

    /**
     * 长流式命令使用专用 Shell 实例，不阻塞共享主 Shell。
     * Flow 在以下情况会自然结束：命令自然退出、collector 取消、或 shell 异常。
     *
     * 完成回调走同步 executor (Runnable.run())，避免我们 awaitClose 里 shutdown 自建线程池
     * 后 libsu 的 JobTask.setResult 仍试图向其提交回调，触发 RejectedExecutionException。
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
        val syncExecutor = Executor { it.run() }
        shell.newJob()
            .add(withFallbackPath(cmd))
            .to(outCb, errCb)
            .submit(syncExecutor) { _ ->
                close()
            }
        awaitClose {
            // shell.close() 会终止 su 进程；它内部的 JobTask 完成回调会同步触发 close()，
            // 由于 callbackFlow 已经在关闭路径上，二次 close 是 no-op，安全。
            runCatching { shell.close() }
        }
    }.flowOn(Dispatchers.IO)

    // 兜底 PATH：KernelSU / 未挂载的 Magisk 环境下 /data/adb/modules/*/system/bin 不会进
    // PATH，导致 `tailscale` / `tailscaled.service` 找不到。把模块实际安装目录直接挂上去。
    private fun withFallbackPath(cmd: String): String =
        "PATH=$FALLBACK_PATH:\$PATH $cmd"

    private companion object {
        const val FALLBACK_PATH =
            "/data/adb/tailscale/bin:/data/adb/tailscale/scripts:/data/adb/modules/magisk-tailscaled/system/bin"
    }
}
