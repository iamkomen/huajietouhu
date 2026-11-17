package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AlmgrenChrissImpactCost
import com.example.data.model.CornishFisherVaRResult
import com.example.data.model.MarketRegime
import com.example.data.model.StressScenarioResult
import com.example.data.repository.ProfitabilityEngine
import com.example.data.repository.QuantEngine
import com.example.ui.components.MarketBreadthCoachCard
import com.example.ui.components.TradeJournalExpectancyCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.StockTerminalViewModel
import com.example.ui.viewmodel.TerminalUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiskMonitorScreen(
    uiState: TerminalUiState,
    viewModel: StockTerminalViewModel? = null,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = LocalAdaptiveLayout.current.windowSizeClass
) {
    val adaptive = rememberAdaptiveLayoutSpec(windowSizeClass)
    val fontScale = LocalDensity.current.fontScale

    val quote = uiState.quote
    val top10Stocks = uiState.retailTop10Portfolio?.stocks.orEmpty()
    val empiricalPortfolioBeta = if (top10Stocks.isNotEmpty()) {
        val totalW = top10Stocks.sumOf { it.recommendedWeightPercent }.coerceAtLeast(1)
        top10Stocks.sumOf { it.beta * it.recommendedWeightPercent } / totalW.toDouble()
    } else null
    val portfolioExpectedReturn = uiState.retailTop10Portfolio?.expectedAnnualizedReturn

    val tailStressTests = QuantEngine.runHistoricalTailStressTest(uiState.screenerQuotes)
    val hedging = QuantEngine.computeMarketNeutralHedging(
        stocks = uiState.screenerQuotes,
        marketRegime = uiState.marketRegime,
        empiricalPortfolioBeta = empiricalPortfolioBeta,
        expectedPortfolioReturn = portfolioExpectedReturn
    )
    val crowding = if (quote != null) {
        QuantEngine.evaluateSectorCrowding(quote.symbol, quote.name, quote.turnoverRate)
    } else null
    val cfVaR = remember(uiState.kLines, quote) {
        QuantEngine.computeCornishFisherVaR(uiState.kLines, quote?.price ?: 10.0)
    }
    val almgrenChriss = remember(quote) {
        if (quote != null) QuantEngine.computeAlmgrenChrissImpactCost(quote, 1000000.0) else null
    }
    val effectiveBreadth = uiState.marketBreadth ?: remember(uiState.marketIndexQuotes, uiState.screenerQuotes, uiState.marketRegime) {
        ProfitabilityEngine.computeEmpiricalMarketBreadth(
            indexQuotes = uiState.marketIndexQuotes,
            universeQuotes = (uiState.screenerQuotes + uiState.retailUniverseQuotes).distinctBy { it.symbol },
            universeKLines = uiState.retailUniverseKLines,
            fallbackRegime = uiState.marketRegime
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(horizontal = adaptive.screenHorizontalPadding, vertical = 12.dp)
    ) {
        Text(
            text = "硬核风控与极端流动性压力测试",
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "华尔街二级市场尾部风险对冲与流动性踩踏推演",
            color = TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Market Regime Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MarketRegime.entries.forEach { regime ->
                val isSelected = uiState.marketRegime == regime
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) WallStreetGoldSoft else TerminalSurfaceElevated)
                        .border(1.dp, if (isSelected) WallStreetGold else TerminalBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel?.setMarketRegime(regime) }
                        .padding(horizontal = 4.dp, vertical = 7.dp)
                        .testTag("regime_chip_${regime.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = regime.title,
                            color = if (isSelected) WallStreetGold else TextPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = regime.badge,
                            color = if (isSelected) WallStreetGold else TextMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("risk_monitor_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = if (adaptive.useNavigationRail) 24.dp else 80.dp)
        ) {
            // 0. Empirical Market Breadth & Dynamic Exposure Coach
            item {
                MarketBreadthCoachCard(breadth = effectiveBreadth)
            }

            // 0.5 Persistent Trade Journal & Realized R-Multiple Expectancy
            item {
                TradeJournalExpectancyCard(
                    entries = uiState.tradeJournalEntries,
                    stats = uiState.journalStats,
                    onMarkOpened = { id -> viewModel?.markTradeOpened(id) },
                    onCloseTrade = { id, entry, stop, shares, exit ->
                        viewModel?.closeTradeEntry(id, entry, stop, shares, exit)
                    },
                    onDeleteEntry = { id -> viewModel?.deleteTradeEntry(id) }
                )
            }

            // 1. Sector Crowding Index
            if (crowding != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TerminalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "【${crowding.sectorName}】行业筹码拥挤度",
                                    color = WallStreetGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = "${crowding.crowdingIndexPercent}%",
                                    color = if (crowding.isOvercrowded) CriticalRed else SuccessGreen,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "机构情绪: ${crowding.institutionalSentiment} (换手率 Z-Score: ${crowding.turnoverZScore})",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }

            // 2. Market-Neutral Hedging Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalCard),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "中金所股指期货市场中性对冲测算",
                            color = QuantCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "名义超额毛收益", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = "+${hedging.grossExpectedReturn}%", color = WallStreetGold, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "基差贴水成本", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = "${hedging.basisDiscountCost}%", color = StockGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "锁定纯Alpha净收益", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = "+${hedging.netAlphaReturn}%", color = StockRed, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = hedging.hedgeVerdict,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 3 & 4: Cornish-Fisher VaR & Almgren-Chriss (side-by-side on Medium/Expanded, stacked on Compact)
            if (adaptive.gridColumns >= 2 && almgrenChriss != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CornishFisherVaRCard(cfVaR = cfVaR, fontScale = fontScale, modifier = Modifier.weight(1f))
                        AlmgrenChrissCard(almgrenChriss = almgrenChriss, fontScale = fontScale, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                item {
                    CornishFisherVaRCard(cfVaR = cfVaR, fontScale = fontScale, modifier = Modifier.fillMaxWidth())
                }
                if (almgrenChriss != null) {
                    item {
                        AlmgrenChrissCard(almgrenChriss = almgrenChriss, fontScale = fontScale, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // 5. Historical Tail Crisis Stress Tests
            item {
                Text(
                    text = "A股极端黑天鹅危机情景压力测试",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val stressRows = tailStressTests.chunked(adaptive.gridColumns)
            items(stressRows) { rowGroup ->
                if (adaptive.gridColumns >= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowGroup.forEach { test ->
                            TailStressCard(test = test, modifier = Modifier.weight(1f))
                        }
                        if (rowGroup.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    TailStressCard(test = rowGroup.first(), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CornishFisherVaRCard(
    cfVaR: CornishFisherVaRResult,
    fontScale: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CriticalRed.copy(alpha = 0.4f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            val isNarrow = isNarrowPortrait(maxWidth, fontScale)

            Column(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Cornish-Fisher 肥尾在险价值 (VaR/CVaR)",
                        color = CriticalRed,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "偏度 ${cfVaR.skewness} / 峰度 ${cfVaR.excessKurtosis}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (isNarrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "高斯正态 99% VaR", color = TextMuted, fontSize = 10.sp)
                                Text(text = "-${cfVaR.gaussianVaR99}%", color = TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "肥尾修正 99% VaR", color = TextMuted, fontSize = 10.sp)
                                Text(text = "-${cfVaR.cornishFisherVaR99}%", color = CriticalRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "极端条件期望损失 CVaR", color = TextMuted, fontSize = 10.sp)
                            Text(text = "-${cfVaR.expectedShortfallCVaR99}%", color = CriticalRed, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "高斯正态 99% VaR", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "-${cfVaR.gaussianVaR99}%", color = TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "肥尾修正 99% VaR", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "-${cfVaR.cornishFisherVaR99}%", color = CriticalRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(text = "条件损失 CVaR", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "-${cfVaR.expectedShortfallCVaR99}%", color = CriticalRed, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "裁决: ${cfVaR.tailFatnessVerdict}",
                    color = WarningAmber,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlmgrenChrissCard(
    almgrenChriss: AlmgrenChrissImpactCost,
    fontScale: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, QuantCyan.copy(alpha = 0.4f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            val isNarrow = isNarrowPortrait(maxWidth, fontScale)

            Column(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Almgren-Chriss 最优执行 (100万母单)",
                        color = QuantCyan,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "参与率 ${almgrenChriss.participationRatePercent}%",
                        color = WallStreetGold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (isNarrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "半价差成本", color = TextMuted, fontSize = 10.sp)
                                Text(text = "${almgrenChriss.halfSpreadCostBps} bps", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "临时冲击", color = TextMuted, fontSize = 10.sp)
                                Text(text = "${almgrenChriss.temporaryImpactBps} bps", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "永久冲击", color = TextMuted, fontSize = 10.sp)
                                Text(text = "${almgrenChriss.permanentImpactBps} bps", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "综合交易成本", color = TextMuted, fontSize = 10.sp)
                                Text(text = "${almgrenChriss.totalCostBps} bps", color = WarningAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "半价差", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                            Text(text = "${almgrenChriss.halfSpreadCostBps} bps", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "临时冲击", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                            Text(text = "${almgrenChriss.temporaryImpactBps} bps", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "永久冲击", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                            Text(text = "${almgrenChriss.permanentImpactBps} bps", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(text = "综合成本", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                            Text(text = "${almgrenChriss.totalCostBps} bps", color = WarningAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "执行算法推荐: ${almgrenChriss.optimalExecutionHorizon}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TailStressCard(
    test: StressScenarioResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = test.scenarioName,
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
                    text = test.historicalPeriod,
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = "全市场最大回撤: ${test.marketDrawdown}%", color = TextMuted, fontSize = 11.sp)
                Text(text = "策略模拟回撤: ${test.simulatedPortfolioDrawdown}%", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "流动性生存率: ${test.liquiditySurvivalRate}% · ${test.tailRiskVerdict}", color = WallStreetGold, fontSize = 11.sp)
        }
    }
}
