package com.imran.clothstore.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imran.clothstore.data.model.EntryCategory
import androidx.compose.ui.tooling.preview.Preview
import com.imran.clothstore.AppSingletons
import com.imran.clothstore.data.local.BackupCacheDao
import com.imran.clothstore.data.local.BackupCacheEntity
import com.imran.clothstore.data.local.LocalCacheRepository
import com.imran.clothstore.ui.theme.ImranClothStoreTheme
import kotlinx.coroutines.flow.flowOf
import com.imran.clothstore.ui.components.AppTopBar
import com.imran.clothstore.ui.components.HeaderCardOverlap
import com.imran.clothstore.ui.components.CalendarStripCard
import com.imran.clothstore.ui.components.CustomerArt
import com.imran.clothstore.ui.components.CustomerIrregularArt
import com.imran.clothstore.ui.components.FabricArt
import com.imran.clothstore.ui.components.DatabaseArt
import com.imran.clothstore.ui.components.PartyArt
import com.imran.clothstore.ui.components.MiniArtCard
import com.imran.clothstore.ui.components.PLTrendsArt
import com.imran.clothstore.ui.components.ReportArt
import com.imran.clothstore.ui.components.SupplierIrregularArt
import com.imran.clothstore.ui.components.SupplierTruckArt
import com.imran.clothstore.ui.screens.notif.NotificationViewModel
import com.imran.clothstore.ui.theme.AppColors

/**
 * হোম ট্যাব — টপবার (নিচে গোলাকার কার্ভ) → হেডারের উপর আংশিক বসানো ৩টা KPI কার্ড (কাপড় ক্রয় হিসাব /
 * P&L Trends / সাপ্তাহিক রিপোর্ট) → ক্যালেন্ডার স্ট্রিপ → ৬টা হোম কার্ড (রেগুলার/ইর-রেগুলার
 * কাস্টমার, রেগুলার/ইর-রেগুলার মহাজন, পার্টি, ডাটাবেইজ), ৩×২ গ্রিডে — KPI কার্ডের সমান সাইজ।
 *
 * পার্টি ও ডাটাবেইজ সংযোগ — সাইড মেনু থেকে হোম কার্ডে আনা হয়েছে।
 * সাপ্তাহিক তথ্য হালনাগাদের এন্ট্রি পয়েন্ট Dashboard ট্যাবের ব্যানারে (DashboardScreen.kt)।
 */
