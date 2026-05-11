package top.cenmin.tailcontrol

import android.app.Application
import com.topjohnwu.superuser.Shell
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import top.cenmin.tailcontrol.core.log.AppLogTree

@HiltAndroidApp
class TailControlApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun appLogTree(): AppLogTree
    }

    override fun onCreate() {
        super.onCreate()

        // DEBUG / RELEASE 都把日志落盘到 filesDir/logs/app.log，方便用户报错时回溯。
        val tree = EntryPointAccessors
            .fromApplication(this, AppEntryPoint::class.java)
            .appLogTree()
        Timber.plant(tree)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 把崩溃栈也塞进 app.log，再链回原 handler，不吞调用方（系统弹窗 / Crashlytics 等）。
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { Timber.tag("uncaught").e(error, "thread=%s", thread.name) }
            previous?.uncaughtException(thread, error)
        }

        // 共享主 Shell 配置：每个进程缓存一个 su 进程，所有 Shell.cmd(...) 复用。
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }
}
