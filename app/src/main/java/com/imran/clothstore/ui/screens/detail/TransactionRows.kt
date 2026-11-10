package com.imran.clothstore.ui.screens.detail

import com.imran.clothstore.data.model.Entry

/** ডিটেইল স্ক্রিনের Excel টেবিলের একটা রো — running বাকি সহ */
data class TxnRow(
    val date: String,
    val memo: String,
    val type: TxnType,
    val amount: Double,
    val runningBaki: Double,
    val goj: String,
    val note: String,
    /**
     * একই (non-empty) মেমোর bill + joma মার্জ হলে জমার অংশ এখানে থাকে (ওয়েব অ্যাপের
     * _mergeJoma প্যাটার্ন — index.html লাইন ৬১৫০-৬১৬৪)। null মানে এই রো merge হয়নি —
     * টেবিলের "জমা" কলামে তখন "—" দেখানো হয়। শুধু BILL_OR_PAWNA টাইপ রো-তেই সেট হতে পারে।
     */
    val mergedJomaAmount: Double? = null,
    /** এই রো-র উৎস: history-র ইনডেক্স (>= 0), অথবা SRC_BASE_BILL / SRC_BASE_JOMA (মূল এন্ট্রির ফিল্ড) */
    val src: Int = SRC_BASE_BILL,
    /** মার্জ হওয়া জমার উৎস (একই নিয়ম), মার্জ না হলে null */
    val jomaSrc: Int? = null
)

/** মূল এন্ট্রির bill/initPawna ফিল্ড থেকে আসা রো */
const val SRC_BASE_BILL = -1
/** মূল এন্ট্রির joma ফিল্ড থেকে আসা রো */
const val SRC_BASE_JOMA = -2

enum class TxnType { BILL_OR_PAWNA, JOMA }

/**
 * ওয়েব অ্যাপের fvOpenDetail() এর টেবিল-বিল্ডিং লজিকের Kotlin সংস্করণ।
 * প্রাথমিক এন্ট্রি (bill/joma) + history[] থেকে কালানুক্রমিক লেনদেন তালিকা এবং
 * প্রতিটা ধাপে running বাকি হিসাব করে।
 *
 * isCustomer=true হলে "bill" ফিল্ড ব্যবহৃত হয়; false (মহাজন) হলে initPawna হিসাব হয়
 * baki + joma থেকে (যেহেতু ওয়েব অ্যাপে সরবরাহকারীর জন্য baki আগে থেকেই netted থাকে)।
 */
