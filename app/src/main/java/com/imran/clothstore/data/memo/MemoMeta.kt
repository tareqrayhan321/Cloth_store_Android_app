package com.imran.clothstore.data.memo

/**
 * একটা মেমো-ছবির metadata — Firestore-এ শুধু এটুকুই থাকে।
 * আসল ছবি Google Drive-এ; driveFileId দিয়ে সেটা খুঁজে আনা হয়।
 *
 * Firestore পথ: party_memos/{partyKey}/files/{driveFileId}
 * partyKey = "{EntryCategory.name}_{entryId}"
 */
data class MemoMeta(
    val driveFileId: String = "",
    val fileName: String = "",
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    /** আপলোডের সময়, epoch millis */
    val createdAt: Long = 0L
) {
    val id: String get() = driveFileId
}

fun partyKeyOf(categoryName: String, entryId: Long): String = "${categoryName}_$entryId"
