package com.imran.clothstore.access

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * মোবাইল নাম্বারের ভল্ট (v1.1.6, মালিকের নির্দেশে) — `imran_store/contacts`।
 *
 * পটভূমি: মালিক চান বৈধ ব্যবহারকারীরা আগের মতোই পার্টির মোবাইল নাম্বার পাবেন,
 * আর বাকিরা লেনদেনের ডাটা দেখতে ও এন্ট্রি দিতে পারলেও নাম্বার পাবেন না। তাই
 * নাম্বার মূল backup ডক থেকে সরিয়ে এই আলাদা ডকে রাখা হয়; Firestore rules-এ
 * এই ডক শুধু বৈধদের (মালিক + devices ডকে valid=true চিহ্নিত ডিভাইস) জন্য খোলা।
 *
 * বৈধতার প্রমাণ = এই ডক সার্ভার থেকে পড়া যাওয়া। PERMISSION_DENIED মানেই
 * ডিভাইস বৈধ নয় — তখন অ্যাপ নাম্বারছাড়া মোডে চলে (লক হয় না, ডাটা মোছে না;
 * শুধু লোকাল ক্যাশের নাম্বার মুছে যায়)। শেষ জানা অবস্থা SharedPreferences-এ
 * থাকে, যাতে অ্যাপ খোলার প্রথম ফ্রেম থেকেই ফর্মে নাম্বারের ঘর দেখানো/লুকানো
 * ঠিক থাকে।
 */
object ContactsVault {
    private const val TAG = "ContactsVault"
    private const val COLLECTION = "imran_store"
    private const val DOCUMENT = "contacts"
    private const val PREFS = "contacts_vault"
    private const val KEY_ALLOWED = "allowed"

    private val _allowed = MutableStateFlow(false)
    val allowed: StateFlow<Boolean> = _allowed

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        _allowed.value = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOWED, false)
    }

    fun setAllowed(value: Boolean) {
        _allowed.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_ALLOWED, value)?.apply()
    }

    private fun docRef() =
        FirebaseFirestore.getInstance().collection(COLLECTION).document(DOCUMENT)

    /**
     * নাম্বার-ম্যাপ পড়ে (id → mob)। ডক না থাকলে খালি ম্যাপ।
     * অনুমতি না থাকলে FirebaseFirestoreException (PERMISSION_DENIED) ছোড়ে —
     * কলার সেটাই বৈধতার রায় হিসেবে ব্যবহার করে।
     */
    suspend fun fetchMobs(source: Source = Source.DEFAULT): Map<Long, String> {
        val snapshot = docRef().get(source).await()
        if (!snapshot.exists()) return emptyMap()
        val raw = snapshot.get("mobs") as? Map<*, *> ?: return emptyMap()
        val result = LinkedHashMap<Long, String>()
        raw.forEach { (k, v) ->
            val id = (k as? String)?.toLongOrNull() ?: return@forEach
            val mob = v as? String ?: return@forEach
            if (mob.isNotBlank()) result[id] = mob
        }
        return result
    }

    /** পুরো নাম্বার-ম্যাপ একবারে লেখে (id চাবি string আকারে — Firestore ম্যাপের নিয়ম) */
    suspend fun pushMobs(mobs: Map<Long, String>) {
        val data = mapOf(
            "mobs" to mobs.mapKeys { it.key.toString() },
            "updatedAt" to System.currentTimeMillis()
        )
        docRef().set(data).await()
        Log.d(TAG, "contacts pushed: ${mobs.size} numbers")
    }

    fun isDenied(e: Exception): Boolean =
        (e as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED
}