fun buildTransactionRows(entry: Entry, isCustomer: Boolean): List<TxnRow> {
    data class RawTxn(
        val date: String, val memo: String, val type: TxnType,
        val amount: Double, val goj: String, val note: String, val src: Int
    )

    val raw = mutableListOf<RawTxn>()

    if (isCustomer) {
        if (entry.bill > 0) {
            raw.add(RawTxn(entry.date, entry.memo, TxnType.BILL_OR_PAWNA, entry.bill, entry.goj, entry.note, SRC_BASE_BILL))
        }
        if (entry.joma > 0) {
            raw.add(RawTxn(entry.date, entry.memo, TxnType.JOMA, entry.joma, "", "", SRC_BASE_JOMA))
        }
        entry.history.forEachIndexed { idx, h ->
            when (h.type) {
                "bokeyoa" -> raw.add(RawTxn(h.date, h.memo, TxnType.BILL_OR_PAWNA, h.bokeyoaAmount(isCustomer = true), h.goj, h.note, idx))
                "joma" -> raw.add(RawTxn(h.date, h.memo, TxnType.JOMA, h.joma, "", h.note, idx))
            }
        }
    } else {
        // মহাজনের ক্ষেত্রে: initPawna এন্ট্রিতে সেভ করা আসল/প্রাথমিক পাওনা ব্যবহার করা হয়
        // (এন্ট্রি তৈরির সময় একবারই সেট হয়) — পুরনো ডেটায় ফিল্ডটা না থাকলে baki + joma
        // দিয়ে ফলব্যাক করা হয় (baki প্রতিটা নতুন লেনদেনের পর netted হয়ে যায় বলে সেই
        // ফলব্যাক প্রথম লেনদেনের পর থেকে আর সঠিক থাকে না, কিন্তু initPawna না থাকা মানেই
        // পুরনো এন্ট্রি — ওয়েব অ্যাপের ফলব্যাক প্যাটার্নের সাথে সামঞ্জস্যপূর্ণ)
        val initPawna = entry.initPawna ?: (entry.baki + entry.joma)
        if (initPawna > 0) {
            raw.add(RawTxn(entry.date, entry.memo, TxnType.BILL_OR_PAWNA, initPawna, entry.goj, entry.note, SRC_BASE_BILL))
        }
        if (entry.joma > 0) {
            raw.add(RawTxn(entry.date, entry.memo, TxnType.JOMA, entry.joma, "", "", SRC_BASE_JOMA))
        }
        entry.history.forEachIndexed { idx, h ->
            when (h.type) {
                // ওয়েব অ্যাপে মহাজনের history আইটেমে বকেয়ার পরিমাণ h.baki ফিল্ডে থাকে, h.bill এ নয়
                "bokeyoa" -> raw.add(RawTxn(h.date, h.memo, TxnType.BILL_OR_PAWNA, h.bokeyoaAmount(isCustomer = false), h.goj, h.note, idx))
                "joma" -> raw.add(RawTxn(h.date, h.memo, TxnType.JOMA, h.joma, "", h.note, idx))
            }
        }
    }

    // কাস্টমার ও মহাজন — দুই ক্ষেত্রেই তারিখ অনুযায়ী সাজানো (আগে শুধু মহাজনে হতো, তাই কাস্টমারে পরে
    // বসানো আগের তারিখের লেনদেন শেষে চলে যেত)। sortedBy স্থিতিশীল: একই তারিখে ইনপুটের ক্রম বজায় থাকে।
    // তারিখ ফাঁকা থাকলে সবার শেষে; "yyyy-MM-dd" ছাড়া সময়ের অংশ থাকলেও প্রথম ১০ অক্ষরই ধরা হয়।
    val sortedRaw = raw.sortedBy { dateSortKey(it.date) }
    raw.clear()
    raw.addAll(sortedRaw)

    // ── bill + joma merge করে এক রো-তে দেখানো — নিয়মটা সব ক্ষেত্রে সমান (তারেকের নির্দেশ) ──
    // বকেয়া/পাওনা ফর্মে একসাথে দেওয়া জমা সবসময় সেই বিলের লাইনেই বসবে। মার্জ হয় যেকোনো একভাবে:
    //  (১) একই (non-empty, trim করা) মেমো — ওয়েব অ্যাপের _mergeJoma প্যাটার্ন (index.html ৬১৫০-৬১৬৪);
    //  (২) মেমো না থাকলেও: একই তারিখে বিলের ঠিক পরের JOMA রো — ফর্ম জোড়া হিসেবে সেভ হওয়া জমা
    //      (addBokeyoa/editRow/নতুন এন্ট্রি সব জায়গায় বিলের পরপরই জমা বসে)। আগে শুধু (১) ছিল,
    //      তাই মেমো ফাঁকা থাকলে একই ফর্মের জমা আলাদা লাইনে চলে যেত — সেটাই ছিল অসঙ্গতি।
    // পাওয়া গেলে জমার অংশ mergedJomaAmount-এ থাকে, JOMA রো আউটপুট থেকে বাদ পড়ে
    // (একটা JOMA একবারই কোনো একটা BILL-এর সাথে মার্জ হতে পারে)।
    data class MergedTxn(
        val date: String, val memo: String, val type: TxnType,
        val amount: Double, val goj: String, val note: String,
        val mergedJomaAmount: Double?, val mergedJomaNote: String?,
        val src: Int, val mateSrc: Int?
    )

    val consumedJomaIdx = mutableSetOf<Int>()
    val merged = mutableListOf<MergedTxn>()
    for (i in raw.indices) {
        val t = raw[i]
        if (t.type == TxnType.BILL_OR_PAWNA) {
            val memoKey = t.memo.trim()
            var mateIdx = -1
            for (j in raw.indices) {
                if (j == i || j in consumedJomaIdx) continue
                val cand = raw[j]
                if (cand.type != TxnType.JOMA) continue
                val sameMemo = memoKey.isNotEmpty() && cand.memo.trim() == memoKey
                // ফর্ম-জোড়া: একই তারিখে বিলের ঠিক পরের জমা — তবে জমার নিজের মেমো ফাঁকা হলেই
                // (আলাদা মেমোওয়ালা জমা অন্য বিলের জোড়া হতে পারে, সেটা এই বিলে টানা যাবে না)
                val formPair = j == i + 1 && t.date.isNotBlank() && cand.memo.trim().isEmpty() &&
                    dateSortKey(cand.date) == dateSortKey(t.date)
                if (sameMemo || formPair) {
                    mateIdx = j
                    break
                }
            }
            if (mateIdx >= 0) {
                consumedJomaIdx.add(mateIdx)
                val mate = raw[mateIdx]
                merged.add(
                    MergedTxn(
                        date = t.date, memo = t.memo, type = t.type, amount = t.amount,
                        goj = t.goj, note = t.note.ifBlank { mate.note },
                        mergedJomaAmount = mate.amount, mergedJomaNote = mate.note,
                        src = t.src, mateSrc = mate.src
                    )
                )
                continue
            }
        }
        if (i in consumedJomaIdx) continue
        merged.add(
            MergedTxn(
                date = t.date, memo = t.memo, type = t.type, amount = t.amount,
                goj = t.goj, note = t.note, mergedJomaAmount = null, mergedJomaNote = null,
                src = t.src, mateSrc = null
            )
        )
    }

    var running = 0.0
    return merged.map { t ->
        if (t.type == TxnType.BILL_OR_PAWNA) {
            running += t.amount
            if (t.mergedJomaAmount != null && t.mergedJomaAmount != 0.0) {
                running = maxOf(0.0, running - t.mergedJomaAmount)
            }
        } else {
            running = maxOf(0.0, running - t.amount)
        }
        TxnRow(
            date = t.date,
            memo = t.memo,
            type = t.type,
            amount = t.amount,
            runningBaki = running,
            goj = t.goj,
            note = t.note,
            mergedJomaAmount = t.mergedJomaAmount,
            src = t.src,
            jomaSrc = t.mateSrc
        )
    }
}

