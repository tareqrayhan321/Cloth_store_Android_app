package com.imran.clothstore.data.backup

import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

/**
 * ওয়েব অ্যাপের Firestore সিঙ্ক পেলোড (imran_store/backup) এর হুবহু কাঠামো।
 * এটাই একমাত্র ডকুমেন্ট যেখানে পুরো অ্যাপের সব ডেটা থাকে — প্রতিটা ক্যাটাগরির জন্য আলাদা
 * কালেকশন নেই। এই মডেল আগের ধাপগুলোতে ভুলভাবে তৈরি করা /entries/{category}/items,
 * /weekly_reports, /notifications, /fabric_groups কালেকশনগুলোর প্রতিস্থাপন।
 *
 * Firestore পাথ: imran_store/backup (একটাই ডকুমেন্ট, cloud.doc("backup"))
 *
 * @Serializable — অফলাইন-ফার্স্ট Room ক্যাশে (data/local/) JSON হিসেবে সংরক্ষণের জন্য
 * (দেখুন BackupJson.kt)। Firestore-এর সাথে যোগাযোগ এখনো toObject()/POJO ম্যাপিং দিয়েই হয়,
 * এই annotation শুধু লোকাল ক্যাশ লেয়ারের জন্য।
 */
@Serializable
data class BackupPayload(
    /** ওয়েব অ্যাপের সিঙ্ক সিক্রেট — নিরাপত্তা যাচাইয়ের জন্য নয়, শুধু ডেটা ট্যাগ */
    val secret: String = "1212",
    val updatedAt: Long = System.currentTimeMillis(),

    // ── is_c1_list .. is_c4_list: চারটা এন্ট্রি ক্যাটাগরি (id-ভিত্তিক merge) ──
    @get:PropertyName("is_c1_list")
    @set:PropertyName("is_c1_list")
    var is_c1_list: List<BackupEntry> = emptyList(), // রেগুলার কাস্টমার
    @get:PropertyName("is_c2_list")
    @set:PropertyName("is_c2_list")
    var is_c2_list: List<BackupEntry> = emptyList(), // ইর-রেগুলার কাস্টমার
    @get:PropertyName("is_c3_list")
    @set:PropertyName("is_c3_list")
    var is_c3_list: List<BackupEntry> = emptyList(), // রেগুলার মহাজন
    @get:PropertyName("is_c4_list")
    @set:PropertyName("is_c4_list")
    var is_c4_list: List<BackupEntry> = emptyList(), // ইর-রেগুলার মহাজন

    // ── fabricPurchaseData_v2: কাপড় ক্রয় গ্রুপ (id-ভিত্তিক merge) ──
    val fabricPurchaseData_v2: List<BackupFabricGroup> = emptyList(),

    // ── chart_data: সাপ্তাহিক রিপোর্ট (ts-ভিত্তিক merge, সর্বোচ্চ ২৬টা) ──
    val chart_data: List<BackupWeeklyReport> = emptyList(),

    // ── profitStripData_v1: সর্বশেষ net/gross (single-object, last-write-wins) ──
    val profitStripData_v1: ProfitStripData? = null,

    // ── is_deleted_ids: প্রতিটা key অনুযায়ী ডিলিট হওয়া id-দের তালিকা (tombstone) ──
    @get:PropertyName("is_deleted_ids")
    @set:PropertyName("is_deleted_ids")
    var is_deleted_ids: Map<String, List<Long>> = emptyMap()
)

/** ওয়েব অ্যাপের KPI স্ট্রিপে দেখানো Weekly Gross/Net Profit */
@Serializable
data class ProfitStripData(
    val net: Double = 0.0,
    val gross: Double = 0.0
)

/**
 * is_c1_list..is_c4_list এর একটা এন্ট্রি (কাস্টমার/মহাজন)।
 * id সংখ্যা (Date.now()), string নয় — merge/dedup এই id দিয়েই হয়।
 * name, addr, mob, memo, bill, joma, baki, goj, note, date হুবহু ওয়েব অ্যাপের ফিল্ড নাম।
 */
@Serializable
data class BackupEntry(
    val id: Long = 0L,
    val name: String = "",
    val addr: String = "",
    val mob: String = "",
    val photoUrl: String = "",
    val memo: String = "",
    val bill: Double = 0.0,
    val joma: Double = 0.0,
    val baki: Double = 0.0,
    /** মহাজন এন্ট্রির আসল/প্রাথমিক পাওনা — একবার সেভ হয়ে আর বদলায় না। দেখুন Entry.kt-এর কমেন্ট। */
    val initPawna: Double? = null,
    val goj: String = "",
    val note: String = "",
    val date: String = "",
    val order: Long = 0L,
    /**
     * history আইটেমের বকেয়ার পরিমাণ ফিল্ড কাস্টমার ও মহাজনের জন্য আলাদা:
     * কাস্টমারে h.bill, মহাজনে h.baki। তাই দুটো ফিল্ডই এখানে রাখা হয়েছে —
     * ক্যাটাগরি অনুযায়ী সঠিকটা ব্যবহার হবে (দেখুন HistoryReader.kt)।
     */
    val history: List<BackupHistoryItem> = emptyList()
)

@Serializable
data class BackupHistoryItem(
    val type: String = "", // "bokeyoa" | "joma"
    val date: String = "",
    val memo: String = "",
    /** কাস্টমার ক্যাটাগরিতে (is_c1_list/is_c2_list) ব্যবহৃত */
    val bill: Double = 0.0,
    /** মহাজন ক্যাটাগরিতে (is_c3_list/is_c4_list) ব্যবহৃত — কাস্টমারের bill এর সমতুল্য */
    val baki: Double = 0.0,
    val joma: Double = 0.0,
    val goj: String = "",
    val note: String = "",
    /** জোড়া-চিহ্ন — Entry.kt-এর HistoryItem.pair দেখুন; ওয়েব অ্যাপ ফিল্ডটা উপেক্ষা করে */
    val pair: Long = 0L
)

/** fabricPurchaseData_v2 এর একটা গ্রুপ — id সংখ্যা */
@Serializable
data class BackupFabricGroup(
    val id: Long = 0L,
    val name: String = "",
    val unit: String = "গজ",
    val purchases: List<BackupFabricPurchase> = emptyList()
)

@Serializable
data class BackupFabricPurchase(
    val id: Long = 0L,
    val date: String = "",
    val qty: Double = 0.0,
    val rate: Double = 0.0,
    val total: Double = 0.0
)

/**
 * chart_data এর একটা এন্ট্রি — ts দিয়ে merge হয় (id নয়)। month আলাদা ফিল্ড হিসেবে
 * রাখা হয়েছে (0-11), label শুধু প্রদর্শনের জন্য তৈরি টেক্সট।
 */
@Serializable
data class BackupWeeklyReport(
    val id: Long = 0L,
    val ts: Long = 0L,
    val label: String = "",
    val year: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val sales: Double = 0.0,
    val cogs: Double = 0.0,
    val gross: Double = 0.0,
    val opex: Double = 0.0,
    val fixed: Double = 0.0,
    val zakat: Double = 0.0,
    val net: Double = 0.0,
    val cashIn: Double = 0.0,
    val cashOut: Double = 0.0,
    val cashPurchase: Double = 0.0,
    val oldDebt: Double = 0.0,
    val cashProfit: Double = 0.0,
    val stockInYard: Double = 0.0,
    val stockOutYard: Double = 0.0,
    val purchaseTotal: Double = 0.0,
    val cashSale: Double = 0.0,
    val creditSale: Double = 0.0,
    val oldCollection: Double = 0.0
)
