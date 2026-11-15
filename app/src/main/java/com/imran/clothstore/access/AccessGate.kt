package com.imran.clothstore.access

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.imran.clothstore.AppSingletons
import com.imran.clothstore.data.backup.MobsCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * কিল-সুইচ / প্রবেশ-যাচাই (v1.1.5 থেকে, মালিকের নির্দেশে)।
 *
 * পটভূমি: Firestore rules এখন owner-only — শুধু মালিকের ডিভাইসই
 * `imran_store/backup` পড়তে/লিখতে পারে। কিন্তু অ্যাপ অফলাইন-ফার্স্ট বলে
 * অননুমোদিত ফোনের লোকাল Room ক্যাশে পুরনো ডাটা জমে থাকে; সার্ভার থেকে
 * সেই ক্যাশ মোছা যায় না — শুধু অ্যাপ নিজে পারবে। এই গেট সেই কাজটাই করে।
 *
 * যাচাই: সাইন-ইনের পর সার্ভার থেকে সরাসরি (Source.SERVER) backup ডক
 * পড়ার চেষ্টা —
 *  - পড়া গেলে: ডিভাইস অনুমোদিত, সব স্বাভাবিক (আগের ব্লক থাকলে উঠে যায়)।
 *  - PERMISSION_DENIED: ডিভাইস অননুমোদিত — লোকাল সব ডাটা মুছে
 *    (Room ক্যাশ, মেমো-ছবির ক্যাশ, Firestore-এর নিজের ক্যাশ) অ্যাপ লক।
 *    ব্লক-অবস্থা SharedPreferences-এ থাকে, তাই রিস্টার্টেও লক থাকে;
 *    পরে মালিক অনুমতি দিলে পরের চালুতে যাচাই সফল হয়ে লক নিজে উঠে যায়।
 *  - অন্য যেকোনো এরর (নেট নেই ইত্যাদি): কোনো সিদ্ধান্ত নয় — আগের
 *    অবস্থাই থাকে। অফলাইনে মালিকের অ্যাপ কখনো লক হবে না।
 */
object AccessGate {
    private const val TAG = "AccessGate"
    private const val PREFS = "access_gate"
    private const val KEY_BLOCKED = "blocked"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        _blocked.value = prefs().getBoolean(KEY_BLOCKED, false)
    }

    private fun prefs() =
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** সাইন-ইন সম্পন্ন হলেই ClothStoreApplication থেকে ডাকা হয়। */
    fun verify() {
        val context = appContext ?: return
        scope.launch {
            var backupOk = false
            try {
                FirebaseFirestore.getInstance()
                    .collection("imran_store").document("backup")
                    .get(Source.SERVER)
                    .await()
                backupOk = true
                if (_blocked.value) {
                    prefs().edit().putBoolean(KEY_BLOCKED, false).apply()
                    _blocked.value = false
                }
                Log.d(TAG, "access verified")
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.w(TAG, "access denied by server — wiping local data")
                    wipe(context)
                } else {
                    Log.w(TAG, "access check inconclusive: ${e.code}", e)
                }
            } catch (e: Exception) {
                Log.w(TAG, "access check failed", e)
            }
            // ধাপ ২ (v1.1.6): backup পড়া গেলেও নাম্বার-ভল্ট আলাদা করে যাচাই হয়
            if (backupOk) checkContacts()
        }
    }

    /**
     * নাম্বার-ভল্ট যাচাই (v1.1.6): `imran_store/contacts` সার্ভার থেকে পড়া গেলে
     * ডিভাইস বৈধ — নাম্বার দেখা যাবে। PERMISSION_DENIED হলে ডিভাইস বৈধ নয়:
     * অ্যাপ লক হয় না, লেনদেনের ডাটাও মোছে না — শুধু লোকাল ক্যাশে জমে থাকা
     * নাম্বারগুলো মুছে যায় (পুরনো ভার্সনের ক্যাশে নাম্বার থেকে যেতে পারে)।
     * অন্য এররে (নেট নেই ইত্যাদি) কোনো সিদ্ধান্ত নয় — আগের অবস্থাই থাকে।
     */
    private suspend fun checkContacts() {
        try {
            ContactsVault.fetchMobs(Source.SERVER)
            ContactsVault.setAllowed(true)
            Log.d(TAG, "contacts access verified (valid device)")
        } catch (e: Exception) {
            if (ContactsVault.isDenied(e)) {
                ContactsVault.setAllowed(false)
                stripLocalNumbers()
                Log.w(TAG, "contacts denied — numbers hidden on this device")
            } else {
                Log.w(TAG, "contacts check inconclusive", e)
            }
        }
    }

    /**
     * অবৈধ ডিভাইসের লোকাল ক্যাশ থেকে শুধু নাম্বার মোছা — এন্ট্রি/হিসাব সব থাকে।
     * pendingSync পতাকা আগের মতোই রাখা হয়, যাতে অফলাইনে করা এন্ট্রি হারিয়ে না যায়।
     */
    private suspend fun stripLocalNumbers() {
        try {
            val local = AppSingletons.localCacheRepository
            val payload = local.getOnce()
            if (MobsCodec.extractMobs(payload).isNotEmpty()) {
                val pending = local.hasPendingSync()
                local.save(MobsCodec.stripMobs(payload), markPendingSync = pending)
            }
        } catch (e: Exception) {
            Log.w(TAG, "local number strip failed", e)
        }
    }

    private suspend fun wipe(context: Context) {
        // পতাকা আগে বসে — UI সঙ্গে সঙ্গে লক-স্ক্রিনে যায়, রিস্টার্টেও থাকে
        prefs().edit().putBoolean(KEY_BLOCKED, true).apply()
        _blocked.value = true
        try {
            AppSingletons.localCacheRepository.clear()
        } catch (e: Exception) {
            Log.w(TAG, "room wipe failed", e)
        }
        try {
            File(context.cacheDir, "memo_images").deleteRecursively()
            File(context.cacheDir, "memo_camera").deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "memo cache wipe failed", e)
        }
        try {
            WorkManager.getInstance(context).cancelAllWork()
        } catch (e: Exception) {
            Log.w(TAG, "work cancel failed", e)
        }
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w(TAG, "signOut failed", e)
        }
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.terminate().await()
            firestore.clearPersistence().await()
        } catch (e: Exception) {
            Log.w(TAG, "firestore persistence wipe failed", e)
        }
    }
}
