package com.imran.clothstore.ui.screens.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imran.clothstore.data.model.WeeklyReport
import com.imran.clothstore.util.toBengaliDigits

private data class PieSegment(val label: String, val value: Double, val color: Color)

/**
 * সতর্কতা ব্যানার ✕ দিয়ে বন্ধ করলে সেটা স্থায়ীভাবে মনে রাখা হয় (SharedPreferences)।
 * চাবি = "<সপ্তাহ>|<সতর্কতার ধরন>" — তাই নতুন সপ্তাহের রিপোর্ট এলে চাবি বদলে যায়
 * এবং সতর্কতা আবার নতুন করে দেখায়; একই সপ্তাহে বন্ধ করা সতর্কতা অ্যাপ রিস্টার্টেও ফিরে আসে না।
 */
private object AlertDismissStore {
    private const val PREFS = "fund_flow_alerts"
    private const val KEY = "dismissed"

    fun load(context: android.content.Context): Set<String> =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun add(context: android.content.Context, entry: String): Set<String> {
        val updated = load(context) + entry
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, updated).apply()
        return updated
    }
}

/**
 * ওয়েব অ্যাপের dashFundFlowCard (ffhRenderCashflowPie) এর সরলীকৃত সমতুল্য।
 * সবচেয়ে সাম্প্রতিক সপ্তাহের রিপোর্ট থেকে একটা ডোনাট চার্ট (নগদ আয়/ক্রয়/পুরনো দেনা/
 * পরিচালন ব্যয়/স্থায়ী খরচ) + লেজেন্ড + তিনটা সতর্কতা ব্যানার (Critical Loss, Liquidity Risk, Cash Gap)।
 *
 * মূল ওয়েব অ্যাপে নেস্টেড সাব-ব্যান্ড (Cash Profit/Loss আর্ক) ছিল অত্যন্ত সূক্ষ্ম ভিজ্যুয়াল ডিটেইল —
 * এখানে সরলীকৃত করে মূল সেগমেন্টগুলোই দেখানো হয়েছে, যাতে ডেটা-নির্ভুলতা বজায় থাকে।
 */
@Composable
fun FundFlowCard(latest: WeeklyReport?) {
    val context = LocalContext.current
    var dismissedKeys by remember { mutableStateOf(AlertDismissStore.load(context)) }
    // একই সপ্তাহের রিপোর্ট আবার সেভ হলেও (id/ts বদলালেও) বছর+সপ্তাহ একই থাকে; পুরনো এন্ট্রিতে না থাকলে ts
    val weekKey = latest?.let { if (it.year > 0 && it.week > 0) "${it.year}-W${it.week}" else "ts${it.ts}" }
    fun isDismissed(type: String) = weekKey != null && "$weekKey|$type" in dismissedKeys
    fun dismiss(type: String) {
        if (weekKey != null) dismissedKeys = AlertDismissStore.add(context, "$weekKey|$type")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2D9C4)), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        if (latest == null || (latest.cashIn == 0.0 && latest.cashOut == 0.0 && latest.cogs == 0.0)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "এখনো কোনো হিসাব নেই",
                    fontSize = 10.5.sp,
                    color = Color(0xFF9A96AD),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            return@Column
        }

        val cashPurchase = if (latest.cashPurchase > 0) latest.cashPurchase else latest.cashOut
        val segments = listOf(
            PieSegment("নগদ আয়", latest.cashIn, Color(0xFF2E8B6F)),
            PieSegment("নগদ ক্রয়", cashPurchase, Color(0xFFC0503A)),
            PieSegment("পুরনো দেনা", latest.oldDebt, Color(0xFFB03C6E)),
            PieSegment("Maintenance Costs", latest.opex, Color(0xFFC79A3E)),
            PieSegment("Fixed Costs", latest.fixed, Color(0xFF6E5EA8))
        ).filter { it.value > 0 }

        if (segments.isEmpty()) {
            Text("এখনো কোনো হিসাব নেই", fontSize = 10.5.sp, color = Color(0xFF9A96AD))
            return@Column
        }

        val totalValue = segments.sumOf { it.value }.let { if (it == 0.0) 1.0 else it }
        val cashLoss = (latest.cashIn - latest.cashOut).let { if (it < 0) it else 0.0 }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                PieChart(segments = segments, modifier = Modifier.size(130.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                segments.forEach { seg ->
                    LegendRow(
                        color = seg.color,
                        label = seg.label,
                        percent = "%.1f%%".format(java.util.Locale.US, seg.value / totalValue * 100).toBengaliDigits(),
                        amount = formatCompactTaka(seg.value)
                    )
                }
                if (cashLoss < 0) {
                    LegendRow(
                        color = Color(0xFFD63B3B),
                        label = "Cash Loss",
                        percent = "",
                        amount = formatCompactTaka(cashLoss),
                        amountColor = Color(0xFFC0392B)
                    )
                }
            }
        }

        // ── সতর্কতা ব্যানার ──
        val netCashFlow = latest.cashIn - latest.cashOut
        val creditSalesRatio = if (latest.sales > 0) (latest.sales - latest.cashIn).coerceAtLeast(0.0) / latest.sales else 0.0

        if (latest.net < 0 && !isDismissed("loss")) {
            AlertBanner(
                dot = Color(0xFFD9452B),
                badge = "CRITICAL LOSS",
                title = "নিট লোকসান সতর্কতা — Net Loss Alert",
                desc = "এই সপ্তাহে মোট ব্যয় আয়কে ছাড়িয়ে গেছে। COGS ও পরিচালন ব্যয় পুনর্যাচাই করুন।",
                action = "১. COGS পুনর্যাচাই ২. পরিচালন ব্যয় কমানোর সুযোগ দেখুন ৩. বিক্রয়মূল্য পর্যালোচনা করুন",
                bg = Color(0xFFFBEAE7),
                onDismiss = { dismiss("loss") }
            )
        }
        if (creditSalesRatio > 0.6 && !isDismissed("liquidity")) {
            AlertBanner(
                dot = Color(0xFFE0B84A),
                badge = "WARNING — Liquidity Risk",
                title = "তারল্য ঝুঁকি — Credit Sales > 60%",
                desc = "মোট বিক্রয়ের ৬০%-এর বেশি বাকিতে। নগদ প্রবাহে সমস্যার ঝুঁকি আছে।",
                action = "১. পুরনো বাকি আদায় ত্বরান্বিত করুন ২. Cash Discount বিবেচনা করুন",
                bg = Color.White,
                onDismiss = { dismiss("liquidity") }
            )
        }
        if (latest.net > 0 && netCashFlow < 0 && !isDismissed("gap")) {
            AlertBanner(
                dot = Color(0xFF2F6DB5),
                badge = "INFO — Accrual Gap",
                title = "নগদ ঘাটতি — P&L লাভ সত্ত্বেও Cashflow ঋণাত্মক",
                desc = "বাকি ক্রয় বা বকেয়া পরিশোধ ক্যাশফ্লো কমাচ্ছে।",
                action = "১. বাকি আদায় আগে করুন ২. ক্রয় পেমেন্ট সিডিউল পুনর্বিন্যাস করুন",
                bg = Color(0xFFE9F1FB),
                onDismiss = { dismiss("gap") }
            )
        }
    }
}

