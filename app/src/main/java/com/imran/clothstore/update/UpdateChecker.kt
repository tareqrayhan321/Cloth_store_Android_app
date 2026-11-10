package com.imran.clothstore.update

import com.imran.clothstore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/tareqrayhan321/Cloth_store_Android_app/releases/latest"

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val apkUrl: String?
)

object UpdateChecker {
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ClothStore/${BuildConfig.VERSION_NAME}")
            }

            try {
                val http = connection
                if (http.responseCode !in 200..299) return@withContext null
                val release = JSONObject(http.inputStream.bufferedReader().use { it.readText() })
                val remoteVersion = release.optString("tag_name").removePrefix("v")
                if (remoteVersion.isBlank() || !isNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                    return@withContext null
                }

                var apkUrl: String? = null
                val assets = release.optJSONArray("assets")
                if (assets != null) {
                    for (index in 0 until assets.length()) {
                        val asset = assets.optJSONObject(index) ?: continue
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }

                UpdateInfo(
                    versionName = remoteVersion,
                    releaseUrl = release.optString("html_url"),
                    apkUrl = apkUrl
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            // Update checks must never prevent the app from opening offline.
            null
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val maxParts = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until maxParts) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) return remotePart > currentPart
        }
        return false
    }
}
