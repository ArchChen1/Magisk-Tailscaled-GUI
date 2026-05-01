package top.cenmin.tailcontrol.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.DnsStatus
import top.cenmin.tailcontrol.core.model.TailscaleSettings
import javax.inject.Inject

data class SettingsUiState(
    val settings: TailscaleSettings = TailscaleSettings(),
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
    val dynamicColor: Boolean = true,
    val dnsStatus: DnsStatus? = null,
    val sshServerEnabled: Boolean = false,
    val sshUpdating: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val tailRepo: TailscaleRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.tailscaleSettings.collect { _ui.value = _ui.value.copy(settings = it) }
        }
        viewModelScope.launch {
            prefs.dynamicColorEnabled.collect { _ui.value = _ui.value.copy(dynamicColor = it) }
        }
        refreshIdentity()
    }

    fun refreshIdentity() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = tailRepo.fetchStatus()
            val self = status.self
            val isLoggedIn = self != null && status.backendState !is top.cenmin.tailcontrol.core.model.BackendState.NeedsLogin
            val displayName = self?.userId?.let { uid -> status.users[uid]?.displayName }.orEmpty()
            _ui.value = _ui.value.copy(
                isLoggedIn = isLoggedIn,
                username = displayName.ifEmpty { self?.name.orEmpty() },
            )
            // DNS 状态
            runCatching {
                _ui.value = _ui.value.copy(dnsStatus = tailRepo.dnsStatus())
            }
        }
    }

    fun setSshServer(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(sshUpdating = true)
            val r = tailRepo.setSshServer(enabled)
            _ui.value = if (r.ok) _ui.value.copy(sshServerEnabled = enabled, sshUpdating = false)
            else _ui.value.copy(sshUpdating = false, saveError = r.text.ifBlank { "ssh toggle failed" })
        }
    }

    fun update(update: (TailscaleSettings) -> TailscaleSettings) {
        _ui.value = _ui.value.copy(settings = update(_ui.value.settings))
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _ui.value.settings
            val r = tailRepo.set(s.toCliArgs())
            if (!r.ok && r.text.isNotBlank()) {
                _ui.value = _ui.value.copy(saveError = r.text)
            } else {
                prefs.saveTailscaleSettings(s)
                _ui.value = _ui.value.copy(saveSuccess = true)
            }
        }
    }

    fun consumeSaveError() { _ui.value = _ui.value.copy(saveError = null) }
    fun consumeSaveSuccess() { _ui.value = _ui.value.copy(saveSuccess = false) }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicColorEnabled(enabled) }
    }
}
