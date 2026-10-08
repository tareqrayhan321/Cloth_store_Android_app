package com.imran.clothstore.data.memo

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * মেমো-ছবির metadata Firestore-এ রাখা/পড়া। ছবির বাইট এখানে আসে না (সেটা Drive-এ)।
 * বড় imran_store/backup ডকুমেন্টে হাত দেওয়া হয়নি — ১MB সীমা এড়াতে আলাদা collection।
 */
class MemoRepository {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun files(partyKey: String) =
        db.collection(COLLECTION).document(partyKey).collection(SUB_COLLECTION)

    /** নতুন ছবি আগে (createdAt অবরোহী)। একক-ফিল্ড orderBy, তাই আলাদা ইনডেক্স লাগে না। */
    fun observe(partyKey: String): Flow<List<MemoMeta>> = callbackFlow {
        val reg = files(partyKey)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { d ->
                    MemoMeta(
                        driveFileId = d.getString("driveFileId") ?: d.id,
                        fileName = d.getString("fileName") ?: "",
                        mimeType = d.getString("mimeType") ?: "image/jpeg",
                        sizeBytes = d.getLong("sizeBytes") ?: 0L,
                        width = (d.getLong("width") ?: 0L).toInt(),
                        height = (d.getLong("height") ?: 0L).toInt(),
                        createdAt = d.getLong("createdAt") ?: 0L
                    )
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun add(partyKey: String, meta: MemoMeta) {
        val data = hashMapOf<String, Any>(
            "driveFileId" to meta.driveFileId,
            "fileName" to meta.fileName,
            "mimeType" to meta.mimeType,
            "sizeBytes" to meta.sizeBytes,
            "width" to meta.width,
            "height" to meta.height,
            "createdAt" to meta.createdAt
        )
        files(partyKey).document(meta.driveFileId).set(data).await()
    }

    suspend fun delete(partyKey: String, driveFileId: String) {
        files(partyKey).document(driveFileId).delete().await()
    }

    companion object {
        const val COLLECTION = "party_memos"
        const val SUB_COLLECTION = "files"
    }
}
