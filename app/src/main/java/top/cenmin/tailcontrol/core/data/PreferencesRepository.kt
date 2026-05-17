package top.cenmin.tailcontrol.core.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import top.cenmin.tailcontrol.core.model.ConflictBehavior
import top.cenmin.tailcontrol.core.model.DropConfig
import top.cenmin.tailcontrol.core.model.TailscaleSettings
import javax.inject.Inject
import javax.inject.Singleton

private object Keys {
    val ACCEPT_ROUTES = booleanPreferencesKey("acceptRoutes")
    val ACCEPT_DNS = booleanPreferencesKey("acceptDns")
    val EXIT_NODE = stringPreferencesKey("exitNode")
    val ADVERTISE_EXIT_NODE = booleanPreferencesKey("advertiseExitNode")
    val ADVERTISE_ROUTES = stringPreferencesKey("advertiseRoutes")
    val CUSTOM_NAME = stringPreferencesKey("customName")
    val CUSTOM_PARAMS = stringPreferencesKey("customParams")
    val SSH_SERVER_ENABLED = booleanPreferencesKey("ssh_server_enabled")

    val DROP_ENABLED = booleanPreferencesKey("drop_enabled")
    val DROP_PATH = stringPreferencesKey("drop_path")
    val CONFLICT_BEHAVIOR = stringPreferencesKey("conflict_behavior")
    val PING_ADDRESS = stringPreferencesKey("ping_address")

    val DROP_CURRENT_PATH = stringPreferencesKey("drop_current_path")
    val DROP_CURRENT_BEHAVIOR = stringPreferencesKey("drop_current_behavior")
    val DROP_PID = intPreferencesKey("drop_pid")

    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val ALT_REPO_OPTIMIZATION = booleanPreferencesKey("alt_repo_optimization")
    val HEALTH_BANNER_DISABLED = booleanPreferencesKey("health_banner_disabled")

    val LAST_UPDATE_CHECK_DATE = stringPreferencesKey("last_update_check_date")
}

