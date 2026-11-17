package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.StockTerminalViewModel
import com.example.ui.viewmodel.TerminalUiState

@Composable
fun StockTerminalScreen(
    viewModel: StockTerminalViewModel,
    uiState: TerminalUiState,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = LocalAdaptiveLayout.current.windowSizeClass
) {
    val adaptive = rememberAdaptiveLayoutSpec(windowSizeClass)
    val useTwoColumn = adaptive.gridColumns >= 2
    var showChangelogDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
    ) {
        val quote = uiState.quote

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("terminal_lazy_column"),
            contentPadding = PaddingValues(bottom = if (adaptive.useNavigationRail) 24.dp else 80.dp)
        ) {
            // 1. Stock Header Bar
            item {
                StockHeaderBar(
                    quote = quote,
                    isWatchlisted = uiState.isWatchlisted,
                    onToggleWatchlist = { viewModel.toggleWatchlist() },
                    onSearchClick = { viewModel.openSearchDialog() },
                    onRefreshClick = { viewModel.refreshCurrentStock() },
                    onVersionClick = { showChangelogDialog = true }
                )
            }

            if (quote != null) {
                // 2. Macro Position Guidance & Half-Kelly Formula Banner
                item {
                    MacroPositionBanner(
                        quote = quote,
                        marketRegime = uiState.marketRegime,
                        factorScores = uiState.factorScores,
                        kLines = uiState.kLines
                    )
                }

                if (useTwoColumn) {
                    // 3 & 4 Paired on Medium/Expanded: Chart (left) + 5-Level Order Book (right)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = adaptive.cardHorizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            TradingChartSection(
                                quote = quote,
                                minutePoints = uiState.minutePoints,
                                dailyKLines = uiState.kLines,
                                weeklyKLines = uiState.weeklyKLines,
                                monthlyKLines = uiState.monthlyKLines,
                                selectedPeriod = uiState.selectedChartPeriod,
                                onSelectPeriod = { viewModel.setChartPeriod(it) },
                                isKLineLoading = uiState.isKLineLoading,
                                kLineErrorMessage = uiState.kLineErrorMessage,
                                onRetryKLines = { viewModel.retryKLines() },
                                modifier = Modifier.weight(1.35f),
                                horizontalMargin = 0.dp
                            )
                            OrderBookView(
                                orderBook = quote.orderBook,
                                yesterdayClose = quote.yesterdayClose,
                                modifier = Modifier.weight(1f),
                                horizontalMargin = 0.dp
                            )
                        }
                    }
                } else {
                    // 3. Trading Chart Section (分时走势 / 日K / 周K / 月K)
                    item {
                        TradingChartSection(
                            quote = quote,
                            minutePoints = uiState.minutePoints,
                            dailyKLines = uiState.kLines,
                            weeklyKLines = uiState.weeklyKLines,
                            monthlyKLines = uiState.monthlyKLines,
                            selectedPeriod = uiState.selectedChartPeriod,
                            onSelectPeriod = { viewModel.setChartPeriod(it) },
                            isKLineLoading = uiState.isKLineLoading,
                            kLineErrorMessage = uiState.kLineErrorMessage,
                            onRetryKLines = { viewModel.retryKLines() }
                        )
                    }

                    // 4. Five-level Order Book (实时五档盘口与深度)
                    item {
                        OrderBookView(
                            orderBook = quote.orderBook,
                            yesterdayClose = quote.yesterdayClose
                        )
                    }
                }

                // 5. Multi-Factor Analysis Dashboard (雷达图 + 动态权重调节滑块 + 量化交易建议卡片)
                item {
                    if (uiState.factorScores != null) {
                        QuantRadarView(
                            factorScores = uiState.factorScores,
                            quote = quote,
                            kLines = uiState.kLines
                        )
                    }
                }

                // 6. Historical Win Rate & Combinations (历史胜率统计与策略组合命中)
                item {
                    if (uiState.winRateStats != null) {
                        HistoricalWinRateCard(
                            stats = uiState.winRateStats,
                            onSelectHorizon = { viewModel.setHorizonYears(it) }
                        )
                    }
                }

                // 7. Objective Quantitative Validation & Empirical Audit (严苛数理质检与外部客观实证)
                item {
                    if (uiState.objectiveAuditReport != null) {
                        ObjectiveValidationCard(
                            report = uiState.objectiveAuditReport
                        )
                    }
                }

                if (useTwoColumn) {
                    // 8 & 9 Paired on Medium/Expanded: Technical Signals + Wall Street Memo
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = adaptive.cardHorizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            TradingSignalsSection(
                                quote = quote,
                                kLines = uiState.kLines,
                                modifier = Modifier.weight(1f),
                                horizontalMargin = 0.dp
                            )
                            WallStreetReportView(
                                quote = quote,
                                kLines = uiState.kLines,
                                liveFinancialSnapshot = uiState.currentFinancialSnapshot,
                                modifier = Modifier.weight(1f),
                                horizontalMargin = 0.dp
                            )
                        }
                    }
                } else {
                    // 8. Microstructure Technical Signals (MACD, BOLL, RSI, MA)
                    item {
                        TradingSignalsSection(
                            quote = quote,
                            kLines = uiState.kLines
                        )
                    }

                    // 9. Wall Street Gold-Standard Equity Research Memo (投行顶级分析备忘录)
                    item {
                        WallStreetReportView(
                            quote = quote,
                            kLines = uiState.kLines,
                            liveFinancialSnapshot = uiState.currentFinancialSnapshot
                        )
                    }
                }

                // 10. 「猫会计说投资」交互式 DCF 自由现金流折现估值计算器
                item {
                    DcfCalculatorCard(
                        quote = quote,
                        kLines = uiState.kLines,
                        liveFinancialSnapshot = uiState.currentFinancialSnapshot
                    )
                }

                // 11. VCP 二阶段形态 · 1R以损定仓 · 交易前五重铁律闸门
                item {
                    PreTradeExecutionCard(
                        quote = quote,
                        kLines = uiState.kLines,
                        benchmarkKLines = uiState.benchmarkKLines,
                        marketBreadth = uiState.marketBreadth,
                        marketRegime = uiState.marketRegime,
                        liveFinancialSnapshot = uiState.currentFinancialSnapshot,
                        onSaveTradePlan = { sym, name, entry, stop, target, shares, riskRmb, verdict, thesis ->
                            viewModel.saveTradePlan(
                                symbol = sym,
                                stockName = name,
                                entryPrice = entry,
                                stopPrice = stop,
                                targetPrice = target,
                                shares = shares,
                                plannedRiskRmb = riskRmb,
                                gateVerdict = verdict,
                                thesis = thesis
                            )
                        }
                    )
                }
            } else if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = WallStreetGold)
                    }
                }
            }
        }

        // Search Dialog for Tickers
        if (uiState.isSearchDialogOpen) {
            SearchStockDialog(
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                dictionarySize = uiState.dictionarySize,
                dictionaryLastUpdatedMs = uiState.dictionaryLastUpdatedMs,
                isDictionarySyncing = uiState.isDictionarySyncing,
                onQueryChange = { viewModel.searchStocks(it) },
                onRefreshDictionary = { viewModel.refreshStockDictionary() },
                onSelectStock = { viewModel.selectStock(it) },
                onDismiss = { viewModel.closeSearchDialog() }
            )
        }

        if (showChangelogDialog) {
            VersionChangelogDialog(
                onDismiss = { showChangelogDialog = false }
            )
        }
    }
}