/** ভরাট পাই-চার্ট (মাঝখানে ফাঁকা নেই) — সেগমেন্টের মাঝে পাতলা সাদা বিভাজক রেখা */
@Composable
private fun PieChart(segments: List<PieSegment>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.value }.let { if (it == 0.0) 1.0 else it }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f - 1.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)

        var startAngle = -90f
        segments.forEach { seg ->
            val sweep = (seg.value / total * 360f).toFloat()
            drawArc(
                color = seg.color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )
            startAngle += sweep
        }

        // সেগমেন্টগুলোর মাঝে সাদা বিভাজক (একটাই সেগমেন্ট হলে দরকার নেই)
        if (segments.size > 1) {
            startAngle = -90f
            segments.forEach { seg ->
                val sweep = (seg.value / total * 360f).toFloat()
                drawArc(
                    color = Color.White,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                startAngle += sweep
            }
        }
    }
}

/** dismissAlert() এর সমতুল্য — ✕ ট্যাপ করলে onDismiss কল হয় (স্থায়ীভাবে মনে রাখা হয়, দেখুন AlertDismissStore) */
@Composable
private fun AlertBanner(
    dot: Color,
    badge: String,
    title: String,
    desc: String,
    action: String,
    bg: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .padding(9.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ইমোজির বদলে সতর্কতার মাত্রা বোঝাতে রঙিন বিন্দু
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = 3.dp, end = 8.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(badge, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6C5A2A))
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            Text(desc, fontSize = 8.5.sp, color = Color(0xFF5A5648), modifier = Modifier.padding(top = 2.dp))
            Text(action, fontSize = 8.sp, color = Color(0xFF6C6A64), modifier = Modifier.padding(top = 3.dp))
        }
        Text(
            "✕",
            fontSize = 11.sp,
            color = Color(0xFF9A96AD),
            modifier = Modifier
                .padding(start = 6.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(4.dp)
        )
    }
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    percent: String,
    amount: String,
    amountColor: Color = Color(0xFF8A877E)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            fontSize = 10.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 7.dp),
            maxLines = 1
        )
        if (percent.isNotEmpty()) {
            Text(percent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        }
        Text(
            text = amount,
            fontSize = 9.5.sp,
            color = amountColor,
            fontWeight = if (amountColor == Color(0xFF8A877E)) FontWeight.Normal else FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
