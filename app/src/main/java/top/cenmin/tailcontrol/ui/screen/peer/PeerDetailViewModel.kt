package top.cenmin.tailcontrol.ui.screen.peer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.navigation.toRoute
import top.cenmin.tailcontrol.core.data.TailscaleRepository
import top.cenmin.tailcontrol.core.model.TailscaleDevice
import top.cenmin.tailcontrol.core.model.WhoisInfo
import top.cenmin.tailcontrol.ui.nav.Destinations
import javax.inject.Inject

data class PeerUiState(
    val peer: TailscaleDevice? = null,
    val whois: WhoisInfo? = null,
    val pingActive: Boolean = false,
    val pingLines: List<String> = emptyList(),
    val rttSamples: List<Double> = emptyList(),
    val notFound: Boolean = false,
)

@HiltViewModel
class PeerDetailViewModel @Inject constructor(
    private val tailRepo: TailscaleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val name: String = savedStateHandle.toRoute<Destinations.PeerDetail>().name

    private val _ui = MutableStateFlow(PeerUiState())
    val ui: StateFlow<PeerUiState> = _ui.asStateFlow()

    private var pingJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val status = tailRepo.fetchStatus()
            val peer = (status.peers + listOfNotNull(status.self)).firstOrNull { it.name == name }
            _ui.value = _ui.value.copy(peer = peer, notFound = peer == null)
            // 拉取 whois (用 IPv4 优先，回退 IPv6)
            val ip = peer?.ipv4 ?: peer?.ipv6
            if (!ip.isNullOrBlank()) {
                runCatching {
                    val w = tailRepo.whois(ip)
                    _ui.value = _ui.value.copy(whois = w)
                }
            }
        }
    }

    /** 给用户一个可复制的 tailscale ssh 命令（在 Termux 里执行）。 */
    val sshCommand: String?
        get() = _ui.value.peer?.let { peer ->
            val host = peer.dnsName.trimEnd('.').takeIf { it.isNotBlank() }
                ?: peer.name.takeIf { it.isNotBlank() }
                ?: return null
            "tailscale ssh $host"
        }

    val avgRtt: Double
        get() = _ui.value.rttSamples.takeIf { it.isNotEmpty() }?.average() ?: 0.0

    val p95Rtt: Double
        get() {
            val s = _ui.value.rttSamples.sorted()
            if (s.isEmpty()) return 0.0
            val idx = ((s.size - 1) * 0.95).toInt()
            return s[idx]
        }

    fun startPing() {
        val peer = _ui.value.peer ?: return
        val target = peer.ipv4 ?: peer.ipv6 ?: peer.name
        pingJob?.cancel()
        _ui.value = _ui.value.copy(pingActive = true, pingLines = emptyList(), rttSamples = emptyList())
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            tailRepo.ping(target).collect { line ->
                val cur = _ui.value
                val rtt = parseRtt(line)
                val samples = if (rtt != null) (cur.rttSamples + rtt).takeLast(60) else cur.rttSamples
                _ui.value = cur.copy(
                    pingLines = (cur.pingLines + line).takeLast(80),
                    rttSamples = samples,
                )
            }
            _ui.value = _ui.value.copy(pingActive = false)
        }
    }

    fun stopPing() {
        pingJob?.cancel(); pingJob = null
        _ui.value = _ui.value.copy(pingActive = false)
    }

    /** 解析 `pong from X via DERP/direct in 12.34ms` 行的毫秒数。 */
    private fun parseRtt(line: String): Double? {
        val m = Regex("(?:in\\s+)([0-9]+(?:\\.[0-9]+)?)\\s*ms").find(line) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        pingJob?.cancel()
    }
}
