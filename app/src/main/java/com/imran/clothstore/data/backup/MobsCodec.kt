package com.imran.clothstore.data.backup

/**
 * মোবাইল নাম্বার আলাদা রাখার কোডেক (v1.1.6, মালিকের নির্দেশে)।
 *
 * নীতি: `imran_store/backup` ডকুমেন্টে কখনো মোবাইল নাম্বার থাকবে না — নাম্বার
 * থাকে আলাদা `imran_store/contacts` ডকুমেন্টে, যেটা শুধু বৈধ ব্যবহারকারীরা
 * পড়তে/লিখতে পারে (দেখুন access/ContactsVault ও firestore.rules)। বাকি সবাই
 * লেনদেনের ডাটা দেখতে ও এন্ট্রি দিতে পারবে, কিন্তু নাম্বার পাবে না।
 *
 * এই অবজেক্টের কাজ শুধু payload ↔ নাম্বার-ম্যাপ রূপান্তর — কোনো Firebase
 * নির্ভরতা নেই, তাই ইউনিট টেস্টে সরাসরি যাচাই করা যায়।
 */
object MobsCodec {

    /** payload-এর চারটা এন্ট্রি-তালিকা থেকে id → mob ম্যাপ (ফাঁকা mob বাদ) */
    fun extractMobs(payload: BackupPayload): Map<Long, String> {
        val result = LinkedHashMap<Long, String>()
        fun collect(list: List<BackupEntry>) {
            list.forEach { e -> if (e.mob.isNotBlank()) result[e.id] = e.mob }
        }
        collect(payload.is_c1_list)
        collect(payload.is_c2_list)
        collect(payload.is_c3_list)
        collect(payload.is_c4_list)
        return result
    }

    /** সব এন্ট্রির mob ফাঁকা করে দেয় — backup ডকে push করার আগে বাধ্যতামূলক */
    fun stripMobs(payload: BackupPayload): BackupPayload {
        fun strip(list: List<BackupEntry>): List<BackupEntry> =
            list.map { if (it.mob.isEmpty()) it else it.copy(mob = "") }
        return payload.copy(
            is_c1_list = strip(payload.is_c1_list),
            is_c2_list = strip(payload.is_c2_list),
            is_c3_list = strip(payload.is_c3_list),
            is_c4_list = strip(payload.is_c4_list)
        )
    }

    /** নাম্বার-ম্যাপ থেকে প্রতিটা এন্ট্রিতে mob বসায় (ম্যাপে না থাকলে আগেরটাই থাকে) */
    fun applyMobs(payload: BackupPayload, mobs: Map<Long, String>): BackupPayload {
        if (mobs.isEmpty()) return payload
        fun apply(list: List<BackupEntry>): List<BackupEntry> =
            list.map { e -> mobs[e.id]?.let { e.copy(mob = it) } ?: e }
        return payload.copy(
            is_c1_list = apply(payload.is_c1_list),
            is_c2_list = apply(payload.is_c2_list),
            is_c3_list = apply(payload.is_c3_list),
            is_c4_list = apply(payload.is_c4_list)
        )
    }

    /** payload-এ থাকা সব এন্ট্রি id (চারটা তালিকা মিলিয়ে) */
    fun entryIds(payload: BackupPayload): Set<Long> =
        (payload.is_c1_list + payload.is_c2_list + payload.is_c3_list + payload.is_c4_list)
            .map { it.id }
            .toSet()

    /** tombstone (is_deleted_ids)-এর সব id */
    fun tombstonedIds(payload: BackupPayload): Set<Long> =
        payload.is_deleted_ids.values.flatten().toSet()
}
