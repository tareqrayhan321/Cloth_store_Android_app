package com.imran.clothstore.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imran.clothstore.data.backup.BackupRepository
import com.imran.clothstore.data.backup.entriesFor
import com.imran.clothstore.data.backup.toBackupEntry
import com.imran.clothstore.data.model.Entry
import com.imran.clothstore.data.model.EntryCategory
import com.imran.clothstore.data.model.HistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * fvDetailScreen এর ViewModel — fvDetailSaveBokeyoa()/fvDetailSaveJoma() এর সমতুল্য।
 * নতুন বকেয়া/জমা যোগ করার পর সম্পূর্ণ bill/joma/baki recalculate করে।
 *
 * entryId (Long) দিয়ে পুরো imran_store/backup payload observe করে সেখান থেকে এই
 * নির্দিষ্ট এন্ট্রিটা filter করে বের করে — যাতে অন্য ডিভাইসে হওয়া পরিবর্তনও সাথে সাথে প্রতিফলিত হয়।
 */
class DetailViewModel(
    private val category: EntryCategory,
    private val entryId: Long,
    initialEntry: Entry,
    private val repository: BackupRepository = BackupRepository()
) : ViewModel() {

    private val _entry = MutableStateFlow(initialEntry)
    val entry: StateFlow<Entry> = _entry.asStateFlow()

    val transactionRows: StateFlow<List<TxnRow>> = MutableStateFlow(
        buildTransactionRows(initialEntry, category.isCustomerType)
    ).also { state ->
        viewModelScope.launch {
            _entry.collect { e -> state.value = buildTransactionRows(e, category.isCustomerType) }
        }
    }.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeBackup().collect { payload ->
                val updated = payload.entriesFor(category).firstOrNull { it.id == entryId }
                if (updated != null) _entry.value = updated
            }
        }
    }

    /** নতুন জোড়া-চিহ্ন: বিদ্যমান সর্বোচ্চ pair ও বর্তমান সময়ের চেয়ে বড় একটি অনন্য মান */
    private fun nextPairId(history: List<HistoryItem>): Long =
        maxOf(System.currentTimeMillis(), (history.maxOfOrNull { it.pair } ?: 0L) + 1)

    /** নতুন বকেয়া/বিল (ও ঐচ্ছিক সাথে জমা) যোগ করে — কাস্টমার ও মহাজন উভয়ের জন্য */
    fun addBokeyoa(memo: String, date: String, goj: String, billAmount: Double, note: String, jomaAmount: Double) {
        val current = _entry.value
        val newHistory = current.history.toMutableList()
        // একই ফর্মের বকেয়া+জমা একই pair পায় — টেবিলে জমাটা এই বিলের লাইনেই মার্জ হবে
        val pid = nextPairId(current.history)
        // ওয়েব অ্যাপের মতোই: কাস্টমার হলে bill ফিল্ডে, মহাজন হলে baki ফিল্ডে বকেয়ার পরিমাণ রাখা হয়
        val historyItem = if (category.isCustomerType) {
            HistoryItem(type = "bokeyoa", date = date, memo = memo, bill = billAmount, goj = goj, note = note, pair = pid)
        } else {
            HistoryItem(type = "bokeyoa", date = date, memo = memo, baki = billAmount, goj = goj, note = note, pair = pid)
        }
        newHistory.add(historyItem)
        if (jomaAmount > 0) {
            newHistory.add(HistoryItem(type = "joma", date = date, memo = memo, joma = jomaAmount, pair = pid))
        }
        recalculateAndSave(current.copy(history = newHistory))
    }

    /** শুধু জমা যোগ করে — আলাদা ফর্মের জমা, তাই নিজস্ব pair: কোনো বিলের লাইনে মার্জ হবে না */
    fun addJoma(memo: String, date: String, jomaAmount: Double, note: String) {
        val current = _entry.value
        val newHistory = current.history.toMutableList()
        newHistory.add(HistoryItem(type = "joma", date = date, memo = memo, joma = jomaAmount, note = note, pair = nextPairId(current.history)))
        recalculateAndSave(current.copy(history = newHistory))
    }

    /**
     * একটা টেবিল-রো ডিলিট করে। মার্জ হওয়া রো হলে বিল ও জমা দুটো অংশই মুছে যায়।
     * উৎস: history ইনডেক্স, অথবা মূল এন্ট্রির bill/joma ফিল্ড (SRC_BASE_*)।
     */
    fun deleteRow(row: TxnRow) {
        val srcs = listOfNotNull(row.src, row.jomaSrc)
        var e = _entry.value
        if (SRC_BASE_BILL in srcs) {
            e = e.copy(bill = 0.0, initPawna = if (category.isCustomerType) e.initPawna else 0.0)
        }
        if (SRC_BASE_JOMA in srcs) e = e.copy(joma = 0.0)
        val hist = e.history.filterIndexed { i, _ -> i !in srcs }
        recalculateAndSave(e.copy(history = hist))
    }

    /**
     * একটা টেবিল-রো এডিট করে।
     * BILL রো: মেমো/তারিখ/গজ/পরিমাণ/মন্তব্য + (ঐচ্ছিক) সাথের জমা। JOMA রো: মেমো/তারিখ/জমা/মন্তব্য।
     * মার্জ হওয়া জমা 0 করলে সেই জমা-এন্ট্রি মুছে যায়; মার্জ না থাকলে জমা > 0 দিলে নতুন জমা যোগ হয়।
     */
    fun editRow(
        row: TxnRow,
        memo: String,
        date: String,
        goj: String,
        amount: Double,
        note: String,
        jomaAmount: Double
    ) {
        val isCustomer = category.isCustomerType
        var e = _entry.value
        val newDate = date.ifBlank { row.date }
        val hist = e.history.toMutableList()
        val removeIdx = mutableSetOf<Int>()
        val appended = mutableListOf<HistoryItem>()

        if (row.type == TxnType.BILL_OR_PAWNA) {
            if (row.src == SRC_BASE_BILL) {
                e = e.copy(
                    memo = memo, date = newDate, goj = goj, note = note, bill = amount,
                    initPawna = if (isCustomer) e.initPawna else amount
                )
            } else if (row.src in hist.indices) {
                val h = hist[row.src]
                hist[row.src] = if (isCustomer) {
                    h.copy(memo = memo, date = newDate, goj = goj, note = note, bill = amount)
                } else {
                    h.copy(memo = memo, date = newDate, goj = goj, note = note, baki = amount)
                }
            }
            val js = row.jomaSrc
            if (js == null && jomaAmount > 0) {
                // এডিট ফর্ম থেকে এই বিলের সাথে নতুন জমা — একই ফর্ম, তাই জোড়া বাঁধা হয়:
                // history-বিল হলে তার pair-ই (না থাকলে নতুন pair বানিয়ে বিলেও বসানো হয়),
                // মূল এন্ট্রির বিল হলে PAIR_WITH_BASE।
                val pid = when {
                    row.src == SRC_BASE_BILL -> PAIR_WITH_BASE
                    row.src in hist.indices && hist[row.src].pair != 0L -> hist[row.src].pair
                    row.src in hist.indices -> nextPairId(hist).also { hist[row.src] = hist[row.src].copy(pair = it) }
                    else -> nextPairId(hist)
                }
                appended += HistoryItem(type = "joma", date = newDate, memo = memo, joma = jomaAmount, pair = pid)
            }
            if (js != null) {
                if (jomaAmount > 0) {
                    if (js == SRC_BASE_JOMA) e = e.copy(joma = jomaAmount, memo = memo)
                    else if (js in hist.indices) hist[js] = hist[js].copy(memo = memo, joma = jomaAmount)
                } else {
                    if (js == SRC_BASE_JOMA) e = e.copy(joma = 0.0) else removeIdx += js
                }
            }
        } else {
            if (row.src == SRC_BASE_JOMA) {
                e = e.copy(joma = amount, memo = memo, date = newDate)
            } else if (row.src in hist.indices) {
                hist[row.src] = hist[row.src].copy(memo = memo, date = newDate, joma = amount, note = note)
            }
        }

        val newHist = hist.filterIndexed { i, _ -> i !in removeIdx } + appended
        recalculateAndSave(e.copy(history = newHist))
    }

    /** টেবিলের হিসাব (buildTransactionRows) থেকেই মোট বাকি বের করে সেভ করে — তাই তালিকার অঙ্ক সবসময় টেবিলের শেষ "মোট বাকি"-র সমান */
    private fun recalculateAndSave(updated: Entry) {
        val isCustomer = category.isCustomerType
        val current = _entry.value
        // মহাজনের initPawna না থাকলে (পুরনো এন্ট্রি) history-হীন অবস্থায় baki+joma-ই আসল পাওনা — এখনই স্থির করে রাখা হয়,
        // নইলে লেনদেন যোগের পর baki বদলে ফলব্যাক ভুল হয়ে যেত
        val pinned = if (!isCustomer && updated.initPawna == null && current.history.isEmpty()) {
            updated.copy(initPawna = current.baki + current.joma)
        } else updated

        val finalEntry = pinned.copy(baki = currentBaki(pinned, isCustomer))
        _entry.value = finalEntry

        viewModelScope.launch {
            repository.addOrUpdateEntry(category, finalEntry.toBackupEntry())
        }
    }
}
