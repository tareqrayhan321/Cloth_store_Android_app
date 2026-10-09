package com.imran.clothstore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imran.clothstore.ui.theme.AppColors

/**
 * ওয়েব অ্যাপের .kpi-strip.kpi-strip-3 → তিনটা .profit-kpi-card এর সমতুল্য।
 * Weekly Gross / Net / Cash Profit — মান ঋণাত্মক হলে Net/Cash কার্ডে "Loss" লেখা ও লাল সংখ্যা দেখায় (কার্ডের রং অপরিবর্তিত)
 * (রেফারেন্স স্ক্রিনশট: "Weekly Net Loss" গোলাপি কার্ড, লাল মান)।
 */
@Composable
fun ProfitKpiStrip(
    grossProfit: String,
    netProfit: String,
    cashProfit: String,
    onNetProfitClick: () -> Unit,
    modifier: Modifier = Modifier,
    netIsLoss: Boolean = false,
    cashIsLoss: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProfitKpiCard(
            label = "Weekly Gross Profit",
            value = grossProfit,
            modifier = Modifier.weight(1f)
        )
        ProfitKpiCard(
            label = if (netIsLoss) "Weekly Net Loss" else "Weekly Net Profit",
            value = netProfit,
            isLoss = netIsLoss,
            modifier = Modifier.weight(1f),
            onClick = onNetProfitClick
        )
        ProfitKpiCard(
            label = if (cashIsLoss) "Weekly Cash Loss" else "Weekly Cash Profit",
            value = cashProfit,
            isLoss = cashIsLoss,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProfitKpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isLoss: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    // লস হলেও কার্ডের রং বদলায় না — শুধু সংখ্যা লাল হয়
    val bg = AppColors.CardCream
    val border = AppColors.CardBorder
    Column(
        modifier = modifier
            .height(140.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF6C6A64),
            lineHeight = 14.sp
        )
        // সংখ্যা এক লাইনে থাকবে; না আঁটলে ফন্ট ধাপে ধাপে ছোট হবে (কাটা পড়বে না)
        var valueSize by remember(value) { mutableStateOf(13.sp) }
        var ready by remember(value) { mutableStateOf(false) }
        Text(
            text = value,
            fontSize = valueSize,
            maxLines = 1,
            softWrap = false,
            fontWeight = FontWeight.Bold,
            color = if (isLoss) Color(0xFFC0392B) else Color(0xFF141413),
            onTextLayout = { r ->
                if (r.didOverflowWidth && valueSize > 9.sp) {
                    valueSize = valueSize * 0.92f
                } else {
                    ready = true
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .drawWithContent { if (ready) drawContent() }
        )
    }
}
