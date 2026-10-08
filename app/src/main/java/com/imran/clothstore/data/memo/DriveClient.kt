package com.imran.clothstore.data.memo

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class DriveHttpException(val code: Int, message: String) : Exception(message)

/**
 * Drive REST v3 — অতিরিক্ত লাইব্রেরি ছাড়াই HttpURLConnection দিয়ে। সব মেথড ব্লকিং; IO ডিসপ্যাচারে চালাতে হবে।
 */
class DriveClient {

    data class Uploaded(val id: String, val size: Long)

    /** অ্যাপের ফোল্ডার খুঁজে বের করে, না থাকলে বানায় */
    fun findOrCreateFolder(token: String, name: String): String {
        val q = "name='$name' and mimeType='$FOLDER_MIME' and trashed=false"
        val url = "$API/files?q=${enc(q)}&fields=${enc("files(id,name)")}&spaces=drive&pageSize=1"
        val found = JSONObject(request("GET", url, token).body)
            .optJSONArray("files") ?: JSONArray()
        if (found.length() > 0) return found.getJSONObject(0).getString("id")

        val body = JSONObject().put("name", name).put("mimeType", FOLDER_MIME).toString()
        val created = request(
            "POST", "$API/files?fields=id", token,
            contentType = "application/json; charset=UTF-8",
            payload = body.toByteArray()
        )
        return JSONObject(created.body).getString("id")
    }

    fun upload(token: String, folderId: String, fileName: String, mime: String, bytes: ByteArray): Uploaded {
        val boundary = "memo_" + UUID.randomUUID().toString().replace("-", "")
        val meta = JSONObject()
            .put("name", fileName)
            .put("mimeType", mime)
            .put("parents", JSONArray().put(folderId))
            .toString()

        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: $mime\r\n\r\n"
        val tail = "\r\n--$boundary--"
        val payload = head.toByteArray() + bytes + tail.toByteArray()

        val res = request(
            "POST", "$UPLOAD/files?uploadType=multipart&fields=${enc("id,size")}", token,
            contentType = "multipart/related; boundary=$boundary",
            payload = payload
        )
        val json = JSONObject(res.body)
        return Uploaded(json.getString("id"), json.optString("size", "0").toLongOrNull() ?: bytes.size.toLong())
    }

    /** ছবির বাইট dest ফাইলে নামায় (আংশিক ডাউনলোড যেন ক্যাশে না থাকে — আগে .part, শেষে rename) */
    fun download(token: String, fileId: String, dest: File) {
        val conn = open("GET", "$API/files/$fileId?alt=media", token)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw DriveHttpException(code, readError(conn))
            dest.parentFile?.mkdirs()
            val part = File(dest.parentFile, dest.name + ".part")
            conn.inputStream.use { input -> part.outputStream().use { out -> input.copyTo(out) } }
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    /** ফাইল আগেই মুছে গেলে (404) সেটাকে সফল ধরা হয় */
    fun delete(token: String, fileId: String) {
        try {
            request("DELETE", "$API/files/$fileId", token)
        } catch (e: DriveHttpException) {
            if (e.code != 404) throw e
        }
    }

    // ── নিচের স্তর ──

    private data class Response(val code: Int, val body: String)

    private fun open(method: String, url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun request(
        method: String,
        url: String,
        token: String,
        contentType: String? = null,
        payload: ByteArray? = null
    ): Response {
        val conn = open(method, url, token)
        try {
            if (payload != null) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(payload.size)
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(payload) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw DriveHttpException(code, readError(conn))
            val body = if (code == 204) "" else conn.inputStream.bufferedReader().use { it.readText() }
            return Response(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun readError(conn: HttpURLConnection): String = try {
        conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(300) ?: "HTTP ${conn.responseCode}"
    } catch (e: Exception) {
        "HTTP error"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val FOLDER_NAME = "ImranClothStore-Memos"
    }
}
