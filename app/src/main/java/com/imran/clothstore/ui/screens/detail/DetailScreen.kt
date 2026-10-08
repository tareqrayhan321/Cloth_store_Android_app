package com.imran.clothstore.ui.screens.detail

import androidx.compose.foundation.background
import com.imran.clothstore.util.formatTaka
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
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
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imran.clothstore.data.memo.partyKeyOf
import com.imran.clothstore.ui.screens.memo.MemoScreen
import com.imran.clothstore.ui.screens.memo.MemoViewModel
import com.imran.clothstore.ui.screens.memo.MemoViewModelFactory
import com.imran.clothstore.data.model.EntryCategory
import com.imran.clothstore.ui.theme.AppColors

/**
 * ওয়েব অ্যাপের fvDetailScreen এর সরাসরি Kotlin/Compose সংস্করণ।
 * হেডার (বড় অ্যাভাটার + নাম + ঠিকানা/মোবাইল) → Excel টেবিল → নতুন বকেয়া/জমা ফর্ম।
 * হেডারে ব্যাক আইকন নেই — ফোনের সিস্টেম ব্যাক জেসচার/বাটন দিয়ে ফেরা যায় (BackHandler)।
 */
@Composable
fun DetailScreen(
    category: EntryCategory,
    onBack: () -> Unit,
    viewModel: DetailViewModel
) {
    BackHandler(onBack = onBack)
    val entry by viewModel.entry.collectAsState()
    val rows by viewModel.transactionRows.collectAsState()

    var formKind by remember { mutableStateOf<String?>(null) } // "bokeyoa" | "joma" | null
    var showMemo by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // ── রো লং-প্রেস: অ্যাকশন (এডিট/ডিলিট) → ফর্ম → সিক্রেট কী ──
    var actionRow by remember { mutableStateOf<TxnRow?>(null) }
    var editingRow by remember { mutableStateOf<TxnRow?>(null) }
    var deletingRow by remember { mutableStateOf<TxnRow?>(null) }
    var pendingEdit by remember { mutableStateOf<(() -> Unit)?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── হেডার (ক্রিম, রেফারেন্স অনুযায়ী) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD9CCA6)),
                contentAlignment = Alignment.Center
            ) {
                if (entry.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = entry.photoUrl,
                        contentDescription = "${entry.name} এর ছবি",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                    )
                } else {
                    Text(
                        text = entry.name.trim().take(1).ifEmpty { "?" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = entry.name.ifBlank { "—" },
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                val metaParts = listOf(entry.addr, entry.mob).filter { it.isNotBlank() }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        color = Color(0xFF4A4740),
                        fontSize = 12.sp
                    )
                }
            }
            // ── মেমো বাটন (ডান পাশে) — ক্লিকে মেমোর ছবির গ্যালারি ──
            Row(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.HeaderTeal)
                    .clickable { showMemo = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("  মেমো", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE2D9C4))
        )

        // ── টেবিল (হেডার সারি স্থির, ২য় সারি থেকে স্ক্রল) + ফুটার + ভাসমান বাটন ──
        val density = androidx.compose.ui.platform.LocalDensity.current
        var footerHeight by remember { mutableStateOf(0.dp) }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TransactionTable(
                    rows = rows,
                    onRowLongPress = { actionRow = it },
                    modifier = Modifier.weight(1f),
                    bottomPadding = 64.dp // শেষ রো যেন ভাসমান বাটনের নিচে আটকে না থাকে
                )

                // ── স্টিকি ফুটার: বর্তমান পাওনা/প্রাপ্য ──
                Row(
                    modifier = Modifier
                        .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                        .fillMaxWidth()
                        .background(Color.White)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (category.isCustomerType) "বর্তমান পাওনা/প্রাপ্য" else "বর্তমান বকেয়া",
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = formatTaka(entry.baki),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            // ── ভাসমান জোড়া বাটন: জমা এন্ট্রি | মাঝে ডিভাইডার | পাওনা এন্ট্রি ──
            // হালকা (আধা-স্বচ্ছ) ব্যাকগ্রাউন্ড, তাই নিচের টেবিলের লেখা আবছা দেখা যায়।
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = footerHeight + 10.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.82f))
                    .border(1.dp, Color(0xFFD9D2C0), CircleShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clickable { formKind = "joma" }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("জমা এন্ট্রি", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F9D55), maxLines = 1, softWrap = false)
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFFD9D2C0))
                )
                Box(
                    modifier = Modifier
                        .clickable { formKind = "bokeyoa" }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (category.isCustomerType) "পাওনা এন্ট্রি" else "বকেয়া এন্ট্রি",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828), maxLines = 1, softWrap = false
                    )
                }
            }
        }
    }

    // ── লং-প্রেস অ্যাকশন: এডিট / ডিলিট ──
    actionRow?.let { row ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { actionRow = null },
            containerColor = Color.White,
            title = { Text(if (row.type == TxnType.JOMA) "জমার লেনদেন" else "পাওনা/বকেয়ার লেনদেন", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        listOf(
                            row.memo.takeIf { it.isNotBlank() }?.let { "ম্যামো $it" },
                            formatTaka(row.amount)
                        ).filterNotNull().joinToString(" · "),
                        fontSize = 13.sp,
                        color = Color(0xFF4A4740)
                    )
                    Button(
                        onClick = { editingRow = row; actionRow = null },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.HeaderTeal)
                    ) { Text("এডিট", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { deletingRow = row; actionRow = null },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) { Text("ডিলিট", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { actionRow = null }) { Text("বাতিল", color = Color(0xFF6C6A64)) }
            }
        )
    }

    // ── এডিট ফর্ম (সেভে সিক্রেট কী চাইবে) ──
    editingRow?.let { row ->
        EditTransactionSheet(
            row = row,
            isCustomerType = category.isCustomerType,
            onDismiss = { editingRow = null; pendingEdit = null },
            onSave = { memo, date, goj, amount, note, joma ->
                pendingEdit = {
                    viewModel.editRow(row, memo, date, goj, amount, note, joma)
                    editingRow = null
                }
            }
        )
    }
    pendingEdit?.let { action ->
        com.imran.clothstore.ui.components.SecretKeyDialog(
            message = "পরিবর্তন সেভ করতে সিক্রেট কী দিন।",
            confirmLabel = "সেভ করুন",
            onConfirm = { action(); pendingEdit = null },
            onDismiss = { pendingEdit = null }
        )
    }

    // ── ডিলিট (সিক্রেট কী) ──
    deletingRow?.let { row ->
        com.imran.clothstore.ui.components.SecretKeyDialog(
            message = if (row.jomaSrc != null)
                "এই লেনদেন (বিল ও সাথের জমা দুটোই) ডিলিট করতে সিক্রেট কী দিন।"
            else "এই লেনদেন ডিলিট করতে সিক্রেট কী দিন।",
            confirmLabel = "ডিলিট করুন",
            onConfirm = { viewModel.deleteRow(row); deletingRow = null },
            onDismiss = { deletingRow = null }
        )
    }

    formKind?.let { kind ->
        TransactionFormSheet(
            kind = kind,
            isCustomerType = category.isCustomerType,
            onDismiss = { formKind = null },
            onAddBokeyoa = { memo, date, goj, bill, note, joma ->
                viewModel.addBokeyoa(memo, date, goj, bill, note, joma)
            },
            onAddJoma = { memo, date, joma, note ->
                viewModel.addJoma(memo, date, joma, note)
            }
        )
    }
    // ── মেমো গ্যালারি (পূর্ণ-স্ক্রিন) ──
    if (showMemo) {
        val appCtx = LocalContext.current.applicationContext
        val memoVm = androidx.lifecycle.viewmodel.compose.viewModel<MemoViewModel>(
            key = "memo_${category.name}_${entry.id}",
            factory = MemoViewModelFactory(partyKeyOf(category.name, entry.id), entry.name, appCtx)
        )
        Dialog(
            onDismissRequest = { showMemo = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            MemoScreen(
                partyName = entry.name,
                viewModel = memoVm,
                onBack = { showMemo = false }
            )
        }
    }
}
