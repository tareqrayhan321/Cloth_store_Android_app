package com.imran.clothstore.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupCacheDao {
    /** id সবসময় ধ্রুবক ০ (BackupCacheEntity.SINGLETON_ID) — @Query-এর ভ্যালু কম্পাইল-টাইম
     *  কনস্ট্যান্ট স্ট্রিং হতে হয় বলে এখানে literal 0 হার্ডকোড করা, রেফারেন্স না করে,
     *  অস্পষ্টতা এড়াতে (SINGLETON_ID বদলালে এখানেও বদলাতে হবে) */
    /** রিয়েল-টাইম UI আপডেটের জন্য — Room নিজে থেকেই টেবিল বদলালে নতুন ভ্যালু এমিট করে */
    @Query("SELECT * FROM backup_cache WHERE id = 0")
    fun observe(): Flow<BackupCacheEntity?>

    @Query("SELECT * FROM backup_cache WHERE id = 0")
    suspend fun getOnce(): BackupCacheEntity?

    /** id সবসময় ০ বলে upsert-ই কার্যত "একমাত্র রো replace করো" — @Upsert নিজেই
     *  primary key কনফ্লিক্ট হলে insert-এর বদলে update করে (REPLACE-এর মতো row
     *  ডিলিট-রিইনসার্ট করে না), তাই আলাদা onConflict প্যারামিটার লাগে না */
    @Upsert
    suspend fun upsert(entity: BackupCacheEntity): Long

    @Query("SELECT pendingSync FROM backup_cache WHERE id = 0")
    suspend fun isPendingSync(): Boolean?

    /** কিল-সুইচের (AccessGate) মোছার জন্য — ক্যাশের একমাত্র রো-টাই মুছে দেয় */
    @Query("DELETE FROM backup_cache")
    suspend fun clearAll()
}
