package top.cenmin.tailcontrol.ui.screen.settings.experimental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.AltRepoRouteManager
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import javax.inject.Inject

data class ExperimentalUiState(
    val altRepoOptimization: Boolean = false,
    val healthBannerDisabled: Boolean = false,
    // 路由同步操作状态
    val routeSyncLoading: Boolean = false,
    val routeSyncMessage: String? = null,   // null = 无消息，非 null = 显示 snackbar/dialog
    val routeSyncSuccess: Boolean = true,
    // 是否存在备份文件（决定是否显示"还原"按钮）
    val hasRouteBackup: Boolean = false,
)

@HiltViewModel
class ExperimentalViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val altRepoRouteManager: AltRepoRouteManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExperimentalUiState())
    val ui: StateFlow<ExperimentalUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.altRepoOptimizationEnabled.collect { enabled ->
                _ui.value = _ui.value.copy(altRepoOptimization = enabled)
                // AltRepo 开关打开时刷新备份状态
                if (enabled) refreshBackupState()
            }
        }
        viewModelScope.launch {
            prefs.healthBannerDisabled.collect {
                _ui.value = _ui.value.copy(healthBannerDisabled = it)
            }
        }
    }

    fun setAltRepoOptimization(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAltRepoOptimizationEnabled(enabled)
            if (enabled) {
                // 开启时立即同步路由
                syncRoutes()
            } else {
                // 关闭时从脚本文件中删除托管区域
                _ui.value = _ui.value.copy(routeSyncLoading = true, routeSyncMessage = null)
                val result = altRepoRouteManager.removeRoutes()
                _ui.value = _ui.value.copy(
                    routeSyncLoading = false,
                    routeSyncMessage = result.message,
                    routeSyncSuccess = result.ok,
                )
            }
        }
    }

    fun setHealthBannerDisabled(disabled: Boolean) {
        viewModelScope.launch {
            prefs.setHealthBannerDisabled(disabled)
        }
    }

    /** 手动触发路由同步（写入脚本文件并 restart tun）。 */
    fun syncRoutes() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(routeSyncLoading = true, routeSyncMessage = null)
            val result = altRepoRouteManager.syncRoutes()
            val hasBackup = altRepoRouteManager.hasBackup()
            _ui.value = _ui.value.copy(
                routeSyncLoading = false,
                routeSyncMessage = result.message,
                routeSyncSuccess = result.ok,
                hasRouteBackup = hasBackup,
            )
        }
    }

    /** 还原备份文件，并关闭 AltRepo 开关。 */
    fun restoreBackup() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(routeSyncLoading = true, routeSyncMessage = null)
            val result = altRepoRouteManager.restoreBackup()
            val hasBackup = altRepoRouteManager.hasBackup()
            // 还原成功后关闭 AltRepo 开关（不再触发 removeRoutes，文件已由备份覆盖）
            if (result.ok) {
                prefs.setAltRepoOptimizationEnabled(false)
            }
            _ui.value = _ui.value.copy(
                routeSyncLoading = false,
                routeSyncMessage = result.message,
                routeSyncSuccess = result.ok,
                hasRouteBackup = hasBackup,
            )
        }
    }

    /** 消费消息后清除，避免重复展示。 */
    fun clearSyncMessage() {
        _ui.value = _ui.value.copy(routeSyncMessage = null)
    }

    private suspend fun refreshBackupState() {
        val hasBackup = altRepoRouteManager.hasBackup()
        _ui.value = _ui.value.copy(hasRouteBackup = hasBackup)
    }
}