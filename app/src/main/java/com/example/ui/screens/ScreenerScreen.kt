package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuantFactorScores
import com.example.data.model.ScreenerDcfSignal
import com.example.data.model.StockQuote
import com.example.data.model.TrendStageLabel
import com.example.data.repository.DcfValuationEngine
import com.example.data.repository.ProfitabilityEngine
import com.example.data.repository.QuantEngine
import com.example.data.repository.StockIndexCategory
import com.example.data.repository.StockIndexConstituents
import com.example.ui.components.ScreenerHistoricalDcfChartCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.StockTerminalViewModel
import com.example.ui.viewmodel.TerminalUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenerScreen(
    viewModel: StockTerminalViewModel,
    uiState: TerminalUiState,
    onNavigateToTerminal: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = LocalAdaptiveLayout.current.windowSizeClass
) {
    val adaptive = rememberAdaptiveLayoutSpec(windowSizeClass)
    val fontScale = LocalDensity.current.fontScale

    val filters = listOf(
        "ALL" to "全部成分池",
        "DCF_STRIKE_ZONE" to "DCF七折击球 (边际≥30%)",
        "DCF_UNDERVALUED" to "DCF深度低估 (边际≥15%)",
        "VCP_STAGE2" to "二阶段紧缩突破 (VCP)",
        "ALPHA_LEADER" to "量化顶级优选 (≥72分)",
        "DEEP_VALUE" to "价值洼地 (PE≤20 & PB≤2.5)",
        "HIGH_ROE_MOAT" to "高ROE护城河 (ROE≥15%)",
        "MOMENTUM_BREAKOUT" to "动量突破 (涨幅>1% & 动量≥68)",
        "LOW_VOL_HEDGE" to "低波红利避险 (振幅≤2.5% & 风控≥75)",
        "TECH_GROWTH" to "成长白马 (PE 15~40 & 成长≥68)"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(horizontal = adaptive.screenHorizontalPadding, vertical = 12.dp)
    ) {
        // 1. Header Title & Subtitle
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
                    text = "指数多因子量化选股中枢",
                    color = TextPrimary,
                    fontSize = 18.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "A股真实高频数据驱动 · 拒绝模板伪因子 · 真实高区分度",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(WallStreetGoldSoft)
                    .border(1.dp, WallStreetGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { viewModel.openSearchDialog() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "全市场搜索",
                    color = WallStreetGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Market Indices Live Snapshot Ribbon (fills width evenly on Medium/Expanded portrait)
        if (uiState.marketIndexQuotes.isNotEmpty()) {
            if (adaptive.gridColumns >= 2 && uiState.marketIndexQuotes.size <= 5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.marketIndexQuotes.forEach { indexQuote ->
                        IndexMiniCard(
                            quote = indexQuote,
                            onClick = {
                                viewModel.selectStock(indexQuote.symbol)
                                onNavigateToTerminal()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.marketIndexQuotes.forEach { indexQuote ->
                        IndexMiniCard(
                            quote = indexQuote,
                            onClick = {
                                viewModel.selectStock(indexQuote.symbol)
                                onNavigateToTerminal()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(adaptive.itemSpacing))
        }

        // 3. Index Category Selector Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StockIndexCategory.entries.forEach { category ->
                val isSelected = uiState.selectedIndexCategory == category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) WallStreetGoldSoft else TerminalSurfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) WallStreetGold else TerminalBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.selectIndexCategory(category) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("index_tab_${category.key}")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = category.shortName,
                            color = if (isSelected) WallStreetGold else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = category.constituentCountText,
                            color = if (isSelected) WallStreetGold.copy(alpha = 0.8f) else TextMuted,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Index Category Description Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TerminalSurfaceElevated.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "📌 ${uiState.selectedIndexCategory.title}: ${uiState.selectedIndexCategory.description}",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Multi-Factor Strategy Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filters.forEach { (key, label) ->
                val isSelected = uiState.selectedScreenerFilter == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) QuantCyanSoft else TerminalSurfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) QuantCyan else TerminalBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.updateScreenerFilter(key) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("screener_filter_$key")
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) QuantCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var sortBy by remember { mutableStateOf("SCORE") }

        val constituentSet = remember(uiState.selectedIndexCategory) {
            StockIndexConstituents.getConstituentSet(uiState.selectedIndexCategory)
        }

        val indexScopedQuotes = remember(uiState.screenerQuotes, uiState.selectedIndexCategory, constituentSet) {
            uiState.screenerQuotes.filter { it.symbol.lowercase() in constituentSet }
        }

        val dcfSignalsBySymbol = remember(
            indexScopedQuotes,
            uiState.retailUniverseKLines,
            uiState.cachedFinancialSnapshots
        ) {
            indexScopedQuotes.associate { quote ->
                val kl = uiState.retailUniverseKLines[quote.symbol].orEmpty()
                val snap = uiState.cachedFinancialSnapshots[quote.symbol]
                quote.symbol to DcfValuationEngine.evaluateScreenerDcf(quote, kl, snap)
            }
        }

        val factorScoresBySymbol = remember(
            indexScopedQuotes,
            uiState.retailUniverseKLines
        ) {
            indexScopedQuotes.associate { quote ->
                val kl = uiState.retailUniverseKLines[quote.symbol].orEmpty()
                quote.symbol to QuantEngine.evaluateFactors(quote, kl)
            }
        }

        val filteredList = remember(
            indexScopedQuotes,
            uiState.selectedIndexCategory,
            uiState.selectedScreenerFilter,
            uiState.retailUniverseKLines,
            uiState.benchmarkKLines,
            dcfSignalsBySymbol,
            factorScoresBySymbol
        ) {
            indexScopedQuotes.filter { quote ->
                val kl = uiState.retailUniverseKLines[quote.symbol].orEmpty()
                val scores = factorScoresBySymbol[quote.symbol] ?: QuantEngine.evaluateFactors(quote, kl)
                val dcfSignal = dcfSignalsBySymbol[quote.symbol]
                when (uiState.selectedScreenerFilter) {
                    "DCF_STRIKE_ZONE" -> dcfSignal?.isMaoStrikeZone == true
                    "DCF_UNDERVALUED" -> dcfSignal?.isDcfUndervalued == true
                    "VCP_STAGE2" -> {
                        val vcp = ProfitabilityEngine.analyzeStageAndVcp(quote, kl, uiState.benchmarkKLines)
                        vcp.isVcpTightSetup || (vcp.stage == TrendStageLabel.STAGE_2_UPTREND && vcp.vcpScore >= 58) ||
                            (kl.isEmpty() && scores.momentumScore >= 68 && quote.amplitude in 0.8..3.8 && quote.changePercent >= 0.0)
                    }
                    "ALPHA_LEADER" -> scores.compositeScore >= 72
                    "DEEP_VALUE" -> quote.peTtm in 0.01..20.0 && quote.pb in 0.01..2.5
                    "HIGH_ROE_MOAT" -> scores.roeEstimatedPercent >= 15.0 && scores.qualityScore >= 72
                    "MOMENTUM_BREAKOUT" -> quote.changePercent >= 1.0 && scores.momentumScore >= 68
                    "LOW_VOL_HEDGE" -> quote.amplitude <= 2.5 && scores.riskControlScore >= 75
                    "TECH_GROWTH" -> quote.peTtm in 15.0..45.0 && scores.growthScore >= 68
                    else -> true
                }
            }
        }

        val sortedList = remember(
            filteredList,
            sortBy,
            uiState.retailUniverseKLines,
            uiState.benchmarkKLines,
            dcfSignalsBySymbol,
            factorScoresBySymbol
        ) {
            when (sortBy) {
                "DCF" -> filteredList.sortedByDescending {
                    dcfSignalsBySymbol[it.symbol]?.marginOfSafetyPercent ?: -999.0
                }
                "VCP" -> filteredList.sortedByDescending {
                    ProfitabilityEngine.analyzeStageAndVcp(
                        it,
                        uiState.retailUniverseKLines[it.symbol].orEmpty(),
                        uiState.benchmarkKLines
                    ).vcpScore
                }
                "CHANGE" -> filteredList.sortedByDescending { it.changePercent }
                "PE" -> filteredList.sortedBy { if (it.peTtm <= 0) 9999.0 else it.peTtm }
                "PB" -> filteredList.sortedBy { if (it.pb <= 0) 9999.0 else it.pb }
                "ROE" -> filteredList.sortedByDescending {
                    factorScoresBySymbol[it.symbol]?.roeEstimatedPercent
                        ?: QuantEngine.evaluateFactors(it, uiState.retailUniverseKLines[it.symbol].orEmpty()).roeEstimatedPercent
                }
                "CAP" -> filteredList.sortedByDescending { it.totalMarketCapHundredMillion }
                "TURNOVER" -> filteredList.sortedByDescending { it.turnoverRate }
                else -> filteredList.sortedByDescending {
                    factorScoresBySymbol[it.symbol]?.compositeScore
                        ?: QuantEngine.evaluateFactors(it, uiState.retailUniverseKLines[it.symbol].orEmpty()).compositeScore
                }
            }
        }

        // Sorting Switcher Row (FlowRow ensures sort pills never clip on narrow/high-density screens)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = if (uiState.isScreenerLoading) {
                    "${uiState.selectedIndexCategory.shortName} 命中${sortedList.size}只 · 同步中"
                } else {
                    "${uiState.selectedIndexCategory.shortName} 命中${sortedList.size}只"
                },
                color = WallStreetGold,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 4.dp)
                    .testTag("screener_hit_count_label")
            )

            listOf(
                "SCORE" to "评分↓",
                "DCF" to "DCF边际↓",
                "VCP" to "VCP紧缩↓",
                "CHANGE" to "涨跌↓",
                "PE" to "估值PE↑",
                "PB" to "市净PB↑",
                "ROE" to "ROE↓",
                "CAP" to "市值↓",
                "TURNOVER" to "换手↓"
            ).forEach { (key, label) ->
                val isSelected = sortBy == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) WallStreetGoldSoft else TerminalSurfaceElevated)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) WallStreetGold else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { sortBy = key }
                        .padding(horizontal = adaptive.chipHorizontalPadding, vertical = 3.dp)
                        .testTag("sort_$key")
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) WallStreetGold else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sortedList.isEmpty()) {
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
                    if (uiState.isScreenerLoading && indexScopedQuotes.isEmpty()) {
                        CircularProgressIndicator(
                            color = WallStreetGold,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "正在同步【${uiState.selectedIndexCategory.shortName}】成分股实时行情...",
                            color = TextSecondary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    } else if (indexScopedQuotes.isEmpty()) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.screenerErrorMessage
                                ?: "【${uiState.selectedIndexCategory.shortName}】成分股行情暂未就绪",
                            color = TextSecondary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.retryScreenerStocks() },
                            colors = ButtonDefaults.buttonColors(containerColor = WallStreetGold, contentColor = TerminalBg),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("retry_screener_stocks_button")
                        ) {
                            Text(text = "重新拉取成分股行情", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "当前策略条件下暂无命中股票",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "【${uiState.selectedIndexCategory.shortName}】共有${indexScopedQuotes.size}只成分股，暂无标的满足该严格量化门槛，可重置为全部或切换其他指数池",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.updateScreenerFilter("ALL") },
                            colors = ButtonDefaults.buttonColors(containerColor = WallStreetGold, contentColor = TerminalBg),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("reset_screener_filter_button")
                        ) {
                            Text(text = "显示全部成分标的", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            var selectedDcfTargetSymbol by remember(uiState.selectedIndexCategory) { mutableStateOf<String?>(null) }
            val dcfTargetQuote = remember(selectedDcfTargetSymbol, sortedList, indexScopedQuotes) {
                sortedList.firstOrNull { it.symbol == selectedDcfTargetSymbol }
                    ?: indexScopedQuotes.firstOrNull { it.symbol == selectedDcfTargetSymbol }
                    ?: sortedList.firstOrNull()
            }
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(dcfTargetQuote?.symbol) {
                dcfTargetQuote?.let { viewModel.ensureScreenerTargetHistory(it.symbol) }
            }

            val dcfHistorySeries = remember(
                dcfTargetQuote,
                uiState.retailUniverseKLines,
                uiState.cachedFinancialSnapshots
            ) {
                dcfTargetQuote?.let { target ->
                    val kl = uiState.retailUniverseKLines[target.symbol].orEmpty()
                    val snap = uiState.cachedFinancialSnapshots[target.symbol]
                    DcfValuationEngine.computeHistoricalDcfCurve(target, kl, snap)
                }
            }

            val rowItems = remember(sortedList, adaptive.gridColumns) {
                sortedList.chunked(adaptive.gridColumns)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("screener_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = if (adaptive.useNavigationRail) 24.dp else 80.dp)
            ) {
                if (dcfHistorySeries != null && dcfTargetQuote != null) {
                    item(key = "screener_dcf_history_chart_item") {
                        ScreenerHistoricalDcfChartCard(
                            curveSeries = dcfHistorySeries,
                            candidateQuotes = sortedList,
                            selectedSymbol = dcfTargetQuote.symbol,
                            onSelectTarget = { sym -> selectedDcfTargetSymbol = sym },
                            onOpenTerminal = { sym ->
                                viewModel.selectStock(sym)
                                onNavigateToTerminal()
                            }
                        )
                    }
                }

                items(
                    items = rowItems,
                    key = { rowGroup -> rowGroup.joinToString(",") { it.symbol } }
                ) { rowGroup ->
                    if (adaptive.gridColumns >= 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowGroup.forEach { quote ->
                                ScreenerStockCard(
                                    quote = quote,
                                    factorScores = factorScoresBySymbol[quote.symbol],
                                    dcfSignal = dcfSignalsBySymbol[quote.symbol],
                                    fontScale = fontScale,
                                    onInspectDcfCurve = {
                                        selectedDcfTargetSymbol = quote.symbol
                                        coroutineScope.launch { listState.animateScrollToItem(0) }
                                    },
                                    onClick = {
                                        viewModel.selectStock(quote.symbol)
                                        onNavigateToTerminal()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowGroup.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        val quote = rowGroup.first()
                        ScreenerStockCard(
                            quote = quote,
                            factorScores = factorScoresBySymbol[quote.symbol],
                            dcfSignal = dcfSignalsBySymbol[quote.symbol],
                            fontScale = fontScale,
                            onInspectDcfCurve = {
                                selectedDcfTargetSymbol = quote.symbol
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                            },
                            onClick = {
                                viewModel.selectStock(quote.symbol)
                                onNavigateToTerminal()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenerStockCard(
    quote: StockQuote,
    factorScores: QuantFactorScores? = null,
    dcfSignal: ScreenerDcfSignal? = null,
    fontScale: Float,
    onInspectDcfCurve: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scores = factorScores ?: remember(quote) { QuantEngine.evaluateFactors(quote, emptyList()) }
    val resolvedDcf = dcfSignal ?: remember(quote) { DcfValuationEngine.evaluateScreenerDcf(quote) }
    val priceColor = when {
        quote.isUp -> StockRed
        quote.isDown -> StockGreen
        else -> TextPrimary
    }

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("screener_item_${quote.code}"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val isNarrow = isNarrowPortrait(maxWidth, fontScale)
            val isUltraNarrow = isUltraNarrowPortrait(maxWidth, fontScale)

            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Title Row: Name, Code, Market, Score & Grade Badge
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
                        Text(
                            text = quote.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(TerminalSurfaceElevated)
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

                    val (gradeText, gradeBg, gradeColor) = when {
                        scores.compositeScore >= 80 -> Triple("S级·强推", StockRedSoft, StockRed)
                        scores.compositeScore >= 70 -> Triple("A级·优选", WallStreetGoldSoft, WallStreetGold)
                        scores.compositeScore >= 55 -> Triple("B级·观望", TerminalSurfaceElevated, TextSecondary)
                        else -> Triple("C级·规避", TerminalSurfaceElevated, TextMuted)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(gradeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${scores.compositeScore}分 · $gradeText",
                            color = gradeColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Price and Change Row (FlowRow prevents collision on 320dp)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "¥${quote.formattedPrice}",
                            color = priceColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = quote.formattedChangePercent,
                            color = priceColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = quote.formattedChange,
                            color = priceColor.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text = "残差Alpha: ${if ((scores.barraExposure?.pureResidualAlphaScore ?: 0) >= 70) "+" else ""}${scores.barraExposure?.pureResidualAlphaScore ?: "--"}分",
                        color = QuantCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Five-Factor Score Breakdown Strips
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    if (isUltraNarrow) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FactorPill(name = "价值", score = scores.valueScore, color = QuantCyan)
                            FactorPill(name = "质量", score = scores.qualityScore, color = WallStreetGold)
                            FactorPill(name = "动量", score = scores.momentumScore, color = StockRed)
                            FactorPill(name = "成长", score = scores.growthScore, color = Color(0xFF10B981))
                            FactorPill(name = "风控", score = scores.riskControlScore, color = Color(0xFFA78BFA))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FactorPill(name = "价值", score = scores.valueScore, color = QuantCyan)
                            FactorPill(name = "质量", score = scores.qualityScore, color = WallStreetGold)
                            FactorPill(name = "动量", score = scores.momentumScore, color = StockRed)
                            FactorPill(name = "成长", score = scores.growthScore, color = Color(0xFF10B981))
                            FactorPill(name = "风控", score = scores.riskControlScore, color = Color(0xFFA78BFA))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3.5 Standalone DCF Valuation Indicator Strip
                val dcfToneColor = when {
                    resolvedDcf.isMaoStrikeZone -> SuccessGreen
                    resolvedDcf.isDcfUndervalued -> QuantCyan
                    resolvedDcf.marginOfSafetyPercent >= 0.0 -> WallStreetGold
                    else -> CriticalRed
                }
                val mosSignStr = "${if (resolvedDcf.marginOfSafetyPercent >= 0) "+" else ""}${resolvedDcf.marginOfSafetyPercent}%"
                val impliedGrowthStr = resolvedDcf.impliedReverseGrowthPercent?.let {
                    "${if (it >= 0) "+" else ""}${it}%"
                } ?: if (resolvedDcf.roicSpreadPercent <= 0.0) "ROIC≤WACC" else ">45%"

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(dcfToneColor.copy(alpha = 0.08f))
                        .border(0.75.dp, dcfToneColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("screener_dcf_strip_${quote.code}"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "DCF·${resolvedDcf.statusLabel}",
                        color = dcfToneColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "公允¥${String.format(java.util.Locale.US, "%.2f", resolvedDcf.intrinsicPrice)}",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "边际$mosSignStr",
                        color = dcfToneColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "隐含$impliedGrowthStr",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "猫:${resolvedDcf.cashQualityGrade}",
                        color = if (resolvedDcf.cashQualityGrade.startsWith("A")) WallStreetGold else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "看估值曲线",
                        color = QuantCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(QuantCyanSoft)
                            .clickable(onClick = onInspectDcfCurve)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                            .testTag("screener_dcf_curve_btn_${quote.code}")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Comprehensive Fundamental Metrics Grid (2x2 on narrow portrait, 1x4 otherwise)
                val peStr = when {
                    quote.peTtm < 0.0 -> "亏损"
                    quote.peTtm == 0.0 -> "--"
                    else -> "${String.format(java.util.Locale.US, "%.1f", quote.peTtm)}倍"
                }
                val pbStr = if (quote.pb <= 0.0) "--" else String.format(java.util.Locale.US, "%.2f", quote.pb)
                val roeStr = String.format(java.util.Locale.US, "%.1f%%", scores.roeEstimatedPercent)
                val capStr = if (quote.totalMarketCapHundredMillion <= 0.0) "--" else formatCap(quote.totalMarketCapHundredMillion)

                if (isNarrow) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "PE: $peStr", color = TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                            Text(text = "PB: $pbStr", color = TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "预估ROE: $roeStr",
                                color = if (scores.roeEstimatedPercent >= 15.0) WallStreetGold else TextMuted,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (scores.roeEstimatedPercent >= 15.0) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(text = "市值: $capStr", color = TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "PE: $peStr", color = TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        Text(text = "PB: $pbStr", color = TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        Text(
                            text = "预估ROE: $roeStr",
                            color = if (scores.roeEstimatedPercent >= 15.0) WallStreetGold else TextMuted,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (scores.roeEstimatedPercent >= 15.0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                        Text(text = "市值: $capStr", color = TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "换手: ${String.format(java.util.Locale.US, "%.2f%%", quote.turnoverRate)}",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "振幅: ${String.format(java.util.Locale.US, "%.2f%%", quote.amplitude)}",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (resolvedDcf.isMaoStrikeZone) {
                            MiniTag(text = "DCF七折击球", color = SuccessGreen)
                        } else if (resolvedDcf.isDcfUndervalued) {
                            MiniTag(text = "DCF低估", color = QuantCyan)
                        }
                        if (quote.pb in 0.01..1.0) {
                            MiniTag(text = "破净折价", color = WallStreetGold)
                        }
                        if (scores.roeEstimatedPercent >= 20.0) {
                            MiniTag(text = "特许权高ROE", color = StockRed)
                        }
                        if (scores.valueScore >= 80) {
                            MiniTag(text = "深度低估", color = QuantCyan)
                        }
                        if (scores.momentumScore >= 75) {
                            MiniTag(text = "动量主升", color = StockRed)
                        }
                        if (scores.riskControlScore >= 82) {
                            MiniTag(text = "低波防守", color = Color(0xFFA78BFA))
                        }
                        if (quote.peTtm > 90.0) {
                            MiniTag(text = "高估值博弈", color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactorPill(name: String, score: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = name, color = TextMuted, fontSize = 9.5.sp, maxLines = 1, softWrap = false)
        Text(
            text = "$score",
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun MiniTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

private fun formatCap(hundredMillion: Double): String {
    return if (hundredMillion >= 10000.0) {
        String.format(java.util.Locale.US, "%.2f万亿", hundredMillion / 10000.0)
    } else {
        String.format(java.util.Locale.US, "%.0f亿", hundredMillion)
    }
}

@Composable
private fun IndexMiniCard(
    quote: StockQuote,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when {
        quote.isUp -> StockRed
        quote.isDown -> StockGreen
        else -> TextPrimary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("index_card_${quote.code}")
    ) {
        Column {
            Text(
                text = quote.name,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quote.formattedPrice,
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = quote.formattedChangePercent,
                    color = color,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