@Composable
fun HomeScreen(
    onCardClick: (EntryCategory) -> Unit,
    onNotifClick: () -> Unit,
    onMenuClick: () -> Unit,
    onAggregateReportClick: () -> Unit,
    onPLTrendsClick: () -> Unit,
    onFabricClick: () -> Unit,
    onPartyClick: () -> Unit,
    onDbConnectClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    notifViewModel: NotificationViewModel = viewModel()
) {
    val hasUnread by notifViewModel.hasUnread.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val dbSubtitle by viewModel.dbSubtitle.collectAsState()

    // মিনি-কার্ডের উচ্চতা (140dp) এর প্রায় অর্ধেক হেডারের ভেতরে, বাকি অর্ধেক বডিতে
    val overlap = HeaderCardOverlap

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BodyBg)
    ) {
        Box {
            AppTopBar(
                onNotifClick = onNotifClick,
                onMenuClick = onMenuClick,
                notifBadgeCount = if (hasUnread) 1 else 0
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = overlap)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniArtCard(
                    title = "Purchase Report",
                    onClick = onFabricClick,
                    art = { FabricArt() },
                    labelColor = Color(0xFF1F6B4F),
                    modifier = Modifier.weight(1f)
                )
                MiniArtCard(
                    title = "P&L Trends",
                    onClick = onPLTrendsClick,
                    art = { PLTrendsArt() },
                    labelColor = Color(0xFFB4532A),
                    modifier = Modifier.weight(1f)
                )
                MiniArtCard(
                    title = "Weekly Report",
                    onClick = onAggregateReportClick,
                    art = { ReportArt() },
                    labelColor = Color(0xFFB03A2E),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(overlap + 8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            // নিচের ভাসমান বারের পিছনে শেষ কার্ড যেন লুকিয়ে না যায়
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── ক্যালেন্ডার স্ট্রিপ (বাংলা/হিজরি/ইংরেজি তারিখ + সাপ্তাহিক দিন) ──
            item(span = { GridItemSpan(3) }) {
                CalendarStripCard()
            }

            item {
                MiniArtCard(
                    title = "Regular Customers",
                    subtitle = viewModel.formatSummary(EntryCategory.REGULAR_CUSTOMER, summaries),
                    onClick = { onCardClick(EntryCategory.REGULAR_CUSTOMER) },
                    art = { CustomerArt() },
                    labelColor = AppColors.TextPrimary,
                    cardHeight = 172.dp
                )
            }
            item {
                MiniArtCard(
                    title = "Irregular Customers",
                    subtitle = viewModel.formatSummary(EntryCategory.IRREGULAR_CUSTOMER, summaries),
                    onClick = { onCardClick(EntryCategory.IRREGULAR_CUSTOMER) },
                    art = { CustomerIrregularArt() },
                    labelColor = AppColors.TextPrimary,
                    cardHeight = 172.dp,
                    iconTopPadding = 14.5.dp
                )
            }
            item {
                MiniArtCard(
                    title = "Party",
                    subtitle = viewModel.formatSummary(EntryCategory.PARTY, summaries),
                    onClick = onPartyClick,
                    art = { PartyArt() },
                    labelColor = AppColors.TextPrimary,
                    cardHeight = 172.dp,
                    // Party-র "groups" আইকনের গ্লিফ পাশের আইকনের চেয়ে ছোট — নিচের কিনারা মেলাতে ৪dp বাড়তি
                    iconTopPadding = 18.5.dp
                )
            }
            item {
                MiniArtCard(
                    title = "Regular Suppliers",
                    subtitle = viewModel.formatSummary(EntryCategory.REGULAR_SUPPLIER, summaries),
                    onClick = { onCardClick(EntryCategory.REGULAR_SUPPLIER) },
                    art = { SupplierTruckArt() },
                    labelColor = AppColors.TextPrimary,
                    cardHeight = 172.dp
                )
            }
            item {
                MiniArtCard(
                    title = "Irregular Suppliers",
                    subtitle = viewModel.formatSummary(EntryCategory.IRREGULAR_SUPPLIER, summaries),
                    onClick = { onCardClick(EntryCategory.IRREGULAR_SUPPLIER) },
                    art = { SupplierIrregularArt() },
                    labelColor = AppColors.TextPrimary,
                    cardHeight = 172.dp
                )
            }
            item {
                MiniArtCard(
                    title = "Database",
                    subtitle = dbSubtitle,
                    onClick = onDbConnectClick,
                    art = { DatabaseArt() },
                    labelColor = AppColors.TextPrimary,
                    cardHeight = 172.dp,
                    iconTopPadding = 14.5.dp
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun HomeScreenPreview() {
    // Provide a dummy LocalCacheRepository for the Preview to prevent crashes 
    // when HomeViewModel's default BackupRepository is instantiated.
    if (AppSingletons.appContext == null) {
        val dummyDao = object : BackupCacheDao {
            override fun observe() = flowOf(BackupCacheEntity(payloadJson = "{}"))
            override suspend fun getOnce() = null
            override suspend fun upsert(entity: BackupCacheEntity) = 0L
            override suspend fun isPendingSync() = false
            override suspend fun clearAll() { }
        }
        AppSingletons.localCacheRepository = LocalCacheRepository(dummyDao)
    }

    ImranClothStoreTheme {
        HomeScreen(
            onCardClick = {},
            onNotifClick = {},
            onMenuClick = {},
            onAggregateReportClick = {},
            onPLTrendsClick = {},
            onFabricClick = {},
            onPartyClick = {},
            onDbConnectClick = {}
        )
    }
}