private val Context.tailDataStore by preferencesDataStore(
    name = "tailcontrol",
    produceMigrations = { ctx ->
        listOf(
            // 旧 SharedPreferences 一次性迁移
            SharedPreferencesMigration(
                context = ctx,
                sharedPreferencesName = "tailscale_prefs",
                keysToMigrate = setOf(
                    "acceptRoutes", "acceptDns", "exitNode",
                    "advertiseExitNode", "advertiseRoutes",
                    "customName", "customParams",
                ),
            ),
            SharedPreferencesMigration(
                context = ctx,
                sharedPreferencesName = "drop_prefs",
                keysToMigrate = setOf("drop_enabled", "drop_path", "conflict_behavior", "ping_address"),
            ),
            SharedPreferencesMigration(
                context = ctx,
                sharedPreferencesName = "drop_protect",
                keysToMigrate = setOf("current_path", "current_behavior", "fileGetPid"),
            ),
        )
    }
)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store: DataStore<Preferences> = context.tailDataStore

    val tailscaleSettings: Flow<TailscaleSettings> = store.data.map { p ->
        TailscaleSettings(
            acceptRoutes = p[Keys.ACCEPT_ROUTES] ?: true,
            acceptDns = p[Keys.ACCEPT_DNS] ?: false,
            exitNode = p[Keys.EXIT_NODE].orEmpty(),
            advertiseExitNode = p[Keys.ADVERTISE_EXIT_NODE] ?: false,
            advertiseRoutes = p[Keys.ADVERTISE_ROUTES].orEmpty(),
            customName = p[Keys.CUSTOM_NAME].orEmpty(),
            customParams = p[Keys.CUSTOM_PARAMS].orEmpty(),
        )
    }

    @SuppressLint("SdCardPath")
    val dropConfig: Flow<DropConfig> = store.data.map { p ->
        DropConfig(
            enabled = p[Keys.DROP_ENABLED] ?: false,
            path = p[Keys.DROP_PATH] ?: "/sdcard/Download/TailDrop/",
            conflict = ConflictBehavior.fromCli(p[Keys.CONFLICT_BEHAVIOR]),
        )
    }

    val pingAddress: Flow<String> = store.data.map { it[Keys.PING_ADDRESS].orEmpty() }

    val dynamicColorEnabled: Flow<Boolean> = store.data.map { it[Keys.DYNAMIC_COLOR] ?: true }

    val altRepoOptimizationEnabled: Flow<Boolean> = store.data.map { it[Keys.ALT_REPO_OPTIMIZATION] ?: false }

    val healthBannerDisabled: Flow<Boolean> = store.data.map { it[Keys.HEALTH_BANNER_DISABLED] ?: false }

    val sshServerEnabled: Flow<Boolean> = store.data.map { it[Keys.SSH_SERVER_ENABLED] ?: false }

    suspend fun setSshServerEnabled(enabled: Boolean) {
        store.edit { it[Keys.SSH_SERVER_ENABLED] = enabled }
    }
    suspend fun saveTailscaleSettings(settings: TailscaleSettings) {
        store.edit { p ->
            p[Keys.ACCEPT_ROUTES] = settings.acceptRoutes
            p[Keys.ACCEPT_DNS] = settings.acceptDns
            p[Keys.EXIT_NODE] = settings.exitNode
            p[Keys.ADVERTISE_EXIT_NODE] = settings.advertiseExitNode
            p[Keys.ADVERTISE_ROUTES] = settings.advertiseRoutes
            p[Keys.CUSTOM_NAME] = settings.customName
            p[Keys.CUSTOM_PARAMS] = settings.customParams
        }
    }

    suspend fun setDropEnabled(enabled: Boolean) {
        store.edit { it[Keys.DROP_ENABLED] = enabled }
    }

    suspend fun setDropPath(path: String) {
        store.edit { it[Keys.DROP_PATH] = path }
    }

    suspend fun setConflict(behavior: ConflictBehavior) {
        store.edit { it[Keys.CONFLICT_BEHAVIOR] = behavior.cliValue }
    }

    suspend fun setPingAddress(addr: String) {
        store.edit { it[Keys.PING_ADDRESS] = addr }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        store.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAltRepoOptimizationEnabled(enabled: Boolean) {
        store.edit { it[Keys.ALT_REPO_OPTIMIZATION] = enabled }
    }
    // 添加设置 Health 横幅禁用开关
    suspend fun setHealthBannerDisabled(disabled: Boolean) {
        store.edit { it[Keys.HEALTH_BANNER_DISABLED] = disabled }
    }

    // Drop daemon 内部状态——给 DropProtectService 用的
    suspend fun rememberDropRuntime(path: String, behavior: ConflictBehavior, pid: Int?) {
        store.edit { p ->
            p[Keys.DROP_CURRENT_PATH] = path
            p[Keys.DROP_CURRENT_BEHAVIOR] = behavior.cliValue
            if (pid != null) p[Keys.DROP_PID] = pid else p.remove(Keys.DROP_PID)
        }
    }

    suspend fun loadDropRuntime(): Triple<String?, ConflictBehavior?, Int?> {
        val p = store.data.first()
        return Triple(
            p[Keys.DROP_CURRENT_PATH],
            p[Keys.DROP_CURRENT_BEHAVIOR]?.let(ConflictBehavior::fromCli),
            p[Keys.DROP_PID],
        )
    }

    suspend fun clearDropPid() {
        store.edit { it.remove(Keys.DROP_PID) }
    }

    // 更新检查
    suspend fun getLastUpdateCheckDate(): String? {
        return store.data.map { preferences ->
            preferences[Keys.LAST_UPDATE_CHECK_DATE]
        }.first()
    }

    suspend fun setLastUpdateCheckDate(date: String) {
        store.edit { preferences ->
            preferences[Keys.LAST_UPDATE_CHECK_DATE] = date
        }
    }
}