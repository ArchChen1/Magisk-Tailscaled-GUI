package top.cenmin.tailcontrol.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TailscaleSettings(
    val acceptRoutes: Boolean = true,
    val acceptDns: Boolean = false,
    val exitNode: String = "",
    val advertiseExitNode: Boolean = false,
    val advertiseRoutes: String = "",
    val customName: String = "",
    val customParams: String = "",
) {
    fun toCliArgs(): String {
        val args = mutableListOf(
            "--accept-routes=$acceptRoutes",
            "--accept-dns=$acceptDns",
            "--advertise-exit-node=$advertiseExitNode",
            "--exit-node=${shellQuote(exitNode)}",
            "--advertise-routes=${shellQuote(advertiseRoutes)}",
            "--hostname=${shellQuote(customName)}",
        )
        if (customParams.isNotBlank()) args.add(customParams)
        return args.joinToString(" ")
    }

    private fun shellQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"
}
