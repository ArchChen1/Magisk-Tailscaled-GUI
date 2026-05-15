package top.cenmin.tailcontrol.core.network

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import top.cenmin.tailcontrol.BuildConfig
import top.cenmin.tailcontrol.core.model.UpdateInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val GITHUB_API_URL = "https://api.github.com/repos/ArchChen1/Magisk-Tailscaled-GUI/releases/latest"

    suspend fun checkUpdate(): Result<CheckUpdateResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty(
                    "User-Agent",
                    "Magisk-Tailscaled-GUI/${BuildConfig.VERSION_NAME}"
                )
            }

            val responseCode = connection.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP $responseCode"))
            }

            val responseBody = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            val updateInfo = json.decodeFromString<UpdateInfo>(responseBody)
            val currentVersion = BuildConfig.VERSION_NAME
            val hasNewVersion = updateInfo.isNewerThan(currentVersion)

            Result.success(CheckUpdateResult(hasNewVersion, updateInfo, currentVersion))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openReleasesPage() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://github.com/ArchChen1/Magisk-Tailscaled-GUI/releases/latest".toUri()
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    data class CheckUpdateResult(
        val hasNewVersion: Boolean,
        val updateInfo: UpdateInfo,
        val currentVersion: String
    )
}