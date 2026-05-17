package top.cenmin.tailcontrol.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.PreferencesRepository  // 添加依赖
import top.cenmin.tailcontrol.core.manager.UpdateChecker
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val prefs: PreferencesRepository  // 添加 PreferencesRepository 依赖
) : ViewModel() {

    sealed interface UpdateUiState {
        data object Idle : UpdateUiState
        data object Checking : UpdateUiState
        data class HasUpdate(val result: UpdateChecker.CheckUpdateResult) : UpdateUiState
        data object NoUpdate : UpdateUiState
        data class Error(val message: String) : UpdateUiState
    }

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    // 用于静默检查的结果（不触发 UI 状态变化）
    private val _silentUpdateResult = MutableStateFlow<UpdateChecker.CheckUpdateResult?>(null)
    val silentUpdateResult: StateFlow<UpdateChecker.CheckUpdateResult?> = _silentUpdateResult.asStateFlow()

    // 原有的检查方法（会更新 UI 状态）
    fun checkUpdate(showNoUpdateTip: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking

            val result = updateChecker.checkUpdate()

            result.onSuccess { checkResult ->
                if (checkResult.hasNewVersion) {
                    _uiState.value = UpdateUiState.HasUpdate(checkResult)
                } else {
                    if (showNoUpdateTip) {
                        _uiState.value = UpdateUiState.NoUpdate
                    } else {
                        _uiState.value = UpdateUiState.Idle
                    }
                }
            }.onFailure { e ->
                _uiState.value = UpdateUiState.Error(e.message ?: "检查更新失败")
            }
        }
    }

    // 静默每日检查更新
    suspend fun silentCheckUpdateIfNeeded() {
        // 检查今天是否已经检查过
        if (!shouldCheckToday()) return

        val result = updateChecker.checkUpdate()

        result.onSuccess { checkResult ->
            if (checkResult.hasNewVersion) {
                _silentUpdateResult.value = checkResult
            }
        }

        // 标记今天已检查
        prefs.setLastUpdateCheckDate(getTodayDateString())
    }

    // 获取静默检查发现的更新
    fun consumeSilentUpdate(): UpdateChecker.CheckUpdateResult? {
        val result = _silentUpdateResult.value
        _silentUpdateResult.value = null
        return result
    }

    // 检查今天是否需要检查
    private suspend fun shouldCheckToday(): Boolean {
        val lastCheckDate = prefs.getLastUpdateCheckDate()
        val today = getTodayDateString()
        return lastCheckDate != today
    }

    // 获取今天的日期字符串
    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun openDownloadPage() {
        updateChecker.openReleasesPage()
    }

    fun resetState() {
        if (_uiState.value !is UpdateUiState.Checking) {
            _uiState.value = UpdateUiState.Idle
        }
    }
}