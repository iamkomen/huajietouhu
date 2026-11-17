package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.FactorScoreAlertEntity
import com.example.data.local.WatchlistEntity
import com.example.data.model.KLinePoint
import com.example.data.model.RetailTop10Stock
import com.example.data.model.StockQuote
import com.example.data.model.VolatilityRiskLevel
import com.example.data.repository.QuantEngine
import com.example.ui.components.VolatilityHeatmapCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.StockTerminalViewModel
import com.example.ui.viewmodel.TerminalUiState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WatchlistScreen(
    viewModel: StockTerminalViewModel,
    uiState: TerminalUiState,
    onNavigateToTerminal: () -> Unit,
    windowSizeClass: WindowSizeClass = LocalAdaptiveLayout.current.windowSizeClass,
    modifier: Modifier = Modifier
) {
    val adaptive = rememberAdaptiveLayoutSpec(windowSizeClass)
    // 0 = 散户至尊十绝选股器, 1 = 我的持久化自选池
    var selectedViewTab by remember { mutableStateOf(0) }
    var batchAddedSuccess by remember { mutableStateOf(false) }
    var selectedVolRiskFilter by remember { mutableStateOf<VolatilityRiskLevel?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(horizontal = adaptive.screenHorizontalPadding, vertical = 10.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "自选标的与散户十绝",
                    color = TextPrimary,
                    fontSize = if (adaptive.isCompactWidth) 18.sp else 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "散户精力边界 · 最多10只 · 行业去相关",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Notifications Bell with Badge
                IconButton(
                    onClick = { viewModel.openAlertsDialog() },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("watchlist_alerts_bell_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (uiState.unreadAlertCount > 0) {
                                Badge(
                                    containerColor = CriticalRed,
                                    contentColor = TextPrimary
                                ) {
                                    Text(
                                        text = "${uiState.unreadAlertCount}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "因子异动提醒",
                            tint = if (uiState.unreadAlertCount > 0) WallStreetGold else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(WallStreetGoldSoft)
                        .border(1.dp, WallStreetGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { viewModel.openSearchDialog() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("watchlist_add_stock_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ 标的",
                        color = WallStreetGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main View Mode Switcher: 【散户十绝】 vs 【我的自选池】
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TerminalSurfaceElevated)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedViewTab == 0) WallStreetGoldSoft else Color.Transparent)
                    .border(
                        width = if (selectedViewTab == 0) 1.dp else 0.dp,
                        color = if (selectedViewTab == 0) WallStreetGold else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { selectedViewTab = 0 }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .testTag("tab_retail_top10_screener"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = if (selectedViewTab == 0) WallStreetGold else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "散户十绝 (≤10只)",
                        color = if (selectedViewTab == 0) WallStreetGold else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedViewTab == 1) WallStreetGoldSoft else Color.Transparent)
                    .border(
                        width = if (selectedViewTab == 1) 1.dp else 0.dp,
                        color = if (selectedViewTab == 1) WallStreetGold else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { selectedViewTab = 1 }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .testTag("tab_my_watchlist"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = if (selectedViewTab == 1) WallStreetGold else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "我的自选池 (${maxOf(uiState.watchlistEntities.size, uiState.watchlistQuotes.size)})",
                        color = if (selectedViewTab == 1) WallStreetGold else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // VIEW 0: 散户至尊十绝选股器
        if (selectedViewTab == 0) {
            val portfolio = uiState.retailTop10Portfolio ?: QuantEngine.generateRetailTop10Portfolio(uiState.selectedRetailStrategy)

            val displayedStocks = if (selectedVolRiskFilter == null) {
                portfolio.stocks
            } else if (selectedVolRiskFilter == VolatilityRiskLevel.HIGH) {
                portfolio.stocks.filter { it.volatilityLevel == VolatilityRiskLevel.HIGH || it.volatilityLevel == VolatilityRiskLevel.EXTREME }
            } else {
                portfolio.stocks.filter { it.volatilityLevel == selectedVolRiskFilter }
            }

            // Sub Strategy Filters: 2x2 on narrow/high fontScale, 1x4 on wide screens
            val strategies = listOf(
                "BALANCED" to "🏆 稳健全能十绝",
                "HIGH_DIVIDEND" to "🛡️ 低波红利防守",
                "TECH_GROWTH" to "🚀 科技硬核成长",
                "TURNAROUND" to "⚡ 困境超跌反转"
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val useTwoByTwo = maxWidth < 430.dp || fontScale >= 1.15f
                if (useTwoByTwo) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        strategies.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                pair.forEach { (key, label) ->
                                    StrategyFilterPill(
                                        label = label,
                                        isSelected = uiState.selectedRetailStrategy == key,
                                        onClick = {
                                            viewModel.setRetailStrategy(key)
                                            batchAddedSuccess = false
                                            selectedVolRiskFilter = null
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        strategies.forEach { (key, label) ->
                            StrategyFilterPill(
                                label = label,
                                isSelected = uiState.selectedRetailStrategy == key,
                                onClick = {
                                    viewModel.setRetailStrategy(key)
                                    batchAddedSuccess = false
                                    selectedVolRiskFilter = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable List containing Heatmap, Portfolio Summary Card, and Stock Items
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("retail_top10_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Interactive Volatility Heatmap Card (Max 10 Slots & Risk Budget)
                item {
                    VolatilityHeatmapCard(
                        portfolio = portfolio,
                        selectedFilterLevel = selectedVolRiskFilter,
                        onSelectFilterLevel = { selectedVolRiskFilter = it },
                        onSelectStockSlot = { stock ->
                            viewModel.selectStock(stock.quote.symbol)
                            onNavigateToTerminal()
                        }
                    )
                }

                // Portfolio Health & Allocation Dashboard Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TerminalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
                    ) {
                        Column(modifier = Modifier.padding(adaptive.cardInnerPadding)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = portfolio.strategyTitle,
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = portfolio.strategySubtitle,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Batch Add Button
                                Button(
                                    onClick = {
                                        viewModel.batchAddTop10ToWatchlist()
                                        batchAddedSuccess = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (batchAddedSuccess) SuccessGreenSoft else WallStreetGoldSoft,
                                        contentColor = if (batchAddedSuccess) SuccessGreen else WallStreetGold
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("batch_add_top10_button")
                                ) {
                                    Icon(
                                        imageVector = if (batchAddedSuccess) Icons.Default.Check else Icons.Default.BookmarkAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (batchAddedSuccess) "已全部导入" else "一键全存自选",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val fontScale = LocalDensity.current.fontScale
                                val narrowPills = isNarrowPortrait(maxWidth, fontScale)
                                val pillMetrics = listOf(
                                    Triple("总仓位/现金", "${portfolio.recommendedTotalStockWeight}/${portfolio.cashCushionWeight}%", WallStreetGold),
                                    Triple("协方差σ_p", "${String.format(java.util.Locale.US, "%.1f", portfolio.weightedPortfolioVol)}%", if (portfolio.highRiskStockCount <= portfolio.highRiskLimitMax) SuccessGreen else StockRed),
                                    Triple("分散比率DR", "${portfolio.diversificationRatio}x", QuantCyan),
                                    Triple("去相关得分", "${portfolio.sectorDiversificationScore}分", WallStreetGold)
                                )
                                if (narrowPills) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        pillMetrics.chunked(2).forEach { pair ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                pair.forEach { (lbl, valStr, clr) ->
                                                    Top10MetricPill(
                                                        label = lbl,
                                                        value = valStr,
                                                        color = clr,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        pillMetrics.forEach { (lbl, valStr, clr) ->
                                            Top10MetricPill(
                                                label = lbl,
                                                value = valStr,
                                                color = clr,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "💡 散户风控铁律：${portfolio.riskBudgetVerdict}；有效押注 ENB=${portfolio.effectiveNumBets}，单票≤12%。",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // If filter resulted in empty list
                if (displayedStocks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "当前筛选条件 [${selectedVolRiskFilter?.label ?: ""}] 下无匹配标的",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (adaptive.gridColumns >= 2) {
                    val rows = displayedStocks.chunked(adaptive.gridColumns)
                    items(rows, key = { row -> row.joinToString("_") { it.quote.symbol } }) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowItems.forEach { item ->
                                val isSaved = uiState.watchlistEntities.any { it.symbol == item.quote.symbol }
                                val originalRank = portfolio.stocks.indexOfFirst { it.quote.symbol == item.quote.symbol } + 1
                                RetailStockCard(
                                    rank = if (originalRank > 0) originalRank else 1,
                                    item = item,
                                    isWatchlisted = isSaved,
                                    onSelect = {
                                        viewModel.selectStock(item.quote.symbol)
                                        onNavigateToTerminal()
                                    },
                                    onToggleWatchlist = {
                                        viewModel.toggleStockWatchlist(item.quote.symbol, item.quote.name)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size < adaptive.gridColumns) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    itemsIndexed(displayedStocks, key = { _, item -> item.quote.symbol }) { index, item ->
                        val isSaved = uiState.watchlistEntities.any { it.symbol == item.quote.symbol }
                        val originalRank = portfolio.stocks.indexOfFirst { it.quote.symbol == item.quote.symbol } + 1
                        RetailStockCard(
                            rank = if (originalRank > 0) originalRank else (index + 1),
                            item = item,
                            isWatchlisted = isSaved,
                            onSelect = {
                                viewModel.selectStock(item.quote.symbol)
                                onNavigateToTerminal()
                            },
                            onToggleWatchlist = {
                                viewModel.toggleStockWatchlist(item.quote.symbol, item.quote.name)
                            }
                        )
                    }
                }
            }
        } else {
            // VIEW 1: 原有持久化自选池
            // In-App Push-like Notification Banner
            AnimatedVisibility(
                visible = uiState.activePushAlert != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val alert = uiState.activePushAlert
                if (alert != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("push_like_alert_banner"),
                        colors = CardDefaults.cardColors(containerColor = StockRedSoft),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockRed.copy(alpha = 0.6f)))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = StockRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "【实时因子异动推送】${alert.stockName}",
                                        color = StockRed,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissPushAlert() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = alert.alertMessage,
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.dismissPushAlert()
                                        viewModel.selectStock(alert.symbol)
                                        onNavigateToTerminal()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "立即穿透研判 ➔", color = WallStreetGold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.watchlistEntities.isEmpty() && uiState.watchlistQuotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "自选池暂无标的", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "您可以切换上方【散户十绝】一键存入，或点击“+ 标的”手动检索", color = TextMuted, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { selectedViewTab = 0 },
                            colors = ButtonDefaults.buttonColors(containerColor = WallStreetGold, contentColor = TerminalBg),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("empty_watchlist_goto_top10_button")
                        ) {
                            Icon(imageVector = Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "浏览散户至尊十绝组合", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            } else if (uiState.watchlistQuotes.isEmpty()) {
                // 自选池在 Room 中有标的，实时行情正在首次回包同步中
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("watchlist_lazy_column"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (adaptive.gridColumns >= 2) {
                        val rows = uiState.watchlistEntities.chunked(adaptive.gridColumns)
                        items(rows, key = { row -> row.joinToString("_") { it.symbol } }) { rowEntities ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                rowEntities.forEach { entity ->
                                    WatchlistSyncingCard(
                                        entity = entity,
                                        onSelect = {
                                            viewModel.selectStock(entity.symbol)
                                            onNavigateToTerminal()
                                        },
                                        onRemove = {
                                            viewModel.removeStockFromWatchlist(entity.symbol)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowEntities.size < adaptive.gridColumns) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(uiState.watchlistEntities, key = { it.symbol }) { entity ->
                            WatchlistSyncingCard(
                                entity = entity,
                                onSelect = {
                                    viewModel.selectStock(entity.symbol)
                                    onNavigateToTerminal()
                                },
                                onRemove = {
                                    viewModel.removeStockFromWatchlist(entity.symbol)
                                }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("watchlist_lazy_column"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (adaptive.gridColumns >= 2) {
                        val rows = uiState.watchlistQuotes.chunked(adaptive.gridColumns)
                        items(rows, key = { row -> row.joinToString("_") { it.symbol } }) { rowQuotes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                rowQuotes.forEach { quote ->
                                    val entity = uiState.watchlistEntities.find { it.symbol == quote.symbol }
                                    val kLines = uiState.retailUniverseKLines[quote.symbol].orEmpty()
                                    WatchlistStockCard(
                                        quote = quote,
                                        entity = entity,
                                        kLines = kLines,
                                        onSelect = {
                                            viewModel.selectStock(quote.symbol)
                                            onNavigateToTerminal()
                                        },
                                        onToggleNotification = { enabled ->
                                            viewModel.toggleWatchlistNotification(quote.symbol, enabled)
                                        },
                                        onCycleThreshold = {
                                            val nextDelta = when (entity?.notifyThresholdDelta ?: 5) {
                                                3 -> 5
                                                5 -> 8
                                                8 -> 10
                                                else -> 3
                                            }
                                            viewModel.updateWatchlistThreshold(quote.symbol, nextDelta)
                                        },
                                        onRemove = {
                                            viewModel.removeStockFromWatchlist(quote.symbol)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowQuotes.size < adaptive.gridColumns) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(uiState.watchlistQuotes, key = { it.symbol }) { quote ->
                            val entity = uiState.watchlistEntities.find { it.symbol == quote.symbol }
                            val kLines = uiState.retailUniverseKLines[quote.symbol].orEmpty()
                            WatchlistStockCard(
                                quote = quote,
                                entity = entity,
                                kLines = kLines,
                                onSelect = {
                                    viewModel.selectStock(quote.symbol)
                                    onNavigateToTerminal()
                                },
                                onToggleNotification = { enabled ->
                                    viewModel.toggleWatchlistNotification(quote.symbol, enabled)
                                },
                                onCycleThreshold = {
                                    val nextDelta = when (entity?.notifyThresholdDelta ?: 5) {
                                        3 -> 5
                                        5 -> 8
                                        8 -> 10
                                        else -> 3
                                    }
                                    viewModel.updateWatchlistThreshold(quote.symbol, nextDelta)
                                },
                                onRemove = {
                                    viewModel.removeStockFromWatchlist(quote.symbol)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Factor Alerts Log Dialog
        if (uiState.isAlertsDialogOpen) {
            FactorAlertsHistoryDialog(
                alerts = uiState.factorAlerts,
                onDismiss = { viewModel.closeAlertsDialog() },
                onSelectStock = { sym ->
                    viewModel.closeAlertsDialog()
                    viewModel.selectStock(sym)
                    onNavigateToTerminal()
                },
                onClearAll = { viewModel.clearAllAlerts() }
            )
        }
    }
}

@Composable
private fun StrategyFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) WallStreetGoldSoft else TerminalSurfaceElevated)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) WallStreetGold else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) WallStreetGold else TextMuted,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Top10MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalSurfaceElevated)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RetailStockCard(
    rank: Int,
    item: RetailTop10Stock,
    isWatchlisted: Boolean,
    onSelect: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val q = item.quote
    val priceColor = when {
        q.isUp -> StockRed
        q.isDown -> StockGreen
        else -> TextPrimary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("retail_stock_card_${q.code}"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Rank, Name, Code, Sector, Live Sync Badge, Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (rank <= 3) WallStreetGoldSoft else TerminalSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#$rank",
                            color = if (rank <= 3) WallStreetGold else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = q.name,
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = q.code,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(TerminalSurfaceElevated)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.sectorName,
                            color = QuantCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (item.isLiveQuoteSynced) SuccessGreenSoft else TerminalSurfaceElevated)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (item.isLiveQuoteSynced) "实盘" else "预同步",
                            color = if (item.isLiveQuoteSynced) SuccessGreen else TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = q.formattedPrice,
                            color = priceColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = q.formattedChangePercent,
                            color = priceColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isWatchlisted) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isWatchlisted) "已在自选" else "加入自选",
                            tint = if (isWatchlisted) WallStreetGold else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2A: Position Sizing, Risk Parity Reference, Euler Risk Contribution CTR%
            val (volBadgeBg, volBadgeColor) = when (item.volatilityLevel) {
                VolatilityRiskLevel.LOW -> Pair(SuccessGreenSoft, SuccessGreen)
                VolatilityRiskLevel.MODERATE -> Pair(QuantCyanSoft, QuantCyan)
                VolatilityRiskLevel.HIGH -> Pair(WallStreetGoldSoft, WallStreetGold)
                VolatilityRiskLevel.EXTREME -> Pair(StockRedSoft, StockRed)
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(WallStreetGoldSoft)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "配置 ${item.recommendedWeightPercent}% (RP推荐 ${item.invVolSuggestedWeight}%)",
                        color = WallStreetGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "风险贡献 CTR ${item.riskContributionPercent}%",
                        color = QuantCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(QuantCyanSoft)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "量化 ${item.compositeScore}分",
                        color = QuantCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Row 2B: Volatility Source & Feature Tag (wraps cleanly on narrow / large fontScale)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(volBadgeBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HV ${String.format(java.util.Locale.US, "%.1f", item.realizedVolAnnualized)}% · ${item.volatilityLevel.label} (${item.volSourceLabel})",
                        color = volBadgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SuccessGreenSoft)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.dividendYieldOrGrowth,
                        color = SuccessGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Rationale
            Text(
                text = "📌 个人配置理由: ${item.selectionRationale}",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Row 4: Beta & Operational Sizing Range & Stop Loss / Target
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "β: ${String.format(java.util.Locale.US, "%.2f", item.beta)}",
                    color = WallStreetGold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "区间 ${item.entryPriceRange}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "止损 ¥${String.format(java.util.Locale.US, "%.2f", item.stopLossPrice)}",
                    color = StockGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "目标 ¥${String.format(java.util.Locale.US, "%.2f", item.targetPrice)}",
                    color = StockRed,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "穿透 ➔",
                    color = WallStreetGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun WatchlistSyncingCard(
    entity: WatchlistEntity,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entity.name,
                        color = TextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entity.symbol.drop(2),
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (entity.lastCompositeScore > 0) "上次综合量化分: ${entity.lastCompositeScore} · 正在同步实盘快照..." else "正在同步实盘快照与五因子评分...",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "移出自选",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchlistStockCard(
    quote: StockQuote,
    entity: WatchlistEntity?,
    kLines: List<KLinePoint>,
    onSelect: () -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onCycleThreshold: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priceColor = when {
        quote.isUp -> StockRed
        quote.isDown -> StockGreen
        else -> TextPrimary
    }

    // 实时动态演算多因子得分：优先使用实盘报价 + K线现算结果，杜绝 Room 初始 0 值遮蔽真实指标
    val liveScores = remember(
        quote.symbol,
        quote.price,
        quote.peTtm,
        quote.pb,
        quote.turnoverRate,
        quote.amplitude,
        quote.changePercent,
        kLines.size
    ) {
        if (quote.price > 0.0) QuantEngine.evaluateFactors(quote, kLines) else null
    }

    val compositeScore = liveScores?.compositeScore ?: entity?.lastCompositeScore ?: 0
    val valueScore = liveScores?.valueScore ?: entity?.lastValueScore ?: 0
    val growthScore = liveScores?.growthScore ?: entity?.lastGrowthScore ?: 0
    val momentumScore = liveScores?.momentumScore ?: entity?.lastMomentumScore ?: 0
    val qualityScore = liveScores?.qualityScore ?: entity?.lastQualityScore ?: 0
    val ratingText = liveScores?.rating ?: entity?.lastRating.orEmpty().ifBlank { "同步中" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("watchlist_stock_card_${quote.code}"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // First Row: Left Name/Code/Market (weight=1f), Right stacked Price & Change% (never wraps)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = quote.name,
                        color = TextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = quote.code,
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(WallStreetGoldSoft)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = quote.market.uppercase(),
                            color = WallStreetGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = quote.formattedPrice,
                        color = priceColor,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = quote.formattedChangePercent,
                        color = priceColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Meta strip: Rating + Valuation + Turnover (wraps cleanly without hidden horizontal clipping)
            val peLabel = when {
                quote.peTtm < 0.0 -> "亏损"
                quote.peTtm == 0.0 -> "--"
                else -> String.format(java.util.Locale.US, "%.1f", quote.peTtm)
            }
            val pbLabel = if (quote.pb <= 0.0) "--" else String.format(java.util.Locale.US, "%.2f", quote.pb)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (compositeScore >= 75) StockRedSoft else WallStreetGoldSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = ratingText,
                        color = if (compositeScore >= 75) StockRed else WallStreetGold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = "PE:$peLabel  PB:$pbLabel",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "换手:${String.format(java.util.Locale.US, "%.2f%%", quote.turnoverRate)}  ROE:${String.format(java.util.Locale.US, "%.1f%%", liveScores?.roeEstimatedPercent ?: 0.0)}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second Row: Factor Scores Badges (Value, Growth, Momentum, Quality, Composite)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FactorScoreChip(
                    label = "综合",
                    score = compositeScore,
                    modifier = Modifier.weight(1f)
                )
                FactorScoreChip(
                    label = "价值",
                    score = valueScore,
                    modifier = Modifier.weight(1f)
                )
                FactorScoreChip(
                    label = "成长",
                    score = growthScore,
                    modifier = Modifier.weight(1f)
                )
                FactorScoreChip(
                    label = "动量",
                    score = momentumScore,
                    modifier = Modifier.weight(1f)
                )
                FactorScoreChip(
                    label = "质量",
                    score = qualityScore,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Third Row: Alert Threshold Config & Remove Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isNotifyEnabled = entity?.isNotificationEnabled ?: true
                    IconButton(
                        onClick = { onToggleNotification(!isNotifyEnabled) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isNotifyEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = if (isNotifyEnabled) "已开启异动提醒" else "已静音",
                            tint = if (isNotifyEnabled) WallStreetGold else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    val threshold = entity?.notifyThresholdDelta ?: 5
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TerminalSurfaceElevated)
                            .clickable { onCycleThreshold() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "异动阈值 ±${threshold}分 (点击切换)",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Delete from Watchlist Button
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "移出自选",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FactorScoreChip(label: String, score: Int, modifier: Modifier = Modifier) {
    val scoreColor = when {
        score >= 75 -> StockRed
        score >= 55 -> WallStreetGold
        else -> TextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(TerminalSurfaceElevated)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, color = TextMuted, fontSize = 9.sp)
            Text(
                text = "$score",
                color = scoreColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FactorAlertsHistoryDialog(
    alerts: List<FactorScoreAlertEntity>,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = 520.dp)
                .testTag("factor_alerts_history_dialog"),
            colors = CardDefaults.cardColors(containerColor = TerminalCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = WallStreetGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "自选股因子异动日志",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (alerts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text(text = "暂无因子异动记录", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alerts) { alert ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectStock(alert.symbol) },
                                colors = CardDefaults.cardColors(containerColor = TerminalSurfaceElevated),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${alert.stockName} (${alert.symbol})",
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        )
                                        Text(
                                            text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(alert.timestamp)),
                                            color = TextMuted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(text = alert.alertMessage, color = TextSecondary, fontSize = 11.5.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onClearAll) {
                            Text(text = "清空所有记录", color = CriticalRed, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}