@Composable
private fun SearchStockDialog(
    searchQuery: String,
    searchResults: List<com.example.data.model.StockSearchItem>,
    dictionarySize: Int,
    dictionaryLastUpdatedMs: Long,
    isDictionarySyncing: Boolean,
    onQueryChange: (String) -> Unit,
    onRefreshDictionary: () -> Unit,
    onSelectStock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .testTag("search_stock_dialog"),
            colors = CardDefaults.cardColors(containerColor = TerminalCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            text = "标的快速检索（本地字典）",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val syncSubtitle = when {
                            isDictionarySyncing -> "正在后台更新全A股字典（检索不中断）..."
                            dictionaryLastUpdatedMs > 0L -> "已缓存 ${dictionarySize} 只标的 · 每12小时自动更新"
                            else -> "内置核心 ${dictionarySize} 只标的 · 后台定时同步全市场"
                        }
                        Text(
                            text = syncSubtitle,
                            color = if (isDictionarySyncing) WallStreetGold else SuccessGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("search_dictionary_status")
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TerminalSurfaceElevated)
                                .clickable(enabled = !isDictionarySyncing) { onRefreshDictionary() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("refresh_dictionary_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isDictionarySyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = WallStreetGold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "更新字典",
                                    tint = WallStreetGold,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDictionarySyncing) "同步中" else "更新字典",
                                color = WallStreetGold,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", tint = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_search_input"),
                    placeholder = {
                        Text(
                            text = "输入代码/拼音/名称 (如 600938、中国海油、zghy)",
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = WallStreetGold)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "清空", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = WallStreetGold,
                        unfocusedBorderColor = TerminalBorder,
                        focusedContainerColor = TerminalSurfaceElevated,
                        unfocusedContainerColor = TerminalSurfaceElevated
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickHot = listOf(
                        "贵州茅台" to "sh600519",
                        "比亚迪" to "sz002594",
                        "赛力斯" to "sh601127",
                        "寒武纪" to "sh688256",
                        "宁德时代" to "sz300750"
                    )
                    quickHot.forEach { (name, symbol) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TerminalSurfaceElevated)
                                .clickable { onSelectStock(symbol) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = name,
                                color = WallStreetGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (searchQuery.isEmpty()) "指数核心池与经典龙头资产" else "检索结果 (支持6位代码 / 拼音首字母 / 中文名称)",
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (searchQuery.trim().length == 6 && searchQuery.trim().all { it.isDigit() }) {
                        val inputCode = searchQuery.trim()
                        val directMarket = com.example.data.remote.StockApiService.inferMarketFromCode(inputCode)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WallStreetGoldSoft)
                                    .clickable { onSelectStock("$directMarket$inputCode") }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "⚡ 直连探测: $inputCode (${directMarket.uppercase()})",
                                    color = WallStreetGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text(text = "实时拉取", color = WallStreetGold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    val trimmedQuery = searchQuery.trim()
                    if (searchResults.isEmpty() && trimmedQuery.isNotEmpty() && !trimmedQuery.all { it.isDigit() }) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 8.dp)
                                    .testTag("search_empty_hint"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "本地字典暂未匹配 \"$trimmedQuery\"，可输入6位代码直连或点击右上角「更新字典」",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    items(searchResults, key = { it.code }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TerminalSurfaceElevated)
                                .clickable { onSelectStock(item.symbol) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("search_result_${item.code}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.code,
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (item.pinyin.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.pinyin,
                                        color = TextMuted.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(WallStreetGoldSoft)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.market.uppercase(),
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
            }
        }
    }
}
