package com.imran.clothstore.ui.screens.memo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.imran.clothstore.data.memo.DriveAuth
import com.imran.clothstore.data.memo.DriveClient
import com.imran.clothstore.data.memo.DriveHttpException
import com.imran.clothstore.data.memo.MemoMeta
import com.imran.clothstore.data.memo.MemoRepository
import com.imran.clothstore.util.MemoImage
import com.imran.clothstore.util.toBengaliDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

enum class DriveState { CHECKING, CONNECTED, NEEDS_CONSENT, ERROR }

data class UploadProgress(val done: Int, val total: Int)

/**
 * পার্টির মেমো-ছবি: ছবি যায় Google Drive-এ, metadata (ফাইল আইডি, নাম, সাইজ, সময়) যায় Firestore-এ।
 */
class MemoViewModel(
    private val partyKey: String,
    private val partyName: String,
    private val appContext: Context,
    private val repo: MemoRepository = MemoRepository(),
    private val drive: DriveClient = DriveClient()
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    val memos: StateFlow<List<MemoMeta>> = repo.observe(partyKey)
        .catch { _message.value = "মেমো তালিকা পড়া যায়নি: ${it.message}" ; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _driveState = MutableStateFlow(DriveState.CHECKING)
    val driveState: StateFlow<DriveState> = _driveState.asStateFlow()

    private val _progress = MutableStateFlow<UploadProgress?>(null)
    val progress: StateFlow<UploadProgress?> = _progress.asStateFlow()

    /** সম্মতি-স্ক্রিন খুলতে স্ক্রিনকে সিগন্যাল */
    private val _consent = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val consent: SharedFlow<PendingIntent> = _consent.asSharedFlow()

    private var cachedToken: String? = null
    private var tokenAt = 0L
    private var pendingConsent: PendingIntent? = null
    private val downloadGate = Semaphore(3)
    private val prefs = appContext.getSharedPreferences("memo_prefs", Context.MODE_PRIVATE)
    private val cacheDir get() = File(appContext.cacheDir, "memo_images")

    init {
        // নীরবে চেক: আগে অনুমতি দেওয়া থাকলে UI ছাড়াই টোকেন মেলে
        viewModelScope.launch { token() }
    }

    // ── টোকেন ──

    private suspend fun token(force: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!force && cachedToken != null && now - tokenAt < TOKEN_TTL_MS) return cachedToken
        return when (val r = DriveAuth.authorize(appContext)) {
            is DriveAuth.Result.Token -> {
                cachedToken = r.value; tokenAt = now
                _driveState.value = DriveState.CONNECTED
                r.value
            }
            is DriveAuth.Result.NeedsConsent -> {
                cachedToken = null
                pendingConsent = r.pendingIntent
                _driveState.value = DriveState.NEEDS_CONSENT
                null
            }
            is DriveAuth.Result.Failed -> {
                cachedToken = null
                _driveState.value = DriveState.ERROR
                _message.value = r.message
                null
            }
        }
    }

    /** "Google Drive সংযুক্ত করুন" বোতাম */
    fun connectDrive() {
        viewModelScope.launch {
            val tok = token()
            if (tok == null) pendingConsent?.let { _consent.tryEmit(it) }
        }
    }

    fun onConsentResult(data: Intent?) {
        when (val r = DriveAuth.fromConsentResult(appContext, data)) {
            is DriveAuth.Result.Token -> {
                cachedToken = r.value; tokenAt = System.currentTimeMillis()
                _driveState.value = DriveState.CONNECTED
            }
            is DriveAuth.Result.Failed -> {
                _driveState.value = DriveState.NEEDS_CONSENT
                _message.value = r.message
            }
            else -> Unit
        }
    }

    /** 401 এলে একবার নতুন টোকেন নিয়ে আবার চেষ্টা */
    private suspend fun <T> withDrive(block: suspend (String) -> T): T {
        val t1 = token() ?: throw IllegalStateException("Google Drive সংযুক্ত নেই")
        return try {
            block(t1)
        } catch (e: DriveHttpException) {
            if (e.code != 401) throw e
            val t2 = token(force = true) ?: throw IllegalStateException("Google Drive সংযুক্ত নেই")
            block(t2)
        }
    }

    // ── ফোল্ডার ──

    private suspend fun folderId(forceRefresh: Boolean = false): String {
        if (!forceRefresh) prefs.getString(KEY_FOLDER, null)?.let { return it }
        val id = withDrive { t -> withContext(Dispatchers.IO) { drive.findOrCreateFolder(t, DriveClient.FOLDER_NAME) } }
        prefs.edit().putString(KEY_FOLDER, id).apply()
        return id
    }

    // ── আপলোড ──

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty() || _progress.value != null) return
        viewModelScope.launch {
            if (token() == null) {
                _message.value = "আগে Google Drive সংযুক্ত করুন"
                return@launch
            }
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

        suspend fun send(folder: String) = withDrive { t ->
            withContext(Dispatchers.IO) { drive.upload(t, folder, fileName, "image/jpeg", img.bytes) }
        }

        val up = try {
            send(folderId())
        } catch (e: DriveHttpException) {
            // ফোল্ডার ড্রাইভ থেকে মুছে ফেলা হয়ে থাকলে নতুন করে বানিয়ে আবার চেষ্টা
            if (e.code == 404) send(folderId(forceRefresh = true)) else throw e
        }

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
            runCatching { withDrive { t -> withContext(Dispatchers.IO) { drive.delete(t, up.id) } } }
            throw e
        }
    }

    // ── ছবি দেখানো ──

    private fun cacheFile(fileId: String) = File(cacheDir, "$fileId.jpg")

    /** ক্যাশে থাকলে সাথে সাথে; নাহলে Drive থেকে নামিয়ে। ফাইল অপরিবর্তনীয়, তাই ক্যাশ চিরস্থায়ী। */
    suspend fun imageFile(fileId: String): File? {
        val cached = cacheFile(fileId)
        if (cached.exists() && cached.length() > 0) return cached
        // সংযুক্ত না থাকলে গ্রিডের প্রতিটা ছবি আলাদা করে অনুমোদন চাইতে যাবে না
        if (_driveState.value == DriveState.NEEDS_CONSENT || _driveState.value == DriveState.ERROR) return null
        return try {
            downloadGate.withPermit {
                if (cached.exists() && cached.length() > 0) return@withPermit cached
                withDrive { t -> withContext(Dispatchers.IO) { drive.download(t, fileId, cached) } }
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
                withDrive { t -> withContext(Dispatchers.IO) { drive.delete(t, meta.driveFileId) } }
                repo.delete(partyKey, meta.driveFileId)
                withContext(Dispatchers.IO) { cacheFile(meta.driveFileId).delete() }
                _message.value = "ছবি মুছে ফেলা হয়েছে"
            } catch (e: Exception) {
                _message.value = "মুছতে পারিনি: ${e.message}"
            }
        }
    }

    companion object {
        private const val TOKEN_TTL_MS = 45 * 60 * 1000L
        private const val KEY_FOLDER = "drive_folder_id"
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
