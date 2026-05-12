package top.cenmin.tailcontrol.core.model

data class DnsStatus(
    val tailscaleDnsEnabled: Boolean = false,
    val magicDnsEnabled: Boolean = false,
    val magicDnsSuffix: String? = null,
    val deviceDnsName: String? = null,
)
