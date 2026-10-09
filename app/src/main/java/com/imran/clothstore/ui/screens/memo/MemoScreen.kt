package com.imran.clothstore.ui.screens.memo

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.imran.clothstore.data.memo.MemoMeta
import com.imran.clothstore.ui.components.SecretKeyDialog
import com.imran.clothstore.ui.theme.AppColors
import com.imran.clothstore.util.toBengaliDigits
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun memoDate(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms)).toBengaliDigits()

/**
 * পার্টির মেমো-ছবির গ্যালারি। ছবি Google Drive-এ (Apps Script-এর মাধ্যমে), তালিকা (metadata) Firestore-এ।
 */
@Composable
fun MemoScreen(
    partyName: String,
    viewModel: MemoViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val memos by viewModel.memos.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val message by viewModel.message.collectAsState()

    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) viewModel.upload(uris) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = cameraUri
        cameraUri = null
        if (ok && uri != null) viewModel.upload(listOf(uri))
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    val canAdd = viewModel.configured && progress == null

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // ── হেডার ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.HeaderTeal)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("মেমো", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    partyName.ifBlank { "—" } + if (memos.isNotEmpty()) " · ${memos.size.toBengaliDigits()}টি ছবি" else "",
                    color = Color(0xFFCFE3E4), fontSize = 12.sp, maxLines = 1
                )
            }
            Box {
                Button(
                    onClick = { addMenuOpen = true },
                    enabled = canAdd,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Gold, contentColor = AppColors.Ink),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  ছবি যোগ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("গ্যালারি থেকে (একাধিক)") },
                        leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) },
                        onClick = {
                            addMenuOpen = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("ক্যামেরা") },
                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        onClick = {
                            addMenuOpen = false
                            val dir = File(context.cacheDir, "memo_camera").apply { mkdirs() }
                            val f = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    )
                }
            }
        }

        // ── আপলোড প্রগ্রেস ──
        progress?.let { p ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "আপলোড হচ্ছে… ${(p.done + 1).toBengaliDigits()}/${p.total.toBengaliDigits()}",
                    fontSize = 12.sp, color = AppColors.TextPrimary
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = AppColors.Gold)
            }
        }

        // ── সেটআপ বাকি থাকলে সতর্কতা ──
        if (!viewModel.configured) {
            Text(
                "ছবি জমার সেটআপ বাকি: MemoConfig.kt-এ Apps Script-এর URL ও কী বসাতে হবে।",
                fontSize = 13.sp, color = AppColors.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFF6DC))
                    .padding(14.dp)
            )
        }

        // ── গ্যালারি ──
        if (memos.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "এখনো কোনো মেমোর ছবি নেই\nউপরের \"ছবি যোগ\" চাপুন",
                    fontSize = 14.sp, color = Color(0xFF6C6A64),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(memos, key = { _, m -> m.driveFileId }) { index, meta ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFE9D8))
                            .clickable { viewerIndex = index }
                    ) {
                        DriveImage(meta.driveFileId, viewModel, Modifier.fillMaxSize(), ContentScale.Crop)
                        Text(
                            memoDate(meta.createdAt),
                            fontSize = 10.sp, color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color(0x99000000))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    viewerIndex?.let { start ->
        MemoViewer(
            memos = memos,
            startIndex = start,
            viewModel = viewModel,
            onClose = { viewerIndex = null }
        )
    }
}

/** Drive-এর ছবি দেখায় — ক্যাশে থাকলে সাথে সাথে, নাহলে নামিয়ে */
@Composable
private fun DriveImage(
    fileId: String,
    viewModel: MemoViewModel,
    modifier: Modifier,
    contentScale: ContentScale
) {
    var file by remember(fileId) { mutableStateOf<File?>(null) }
    var failed by remember(fileId) { mutableStateOf(false) }

    LaunchedEffect(fileId) {
        if (file == null) {
            failed = false
            val f = viewModel.imageFile(fileId)
            if (f != null) file = f else failed = true
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val f = file
        when {
            f != null -> AsyncImage(
                model = f,
                contentDescription = "মেমোর ছবি",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
            failed -> Icon(Icons.Filled.BrokenImage, contentDescription = "ছবি লোড হয়নি", tint = Color(0xFF9A9486))
            else -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = AppColors.Gold)
        }
    }
}

/** পূর্ণ-স্ক্রিন ভিউয়ার: সোয়াইপ, পিঞ্চ-জুম, ডিলিট (সিক্রেট কী সহ) */
@Composable
private fun MemoViewer(
    memos: List<MemoMeta>,
    startIndex: Int,
    viewModel: MemoViewModel,
    onClose: () -> Unit
) {
    if (memos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, memos.lastIndex),
        pageCount = { memos.size }
    )
    var deleting by remember { mutableStateOf<MemoMeta?>(null) }
    var zoomed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                key = { i -> memos.getOrNull(i)?.driveFileId ?: i },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val meta = memos.getOrNull(page)
                if (meta != null) {
                    ZoomableDriveImage(
                        fileId = meta.driveFileId,
                        viewModel = viewModel,
                        isCurrent = pagerState.currentPage == page,
                        onZoomChanged = { z -> if (pagerState.currentPage == page) zoomed = z }
                    )
                }
            }

            val current = memos.getOrNull(pagerState.currentPage)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "বন্ধ", tint = Color.White)
                }
                Text(
                    "${(pagerState.currentPage + 1).toBengaliDigits()} / ${memos.size.toBengaliDigits()}",
                    color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { deleting = current }, enabled = current != null) {
                    Icon(Icons.Filled.Delete, contentDescription = "ডিলিট", tint = Color(0xFFFF8A80))
                }
            }

            current?.let {
                Text(
                    memoDate(it.createdAt),
                    color = Color.White, fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .background(Color(0x66000000), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }

    deleting?.let { meta ->
        SecretKeyDialog(
            message = "এই মেমোর ছবিটি Google Drive ও তালিকা থেকে মুছতে সিক্রেট কী দিন।",
            confirmLabel = "ডিলিট করুন",
            onConfirm = { viewModel.delete(meta); deleting = null; zoomed = false },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun ZoomableDriveImage(
    fileId: String,
    viewModel: MemoViewModel,
    isCurrent: Boolean,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // অন্য পেজে সরে গেলে জুম রিসেট
    LaunchedEffect(isCurrent) {
        if (!isCurrent) { scale = 1f; offset = Offset.Zero }
    }
    LaunchedEffect(scale, isCurrent) { if (isCurrent) onZoomChanged(scale > 1.01f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // দুই আঙুল, অথবা ইতোমধ্যে জুম করা থাকলে — তখনই আমরা ইভেন্ট নিই; নাহলে পেজার সোয়াইপ পায়
                        if (event.changes.size > 1 || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            val maxX = size.width * (newScale - 1f) / 2f
                            val maxY = size.height * (newScale - 1f) / 2f
                            val pan = event.calculatePan()
                            offset = if (newScale <= 1f) Offset.Zero else Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offset.x, translationY = offset.y
            ),
        contentAlignment = Alignment.Center
    ) {
        DriveImage(fileId, viewModel, Modifier.fillMaxSize(), ContentScale.Fit)
    }
}
