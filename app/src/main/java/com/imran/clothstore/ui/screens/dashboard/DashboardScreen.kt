package com.imran.clothstore.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imran.clothstore.ui.components.AppTopBar
import com.imran.clothstore.ui.components.HeaderCardOverlap
import com.imran.clothstore.ui.components.ProfitKpiStrip
import com.imran.clothstore.ui.screens.notif.NotificationViewModel
import com.imran.clothstore.ui.screens.reports.FundFlowCard
import com.imran.clothstore.ui.screens.reports.WeeklyReportsViewModel
import com.imran.clothstore.util.toBengaliDigits
import com.imran.clothstore.ui.screens.reports.formatTaka
import com.imran.clothstore.ui.theme.AppColors

/**
 * ওয়েব অ্যাপের "ড্যাশবোর্ড" ট্যাবের (dashScreen) সরাসরি Kotlin/Compose সংস্করণ।
 * রেফারেন্স স্ক্রিনশট (Weekly KPI → Aggregate Report সামারি কার্ড → Fund Flow পাই-চার্ট →
 * প্রোমোশনাল ব্যানার) অনুযায়ী হুবহু কাঠামো — Home ট্যাবের ৬টা ক্যাটাগরি কার্ড, ক্যালেন্ডার,
 * বা ৩-কার্ড সারি এখানে নেই, সেগুলো শুধু Home ট্যাবেই থাকে (HomeScreen.kt দেখুন)।
 *
 * একই WeeklyReportsViewModel পুনর্ব্যবহার করা হয়েছে (Home-এর সাথে শেয়ার্ড instance,
 * AppNavGraph থেকে পাস করা), যাতে দুইবার Firestore listener খুলতে না হয়।
 */
@Composable
fun DashboardScreen(
    onNotifClick: () -> Unit,
    onMenuClick: () -> Unit,
    onNetProfitClick: () -> Unit,
    onAggregateReportClick: () -> Unit,
    onPaikkariClick: () -> Unit,
    reportsViewModel: WeeklyReportsViewModel = viewModel(),
    notifViewModel: NotificationViewModel = viewModel()
) {
    val hasUnread by notifViewModel.hasUnread.collectAsState()
    val reports by reportsViewModel.reportsNewestFirst.collectAsState()
    val latestReport = reports.firstOrNull()

    // ── Aggregate Report সামারি (In Stock / Out Stock) — বর্তমান (সর্বশেষ) সপ্তাহের গজ ──
    // KPI স্ট্রিপ ও পাই-চার্টের মতোই latestReport থেকে; সব সপ্তাহের যোগফল আলাদা ওভারলেতে (সর্বমোট) দেখা যায়
    val curInStock = latestReport?.stockInYard ?: 0.0
    val curOutStock = latestReport?.stockOutYard ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BodyBg)
    ) {
        // ── হেডার + KPI স্ট্রিপ ওভারল্যাপ (কার্ডের অর্ধেক হেডারের ভেতরে) ──
        val overlap = HeaderCardOverlap
        Box {
            AppTopBar(
                onNotifClick = onNotifClick,
                onMenuClick = onMenuClick,
                notifBadgeCount = if (hasUnread) 1 else 0
            )

            val netVal = latestReport?.net
            val cashVal = latestReport?.let { if (it.cashProfit != 0.0) it.cashProfit else it.net }
            ProfitKpiStrip(
                grossProfit = latestReport?.let { formatTaka(it.gross) } ?: "—",
                netProfit = netVal?.let { formatTaka(it) } ?: "—",
                cashProfit = cashVal?.let { formatTaka(it) } ?: "—",
                netIsLoss = (netVal ?: 0.0) < 0,
                cashIsLoss = (cashVal ?: 0.0) < 0,
                onNetProfitClick = onNetProfitClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = overlap)
            )
        }
        Spacer(modifier = Modifier.height(overlap + 8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp) // নিচে ভাসমান বারের জায়গা
        ) {
            // ── Aggregate Report সামারি কার্ড (ট্যাপ করলে বিস্তারিত টেবিল খোলে) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(BorderStroke(1.dp, Color(0xFFE2D9C4)), RoundedCornerShape(16.dp))
                    .clickable(onClick = onAggregateReportClick)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aggregate Report", fontSize = 16.sp, fontWeight = FontWeight.Normal, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                    Text(
                        text = if (latestReport == null) "কোনো ডাটা নেই"
                        else latestReport.label.ifBlank { "চলতি সপ্তাহ" }.toBengaliDigits(),
                        fontSize = 11.sp,
                        color = Color(0xFF6C6A64)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AggStatBox(
                        label = "In Stock",
                        value = "${yardNum(curInStock)} Yard",
                        modifier = Modifier.weight(1f)
                    )
                    AggStatBox(
                        label = "Out Stock",
                        value = "${yardNum(curOutStock)} Yard",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Fund Flow / পাই-চার্ট কার্ড ──
            FundFlowCard(latest = latestReport)

            Spacer(modifier = Modifier.height(10.dp))

            // ── প্রোমোশনাল ব্যানার (সাপ্তাহিক তথ্য হালনাগাদ) ──
            // রেফারেন্সে কাপড়ের রোলের ছবি ব্যাকগ্রাউন্ড; ছবির অ্যাসেট প্রজেক্টে না থাকায় গাঢ় গ্রেডিয়েন্ট
            // ব্যবহার করা হয়েছে (drawable যোগ করলে Image() দিয়ে বদলানো যাবে)।
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF6B6A66), Color(0xFF3F4A4C))
                        )
                    )
                    .border(BorderStroke(1.dp, Color(0xFFE2D9C4)), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "চলতি সপ্তাহের আর্থিক লেনদেনের সমস্ত তথ্য ইনপুট দিয়ে আপনার ব্যবসায়ের হিসাবগুলো হালনাগাদ রাখুন।",
                    color = Color(0xFFFFC107),
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(
                    onClick = onPaikkariClick,
                    modifier = Modifier.padding(top = 18.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B4A3A))
                ) {
                    Text(
                        "সাপ্তাহিক তথ্য হালনাগাদ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AggStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEFE9DD))
            .padding(vertical = 12.dp, horizontal = 14.dp)
    ) {
        Text(label, fontSize = 11.5.sp, color = Color(0xFF6C6A64))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

/** "১০০" / "২,৯৮৮.২৫" — বাংলা অঙ্ক, লক্ষ-পদ্ধতির কমা, শেষে অপ্রয়োজনীয় শূন্য নেই (ইউনিট আলাদাভাবে "Yard" বসে) */
private fun yardNum(v: Double): String {
    val r = kotlin.math.round(v * 100) / 100
    val whole = r.toLong()
    val frac = r - whole
    val wholeStr = com.imran.clothstore.util.groupLakh(whole.toString())
    val fracStr = if (frac == 0.0) "" else "." + "%.2f".format(java.util.Locale.US, frac).substringAfter(".").trimEnd('0')
    return (wholeStr + fracStr).toBengaliDigits()
}
