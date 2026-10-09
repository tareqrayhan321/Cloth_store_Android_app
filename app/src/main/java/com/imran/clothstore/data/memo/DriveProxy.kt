package com.imran.clothstore.data.memo

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Apps Script web app-এর মাধ্যমে Drive-এ ছবি রাখা/আনা/মোছা। ফোনে Google লগইন লাগে না।
 * সব মেথড ব্লকিং; IO ডিসপ্যাচারে চালাতে হবে।
 */
class DriveProxy {

    data class Uploaded(val id: String, val size: Long)

    fun upload(fileName: String, mime: String, bytes: ByteArray): Uploaded {
        val json = call(
            JSONObject()
                .put("action", "upload")
                .put("name", fileName)
                .put("mime", mime)
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
        )
        return Uploaded(json.getString("id"), json.optLong("size", bytes.size.toLong()))
    }

    /** ছবির বাইট dest ফাইলে নামায় (আগে .part, শেষে rename — আংশিক ফাইল ক্যাশে থাকে না) */
    fun download(fileId: String, dest: File) {
        val json = call(JSONObject().put("action", "get").put("id", fileId))
        val bytes = Base64.decode(json.getString("data"), Base64.DEFAULT)
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        part.writeBytes(bytes)
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
    }

    fun delete(fileId: String) {
        call(JSONObject().put("action", "delete").put("id", fileId))
    }

    // ── নিচের স্তর ──

    private fun call(body: JSONObject): JSONObject {
        check(MemoConfig.isConfigured) { "MemoConfig.kt-এ SCRIPT_URL আর SCRIPT_KEY বসানো হয়নি" }
        body.put("key", MemoConfig.SCRIPT_KEY)
        val payload = body.toString().toByteArray(Charsets.UTF_8)

        val first = open(MemoConfig.SCRIPT_URL, "POST")
        var text: String
        try {
            first.doOutput = true
            first.setFixedLengthStreamingMode(payload.size)
            first.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            first.outputStream.use { it.write(payload) }
            val code = first.responseCode
            if (code in 300..399) {
                // Apps Script কাজ করে ফলাফল একটা রিডাইরেক্ট-লিংকে রাখে; সেটা GET করে পড়তে হয়
                val loc = first.getHeaderField("Location") ?: throw RuntimeException("সার্ভারের উত্তর অসম্পূর্ণ")
                val second = open(loc, "GET")
                try {
                    text = readBody(second)
                } finally {
                    second.disconnect()
                }
            } else {
                text = readBody(first)
            }
        } finally {
            first.disconnect()
        }

        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw RuntimeException("Apps Script ঠিকমতো ডেপ্লয় হয়নি (Who has access: Anyone?)")
        }
        if (!json.optBoolean("ok", false)) {
            throw RuntimeException(json.optString("error", "অজানা ত্রুটি"))
        }
        return json
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 90_000
        }

    private fun readBody(conn: HttpURLConnection): String {
        val code = conn.responseCode
        if (code !in 200..299) throw RuntimeException("সার্ভার ত্রুটি (HTTP $code)")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
