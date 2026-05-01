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
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.core.model.TailscaleStatus
import javax.inject.Inject

data class HomeUiState(
    val status: TailscaleStatus = TailscaleStatus(),
    val isRefreshing: Boolean = false,
    val countdownSeconds: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: TailscaleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        startPolling()
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
