package com.imran.clothstore.access

import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.imran.clothstore.BuildConfig

/**
 * ডিভাইস নিবন্ধন — অনুমোদিত-তালিকা (allowlist) ব্যবস্থার জন্য।
 * সাইন-ইন সফল হলেই এই ডিভাইসের anonymous UID, মডেল ও অ্যাপ-ভার্সন
 * Firestore-এর `devices/{uid}` ডকুমেন্টে লিখে রাখে, যাতে সার্ভার-পক্ষ
 * থেকে কোন ডিভাইস বৈধ তা চিহ্নিত করে বাকিদের আটকানো যায়।
 * নিবন্ধন ব্যর্থ হলেও অ্যাপ চলা থামে না (নেট-জনিত সাময়িক ব্যর্থতা হতে পারে)।
 */
object DeviceRegistry {
    private const val TAG = "DeviceRegistry"
    private const val COLLECTION = "devices"

    fun register() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val data = mapOf(
                "uid" to uid,
                "platform" to "android-app",
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "androidSdk" to Build.VERSION.SDK_INT,
                "appVersion" to BuildConfig.VERSION_NAME,
                "lastSeen" to FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance()
                .collection(COLLECTION).document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "device registered: $uid") }
                .addOnFailureListener { e -> Log.w(TAG, "device register failed", e) }
        } catch (e: Exception) {
            Log.w(TAG, "device register skipped", e)
        }
    }
}
