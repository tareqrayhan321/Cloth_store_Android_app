package com.imran.clothstore.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.imran.clothstore.R

/**
 * অ্যাপের অফিসিয়াল বাংলা ফন্ট: বর্ণমালা (Bornomala) — Regular ও Bold দুটো ফাইলই আছে।
 * আগের ডিফল্ট (system) ফন্ট সরিয়ে পুরো অ্যাপে এখন বর্ণমালা চলবে (তারেকের নির্দেশ)।
 * আলাদা Medium/SemiBold ফাইল নেই — Medium-এ Regular, SemiBold-এ Bold ম্যাপ করা,
 * যাতে Android কৃত্রিম বোল্ড বসিয়ে অক্ষর বিকৃত না করে।
 */
val AppFontFamily = FontFamily(
    Font(R.font.bornomala_regular, FontWeight.Normal),
    Font(R.font.bornomala_regular, FontWeight.Medium),
    Font(R.font.bornomala_bold, FontWeight.SemiBold),
    Font(R.font.bornomala_bold, FontWeight.Bold)
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)
