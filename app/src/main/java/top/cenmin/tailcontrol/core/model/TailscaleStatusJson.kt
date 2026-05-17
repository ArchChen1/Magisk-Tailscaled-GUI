package top.cenmin.tailcontrol.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val TailscaleJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

@Serializable
data class TailscaleStatusJson(
    @SerialName("BackendState") val backendState: String? = null,
    @SerialName("Self") val self: PeerJson? = null,
    @SerialName("Peer") val peer: Map<String, PeerJson>? = null,
    @SerialName("User") val user: Map<String, UserJson>? = null,
    @SerialName("MagicDNSSuffix") val magicDnsSuffix: String? = null,
    @SerialName("CurrentTailnet") val currentTailnet: TailnetJson? = null,
    @SerialName("Health") val health: List<String>? = null,
)

@Serializable
data class PeerJson(
    @SerialName("HostName") val hostName: String? = null,
    @SerialName("DNSName") val dnsName: String? = null,
    @SerialName("OS") val os: String? = null,
    @SerialName("UserID") val userId: Long? = null,
    @SerialName("TailscaleIPs") val tailscaleIps: List<String>? = null,
    @SerialName("Relay") val relay: String? = null,
    @SerialName("LastSeen") val lastSeen: String? = null,
    @SerialName("Online") val online: Boolean = false,
    @SerialName("PrimaryRoutes") val primaryRoutes: List<String>? = null,
    @SerialName("AllowedIPs") val allowedIps: List<String>? = null,
    @SerialName("ExitNodeOption") val exitNodeOption: Boolean = false,
    @SerialName("ExitNode") val exitNode: Boolean = false,
    @SerialName("Active") val active: Boolean = false,
    @SerialName("RxBytes") val rxBytes: Long? = null,
    @SerialName("TxBytes") val txBytes: Long? = null,
    @SerialName("CurAddr") val curAddr: String? = null,
)

@Serializable
data class UserJson(
    @SerialName("ID") val id: Long? = null,
    @SerialName("LoginName") val loginName: String? = null,
    @SerialName("DisplayName") val displayName: String? = null,
    @SerialName("ProfilePicURL") val profilePicUrl: String? = null,
)

@Serializable
data class TailnetJson(
    @SerialName("Name") val name: String? = null,
    @SerialName("MagicDNSSuffix") val magicDnsSuffix: String? = null,
    @SerialName("MagicDNSEnabled") val magicDnsEnabled: Boolean = false,
)
