package com.imran.clothstore.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * একমাত্র সোর্স অফ ট্রুথ — সব টাকা/গজ/সংক্ষিপ্ত-সংখ্যা/তারিখ ফরম্যাটিং এখান থেকেই হবে,
 * সব সময় বাংলা অঙ্কে (০-৯) আউটপুট দেবে। ওয়েব অ্যাপের startObserving()/scanAndConvert()
 * (index.html লাইন ১২৬১৮-১২৭০১) গ্লোবাল MutationObserver-এর সমতুল্য আচরণ — কিন্তু
 * Android-এ যেহেতু runtime-এ টেক্সট নোড স্ক্যান করা যায় না, তাই প্রতিটা ফরম্যাটার ফাংশনেই
 * conversion বেক করা হয়েছে।
 *
 * ব্যতিক্রম: ফোন নম্বর কখনো এই ফাংশন দিয়ে ফরম্যাট করা হবে না — ওয়েব অ্যাপে PHONE_RE
 * ম্যাচ হওয়া নম্বর ইংরেজি অঙ্কেই থাকে (দেখুন EntryRowCard.kt, DebtorsListScreen.kt)।
 */

private val BN_DIGITS = mapOf(
    '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
    '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
)

/** যেকোনো সংখ্যা-সংবলিত স্ট্রিং-এর ইংরেজি অঙ্ক বাংলা অঙ্কে বদলে দেয়; অন্য সব অক্ষর অপরিবর্তিত */
fun String.toBengaliDigits(): String = map { BN_DIGITS[it] ?: it }.joinToString("")

fun Int.toBengaliDigits(): String = toString().toBengaliDigits()
fun Long.toBengaliDigits(): String = toString().toBengaliDigits()

/**
 * লক্ষ-পদ্ধতিতে কমা বসানো (বাংলাদেশি রীতি): ডানদিক থেকে প্রথম ৩ অঙ্ক একসাথে,
 * তারপর প্রতি ২ অঙ্কে কমা — যেমন 123500 → "1,23,500", 12345678 → "1,23,45,678"।
 * (মিলিয়ন-পদ্ধতির প্রতি-৩-অঙ্কের কমা নয় — তারেকের নির্দেশ।)
 */
fun groupLakh(digits: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    return head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
}

/** ওয়েব অ্যাপের T()/fmt() ফাংশনের সমতুল্য — "৳১,২৩,৫০০" জাতীয় ফরম্যাট (লক্ষ-পদ্ধতি), ঋণাত্মক হলে '−' প্রিফিক্স, বাংলা অঙ্কে */
fun formatTaka(v: Double): String {
    val rounded = round(v).toLong()
    val absStr = groupLakh(abs(rounded).toString())
    return ((if (rounded < 0) "−" else "") + "৳" + absStr).toBengaliDigits()
}

/** ওয়েব অ্যাপের fmtGaj() এর সমতুল্য — "১২.৫ গজ" জাতীয় ফরম্যাট, বাংলা অঙ্কে */
fun formatGaj(v: Double): String {
    val rounded = round(v * 100) / 100
    return "$rounded গজ".toBengaliDigits()
}

/** ওয়েব অ্যাপের fmtV() এর সমতুল্য — চার্টের বার লেবেলের জন্য সংক্ষিপ্ত ফরম্যাট (১.২ল / ৫.৩হা), বাংলা অঙ্কে */
fun formatCompact(v: Double): String {
    val a = abs(v)
    val s = when {
        a >= 100000 -> "%.1fল".format(a / 100000)
        a >= 1000 -> "%.1fহা".format(a / 1000)
        else -> round(a).toLong().toString()
    }
    return ((if (v >= 0) "+" else "−") + s).toBengaliDigits()
}

/** "yyyy-MM-dd" বা ISO তারিখ স্ট্রিং থেকে "২৬/৯/২০২৬" জাতীয় বাংলা-অঙ্কে প্রদর্শনযোগ্য তারিখ।
 *  পার্স ব্যর্থ হলে মূল স্ট্রিং-ই বাংলা অঙ্কে বদলে ফেরত দেয় (crash এড়াতে)। */
fun formatDate(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date: Date = parser.parse(isoDate.take(10)) ?: return isoDate.toBengaliDigits()
        SimpleDateFormat("d/M/yyyy", Locale.US).format(date).toBengaliDigits()
    } catch (e: Exception) {
        isoDate.toBengaliDigits()
    }
}

/** কার্ডের সংক্ষিপ্ত সংখ্যা লেবেল: "৩.৫ল" (লক্ষ), "১০.৫হা" (হাজার), ঋণাত্মক হলে "-" প্রিফিক্স।
 *  টাকা বোঝাতে আলাদা "ট"/"৳" লেখা হয় না। */
fun formatCompactTaka(v: Double): String {
    val a = abs(v)
    val body = when {
        a >= 100000 -> "%.1fল".format(Locale.US, a / 100000)
        a >= 1000 -> "%.1fহা".format(Locale.US, a / 1000)
        else -> round(a).toLong().toString()
    }
    return ((if (v < 0) "-" else "") + body).toBengaliDigits()
}
