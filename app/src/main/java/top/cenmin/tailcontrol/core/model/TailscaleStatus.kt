package top.cenmin.tailcontrol.core.model

data class TailscaleStatus(
    val backendState: BackendState = BackendState.Unknown,
    val self: TailscaleDevice? = null,
    val peers: List<TailscaleDevice> = emptyList(),
    val users: Map<Long, UserJson> = emptyMap(),
    val rawJson: String = "",
)
