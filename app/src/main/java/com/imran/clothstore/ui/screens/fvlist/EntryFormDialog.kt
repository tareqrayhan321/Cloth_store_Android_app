package com.imran.clothstore.ui.screens.fvlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imran.clothstore.ui.components.AppDateField
import com.imran.clothstore.ui.components.AppTextField
import com.imran.clothstore.ui.theme.AppColors
import coil.compose.AsyncImage
import com.imran.clothstore.data.model.Entry
import com.imran.clothstore.access.ContactsVault
import com.imran.clothstore.util.PhotoUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ওয়েব অ্যাপের ইনলাইন ফর্ম (_epCustomerForm / _epSupplierForm) এর সমতুল্য।
 * কাস্টমার ফর্মে "পাওনা/জমা/গজ" আর মহাজন ফর্মে "বকেয়া/জমা/গজ" লেবেল — বাকি ফিল্ড একই।
 */
@Composable
fun EntryFormDialog(
    isCustomerType: Boolean,
    existing: Entry?,
    onDismiss: () -> Unit,
    onSave: (
        name: String, addr: String, mob: String, memo: String,
        billOrBaki: Double, joma: Double, goj: String, note: String,
        date: String, photoUrl: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var addr by remember { mutableStateOf(existing?.addr ?: "") }
    var mob by remember { mutableStateOf(existing?.mob?.removePrefix("+88") ?: "") }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var billOrBaki by remember { mutableStateOf(existing?.bill?.takeIf { it > 0 }?.toString() ?: existing?.baki?.toString() ?: "") }
    var joma by remember { mutableStateOf(existing?.joma?.toString() ?: "") }
    var goj by remember { mutableStateOf(existing?.goj ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: "") }
    var photoUrl by remember { mutableStateOf(existing?.photoUrl ?: "") }
    var photoStatus by remember { mutableStateOf("") }
    var photoUploading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val numbersAllowed by ContactsVault.allowed.collectAsState()
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        photoUploading = true
        photoStatus = "আপলোড হচ্ছে…"
        scope.launch {
            // ওয়েব অ্যাপের epPhotoPick()-এর সমতুল্য: কম্প্রেস করে base64 dataURL বানানো,
            // ভারী কাজ (bitmap decode/scale) IO ডিসপ্যাচারে
            val result = withContext(Dispatchers.IO) {
                PhotoUpload.compressToDataUrl(context.contentResolver, uri)
            }
            when (result) {
                is PhotoUpload.Result.Success -> {
                    photoUrl = result.dataUrl
                    photoStatus = "ছবি যুক্ত হয়েছে ✓"
                }
                is PhotoUpload.Result.Error -> {
                    photoStatus = "আপলোড ব্যর্থ — ${result.message}"
                }
            }
            photoUploading = false
        }
    }

    val billLabel = if (isCustomerType) "পাওনা" else "বকেয়া"

    // ওয়েব রেফারেন্স অনুযায়ী: ক্রিম প্যানেল, উপরে বসানো (স্ট্যাটাস বারের নিচ থেকে), নিচের কোণা গোলাকার
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // imePadding: কিবোর্ড উঠলে ফর্মের উচ্চতা কিবোর্ডের উপর পর্যন্ত সীমিত হয়; সেভ সারি স্ক্রলের বাইরে
        // নিচে আটকানো থাকে, তাই কিবোর্ড খোলা থাকলেও ✓ সেভ করুন বাটন দেখা যায়।
        Box(modifier = Modifier.fillMaxWidth().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EfCream, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
              Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                // ── ছবি আপলোড বৃত্ত ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(EfCircle)
                            .clickable(enabled = !photoUploading) {
                                photoPickerLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else if (photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "প্রোফাইল ছবি",
                                modifier = Modifier.size(62.dp).clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "ছবি যুক্ত করুন", tint = Color(0xFF4A4740), modifier = Modifier.size(28.dp))
                        }
                    }
                    Text(
                        text = photoStatus.ifBlank { "ছবি যুক্ত করুন (ঐচ্ছিক)" },
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }

                // ── নাম | ঠিকানা ──
                Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(name, { name = it }, if (isCustomerType) "নাম" else "নাম *", Modifier.weight(1f))
                    AppTextField(addr, { addr = it }, "ঠিকানা", Modifier.weight(1f))
                }

                // ── +৮৮ মোবাইল | তারিখ ──
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // নাম্বারের ঘর শুধু বৈধ ডিভাইসে (v1.1.6) — অবৈধরা নাম্বার দেখেও না, লিখতেও পারে না
                    if (numbersAllowed) {
                        AppTextField(
                            value = mob,
                            onValueChange = { mob = it },
                            label = "মোবাইল",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Phone,
                            leadingIcon = { Text("+88", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        )
                    }
                    AppDateField(date, { date = it }, "তারিখ", Modifier.weight(1f))
                }

                // ── ম্যামো নং | পাওনা/বকেয়া ──
                Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(memo, { memo = it }, "ম্যামো নং", Modifier.weight(1f), KeyboardType.Number)
                    AppTextField(billOrBaki, { billOrBaki = it }, billLabel, Modifier.weight(1f), KeyboardType.Decimal)
                }

                // ── জমা | গজ ──
                Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(joma, { joma = it }, "জমা", Modifier.weight(1f), KeyboardType.Decimal)
                    AppTextField(goj, { goj = it }, "গজ", Modifier.weight(1f), KeyboardType.Decimal)
                }

                // ── মন্তব্য (পুরো প্রস্থ) ──
                AppTextField(note, { note = it }, "মন্তব্য", Modifier.padding(top = 14.dp).fillMaxWidth())
              }

                // ── নিচের সারি: ✕ (বৃত্ত) + ✓ সেভ করুন ──
                Row(
                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(EfCloseBg)
                            .border(BorderStroke(1.dp, EfBorder), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন", tint = AppColors.TextPrimary)
                    }
                    Button(
                        onClick = {
                            onSave(
                                name, addr, mob, memo,
                                billOrBaki.toDoubleOrNull() ?: 0.0,
                                joma.toDoubleOrNull() ?: 0.0,
                                goj, note, date,
                                photoUrl
                            )
                        },
                        modifier = Modifier.width(150.dp).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.HeaderTeal)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("সেভ করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

// ── ফর্মের রঙ/মাপ — রেফারেন্স স্ক্রিনশট (ক্রিম প্যানেল + অফ-হোয়াইট ইনপুট) থেকে ──
private val EfCream = Color.White
private val EfCircle = Color(0xFFE6DDC8)
private val EfFieldBg = Color.White
private val EfBorder = Color(0xFFE2D9C4)
private val EfCloseBg = Color.White
private val EfRadius = 12.dp
private val EfFieldHeight = 48.dp
