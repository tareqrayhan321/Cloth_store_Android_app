package com.imran.clothstore

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.imran.clothstore.access.DeviceRegistry
import com.imran.clothstore.access.AccessGate
import com.imran.clothstore.data.backup.BackupRepository
import com.imran.clothstore.data.local.AppDatabase
import com.imran.clothstore.data.local.LocalCacheRepository
import com.imran.clothstore.data.sync.SyncStatus
import com.imran.clothstore.data.sync.SyncWorker
import com.imran.clothstore.ui.screens.notif.NotificationCenter

/**
 * NotificationCenter-কে DataStore persistence-এর জন্য applicationContext সরবরাহ করতে
 * এবং ব্যাকগ্রাউন্ড Firestore sync (WorkManager) শিডিউল করতে একটা কাস্টম Application ক্লাস —
 * যাতে প্রসেস কিল হলেও (ব্যাকগ্রাউন্ডে/মেমোরি চাপে, Android-এ যা খুবই সাধারণ) নোটিফিকেশন
 * তালিকা টিকে থাকে (দেখুন NotificationViewModel.kt) এবং অফলাইনে করা পরিবর্তন পরবর্তীতে
 * নেটওয়ার্ক ফিরলে স্বয়ংক্রিয়ভাবে Firestore-এ sync হয় (আইটেম #১, দেখুন SyncWorker.kt)।
 */
class ClothStoreApplication : Application() {
    private var backupListener: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        NotificationCenter.init(applicationContext)
        // BackupRepository()-এর no-arg কনস্ট্রাক্টর (৮টা ViewModel এখনো এভাবেই ব্যবহার করে)
        // যাতে Context ছাড়াই Room/LocalCacheRepository পায় — সেটার জন্য এই এক-বার-init
        // হওয়া হোল্ডার। ViewModel-গুলোর কনস্ট্রাক্টর সিগনেচার অপরিবর্তিত রাখতেই এই প্যাটার্ন,
        // যাতে ৮টা ViewModel ক্লাস + তাদের ইনস্ট্যানশিয়েশন সাইট (নেভ গ্রাফ) বদলাতে না হয়।
        AppSingletons.localCacheRepository = LocalCacheRepository(
            AppDatabase.getInstance(applicationContext).backupCacheDao()
        )
        AppSingletons.appContext = applicationContext
        AccessGate.init(applicationContext)

        // Firestore rules-এ request.auth != null চেক পাস করাতে অ্যানোনিমাস সাইন-ইন।
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                startAuthenticatedSync()
            } else {
                auth.signInAnonymously()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "signInAnonymously: success, uid=${auth.currentUser?.uid}")
                            startAuthenticatedSync()
                        } else {
                            Log.w(TAG, "signInAnonymously: failed", task.exception)
                            SyncWorker.schedulePeriodic(applicationContext)
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase not initialized or missing google-services.json", e)
            // Firebase না থাকলেও অন্তত পিরিয়ডিক সিঙ্ক শিডিউল হোক (যদিও ফায়ারস্টোর ফেইল করবে, লোকাল চলবে)
            SyncWorker.schedulePeriodic(applicationContext)
        }
    }

    /** Auth সম্পন্ন হলে listener এবং cold-start pull—দুটোই চালু করে। */
    private fun startAuthenticatedSync() {
        DeviceRegistry.register()
        AccessGate.verify()
        SyncWorker.schedulePeriodic(applicationContext)
        startBackupListener()
        // নতুন install-এর Room cache খালি থাকে; initial listener event-এর অপেক্ষা না করে
        // বিদ্যমান Firestore document-টি স্পষ্টভাবে pull করার অনুরোধ করা হয়।
        SyncWorker.triggerImmediate(applicationContext)
    }

    /** Remote document পরিবর্তনে বিদ্যমান Room/WorkManager sync-কে জাগিয়ে তোলে। */
    private fun startBackupListener() {
        try {
            backupListener?.remove()
            backupListener = BackupRepository().addRemoteSnapshotListener(
                onChange = { _ -> SyncWorker.triggerFromRemote(applicationContext) },
                onError = { error ->
                    Log.e(TAG, "Firestore backup listener failed", error)
                    SyncStatus.setRealtimeActive(false)
                }
            )
            SyncStatus.setRealtimeActive(true)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start Firestore backup listener", e)
            SyncStatus.setRealtimeActive(false)
        }
    }

    companion object {
        private const val TAG = "ClothStoreApplication"
    }
}

/**
 * প্রসেস-স্কোপড হোল্ডার — শুধু BackupRepository()-এর no-arg ডিফল্ট কনস্ট্রাক্টরের জন্য।
 * ClothStoreApplication.onCreate()-এ একবার সেট হয়, তারপর কখনো বদলায় না। Android সবসময়
 * Application.onCreate() অন্য যেকোনো কম্পোনেন্টের আগে চালায় বলে ViewModel তৈরির সময়
 * এটা init হয়ে থাকার কথা — তবু defensively lateinit-এর বদলে nullable + lazy-safe getter,
 * যাতে কোনো টেস্ট/এজ-কেসে ভুলবশত আগে অ্যাক্সেস হলে ক্র্যাশের বদলে একটা পরিষ্কার এরর দেয়।
 */
object AppSingletons {
    private var _localCacheRepository: LocalCacheRepository? = null
    var localCacheRepository: LocalCacheRepository
        get() = _localCacheRepository
            ?: error("AppSingletons ব্যবহারের আগে init হয়নি — ClothStoreApplication.onCreate() চলেনি")
        set(value) { _localCacheRepository = value }

    var appContext: android.content.Context? = null
}
