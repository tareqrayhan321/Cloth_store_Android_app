package com.imran.clothstore.update

import com.imran.clothstore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// GitHub API নয় — প্রতিটা রিলিজে asset হিসেবে থাকা version.json সরাসরি
// ডাউনলোড-লিংক থেকে পড়া হয় (releases/latest/download/… নিজে থেকেই সর্বশেষ
// রিলিজে redirect করে)। আগের API-পথ unauthenticated rate-limit-এ (শেয়ার্ড
// IP-তে 403) আটকে যেত বলে ফোনে আপডেট-পপআপ আসত না — তাই এই পথ (2026-11-12)।
// version.json রিলিজ workflow প্রতিবার বানিয়ে রিলিজে asset করে দেয়।
private const val VERSION_JSON_URL =
    "https://github.com/tareqrayhan321/Cloth_store_Android_app/releases/latest/download/version.json"

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val apkUrl: String?
)

object UpdateChecker {
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(VERSION_JSON_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ClothStore/${BuildConfig.VERSION_NAME}")
            }

            try {
                val http = connection
                if (http.responseCode !in 200..299) return@withContext null
                val json = JSONObject(http.inputStream.bufferedReader().use { it.readText() })
                val remoteVersion = json.optString("versionName").removePrefix("v")
                if (remoteVersion.isBlank() || !isNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                    return@withContext null
                }

                UpdateInfo(
                    versionName = remoteVersion,
                    releaseUrl = json.optString("releaseUrl"),
                    apkUrl = json.optString("apkUrl").takeIf { it.isNotBlank() }
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
