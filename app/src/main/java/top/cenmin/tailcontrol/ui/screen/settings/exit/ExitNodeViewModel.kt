package top.cenmin.tailcontrol.ui.screen.settings.exit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.TailscaleDevice
import javax.inject.Inject

data class ExitNodeUiState(
    val candidates: List<TailscaleDevice> = emptyList(),
    val selected: String = "",
    val suggestion: String? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
    val saveDone: Boolean = false,
)

@HiltViewModel
class ExitNodeViewModel @Inject constructor(
    private val tailRepo: TailscaleRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExitNodeUiState())
    val ui: StateFlow<ExitNodeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val status = tailRepo.fetchStatus()
            val savedExit = prefs.tailscaleSettings.first().exitNode
            val candidates = status.peers.filter { it.exitNodeOption }
            _ui.value = _ui.value.copy(candidates = candidates, selected = savedExit)
            // 拉一次建议
            runCatching {
                val s = tailRepo.exitNodeSuggest()
                if (!s.isNullOrBlank()) _ui.value = _ui.value.copy(suggestion = s)
            }
        }
    }

    fun select(name: String) {
        _ui.value = _ui.value.copy(selected = name)
    }

    fun applySuggestion() {
        _ui.value.suggestion?.let { select(it) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(saving = true)
            val current = prefs.tailscaleSettings.first()
            val updated = current.copy(exitNode = _ui.value.selected)
            val r = tailRepo.set("--exit-node=${shellQuote(updated.exitNode)}")
            if (!r.ok && r.text.isNotBlank()) {
                _ui.value = _ui.value.copy(saving = false, saveError = r.text)
                return@launch
            }
            prefs.saveTailscaleSettings(updated)
            _ui.value = _ui.value.copy(saving = false, saveDone = true)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onDone()
            }
        }
    }

    fun consumeError() { _ui.value = _ui.value.copy(saveError = null) }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
