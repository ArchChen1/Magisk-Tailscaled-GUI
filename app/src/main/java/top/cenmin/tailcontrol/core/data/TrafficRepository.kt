package top.cenmin.tailcontrol.core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import top.cenmin.tailcontrol.core.shell.RootShell
import javax.inject.Inject
import javax.inject.Singleton

data class TrafficSample(
    val timestampMillis: Long,
    val rxBytes: Long,
    val txBytes: Long,
    /** Bytes per second (instantaneous between samples). */
    val rxBps: Double,
    val txBps: Double,
)

@Singleton
class TrafficRepository @Inject constructor(
    private val shell: RootShell,
) {
    /**
     * 采样 /proc/<pidof tailscaled>/net/dev 中 tailscale0 的 RX/TX 字节，
     * 每 [sampleMillis] 毫秒发一个 [TrafficSample]。
     */
    fun samples(sampleMillis: Long = 500L): Flow<TrafficSample> = flow {
        var lastRx: Long = -1
        var lastTx: Long = -1
        var lastTs = 0L
        while (true) {
            val pid = shell.execText("pidof tailscaled").trim().split(Regex("\\s+"))
                .firstOrNull()?.toIntOrNull()
            if (pid == null) {
                delay(sampleMillis)
                continue
            }
            val raw = shell.execText("cat /proc/$pid/net/dev 2>/dev/null")
            val line = raw.lineSequence().firstOrNull { it.contains("tailscale0:") }
            if (line == null) {
                delay(sampleMillis)
                continue
            }
            // proc/net/dev 行格式：
            //   tailscale0: rx_bytes packets errs drop fifo frame compressed multicast tx_bytes packets ...
            val tokens = line.substringAfter(":").trim().split(Regex("\\s+"))
            val rx = tokens.getOrNull(0)?.toLongOrNull() ?: 0L
            val tx = tokens.getOrNull(8)?.toLongOrNull() ?: 0L
            val now = System.currentTimeMillis()
            val rxBps = if (lastRx >= 0 && lastTs > 0) {
                ((rx - lastRx) * 1000.0 / (now - lastTs).coerceAtLeast(1)).coerceAtLeast(0.0)
            } else 0.0
            val txBps = if (lastTx >= 0 && lastTs > 0) {
                ((tx - lastTx) * 1000.0 / (now - lastTs).coerceAtLeast(1)).coerceAtLeast(0.0)
            } else 0.0
            emit(TrafficSample(now, rx, tx, rxBps, txBps))
            lastRx = rx
            lastTx = tx
            lastTs = now
            delay(sampleMillis)
        }
    }.flowOn(Dispatchers.IO)
}
