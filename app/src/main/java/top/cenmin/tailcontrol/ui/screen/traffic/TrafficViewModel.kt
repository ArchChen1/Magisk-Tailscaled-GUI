package top.cenmin.tailcontrol.ui.screen.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.cenmin.tailcontrol.core.data.TrafficRepository
import javax.inject.Inject

private const val MAX_POINTS = 120

data class TrafficUiState(
    val rxBps: List<Double> = emptyList(),
    val txBps: List<Double> = emptyList(),
    val totalRxBytes: Long = 0,
    val totalTxBytes: Long = 0,
    val hasData: Boolean = false,
)

@HiltViewModel
class TrafficViewModel @Inject constructor(
    private val trafficRepo: TrafficRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TrafficUiState())
    val ui: StateFlow<TrafficUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            trafficRepo.samples().collect { sample ->
                val cur = _ui.value
                _ui.value = TrafficUiState(
                    rxBps = (cur.rxBps + sample.rxBps).takeLast(MAX_POINTS),
                    txBps = (cur.txBps + sample.txBps).takeLast(MAX_POINTS),
                    totalRxBytes = sample.rxBytes,
                    totalTxBytes = sample.txBytes,
                    hasData = true,
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024
        idx++
    }
    return "%.2f %s".format(value, units[idx])
}

fun formatBps(bps: Double): String {
    if (bps <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KiB/s", "MiB/s", "GiB/s")
    var v = bps
    var idx = 0
    while (v >= 1024 && idx < units.lastIndex) {
        v /= 1024
        idx++
    }
    return "%.1f %s".format(v, units[idx])
}

