package top.cenmin.tailcontrol.ui.screen.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.LogRepository
import top.cenmin.tailcontrol.core.data.LogSource
import javax.inject.Inject

private const val MAX_LINES = 800

data class LogUiState(
    val source: LogSource = LogSource.Tailscaled,
    val lines: List<String> = emptyList(),
    val paused: Boolean = false,
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepo: LogRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LogUiState())
    val ui: StateFlow<LogUiState> = _ui.asStateFlow()

    private var streamJob: Job? = null

    init { switchSource(LogSource.Tailscaled) }

    fun switchSource(source: LogSource, initialLines: Int = 200) {
        streamJob?.cancel()
        _ui.value = _ui.value.copy(source = source, lines = emptyList())
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            logRepo.tail(source, initialLines).collect { line ->
                if (_ui.value.paused) return@collect
                val cur = _ui.value.lines
                val next = if (cur.size >= MAX_LINES) cur.drop(cur.size - MAX_LINES + 1) + line
                else cur + line
                _ui.value = _ui.value.copy(lines = next)
            }
        }
    }

    fun togglePause() {
        _ui.value = _ui.value.copy(paused = !_ui.value.paused)
    }

    /** 清空 UI 列表 + 重启 tail 流（不读历史，只看新增行） */
    fun clear() {
        switchSource(_ui.value.source, initialLines = 0)
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
