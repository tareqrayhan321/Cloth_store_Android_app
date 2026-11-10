package com.imran.clothstore.data.backup

import com.imran.clothstore.data.model.Entry
import com.imran.clothstore.data.model.EntryCategory
import com.imran.clothstore.data.model.FabricGroup
import com.imran.clothstore.data.model.FabricPurchase
import com.imran.clothstore.data.model.HistoryItem
import com.imran.clothstore.data.model.WeeklyReport

/**
 * BackupPayload-এর raw (ওয়েব অ্যাপের সাথে হুবহু) টাইপ ও UI লেয়ারের পরিষ্কার domain
 * model — Entry/FabricGroup/WeeklyReport — এর মধ্যে দ্বিমুখী রূপান্তর। UI/ViewModel লেয়ার
 * কখনো Backup* টাইপ সরাসরি দেখে না, শুধু এই ম্যাপারের মধ্য দিয়ে যায়।
 */

fun BackupEntry.toEntry(): Entry = Entry(
    id = id, name = name, addr = addr, mob = mob, photoUrl = photoUrl, memo = memo,
    bill = bill, joma = joma, baki = baki, initPawna = initPawna, goj = goj, note = note, date = date, order = order,
    history = history.map { it.toHistoryItem() }
)

fun Entry.toBackupEntry(): BackupEntry = BackupEntry(
    id = id, name = name, addr = addr, mob = mob, photoUrl = photoUrl, memo = memo,
    bill = bill, joma = joma, baki = baki, initPawna = initPawna, goj = goj, note = note, date = date, order = order,
    history = history.map { it.toBackupHistoryItem() }
)

fun BackupHistoryItem.toHistoryItem(): HistoryItem = HistoryItem(
    type = type, date = date, memo = memo, bill = bill, baki = baki, joma = joma, goj = goj, note = note, pair = pair
)

fun HistoryItem.toBackupHistoryItem(): BackupHistoryItem = BackupHistoryItem(
    type = type, date = date, memo = memo, bill = bill, baki = baki, joma = joma, goj = goj, note = note, pair = pair
)

fun BackupWeeklyReport.toWeeklyReport(): WeeklyReport = WeeklyReport(
    id = id, label = label, ts = ts, year = year, week = week, month = month,
    sales = sales, cogs = cogs, gross = gross, opex = opex, fixed = fixed, zakat = zakat, net = net,
    cashIn = cashIn, cashOut = cashOut, cashPurchase = cashPurchase, oldDebt = oldDebt,
    cashProfit = cashProfit, stockInYard = stockInYard, stockOutYard = stockOutYard,
    purchaseTotal = purchaseTotal, cashSale = cashSale, creditSale = creditSale,
    oldCollection = oldCollection
)

fun WeeklyReport.toBackupWeeklyReport(): BackupWeeklyReport = BackupWeeklyReport(
    id = id, label = label, ts = ts, year = year, week = week, month = month,
    sales = sales, cogs = cogs, gross = gross, opex = opex, fixed = fixed, zakat = zakat, net = net,
    cashIn = cashIn, cashOut = cashOut, cashPurchase = cashPurchase, oldDebt = oldDebt,
    cashProfit = cashProfit, stockInYard = stockInYard, stockOutYard = stockOutYard,
    purchaseTotal = purchaseTotal, cashSale = cashSale, creditSale = creditSale,
    oldCollection = oldCollection
)

fun BackupFabricGroup.toFabricGroup(): FabricGroup = FabricGroup(
    id = id, name = name, unit = unit, purchases = purchases.map { it.toFabricPurchase() }
)

fun BackupFabricPurchase.toFabricPurchase(): FabricPurchase = FabricPurchase(
    id = id, date = date, qty = qty, rate = rate, total = total
)

/** payload থেকে নির্দিষ্ট ক্যাটাগরির এন্ট্রি লিস্ট Entry ডোমেইন টাইপে বের করে আনে */
fun BackupPayload.entriesFor(category: EntryCategory): List<Entry> = when (category.backupKey) {
    "is_c1_list" -> is_c1_list
    "is_c2_list" -> is_c2_list
    "is_c3_list" -> is_c3_list
    "is_c4_list" -> is_c4_list
    else -> emptyList()
}.map { be ->
    val e = be.toEntry()
    e.copy(baki = com.imran.clothstore.ui.screens.detail.currentBaki(e, category.isCustomerType))
}

fun BackupPayload.fabricGroups(): List<FabricGroup> = fabricPurchaseData_v2.map { it.toFabricGroup() }

fun BackupPayload.weeklyReports(): List<WeeklyReport> = chart_data.map { it.toWeeklyReport() }
