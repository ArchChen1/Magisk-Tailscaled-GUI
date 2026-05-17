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

    // 缓存 tailscale 命令是否可用
    private var tailscaleAvailable: Boolean? = null

    suspend fun isRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    // 检测 tailscale 命令是否可用（带缓存）
    private fun isTailscaleAvailable(): Boolean {
        tailscaleAvailable?.let { return it }
        val result = runCatching {
            val r = Shell.cmd("which tailscale").exec()
            r.isSuccess && r.out.isNotEmpty()
        }.getOrDefault(false)
        tailscaleAvailable = result
        return result
    }

    // 判断命令是否需要使用 fallback PATH
    private fun needsFallbackPath(cmd: String): Boolean {
        // 只对 tailscale 相关命令进行检测
        if (!cmd.contains("tailscale", ignoreCase = true)) return false
        return !isTailscaleAvailable()
    }

    suspend fun exec(cmd: String): CommandResult = withContext(Dispatchers.IO) {
        // 根据检测结果决定是否使用 fallback PATH
        val finalCmd = if (needsFallbackPath(cmd)) {
            withFallbackPath(cmd)
        } else {
            cmd
        }

        // 执行命令
        runCatching {
            val r = Shell.cmd(finalCmd).exec()
            CommandResult(r.isSuccess, r.code, r.out, r.err)
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
        // 根据检测结果决定是否使用 fallback PATH
        val finalCmd = if (needsFallbackPath(cmd)) {
            withFallbackPath(cmd)
        } else {
            cmd
        }

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
            .add(finalCmd)
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
        $$"PATH=$$FALLBACK_PATH:$PATH $$cmd"

    private companion object {
        const val FALLBACK_PATH =
            "/data/adb/tailscale/bin:/data/adb/tailscale/scripts:/data/adb/modules/magisk-tailscaled/system/bin"
    }
}