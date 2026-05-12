package top.cenmin.tailcontrol.ui.screen.netcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.NetcheckHistoryStore
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.NetcheckReport
import javax.inject.Inject

data class NetcheckUiState(
    val running: Boolean = false,
    val current: String = "",
)

@HiltViewModel
class NetcheckViewModel @Inject constructor(
    private val tailRepo: TailscaleRepository,
    private val historyStore: NetcheckHistoryStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(NetcheckUiState())
    val ui: StateFlow<NetcheckUiState> = _ui.asStateFlow()

    val history: StateFlow<List<NetcheckReport>> =
        historyStore.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** netcheck 是一次性命令，跑到结束。运行时按钮 disable，没有手动取消。 */
    fun run() {
        if (_ui.value.running) return
        _ui.value = NetcheckUiState(running = true, current = "")
        viewModelScope.launch(Dispatchers.IO) {
            val sb = StringBuilder()
            tailRepo.netcheck().collect { line ->
                sb.appendLine(line)
                _ui.value = _ui.value.copy(current = sb.toString())
            }
            val final = sb.toString().trim()
            if (final.isNotEmpty()) {
                historyStore.add(NetcheckReport(System.currentTimeMillis(), final))
            }
            _ui.value = _ui.value.copy(running = false)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }
}
