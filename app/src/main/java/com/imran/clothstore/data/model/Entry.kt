package com.imran.clothstore.data.model

/**
 * fvOpen(1..4) এবং partyOpen() স্ক্রিনে যে তালিকা (কাস্টমার/মহাজন/পার্টি) দেখানো হয়
 * তার একটা এন্ট্রি। মূল ওয়েব অ্যাপের ফর্ম ফিল্ড অনুযায়ী (epSaveCustomer/epSaveSupplier)
 * — নাম, ঠিকানা, মোবাইল, ম্যামো নং, বিল/বাকি, জমা, গজ, মন্তব্য — হুবহু রাখা হয়েছে।
 *
 * id সংখ্যা (Long) কারণ ওয়েব অ্যাপ Date.now() দিয়ে numeric id তৈরি করে এবং সেই id দিয়েই
 * Firestore-এর imran_store/backup ডকুমেন্টের is_c1_list..is_c4_list array-এ merge/dedup হয়।
 */
data class Entry(
    val id: Long = 0L,
    val name: String = "",
    /** ঠিকানা */
    val addr: String = "",
    /** মোবাইল নম্বর, +৮৮ প্রিফিক্সসহ (যেমন +8801XXXXXXXXX) */
    val mob: String = "",
    val photoUrl: String = "",
    /** ম্যামো/চালান নম্বর */
    val memo: String = "",
    /** কাস্টমারের ক্ষেত্রে মোট বিল, মহাজনের ক্ষেত্রে প্রাথমিক পাওনা */
    val bill: Double = 0.0,
    /** জমা/পরিশোধিত টাকা */
    val joma: Double = 0.0,
    /** নীট বাকি = max(0, bill - joma) — কাস্টমার ও মহাজন উভয় ক্ষেত্রে */
    val baki: Double = 0.0,
    /**
     * মহাজন এন্ট্রির আসল/প্রাথমিক পাওনা — এন্ট্রি তৈরির সময় একবারই সেভ হয়, পরে কখনো বদলায় না।
     * ওয়েব অ্যাপের item.initPawna এর সমতুল্য (index.html লাইন ৬২১৩, ৬৫৩৭)। null মানে পুরনো
     * ডেটা যেখানে ফিল্ডটা এখনো লেখা হয়নি — সেক্ষেত্রে baki + joma দিয়ে ফলব্যাক করতে হবে
     * (দেখুন TransactionRows.kt)।
     */
    val initPawna: Double? = null,
    /** গজ (কাপড়ের পরিমাণ) — সংখ্যা না হয়ে টেক্সট হতে পারে বলে String */
    val goj: String = "",
    val note: String = "",
    /** তারিখ, ISO ফরম্যাটে অথবা "yyyy-MM-dd" */
    val date: String = "",
    val order: Long = 0L,
    /** ডিটেইল স্ক্রিনে Excel টেবিলে দেখানো পরবর্তী লেনদেনসমূহ (প্রাথমিক এন্ট্রির পরে যোগ হওয়া বকেয়া/জমা) */
    val history: List<HistoryItem> = emptyList()
)

/**
 * ডিটেইল স্ক্রিনে "নতুন বকেয়া" বা "নতুন জমা" বাটনে যোগ হওয়া একটা লেনদেন।
 * ওয়েব অ্যাপের item.history[] এর একটা এন্ট্রির সমতুল্য।
 *
 * গুরুত্বপূর্ণ: বকেয়ার পরিমাণ কাস্টমারে h.bill ফিল্ডে থাকে, মহাজনে h.baki ফিল্ডে —
 * ওয়েব অ্যাপের আসল কোডেই এই অসামঞ্জস্য আছে। তাই দুটো ফিল্ডই রাখা হয়েছে এবং
 * amountFor(isCustomer) হেল্পার দিয়ে সঠিকটা পড়া হয়।
 */
data class HistoryItem(
    val type: String = "", // "bokeyoa" (বকেয়া/বিল) অথবা "joma" (জমা)
    val date: String = "",
    val memo: String = "",
    /** type == "bokeyoa" এবং কাস্টমার ক্যাটাগরি হলে ব্যবহৃত */
    val bill: Double = 0.0,
    /** type == "bokeyoa" এবং মহাজন ক্যাটাগরি হলে ব্যবহৃত */
    val baki: Double = 0.0,
    /** type == "joma" হলে জমার পরিমাণ */
    val joma: Double = 0.0,
    val goj: String = "",
    val note: String = "",
    /**
     * জোড়া-চিহ্ন (2026-11-10, তারেকের নিয়ম): একই ফর্ম থেকে একসাথে সেভ হওয়া বকেয়া+জমা
     * একই pair মান পায় — টেবিলে শুধু সেই জমাই বিলের লাইনে মার্জ হয়। আলাদা জমা-ফর্মের
     * জমা নিজস্ব আলাদা pair পায় (কখনো মার্জ নয়)। 0 = পুরনো/ওয়েব ডেটা (চিহ্ন নেই) —
     * সেক্ষেত্রে আগের মেমো-মিলের নিয়মই প্রযোজ্য।
     */
    val pair: Long = 0L
) {
    /** ওয়েব অ্যাপের isCustomer ? h.bill : h.baki লজিকের সমতুল্য */
    fun bokeyoaAmount(isCustomer: Boolean): Double = if (isCustomer) bill else baki
}

/** হোম কার্ডের ধরন — fvOpen(কার্ড নম্বর) এর সাথে মিল রেখে */
enum class EntryCategory(
    val cardId: Int,
    val titleBn: String,
    val isCustomerType: Boolean,
    /** ওয়েব অ্যাপের localStorage key — imran_store/backup পেলোডে এই নামেই array থাকে */
    val backupKey: String
) {
    REGULAR_CUSTOMER(1, "রেগুলার কাস্টমার", true, "is_c1_list"),
    IRREGULAR_CUSTOMER(2, "ইর-রেগুলার কাস্টমার", true, "is_c2_list"),
    REGULAR_SUPPLIER(3, "রেগুলার মহাজন", false, "is_c3_list"),
    IRREGULAR_SUPPLIER(4, "ইর-রেগুলার মহাজন", false, "is_c4_list"),
    PARTY(10, "পার্টি", true, "")
}

/** হোম কার্ডে দেখানো সামারি — "মোট X জন · ৳Y" */
data class CategorySummary(
    val category: EntryCategory,
    val totalCount: Int = 0,
    val totalAmount: Double = 0.0
)
