package com.imran.clothstore.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imran.clothstore.R
import com.imran.clothstore.ui.theme.AppColors
import com.imran.clothstore.ui.theme.AppFontFamily

/**
 * অ্যাপ টাইটেলের ফন্টও এখন বর্ণমালা (Bornomala) — পুরো অ্যাপের সঙ্গে একই ফন্ট
 * (ui.theme-র AppFontFamily; আগের Alinur Sangbadpatra সরানো হয়েছে)।
 */

/**
 * KPI কার্ড হেডারের নিচের কিনারার উপর যতটা নামে/ওভারল্যাপ করে (dp)। হেডারের নিচের প্যাডিং
 * এর চেয়ে বেশি রাখা হয়েছে যাতে টাইটেল/সাবটাইটেল কখনো কার্ডের নিচে ঢাকা না পড়ে।
 * Home ও Dashboard দুই স্ক্রিনই এই একই মান ব্যবহার করে।
 */
val HeaderCardOverlap = 64.dp
val HeaderBottomPadding = HeaderCardOverlap + 22.dp


/**
 * ওয়েব অ্যাপের <header class="hdr"> → <div class="hdr-topbar"> এর সমতুল্য।
 * অ্যাপ টাইটেল "ইমরান ক্লথ স্টোর" + সাবটাইটেল, ডানে নোটিফিকেশন ও মেনু বাটন।
 */
@Composable
fun AppTopBar(
    onNotifClick: () -> Unit,
    onMenuClick: () -> Unit,
    notifBadgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF0B4A4E), AppColors.HeaderTeal)
                    )
                )
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = HeaderBottomPadding),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.emean_emblem_cream),
                    contentDescription = null,
                    modifier = Modifier.height(38.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ইমরান ক্লথ স্টোর",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "পাওনা লিস্ট, ক্যাশফ্লো, প্রফিট-লস",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = onNotifClick) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "নোটিফিকেশন",
                            tint = Color.White
                        )
                    }
                    if (notifBadgeCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp, end = 10.dp)
                                .size(10.dp)
                                .background(Color(0xFFE53935), CircleShape)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "মেনু",
                        tint = Color.White
                    )
                }
            }
        }
        // নিচের সাদা/বডি শিট — হেডারের উপর উঠে এসেছে, উপরের কোণ গোলাকার
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    AppColors.BodyBg,
                    androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
        )
    }
}
