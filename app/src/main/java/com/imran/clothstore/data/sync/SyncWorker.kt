package com.imran.clothstore.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.imran.clothstore.data.backup.BackupPayload
import com.imran.clothstore.data.backup.BackupRepository
import com.imran.clothstore.data.backup.MobsCodec
import com.imran.clothstore.access.ContactsVault
import com.imran.clothstore.data.local.AppDatabase
import com.imran.clothstore.data.local.LocalCacheRepository
import com.imran.clothstore.ui.screens.notif.NotificationCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * ওয়েব অ্যাপের dbPush()/dbPull() (index.html লাইন ৫০৯১, ৫১৫৫) এর ব্যাকগ্রাউন্ড
 * সমতুল্য — WorkManager দিয়ে চালানো, যাতে ইউজারকে UI ব্লক করে অপেক্ষা করতে না হয়
 * এবং নেটওয়ার্ক না থাকলে স্বয়ংক্রিয়ভাবে রিট্রাই হয় (roadmap আইটেম #১, ধাপ ৩)।
 *
 * প্রবাহ, প্রতিবার চলার সময়:
 * ১. Room থেকে স্থানীয় অবস্থা পড়া (pendingSync থাকলেই কেবল push করার দরকার, নাহলে শুধু pull)
 * ২. Firestore থেকে দূরবর্তী অবস্থা fetch
 * ৩. SyncEngine.merge() দিয়ে দুটো merge করা (ওয়েব অ্যাপের dbPush() লজিক হুবহু)
 * ৪. merged ফলাফল Firestore-এ push + Room-এ pendingSync=false দিয়ে সেভ
 *
 * নেটওয়ার্ক না থাকলে Firestore কল ব্যর্থ হবে, WorkManager স্বয়ংক্রিয়ভাবে ব্যাকঅফ-সহ
 * রিট্রাই করবে (retry() রিটার্ন করলে) — ততক্ষণ Room-এর স্থানীয় ডেটাই UI-তে দেখা যাবে,
 * অ্যাপ অকেজো হবে না।
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // ইউজারের নিজের পেন্ডিং পরিবর্তন থাকলেই কেবল সাফল্য/ব্যর্থতার নোটিফিকেশন দেওয়া হয় (শুধু pull হলে নয়)
        var hasPending = false
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val local = LocalCacheRepository(db.backupCacheDao())
            
            // Handle missing google-services.json gracefully
            val firestore = try {
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Firestore is unavailable; sync cannot start", e)
                return Result.failure() // Can't sync without Firebase
            }
            
            val repository = BackupRepository(local, applicationContext)

            hasPending = local.hasPendingSync()
            val localPayload = local.getOnce()
            val remotePayload = try {
                repository.fetchRemoteOnce()
            } catch (e: Exception) {
                // নেটওয়ার্ক নেই বা Firestore আনরিচেবল — এখনো কিছু push করা যায়নি,
                // পরে আবার চেষ্টা করার জন্য retry() রিটার্ন করা হচ্ছে
                Log.w(TAG, "Could not read the remote backup; retrying", e)
                if (hasPending) notifyFailure(e)
                return Result.retry()
            }
            Log.i(
                TAG,
                "Remote backup loaded: parties=${remotePayload.is_c1_list.size + remotePayload.is_c2_list.size + remotePayload.is_c3_list.size + remotePayload.is_c4_list.size}, weekly=${remotePayload.chart_data.size}, fabric=${remotePayload.fabricPurchaseData_v2.size}"
            )

            // ── মোবাইল নাম্বার (v1.1.6): contacts ডক পড়া গেলেই এই ডিভাইস বৈধ ──
            // backup ডকে কখনো নাম্বার push হয় না; বৈধ ডিভাইসে নাম্বার contacts থেকে
            // এনে Room-এ মিলিয়ে রাখা হয়, অবৈধ ডিভাইসে Room-ও নাম্বারছাড়া থাকে।
            var mobs: Map<Long, String>? = null
            try {
                mobs = ContactsVault.fetchMobs()
            } catch (e: Exception) {
                if (ContactsVault.isDenied(e)) {
                    mobs = null
                } else {
                    // নেট-জনিত অনিশ্চয়তায় কোনো সিদ্ধান্ত নয় — পরে আবার চেষ্টা হবে
                    Log.w(TAG, "Contacts fetch inconclusive; retrying sync", e)
                    return Result.retry()
                }
            }
            val numbersAllowed = mobs != null
            ContactsVault.setAllowed(numbersAllowed)
            var migration = false
            if (numbersAllowed) {
                // মাইগ্রেশন/উদ্ধার: backup ডকে এখনো নাম্বার থাকলে (পুরনো ভার্সনের লেখা
                // বা ওয়েব অ্যাপের push) সেগুলো contacts-এ তুলে নেওয়া হয় — এরপরের
                // push-এই backup নাম্বারছাড়া হয়ে যায়, কোনো নাম্বার হারায় না
                val remoteMobs = MobsCodec.extractMobs(remotePayload)
                if (remoteMobs.isNotEmpty()) {
                    val harvested = (mobs ?: emptyMap()) + remoteMobs +
                        MobsCodec.extractMobs(localPayload)
                    try {
                        ContactsVault.pushMobs(harvested)
                        mobs = harvested
                        migration = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Contacts harvest push failed; retrying sync", e)
                        return Result.retry()
                    }
                }
            }

            val localForMerge =
                if (numbersAllowed) localPayload else MobsCodec.stripMobs(localPayload)
            val mergedRaw: BackupPayload = if (hasPending) {
                SyncEngine.merge(local = localForMerge, remote = remotePayload)
            } else {
                // কোনো স্থানীয় পরিবর্তন পেন্ডিং নেই — শুধু remote-কে সত্য ধরে নেওয়া (সাধারণ pull)
                remotePayload
            }
            val merged: BackupPayload = if (numbersAllowed) {
                MobsCodec.applyMobs(mergedRaw, mobs ?: emptyMap())
            } else {
                MobsCodec.stripMobs(mergedRaw)
            }

            // বৈধ ডিভাইসের নতুন/বদলানো/মোছা নাম্বার contacts-এ প্রতিফলিত করা
            if (numbersAllowed && (hasPending || migration)) {
                val dead = MobsCodec.tombstonedIds(merged)
                val present = MobsCodec.entryIds(merged)
                val desired = (MobsCodec.extractMobs(merged) +
                    (mobs ?: emptyMap()).filterKeys { it !in present && it !in dead })
                    .filterKeys { it !in dead }
                if (desired != mobs) {
                    try {
                        ContactsVault.pushMobs(desired)
                        mobs = desired
                    } catch (e: Exception) {
                        Log.w(TAG, "Contacts push failed; retrying sync", e)
                        return Result.retry()
                    }
                }
            }

            if (hasPending) {
                // set().await() শুধু সার্ভার নিশ্চিত করলেই শেষ হয়; অফলাইনে চিরকাল ঝুলে থাকত — তাই টাইমআউট
                try {
                    withTimeout(PUSH_TIMEOUT_MS) {
                        repository.pushToRemote(MobsCodec.stripMobs(merged))
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Firestore push timed out", e)
                    notifyFailure(IllegalStateException("সার্ভার সাড়া দেয়নি (ইন্টারনেট ধীর বা বন্ধ)"))
                    return Result.retry()
                }
                notifySuccess()
            } else if (migration) {
                // পেন্ডিং বদল না থাকলেও মাইগ্রেশনের পর backup ডক নাম্বারছাড়া করা বাধ্যতামূলক
                try {
                    withTimeout(PUSH_TIMEOUT_MS) {
                        repository.pushToRemote(MobsCodec.stripMobs(merged))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Migration strip push failed; retrying sync", e)
                    return Result.retry()
                }
            }
            // নেটওয়ার্কে থাকাকালীন ইউজার নতুন কিছু এডিট করে থাকলে (updatedAt বাদে হুবহু তুলনা),
            // পুরনো merged দিয়ে সেটা ওভাররাইট করা হয় না এবং pendingSync-ও true-ই থাকে।
            // ওই এডিটের নিজস্ব sync আগেই শিডিউল হয়ে আছে, সেটাই বাকিটা সামলাবে।
            val latestLocal = local.getOnce()
            if (latestLocal.copy(updatedAt = 0L) != localPayload.copy(updatedAt = 0L)) {
                return Result.success()
            }
            // pull-এর ফলাফল (অন্য ডিভাইসের পরিবর্তনসহ) Room-এ প্রতিফলিত করা, pendingSync ক্লিয়ার করা
            local.save(merged, markPendingSync = false)
            Log.i(TAG, "Room backup cache refreshed successfully")
            SyncStatus.markSynced(applicationContext)

            Result.success()
        } catch (e: CancellationException) {
            // নতুন সেভের কারণে এই কাজ বাতিল হলে সেটা ব্যর্থতা নয় — নতুন কাজ আবার চেষ্টা করবে
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Backup sync failed; WorkManager will retry", e)
            if (hasPending) notifyFailure(e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "BackupSyncWorker"
        private const val PERIODIC_WORK_NAME = "backup_sync_periodic"
        private const val ONE_TIME_WORK_NAME = "backup_sync_immediate"
        private const val PUSH_TIMEOUT_MS = 20_000L

        @Volatile private var lastErrorMsg = ""
        @Volatile private var lastErrorAt = 0L

        /** ফায়ারস্টোরে ডাটা সত্যিই পৌঁছালে নোটিফিকেশন প্যানেলে সফল মেসেজ */
        private fun notifySuccess() {
            lastErrorMsg = ""
            NotificationCenter.push("success", "ফায়ারস্টোরে ডাটা সফলভাবে সেভ হয়েছে")
        }

        /** ব্যর্থ হলে কারণসহ মেসেজ — একই মেসেজ ৫ মিনিটের মধ্যে বারবার দেখানো হয় না (রিট্রাইয়ে স্প্যাম এড়াতে) */
        private fun notifyFailure(e: Throwable) {
            val msg = "ফায়ারস্টোরে সেভ ব্যর্থ: ${reasonOf(e)}"
            val now = System.currentTimeMillis()
            if (msg == lastErrorMsg && now - lastErrorAt < 5 * 60_000L) return
            lastErrorMsg = msg
            lastErrorAt = now
            NotificationCenter.push("error", msg)
        }

        private fun reasonOf(e: Throwable): String {
            val code = (e as? FirebaseFirestoreException)?.code
            return when (code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "অনুমতি নেই (Firestore rules বা অ্যানোনিমাস লগইন সমস্যা)"
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> "লগইন হয়নি"
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "ইন্টারনেট বা সার্ভার পাওয়া যায়নি"
                else -> (e.message ?: e::class.java.simpleName).take(90)
            }
        }

        /** ClothStoreApplication.onCreate() থেকে একবার কল হয় — প্রতি ১৫ মিনিটে ব্যাকগ্রাউন্ড pull/push */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Firestore-এর remote পরিবর্তন জানালে ডাকা হয়। KEEP নীতি: চলমান/অপেক্ষমান push বাতিল করে না
         * (আগে REPLACE হওয়ায় নিজের push-এর snapshot ইভেন্টেই push বাতিল হয়ে লুপ হতে পারত)।
         */
        fun triggerFromRemote(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /** একটা এন্ট্রি সেভ/ডিলিট হওয়ার সাথে সাথে দ্রুত sync করার চেষ্টা (নেটওয়ার্ক থাকলে) —
         *  BackupRepository-এর প্রতিটা write-এর পর এটা কল করা হবে (ধাপ ৩ সংযোগ, দেখুন
         *  FvListViewModel ইত্যাদির saveEntry()/deleteEntry() কলের ঠিক পরে) */
        fun triggerImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            // ২ সেকেন্ড debounce — REPLACE নীতির সাথে পরপর কয়েকটা সেভে শুধু শেষটার পরেই একবার sync হয়
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
