package com.imran.clothstore.ui.screens.debtors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imran.clothstore.data.backup.BackupRepository
import com.imran.clothstore.data.backup.entriesFor
import com.imran.clothstore.data.backup.toBackupEntry
import com.imran.clothstore.data.model.Entry
import com.imran.clothstore.data.model.EntryCategory
import com.imran.clothstore.data.model.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** dlRender() এ item._cat হিসেবে ব্যবহৃত ক্যাটাগরিসহ একটা এন্ট্রি */
data class DebtorRow(val entry: Entry, val category: EntryCategory)

/** dlTabChange() এর ট্যাব — "সবাই" বা নির্দিষ্ট একটা ক্যাটাগরি */
sealed class DlTab(val labelBn: String) {
    object All : DlTab("সবাই")
    data class ByCategory(val category: EntryCategory) : DlTab(category.titleBn)
}

/**
 * dlScreen (Debtors List / Party Ledger) এর ViewModel।
 * চারটা ক্যাটাগরি (রেগুলার/ইর-রেগুলার কাস্টমার ও মহাজন) একসাথে মিলিয়ে
 * ফিল্টার-যোগ্য, সার্চ-যোগ্য সম্মিলিত তালিকা তৈরি করে — dlRender() এর সমতুল্য।
 * সবকিছু একটামাত্র imran_store/backup ডকুমেন্ট থেকে আসে।
 */
class DebtorsListViewModel(
    private val repository: BackupRepository = BackupRepository()
) : ViewModel() {

    private val trackedCategories = listOf(
        EntryCategory.REGULAR_CUSTOMER,
        EntryCategory.IRREGULAR_CUSTOMER,
        EntryCategory.REGULAR_SUPPLIER,
        EntryCategory.IRREGULAR_SUPPLIER
    )

    private val _allByCategory = MutableStateFlow<Map<EntryCategory, List<Entry>>>(emptyMap())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeTab = MutableStateFlow<DlTab>(DlTab.All)
    val activeTab: StateFlow<DlTab> = _activeTab.asStateFlow()

    val tabs: List<DlTab> = listOf(DlTab.All) + trackedCategories.map { DlTab.ByCategory(it) }

    init {
        viewModelScope.launch {
            repository.observeBackup().collect { payload ->
                _allByCategory.value = trackedCategories.associateWith { payload.entriesFor(it) }
            }
        }
    }

    val filteredRows: StateFlow<List<DebtorRow>> = combine(
        _allByCategory, _searchQuery, _activeTab
    ) { byCategory, query, tab ->
        val allRows = byCategory.flatMap { (category, entries) ->
            entries.map { DebtorRow(it, category) }
        }

        val categoryFiltered = when (tab) {
            is DlTab.All -> allRows
            is DlTab.ByCategory -> allRows.filter { it.category == tab.category }
        }

        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.entry.name.lowercase().contains(q) ||
                    it.entry.addr.lowercase().contains(q) ||
                    it.entry.mob.lowercase().contains(q)
            }
        }
    }
        // ভারী flatMap/filter Main থ্রেডের বাইরে; Eagerly — স্ক্রিন খোলার আগেই তালিকা তৈরি থাকে
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** মহাজন (৩+৪) ক্যাটাগরির মোট পাওনা */
    val totalPawona: StateFlow<Double> = filteredRows
        .map { rows ->
            rows.filter {
                it.category == EntryCategory.REGULAR_SUPPLIER || it.category == EntryCategory.IRREGULAR_SUPPLIER
            }.sumOf { it.entry.baki }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** কাস্টমার (১+২) ক্যাটাগরির মোট বকেয়া */
    val totalBokea: StateFlow<Double> = filteredRows
        .map { rows ->
            rows.filter {
                it.category == EntryCategory.REGULAR_CUSTOMER || it.category == EntryCategory.IRREGULAR_CUSTOMER
            }.sumOf { it.entry.baki }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    fun onTabChange(tab: DlTab) {
        _activeTab.value = tab
    }

    /** স্ক্রিন থেকে বেরোলে সার্চ/ট্যাব আগের মতো ডিফল্টে ফেরে (ViewModel এখন অ্যাপ-স্কোপে থাকে) */
    fun resetFilters() {
        _searchQuery.value = ""
        _activeTab.value = DlTab.All
    }

    /** dlJomaSave() এর সমতুল্য — নির্দিষ্ট এন্ট্রিতে দ্রুত জমা যোগ করে */
    fun addQuickJoma(row: DebtorRow, jomaAmount: Double, date: String, note: String) {
        if (jomaAmount <= 0) return
        val entry = row.entry
        // দ্রুত-জমা আলাদা ফর্মের — নিজস্ব pair, কোনো বিলের লাইনে মার্জ হবে না
        val soloPair = maxOf(System.currentTimeMillis(), (entry.history.maxOfOrNull { it.pair } ?: 0L) + 1)
        val newHistory = entry.history + HistoryItem(
            type = "joma", date = date, memo = "", joma = jomaAmount, note = note, pair = soloPair
        )
        val isCustomer = row.category.isCustomerType
        val netBaki = com.imran.clothstore.ui.screens.detail.currentBaki(entry.copy(history = newHistory), isCustomer)
        val updated = entry.copy(history = newHistory, baki = netBaki)

        viewModelScope.launch {
            repository.addOrUpdateEntry(row.category, updated.toBackupEntry())
        }
    }
}
