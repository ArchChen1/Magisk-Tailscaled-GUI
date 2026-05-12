package top.cenmin.tailcontrol.core.model

import androidx.annotation.StringRes
import top.cenmin.tailcontrol.R

enum class StatusTone { ONLINE, WARNING, OFFLINE, ERROR, UNKNOWN }

sealed interface BackendState {
    @get:StringRes val labelRes: Int
    val tone: StatusTone

    data object Running : BackendState {
        override val labelRes = R.string.status_service_running
        override val tone = StatusTone.ONLINE
    }
    data object Stopped : BackendState {
        override val labelRes = R.string.status_service_stopped
        override val tone = StatusTone.OFFLINE
    }
    data object Starting : BackendState {
        override val labelRes = R.string.status_service_starting
        override val tone = StatusTone.WARNING
    }
    data object NeedsLogin : BackendState {
        override val labelRes = R.string.status_service_needslogin
        override val tone = StatusTone.WARNING
    }
    data object DaemonOffline : BackendState {
        override val labelRes = R.string.status_protect_offline
        override val tone = StatusTone.ERROR
    }
    data object Unknown : BackendState {
        override val labelRes = R.string.unknown
        override val tone = StatusTone.UNKNOWN
    }

    companion object {
        fun fromCli(raw: String?): BackendState = when (raw) {
            "Running" -> Running
            "Stopped" -> Stopped
            "Starting" -> Starting
            "NeedsLogin" -> NeedsLogin
            null, "" -> DaemonOffline
            else -> Unknown
        }
    }
}
