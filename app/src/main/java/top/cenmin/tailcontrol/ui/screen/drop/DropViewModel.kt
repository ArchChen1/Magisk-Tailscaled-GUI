package top.cenmin.tailcontrol.ui.screen.drop

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.DropRepository
import top.cenmin.tailcontrol.core.data.PreferencesRepository
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.BackendState
import top.cenmin.tailcontrol.core.model.ConflictBehavior
import top.cenmin.tailcontrol.core.model.DropConfig
import top.cenmin.tailcontrol.service.DropProtectService
import javax.inject.Inject

data class DropUiState(
    val config: DropConfig = DropConfig(),
    val pingAddress: String = "",
    val tailscaleState: BackendState = BackendState.Unknown,
    val daemonOutput: String = "",
    val pingDialogOpen: Boolean = false,
    val pingOutput: String = "",
    val isPinging: Boolean = false,
    val killDialogOpen: Boolean = false,
    val processCount: Int = 0,
    val errorToast: String? = null,
    val fileCommandSupported: Boolean = true,
)

@HiltViewModel
class DropViewModel @Inject constructor(
    private val app: Application,
    private val prefs: PreferencesRepository,
    private val tailRepo: TailscaleRepository,
    private val dropRepo: DropRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(DropUiState())
    val ui: StateFlow<DropUiState> = _ui.asStateFlow()

    private var pingJob: Job? = null

    val daemonOutput: StateFlow<String> = dropRepo.output
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            combine(prefs.dropConfig, prefs.pingAddress) { c, p -> c to p }
                .collect { (config, ping) ->
                    _ui.value = _ui.value.copy(config = config, pingAddress = ping)
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val supported = tailRepo.isFileCommandSupported()
            _ui.value = _ui.value.copy(fileCommandSupported = supported)
        }
        refreshTailscaleState()
    }

    fun refreshTailscaleState() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = tailRepo.fetchStatus()
            _ui.value = _ui.value.copy(tailscaleState = s.backendState)
        }
    }

    fun setEnabled(target: Boolean) {
        viewModelScope.launch {
            if (target && !_ui.value.fileCommandSupported) {
                _ui.value = _ui.value.copy(errorToast = "tailscale 1.90+ removed `file` subcommand")
                return@launch
            }
            if (target && _ui.value.tailscaleState !is BackendState.Running) {
                _ui.value = _ui.value.copy(errorToast = "Tailscale not running")
                return@launch
            }
            prefs.setDropEnabled(target)
            val cfg = prefs.dropConfig.first()
            val intent = Intent(app, DropProtectService::class.java).apply {
                action = if (target) DropProtectService.ACTION_START else DropProtectService.ACTION_STOP
                if (target) {
                    putExtra(DropProtectService.EXTRA_PATH, cfg.path)
                    putExtra(DropProtectService.EXTRA_BEHAVIOR, cfg.conflict.cliValue)
                }
            }
            if (target) app.startForegroundService(intent) else app.startService(intent)
        }
    }

    fun setPath(path: String) {
        viewModelScope.launch { prefs.setDropPath(path) }
    }

    fun setConflict(behavior: ConflictBehavior) {
        viewModelScope.launch { prefs.setConflict(behavior) }
    }

    fun setPingAddress(addr: String) {
        viewModelScope.launch { prefs.setPingAddress(addr) }
    }

    fun startPing() {
        val addr = _ui.value.pingAddress
        if (addr.isBlank()) return
        pingJob?.cancel()
        _ui.value = _ui.value.copy(pingDialogOpen = true, pingOutput = "", isPinging = true)
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            tailRepo.ping(addr).collect { line ->
                val cur = _ui.value
                _ui.value = cur.copy(pingOutput = (cur.pingOutput + line + "\n"))
            }
        }
        viewModelScope.launch {
            pingJob?.join()
            _ui.value = _ui.value.copy(isPinging = false)
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        _ui.value = _ui.value.copy(isPinging = false)
    }

    fun closePingDialog() {
        if (_ui.value.isPinging) stopPing()
        _ui.value = _ui.value.copy(pingDialogOpen = false)
    }

    fun openKillDialog() {
        viewModelScope.launch(Dispatchers.IO) {
            val n = tailRepo.pgrepTailscaleCount()
            _ui.value = _ui.value.copy(killDialogOpen = true, processCount = n)
        }
    }

    fun confirmKillProcesses() {
        viewModelScope.launch(Dispatchers.IO) {
            tailRepo.killAllTailscale()
            prefs.setDropEnabled(false)
            // 同时停 drop 服务
            val intent = Intent(app, DropProtectService::class.java).apply {
                action = DropProtectService.ACTION_STOP
            }
            app.startService(intent)
            _ui.value = _ui.value.copy(killDialogOpen = false)
        }
    }

    fun dismissKillDialog() {
        _ui.value = _ui.value.copy(killDialogOpen = false)
    }

    fun consumeError() {
        _ui.value = _ui.value.copy(errorToast = null)
    }
}
