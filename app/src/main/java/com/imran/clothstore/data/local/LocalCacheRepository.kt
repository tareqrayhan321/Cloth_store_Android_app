package com.imran.clothstore.data.local

import com.imran.clothstore.data.backup.BackupPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/**
 * Room-এর BackupCacheDao-কে টাইপড BackupPayload ইন্টারফেসে মুড়ে রাখে, যাতে
 * BackupRepository (data/backup/) সরাসরি JSON এনকোডিং/DAO নিয়ে মাথা না ঘামায়।
 */
class LocalCacheRepository(private val dao: BackupCacheDao) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * পুরো payload-এর decode একবারই, ব্যাকগ্রাউন্ড থ্রেডে হয় — আগে প্রতিটা ViewModel আলাদা করে
     * Main থ্রেডে পুরো JSON decode করত। replay=1 তাই নতুন স্ক্রিন খুলতেই শেষ মান পেয়ে যায়
     * (খালি লিস্টের ঝলকও নেই)। lazy — শুধু UI প্রথমবার observe করলেই চালু হয়
     * (SyncWorker-এর নিজস্ব instance-এ এটা কখনো চালু হয় না)।
     */
    private val shared: Flow<BackupPayload> by lazy {
        dao.observe()
            .map { entity -> entity?.let { BackupJson.decode(it.payloadJson) } ?: BackupPayload() }
            .flowOn(Dispatchers.Default)
            .shareIn(scope, SharingStarted.Eagerly, replay = 1)
    }

    /** লোকাল ক্যাশে এখনো কিছু না থাকলে খালি BackupPayload() দেয় — প্রথমবার অ্যাপ চালু হলে */
    fun observe(): Flow<BackupPayload> = shared

    suspend fun getOnce(): BackupPayload = withContext(Dispatchers.Default) {
        val entity = dao.getOnce() ?: return@withContext BackupPayload()
        BackupJson.decode(entity.payloadJson) ?: BackupPayload()
    }

    /**
     * @param markPendingSync true হলে বোঝায় এই পরিবর্তন এখনো Firestore-এ push হয়নি
     * (ইউজারের করা এডিট) — sync worker এই ফ্ল্যাগ দেখে push করবে।
     * false হলে বোঝায় এই লেখা Firestore থেকে আসা (pull/listener), তাই ইতিমধ্যে sync করা।
     */
    suspend fun save(payload: BackupPayload, markPendingSync: Boolean) = withContext(Dispatchers.Default) {
        dao.upsert(
            BackupCacheEntity(
                payloadJson = BackupJson.encode(payload),
                localUpdatedAt = System.currentTimeMillis(),
                pendingSync = markPendingSync
            )
        )
        Unit
    }

    suspend fun hasPendingSync(): Boolean = dao.isPendingSync() ?: false

    /** কিল-সুইচ (AccessGate) লোকাল ক্যাশ পুরোপুরি মোছার জন্য */
    suspend fun clear() = withContext(Dispatchers.Default) { dao.clearAll() }
}
