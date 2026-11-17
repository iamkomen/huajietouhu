package com.example

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TradeJournalEntity
import com.example.data.model.*
import com.example.data.repository.ProfitabilityEngine
import com.example.ui.components.MarketBreadthCoachCard
import com.example.ui.components.PreTradeExecutionCard
import com.example.ui.components.TradeJournalExpectancyCard
import com.example.ui.theme.AdaptiveLayoutSpec
import com.example.ui.theme.LocalAdaptiveLayout
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.Theme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h900dp-hdpi")
class ProfitabilityEngineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildQuote(
        symbol: String = "sh600519",
        name: String = "贵州茅台",
        price: Double = 10.0,
        changePercent: Double = 1.2,
        peTtm: Double = 18.0,
        amplitude: Double = 2.0
    ): StockQuote {
        return StockQuote(
            code = symbol.takeLast(6),
            market = symbol.take(2),
            symbol = symbol,
            name = name,
            price = price,
            yesterdayClose = price / (1.0 + changePercent / 100.0),
            open = price * 0.995,
            change = price * (changePercent / 100.0),
            changePercent = changePercent,
            high = price * 1.01,
            low = price * 0.99,
            volumeLots = 50000,
            turnoverTenThousand = 5000.0,
            peTtm = peTtm,
            pb = 2.5,
            totalMarketCapHundredMillion = 800.0,
            amplitude = amplitude
        )
    }

    private fun buildStage2TightVcpKLines(targetPrice: Double = 10.0): List<KLinePoint> {
        val list = mutableListOf<KLinePoint>()
        // 60 days rising steadily from 7.20 to 10.00, with last 5 days tightly consolidated & drying volume
        for (i in 0 until 60) {
            val baseClose = if (i < 55) {
                7.20 + (i / 54.0) * (targetPrice - 7.20)
            } else {
                targetPrice - (59 - i) * 0.01
            }
            val spread = if (i >= 55) 0.08 else 0.35
            val vol = if (i >= 55) 12000.0 else 32000.0
            val ma20 = (baseClose - 0.25).coerceAtLeast(6.5)
            val ma60 = (baseClose - 0.85).coerceAtLeast(6.0)
            list.add(
                KLinePoint(
                    date = "2026-09-%02d".format((i % 28) + 1),
                    open = baseClose - spread * 0.3,
                    close = baseClose,
                    high = baseClose + spread * 0.5,
                    low = baseClose - spread * 0.5,
                    volume = vol,
                    ma5 = baseClose - 0.05,
                    ma10 = baseClose - 0.12,
                    ma20 = ma20,
                    ma60 = ma60,
                    bollUpper = baseClose + 1.20,
                    bollMid = ma20,
                    bollLower = ma20 - 1.20
                )
            )
        }
        return list
    }

    private fun buildFlatBenchmarkKLines(): List<KLinePoint> {
        return (0 until 60).map { i ->
            KLinePoint(
                date = "2026-09-%02d".format((i % 28) + 1),
                open = 3800.0,
                close = 3800.0 + i * 0.5,
                high = 3810.0,
                low = 3790.0,
                volume = 100000.0,
                ma5 = 3800.0,
                ma10 = 3800.0,
                ma20 = 3800.0,
                ma60 = 3790.0
            )
        }
    }

    private fun buildStage4DeclineKLines(price: Double = 8.0): List<KLinePoint> {
        return (0 until 60).map { i ->
            val c = 14.0 - (i / 59.0) * (14.0 - price)
            KLinePoint(
                date = "2026-09-%02d".format((i % 28) + 1),
                open = c + 0.1,
                close = c,
                high = c + 0.25,
                low = c - 0.25,
                volume = 25000.0,
                ma5 = c + 0.2,
                ma10 = c + 0.4,
                ma20 = c + 0.8,
                ma60 = c + 1.8
            )
        }
    }

    @Test
    fun `analyzeStageAndVcp detects Stage-2 tight VCP setup vs Stage-4 downtrend`() {
        val bullQuote = buildQuote(price = 10.0, changePercent = 1.5)
        val tightKLines = buildStage2TightVcpKLines(10.0)
        val benchKLines = buildFlatBenchmarkKLines()

        val vcp = ProfitabilityEngine.analyzeStageAndVcp(bullQuote, tightKLines, benchKLines)
        assertEquals(TrendStageLabel.STAGE_2_UPTREND, vcp.stage)
        assertTrue("Expected tight VCP setup, got score=${vcp.vcpScore}", vcp.isVcpTightSetup)
        assertTrue("Contraction ratio should be <= 0.82, was ${vcp.volatilityContractionRatio}", vcp.volatilityContractionRatio <= 0.82)
        assertTrue("Volume dry-up ratio should be <= 0.90, was ${vcp.volumeDryUpRatio}", vcp.volumeDryUpRatio <= 0.90)
        assertTrue("VCP score should be high (>= 75), got ${vcp.vcpScore}", vcp.vcpScore >= 75)
        assertTrue("Suggested stop must be strictly below price", vcp.suggestedStopPrice in 8.9..9.8)

        val bearQuote = buildQuote(price = 8.0, changePercent = -2.4)
        val bearKLines = buildStage4DeclineKLines(8.0)
        val bearVcp = ProfitabilityEngine.analyzeStageAndVcp(bearQuote, bearKLines, benchKLines)
        assertEquals(TrendStageLabel.STAGE_4_DECLINE, bearVcp.stage)
        assertFalse(bearVcp.isVcpTightSetup)
        assertTrue("Stage-4 score must be capped <= 28", bearVcp.vcpScore <= 28)
    }

    @Test
    fun `computeEmpiricalMarketBreadth enforces exposure limits across strong and crash regimes`() {
        val strongIndices = listOf(
            buildQuote(symbol = "sh000001", name = "上证指数", price = 3350.0, changePercent = 1.1),
            buildQuote(symbol = "sz399001", name = "深证成指", price = 10800.0, changePercent = 1.5)
        )
        val strongUniverse = (1..10).map { idx ->
            buildQuote(symbol = "sh60000$idx", price = 20.0, changePercent = 1.8)
        }
        val strongKLines = strongUniverse.associate { q ->
            q.symbol to buildStage2TightVcpKLines(20.0)
        }

        val strongBreadth = ProfitabilityEngine.computeEmpiricalMarketBreadth(
            indexQuotes = strongIndices,
            universeQuotes = strongUniverse,
            universeKLines = strongKLines
        )
        assertEquals(MarketBreadthRegime.OFFENSIVE, strongBreadth.regime)
        assertEquals(85, strongBreadth.maxPermittedExposurePercent)
        assertEquals(100.0, strongBreadth.advancePercent, 0.1)

        val crashIndices = listOf(
            buildQuote(symbol = "sh000001", name = "上证指数", price = 2900.0, changePercent = -2.6),
            buildQuote(symbol = "sz399001", name = "深证成指", price = 9000.0, changePercent = -3.2)
        )
        val crashUniverse = (1..10).map { idx ->
            buildQuote(symbol = "sz00000$idx", price = 8.0, changePercent = -5.5)
        }
        val crashKLines = crashUniverse.associate { q ->
            q.symbol to buildStage4DeclineKLines(8.0)
        }

        val crashBreadth = ProfitabilityEngine.computeEmpiricalMarketBreadth(
            indexQuotes = crashIndices,
            universeQuotes = crashUniverse,
            universeKLines = crashKLines
        )
        assertEquals(MarketBreadthRegime.CASH_PRESERVATION, crashBreadth.regime)
        assertEquals(15, crashBreadth.maxPermittedExposurePercent)
        assertEquals(100.0, crashBreadth.crashFragilityPercent, 0.1)
    }

    @Test
    fun `buildPreTradeExecutionPlan computes exact 1R board-lot sizing and enforces hard blocks`() {
        val quote = buildQuote(price = 10.0, changePercent = 1.0, peTtm = 22.0)
        val kLines = buildStage2TightVcpKLines(10.0)
        val vcp = ProfitabilityEngine.analyzeStageAndVcp(quote, kLines, buildFlatBenchmarkKLines())
        val offensiveBreadth = MarketBreadthSnapshot(
            breadthScore = 80,
            regime = MarketBreadthRegime.OFFENSIVE,
            sampleCount = 10,
            advancePercent = 80.0,
            aboveMa20Percent = 85.0,
            crashFragilityPercent = 0.0,
            indexUpCount = 2,
            indexTotalCount = 2,
            maxPermittedExposurePercent = 85,
            diagnosticSummary = "Strong"
        )

        // capital=100,000, risk=1.0% -> 1R=1,000, entry=10.0, stop=9.5 -> riskPerShare=0.5 -> 2000 shares (20 lots)
        // positionValue=20,000 (20% <= 25% cap), PASS
        val plan = ProfitabilityEngine.buildPreTradeExecutionPlan(
            quote = quote,
            kLines = kLines,
            accountCapitalRmb = 100000.0,
            riskPerTradePercent = 1.0,
            customStopPrice = 9.5,
            breadthSnapshot = offensiveBreadth,
            vcpAnalysis = vcp
        )
        assertEquals(GateVerdict.PASS, plan.gateVerdict)
        assertEquals(1000.0, plan.riskBudgetRmb, 0.01)
        assertEquals(2000, plan.rawSharesByRisk)
        assertEquals(2000, plan.finalBoardLotShares)
        assertEquals(10.5, plan.target1RPrice, 0.01)
        assertEquals(11.0, plan.target2RPrice, 0.01)
        assertEquals(11.5, plan.target3RPrice, 0.01)

        // 100-share board-lot rounding check: risk=1.0% on 100,000, entry=10.0, stop=9.22 -> riskPerShare=0.78 -> floor(1000/0.78)=1282 -> 1200 shares
        val roundedPlan = ProfitabilityEngine.buildPreTradeExecutionPlan(
            quote = quote,
            kLines = kLines,
            accountCapitalRmb = 100000.0,
            riskPerTradePercent = 1.0,
            customStopPrice = 9.22,
            breadthSnapshot = offensiveBreadth,
            vcpAnalysis = vcp
        )
        assertEquals(1282, roundedPlan.rawSharesByRisk)
        assertEquals(1200, roundedPlan.finalBoardLotShares)

        // Invalid stop >= entry -> BLOCK with 0 shares
        val invalidStopPlan = ProfitabilityEngine.buildPreTradeExecutionPlan(
            quote = quote,
            kLines = kLines,
            accountCapitalRmb = 100000.0,
            riskPerTradePercent = 1.0,
            customStopPrice = 10.5,
            breadthSnapshot = offensiveBreadth,
            vcpAnalysis = vcp
        )
        assertEquals(GateVerdict.BLOCK, invalidStopPlan.gateVerdict)
        assertEquals(0, invalidStopPlan.finalBoardLotShares)

        // CASH_PRESERVATION market breadth -> BLOCK
        val crashBreadth = offensiveBreadth.copy(
            breadthScore = 15,
            regime = MarketBreadthRegime.CASH_PRESERVATION,
            maxPermittedExposurePercent = 15
        )
        val blockedByBreadth = ProfitabilityEngine.buildPreTradeExecutionPlan(
            quote = quote,
            kLines = kLines,
            accountCapitalRmb = 100000.0,
            riskPerTradePercent = 1.0,
            customStopPrice = 9.5,
            breadthSnapshot = crashBreadth,
            vcpAnalysis = vcp
        )
        assertEquals(GateVerdict.BLOCK, blockedByBreadth.gateVerdict)
        assertEquals(0, blockedByBreadth.finalBoardLotShares)
    }

    @Test
    fun `computeJournalStats and Room TradeJournalDao accurately compute R-multiple expectancy`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.tradeJournalDao()

        val id1 = dao.insertEntry(
            TradeJournalEntity(
                symbol = "sh600519",
                stockName = "贵州茅台",
                status = "PLANNED",
                entryPrice = 100.0,
                stopPrice = 95.0,
                targetPrice = 110.0,
                shares = 200,
                plannedRiskRmb = 1000.0,
                gateVerdict = "PASS",
                thesis = "VCP 缩量突破"
            )
        )
        val id2 = dao.insertEntry(
            TradeJournalEntity(
                symbol = "sz300750",
                stockName = "宁德时代",
                status = "OPEN",
                entryPrice = 200.0,
                stopPrice = 190.0,
                targetPrice = 220.0,
                shares = 100,
                plannedRiskRmb = 1000.0,
                gateVerdict = "PASS",
                thesis = "二阶段回踩MA20"
            )
        )
        dao.markOpened(id1)
        // id1 closes at +2.5R (+2500 RMB), id2 closes at -1.0R (-1000 RMB)
        dao.closeTrade(id1, exitPrice = 112.5, pnlRmb = 2500.0, rMultiple = 2.5, closedAt = System.currentTimeMillis())
        dao.closeTrade(id2, exitPrice = 190.0, pnlRmb = -1000.0, rMultiple = -1.0, closedAt = System.currentTimeMillis())

        val entries = dao.getAllEntries().first()
        assertEquals(2, entries.size)

        val stats = ProfitabilityEngine.computeJournalStats(entries)
        assertEquals(2, stats.closedCount)
        assertEquals(50.0, stats.winRatePercent, 0.1)
        assertEquals(2.5, stats.avgWinR, 0.01)
        assertEquals(1.0, stats.avgLossR, 0.01)
        // E(R) = 0.5 * 2.5 - 0.5 * 1.0 = +0.75R
        assertEquals(0.75, stats.expectancyR, 0.01)
        assertEquals(2.5, stats.profitFactor, 0.01)
        assertEquals(1500.0, stats.totalRealizedPnlRmb, 0.01)
        assertEquals(100.0, stats.disciplineCompliancePercent, 0.1)

        dao.deleteEntry(id2)
        assertEquals(1, dao.getAllEntries().first().size)
        db.close()
    }

    @Test
    fun `pre-trade sizer, market breadth coach, and journal render without clipping at 320dp and 1_3x font`() {
        val quote = buildQuote(price = 10.0, changePercent = 1.0, peTtm = 20.0)
        val kLines = buildStage2TightVcpKLines(10.0)
        val bench = buildFlatBenchmarkKLines()
        val breadth = MarketBreadthSnapshot(
            breadthScore = 76,
            regime = MarketBreadthRegime.OFFENSIVE,
            sampleCount = 10,
            advancePercent = 70.0,
            aboveMa20Percent = 80.0,
            crashFragilityPercent = 0.0,
            indexUpCount = 2,
            indexTotalCount = 2,
            maxPermittedExposurePercent = 85,
            diagnosticSummary = "样本 10 只 · 上涨广度 70.0% · 站上MA20占比 80.0%"
        )
        val openEntry = TradeJournalEntity(
            id = 1L,
            symbol = "sh600519",
            stockName = "贵州茅台",
            status = "OPEN",
            entryPrice = 10.0,
            stopPrice = 9.5,
            targetPrice = 11.0,
            shares = 2000,
            plannedRiskRmb = 1000.0,
            gateVerdict = "PASS",
            thesis = "VCP突破实盘计划"
        )
        val stats = ProfitabilityEngine.computeJournalStats(listOf(openEntry))
        var savedShares = 0
        var closedExitCaptured = 0.0

        val compactSizeClass = WindowSizeClass.calculateFromSize(DpSize(320.dp, 900.dp))
        val compactSpec = AdaptiveLayoutSpec.fromWindowSizeClass(compactSizeClass, fontScale = 1.3f, screenDensity = 1.5f)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.5f, fontScale = 1.3f),
                LocalAdaptiveLayout provides compactSpec
            ) {
                Theme {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .verticalScroll(rememberScrollState())
                            .background(TerminalBg)
                            .testTag("profitability_ui_root")
                    ) {
                        MarketBreadthCoachCard(breadth = breadth)
                        PreTradeExecutionCard(
                            quote = quote,
                            kLines = kLines,
                            benchmarkKLines = bench,
                            marketBreadth = breadth,
                            marketRegime = MarketRegime.BULL_MOMENTUM,
                            onSaveTradePlan = { _, _, _, _, _, shares, _, _, _ ->
                                savedShares = shares
                            }
                        )
                        TradeJournalExpectancyCard(
                            entries = listOf(openEntry),
                            stats = stats,
                            onMarkOpened = {},
                            onCloseTrade = { _, _, _, _, exitPrice ->
                                closedExitCaptured = exitPrice
                            },
                            onDeleteEntry = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onNodeWithTag("profitability_ui_root").fetchSemanticsNode().boundsInRoot
        listOf(
            "market_breadth_card",
            "market_breadth_score",
            "pre_trade_execution_card",
            "pre_trade_gate_verdict",
            "pre_trade_board_lot_shares",
            "trade_journal_card"
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            val b = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Left clipped for $tag", b.left >= rootBounds.left - 2f)
            assertTrue("Right overflowed for $tag (nodeRight=${b.right}, rootRight=${rootBounds.right})", b.right <= rootBounds.right + 2f)
        }

        // Update capital to 200,000 and stop to 9.50 -> 1R = 2000, riskPerShare = 0.5 -> 4000 shares (40 lots)
        composeRule.onNodeWithTag("pre_trade_capital_input").performScrollTo()
        composeRule.onNodeWithTag("pre_trade_capital_input").performTextReplacement("200000")
        composeRule.onNodeWithTag("pre_trade_stop_input").performScrollTo()
        composeRule.onNodeWithTag("pre_trade_stop_input").performTextReplacement("9.50")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pre_trade_board_lot_shares").performScrollTo()
        composeRule.onNodeWithTag("pre_trade_board_lot_shares").assertTextContains("4000 股", substring = true)

        // Expand VCP 7-point checklist
        composeRule.onNodeWithTag("pre_trade_vcp_toggle").performScrollTo()
        composeRule.onNodeWithTag("pre_trade_vcp_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pre_trade_vcp_checklist").performScrollTo()
        composeRule.onNodeWithTag("pre_trade_vcp_checklist").assertIsDisplayed()

        // Save plan to Room journal callback
        composeRule.onNodeWithTag("pre_trade_save_plan_btn").performScrollTo()
        composeRule.onNodeWithTag("pre_trade_save_plan_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(4000, savedShares)
        composeRule.onNodeWithTag("pre_trade_saved_banner").performScrollTo()
        composeRule.onNodeWithTag("pre_trade_saved_banner").assertIsDisplayed()

        // Close open trade in TradeJournalExpectancyCard
        composeRule.onNodeWithTag("trade_journal_close_btn_sh600519").performScrollTo()
        composeRule.onNodeWithTag("trade_journal_close_btn_sh600519").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trade_close_exit_input").performTextReplacement("11.25")
        composeRule.onNodeWithTag("trade_close_confirm_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(11.25, closedExitCaptured, 0.01)
    }
}
