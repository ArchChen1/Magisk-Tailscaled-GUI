package top.cenmin.tailcontrol.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.core.model.TailscaleStatus
import javax.inject.Inject

data class HomeUiState(
    val status: TailscaleStatus = TailscaleStatus(),
    val isRefreshing: Boolean = false,
    val countdownSeconds: Int = 0,
    val dismissedHealthCheck: Set<String> = emptySet(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: TailscaleRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var healthBannerDisabled = false

    init {
        startPolling()
        viewModelScope.launch {
            prefs.healthBannerDisabled.collect { disabled ->
                healthBannerDisabled = disabled
            }
        }
    }

    fun dismissAllHealthCheck() {
        val allMessages = _state.value.status.healthCheck.toSet()
        _state.value = _state.value.copy(dismissedHealthCheck = allMessages)
    }
    // 获取未关闭的健康消息
    fun getUndismissedHealthCheck(): List<String> {
        // 如果用户在设置中禁用了横幅，直接返回空列表
        if (healthBannerDisabled) return emptyList()
        // 如果服务已停止，不显示横幅
        val backendState = _state.value.status.backendState
        if (backendState == BackendState.Stopped ||
            backendState == BackendState.DaemonOffline) {
            return emptyList()
        }
        return _state.value.status.healthCheck
            .filterNot { _state.value.dismissedHealthCheck.contains(it) }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                refreshOnce()
                for (i in REFRESH_SECONDS downTo 1) {
                    _state.value = _state.value.copy(countdownSeconds = i)
                    delay(1000)
                }
            }
        }
    }

    private suspend fun refreshOnce() {
        val status = repo.fetchStatus()
        _state.value = _state.value.copy(status = status, isRefreshing = false)
    }

    fun manualRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isRefreshing = true)
            refreshOnce()
        }
    }

    fun toggleTailscale() {
        viewModelScope.launch(Dispatchers.IO) {
            when (_state.value.status.backendState) {
                BackendState.Running, BackendState.Starting -> repo.down()
                BackendState.Stopped, BackendState.NeedsLogin -> repo.up()
                BackendState.DaemonOffline -> {
                    repo.daemonStart()
                    repo.up()
                }
                else -> {
                    repo.daemonRestart()
                    repo.up()
                }
            }
            refreshOnce()
        }
    }

    companion object {
        const val REFRESH_SECONDS = 5
    }
}
