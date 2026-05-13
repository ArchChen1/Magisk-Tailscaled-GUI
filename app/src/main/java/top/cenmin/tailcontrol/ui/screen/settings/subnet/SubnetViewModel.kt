package top.cenmin.tailcontrol.ui.screen.settings.subnet

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
import javax.inject.Inject

data class SubnetUiState(
    val rows: List<String> = listOf(""),
    val saving: Boolean = false,
    val saveError: String? = null,
)

private val cidrRegex = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}/\\d{1,2}$")

@HiltViewModel
class SubnetViewModel @Inject constructor(
    private val tailRepo: TailscaleRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SubnetUiState())
    val ui: StateFlow<SubnetUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = prefs.tailscaleSettings.first().advertiseRoutes
            val rows = if (saved.isBlank()) listOf("") else saved.split(",").map { it.trim() }
            _ui.value = _ui.value.copy(rows = rows.ifEmpty { listOf("") })
        }
    }

    fun update(index: Int, value: String) {
        val rows = _ui.value.rows.toMutableList()
        if (index in rows.indices) rows[index] = value
        _ui.value = _ui.value.copy(rows = rows)
    }

    fun add() {
        _ui.value = _ui.value.copy(rows = _ui.value.rows + "")
    }

    fun remove(index: Int) {
        val rows = _ui.value.rows.toMutableList()
        if (index in rows.indices) rows.removeAt(index)
        _ui.value = _ui.value.copy(rows = rows.ifEmpty { listOf("") })
    }

    fun isValid(value: String): Boolean = value.isBlank() || cidrRegex.matches(value)

    fun save(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val joined = _ui.value.rows.map { it.trim() }.filter { it.isNotBlank() }.joinToString(",")
            if (_ui.value.rows.any { !isValid(it) }) {
                _ui.value = _ui.value.copy(saveError = "invalid")
                return@launch
            }
            _ui.value = _ui.value.copy(saving = true)
            val current = prefs.tailscaleSettings.first()
            val updated = current.copy(advertiseRoutes = joined)
            val r = tailRepo.set("--advertise-routes='$joined'")
            if (!r.ok && r.text.isNotBlank()) {
                _ui.value = _ui.value.copy(saving = false, saveError = r.text)
                return@launch
            }
            prefs.saveTailscaleSettings(updated)
            _ui.value = _ui.value.copy(saving = false)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onDone()
            }
        }
    }

    fun consumeError() { _ui.value = _ui.value.copy(saveError = null) }
}
