package top.cenmin.tailcontrol.ui.screen.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.AccountItem
import top.cenmin.tailcontrol.core.model.TailscaleSettings
import javax.inject.Inject

data class AccountsUiState(
    val accounts: List<AccountItem> = emptyList(),
    val isSwitching: Boolean = false,
    val loginDialogText: String = "",
    val loginDialogOpen: Boolean = false,
    val loginUrl: String = "",
    val loginSuccess: Boolean = false,
    val logoutDialogOpen: Boolean = false,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val tailRepo: TailscaleRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AccountsUiState())
    val ui: StateFlow<AccountsUiState> = _ui.asStateFlow()

    private var loginJob: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(accounts = tailRepo.listAccounts())
        }
    }

    fun switchAccount(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(isSwitching = true)
            tailRepo.switchAccount(id)
            _ui.value = _ui.value.copy(isSwitching = false, accounts = tailRepo.listAccounts())
        }
    }

    fun startLogin(extraArgs: String, waitingMessage: String) {
        loginJob?.cancel()
        _ui.value = _ui.value.copy(
            loginDialogOpen = true,
            loginDialogText = waitingMessage,
            loginUrl = "",
            loginSuccess = false,
        )
        loginJob = viewModelScope.launch(Dispatchers.IO) {
            tailRepo.login(extraArgs).collect { line ->
                if (line.contains("https://")) {
                    val url = "https://" + line.substringAfter("https://").substringBefore(' ').trim()
                    _ui.value = _ui.value.copy(loginUrl = url, loginDialogText = url)
                }
                if (line.contains("Success.", ignoreCase = true)) {
                    _ui.value = _ui.value.copy(loginSuccess = true, loginDialogText = "Success.")
                    refresh()
                }
            }
        }
    }

    fun startLoginWithSavedSettings(waitingMessage: String) {
        viewModelScope.launch {
            val settings: TailscaleSettings = prefs.tailscaleSettings.first()
            startLogin(settings.toCliArgs(), waitingMessage)
        }
    }

    fun closeLoginDialog() {
        loginJob?.cancel(); loginJob = null
        _ui.value = _ui.value.copy(loginDialogOpen = false)
    }

    fun openLogoutDialog() { _ui.value = _ui.value.copy(logoutDialogOpen = true) }
    fun dismissLogoutDialog() { _ui.value = _ui.value.copy(logoutDialogOpen = false) }

    fun confirmLogout() {
        viewModelScope.launch(Dispatchers.IO) {
            tailRepo.logout()
            _ui.value = _ui.value.copy(logoutDialogOpen = false)
            refresh()
        }
    }
}
