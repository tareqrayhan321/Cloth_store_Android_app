package com.imran.clothstore.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import com.imran.clothstore.util.formatTaka
import com.imran.clothstore.util.toBengaliDigits

/**
 * ওয়েব অ্যাপের .fv-excel টেবিলের সমতুল্য — রেফারেন্স স্ক্রিনশট অনুযায়ী: গাঢ় সবুজ হেডার সারি (সাদা
 * বোল্ড টেক্সট), পাওনা-সারি গোলাপি-হালকা, জমা-সারি ধূসর-সবুজ, টাকা "৳১,১৪,৮৫৩" ফরম্যাটে।
 * কলাম: তারিখ | ম্যামো | পাওনা/প্রাপ্য | জমা | মোট বাকি | গজ | মন্তব্য — অনুভূমিক স্ক্রলযোগ্য।
 */
@Composable
fun TransactionTable(
    rows: List<TxnRow>,
    onRowLongPress: (TxnRow) -> Unit = {},
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // প্রথম ৬ কলাম (তারিখ..গজ) মিলে স্ক্রিনের প্রস্থ ভরে; মন্তব্য কলাম ডানে স্ক্রল করে দেখতে হবে
    val baseWidths = listOf(62f, 42f, 74f, 64f, 72f, 46f)
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val scale = (screenW / baseWidths.sum()).coerceIn(0.9f, 1.3f)
    val colWidths = baseWidths.map { (it * scale).dp } + 200.dp
    val headers = listOf("তারিখ", "ম্যামো", "পাওনা/প্রাপ্য", "জমা", "মোট বাকি", "গজ", "মন্তব্য")
    // পার্টি লেজারের এক্সেল টেবিলের সাথে হুবহু এক ভিজুয়াল: সাদা / হালকা-বেজ বিকল্প রো, একই গ্রিড ও হেডার রঙ
    val headerBg = Color(0xFF1E3A32)
    val rowEven = Color.White
    val rowOdd = Color(0xFFF7F5EF)
    val grid = Color(0xFFCFCBC0)
    val redNum = Color(0xFFD9452B)
    val greenNum = Color(0xFF2BB673)

    // হেডার সারি স্থির; বডি উল্লম্বে স্ক্রল হয়। দুটোর অনুভূমিক স্ক্রল একই state-এ বাঁধা — একসাথে সরে।
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    Column(modifier = modifier) {
        Row(modifier = Modifier.horizontalScroll(hScroll).background(headerBg).height(HEADER_H)) {
            headers.forEachIndexed { i, h ->
                Box(
                    modifier = Modifier
                        .width(colWidths[i])
                        .fillMaxHeight()
                        .border(0.5.dp, Color(0xFF3B5A50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = h,
                        modifier = Modifier.padding(horizontal = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(vScroll)) {
        Column(modifier = Modifier.horizontalScroll(hScroll)) {
        if (rows.isEmpty()) {
            Text(
                text = "কোনো লেনদেন নেই",
                modifier = Modifier.padding(16.dp),
                fontSize = 11.sp,
                color = Color(0xFF9A96AD)
            )
        }

        rows.forEachIndexed { index, row ->
            val bg = if (index % 2 == 0) rowEven else rowOdd
            Row(
                modifier = Modifier
                    .background(bg)
                    .height(ROW_H)
                    // লং-প্রেসে এডিট/ডিলিট অপশন (সাধারণ ট্যাপে কিছু হয় না)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onRowLongPress(row)
                        }
                    )
            ) {
                Cell(formatDateShort(row.date), colWidths[0], grid, TextAlign.Center)
                Cell(row.memo.ifBlank { "—" }.toBengaliDigits(), colWidths[1], grid, TextAlign.Center, bold = row.memo.isNotBlank())
                Cell(
                    if (row.type == TxnType.BILL_OR_PAWNA) formatTaka(row.amount) else "—",
                    colWidths[2], grid, TextAlign.Center,
                    color = if (row.type == TxnType.BILL_OR_PAWNA) redNum else Color(0xFF141413)
                )
                Cell(
                    when {
                        row.mergedJomaAmount != null -> formatTaka(row.mergedJomaAmount)
                        row.type == TxnType.JOMA -> formatTaka(row.amount)
                        else -> "—"
                    },
                    colWidths[3], grid, TextAlign.Center, bold = true,
                    color = if (row.mergedJomaAmount != null || row.type == TxnType.JOMA) greenNum else Color(0xFF141413)
                )
                Cell(
                    formatTaka(row.runningBaki), colWidths[4], grid, TextAlign.Center, bold = true,
                    color = if (row.runningBaki == 0.0) greenNum else redNum
                )
                Cell(row.goj.ifBlank { "—" }.toBengaliDigits(), colWidths[5], grid, TextAlign.Center)
                Cell(row.note.ifBlank { "" }, colWidths[6], grid, TextAlign.Start)
            }
        }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(bottomPadding))
        }
    }
}

private val HEADER_H = 34.dp
private val ROW_H = 32.dp

@Composable
private fun Cell(text: String, width: androidx.compose.ui.unit.Dp, grid: Color, align: TextAlign, bold: Boolean = false, color: Color = Color(0xFF141413)) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.5.dp, grid),
        contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 2.dp),
            fontSize = 10.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            textAlign = align,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "2026-09-23" জাতীয় ISO তারিখকে "২৩/০৯/২৬" (দিন/মাস/সালের শেষ দুই অঙ্ক, বাংলা অঙ্কে) ফরম্যাটে দেখায় —
 *  রেফারেন্স স্ক্রিনশটের টেবিল অনুযায়ী। পার্স ব্যর্থ হলে মূল স্ট্রিং বাংলা অঙ্কে ফেরত দেয়। */
private fun formatDateShort(date: String): String {
    if (date.isBlank()) return "—"
    return try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date.take(10))
            ?: return date.toBengaliDigits()
        java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.US).format(parsed).toBengaliDigits()
    } catch (e: Exception) {
        date.toBengaliDigits()
    }
}
