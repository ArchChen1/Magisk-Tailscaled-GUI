package top.cenmin.tailcontrol.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    @SerialName("tag_name")
    val tagName: String,           // 版本标签
    val name: String,              // Release标题
    val body: String,              // 更新内容描述
    @SerialName("html_url")
    val htmlUrl: String,           // GitHub发布页面地址
    @SerialName("published_at")
    val publishedAt: String,       // 发布时间
    val assets: List<Asset>? = null // APK附件列表
) {
    // 提取纯版本号（去掉开头的 'v'）
    val versionNumber: String get() = tagName.removePrefix("v")

    // 判断是否比当前版本新
    fun isNewerThan(currentVersion: String): Boolean {
        return compareVersions(versionNumber, currentVersion) > 0
    }


    @Serializable
    data class Asset(
        val name: String,
        @SerialName("browser_download_url")
        val browserDownloadUrl: String
    )
}

// 版本号比较
fun compareVersions(version1: String, version2: String): Int {
    val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
    val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }

    for (i in 0 until maxOf(parts1.size, parts2.size)) {
        val num1 = if (i < parts1.size) parts1[i] else 0
        val num2 = if (i < parts2.size) parts2[i] else 0
        if (num1 != num2) return num1.compareTo(num2)
    }
    return 0
}