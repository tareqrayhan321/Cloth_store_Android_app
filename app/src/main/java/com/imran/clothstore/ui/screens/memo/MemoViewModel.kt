package com.imran.clothstore.ui.screens.memo

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.imran.clothstore.data.memo.DriveProxy
import com.imran.clothstore.data.memo.MemoConfig
import com.imran.clothstore.data.memo.MemoMeta
import com.imran.clothstore.data.memo.MemoRepository
import com.imran.clothstore.util.MemoImage
import com.imran.clothstore.util.toBengaliDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UploadProgress(val done: Int, val total: Int)

/**
 * পার্টির ম্যামো-ছবি: ছবি যায় Google Drive-এ (Apps Script-এর মাধ্যমে, ফোনে লগইন ছাড়া),
 * metadata (ফাইল আইডি, নাম, সাইজ, সময়) যায় Firestore-এ।
 */
class MemoViewModel(
    private val partyKey: String,
    private val partyName: String,
    private val appContext: Context,
    private val repo: MemoRepository = MemoRepository(),
    private val drive: DriveProxy = DriveProxy()
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    val memos: StateFlow<List<MemoMeta>> = repo.observe(partyKey)
        .catch { _message.value = "ম্যামো তালিকা পড়া যায়নি: ${it.message}"; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _progress = MutableStateFlow<UploadProgress?>(null)
    val progress: StateFlow<UploadProgress?> = _progress.asStateFlow()

    val configured: Boolean get() = MemoConfig.isConfigured

    private val downloadGate = Semaphore(3)
    private val cacheDir get() = File(appContext.cacheDir, "memo_images")

    // ── আপলোড ──

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty() || _progress.value != null) return
        if (!configured) {
            _message.value = "MemoConfig.kt-এ Apps Script-এর URL ও কী বসানো হয়নি"
            return
        }
        viewModelScope.launch {
            var ok = 0
            var lastError: String? = null
            uris.forEachIndexed { i, uri ->
                _progress.value = UploadProgress(i, uris.size)
                try {
                    uploadOne(uri)
                    ok++
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                }
            }
            _progress.value = null
            val fail = uris.size - ok
            _message.value = when {
                fail == 0 -> "${ok.toBengaliDigits()}টি ছবি সংরক্ষিত হয়েছে"
                ok == 0 -> "আপলোড ব্যর্থ: $lastError"
                else -> "${ok.toBengaliDigits()}টি সংরক্ষিত, ${fail.toBengaliDigits()}টি ব্যর্থ: $lastError"
            }
        }
    }

    private suspend fun uploadOne(uri: Uri) {
        val img = withContext(Dispatchers.IO) { MemoImage.compress(appContext.contentResolver, uri) }
            ?: throw IllegalStateException("ছবি পড়া যায়নি")
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val safeName = partyName.replace(Regex("[\\\\/:*?\"<>|']"), "").trim().take(40).ifBlank { "party" }
        val fileName = "${safeName}_$stamp.jpg"

        val up = withContext(Dispatchers.IO) { drive.upload(fileName, "image/jpeg", img.bytes) }

        // ডিসপ্লের জন্য সাথে সাথে ক্যাশে রাখা — আবার ডাউনলোড লাগবে না
        withContext(Dispatchers.IO) { cacheFile(up.id).apply { parentFile?.mkdirs() }.writeBytes(img.bytes) }

        try {
            repo.add(
                partyKey,
                MemoMeta(
                    driveFileId = up.id, fileName = fileName, mimeType = "image/jpeg",
                    sizeBytes = img.bytes.size.toLong(), width = img.width, height = img.height, createdAt = now
                )
            )
        } catch (e: Exception) {
            // metadata না লিখতে পারলে Drive-এ এতিম ফাইল রেখে দেওয়া হয় না
            runCatching { withContext(Dispatchers.IO) { drive.delete(up.id) } }
            throw e
        }
    }

    // ── ছবি দেখানো ──

    private fun cacheFile(fileId: String) = File(cacheDir, "$fileId.jpg")

    /** ক্যাশে থাকলে সাথে সাথে; নাহলে Drive থেকে নামিয়ে। ফাইল অপরিবর্তনীয়, তাই ক্যাশ চিরস্থায়ী। */
    suspend fun imageFile(fileId: String): File? {
        val cached = cacheFile(fileId)
        if (cached.exists() && cached.length() > 0) return cached
        if (!configured) return null
        return try {
            downloadGate.withPermit {
                if (cached.exists() && cached.length() > 0) return@withPermit cached
                withContext(Dispatchers.IO) { drive.download(fileId, cached) }
                cached
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── ডিলিট ──

    fun delete(meta: MemoMeta) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { drive.delete(meta.driveFileId) }
                repo.delete(partyKey, meta.driveFileId)
                withContext(Dispatchers.IO) { cacheFile(meta.driveFileId).delete() }
                _message.value = "ছবি মুছে ফেলা হয়েছে"
            } catch (e: Exception) {
                _message.value = "মুছতে পারিনি: ${e.message}"
            }
        }
    }
}

class MemoViewModelFactory(
    private val partyKey: String,
    private val partyName: String,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MemoViewModel(partyKey, partyName, appContext) as T
}