/**
 * এন্ট্রির বর্তমান বাকি — টেবিলের শেষ রো-র "মোট বাকি"-র সাথে হুবহু মেলে (একই হিসাব)।
 * সেভ করা baki ফিল্ড পুরনো/ভুল হলেও (যেমন ওয়েব অ্যাপের মহাজন-এন্ট্রিতে প্রাথমিক পাওনা initPawna-য় থাকে,
 * bill-এ নয়) তালিকা, হোম ও নেভবারে সঠিক অঙ্ক দেখানোর জন্য এটাই ব্যবহার হয়।
 * কোনো রো না থাকলে সেভ করা baki-ই ফেরত দেয়।
 */
fun currentBaki(entry: Entry, isCustomer: Boolean): Double {
    val rows = buildTransactionRows(entry, isCustomer)
    return if (rows.isEmpty()) entry.baki else rows.last().runningBaki
}

/**
 * তারিখকে "yyyy-MM-dd" সাজানোর কী-তে আনে। ওয়েব অ্যাপ বা পুরনো ডেটায় "d/M/yyyy" বা "dd-MM-yyyy" ফরম্যাটও থাকতে পারে —
 * সরাসরি স্ট্রিং তুলনা করলে সেগুলো ভুল জায়গায় বসত। বুঝতে না পারলে বা ফাঁকা হলে সবার শেষে।
 */
private fun dateSortKey(raw: String): String {
    val d = raw.trim()
    if (d.isEmpty()) return "9999-99-99"
    Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(d)?.let {
        val (y, m, day) = it.destructured
        return "%s-%02d-%02d".format(y, m.toInt(), day.toInt())
    }
    Regex("""^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})""").find(d)?.let {
        val (day, m, y) = it.destructured
        return "%s-%02d-%02d".format(y, m.toInt(), day.toInt())
    }
    return "9999-99-99"
}
