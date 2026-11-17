package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CustomDcfInput
import com.example.data.model.DcfTerminalMethod
import com.example.data.model.FinancialStatementSnapshot
import com.example.data.model.KLinePoint
import com.example.data.model.StockQuote
import com.example.data.repository.DcfValuationEngine
import com.example.data.repository.QuantEngine
import com.example.data.repository.StockIndexCategory
import com.example.data.repository.StockIndexConstituents
import com.example.ui.components.DcfCalculatorCard
import com.example.ui.components.WallStreetReportView
import com.example.ui.screens.ScreenerScreen
import com.example.ui.theme.AdaptiveLayoutSpec
import com.example.ui.theme.LocalAdaptiveLayout
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.Theme
import com.example.ui.viewmodel.StockTerminalViewModel
import com.example.ui.viewmodel.TerminalUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h900dp-hdpi")
class DcfValuationObjectiveTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleKLines(): List<KLinePoint> = (0 until 60).map { i ->
        val p = 100.0 + kotlin.math.sin(i * 0.25) * 3.0 + i * 0.1
        KLinePoint(
            date = "2026-08-${(i % 28 + 1).toString().padStart(2, '0')}",
            open = p - 0.5,
            close = p,
            high = p + 1.1,
            low = p - 1.0,
            volume = 25000.0
        )
    }

    @Test
    fun `dcf rejects fake price times 1_15 and differentiates cash cow vs red flag bubble`() {
        val kLines = sampleKLines()

        // 1. 顶级现金奶牛 (高ROIC、高净现比、轻Capex、丰厚净现金)
        val cashCowQuote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "贵州茅台",
            price = 1480.0,
            yesterdayClose = 1470.0,
            open = 1475.0,
            change = 10.0,
            changePercent = 0.68,
            high = 1488.0,
            low = 1468.0,
            volumeLots = 30000,
            turnoverTenThousand = 440000.0,
            peTtm = 20.5,
            pb = 6.5,
            totalMarketCapHundredMillion = 18590.0,
            amplitude = 1.36
        )
        val cashCowF10 = FinancialStatementSnapshot(
            symbol = "sh600519",
            reportPeriod = "2025年报",
            sourceLabel = "东方财富F10财报实盘",
            isLiveAuditedData = true,
            revenueBillion = 1750.0,
            netProfitBillion = 860.0,
            deductedNetProfitBillion = 858.0,
            operatingCashFlowBillion = 960.0,
            capexBillion = 45.0,
            maintenanceCapexBillion = 40.0,
            freeCashFlowBillion = 915.0,
            monetaryCashBillion = 1650.0,
            interestBearingDebtBillion = 10.0,
            netCashBillion = 1640.0,
            accountsReceivableBillion = 12.0,
            inventoryBillion = 480.0,
            contractLiabilityBillion = 195.0,
            goodwillBillion = 0.0,
            equityBillion = 2860.0,
            roicPercent = 29.5,
            roePercent = 31.8,
            grossMarginPercent = 91.5,
            debtToAssetPercent = 18.0,
            cashToEarningsRatio = 1.12,
            capexToOcfPercent = 4.7,
            workingCapitalQualityNote = "先款后货强势占用下游资金"
        )

        val (_, cowAudit, cowDcf) = DcfValuationEngine.evaluateProfessionalDcf(
            quote = cashCowQuote,
            kLines = kLines,
            liveSnapshot = cashCowF10
        )

        assertTrue("Cash cow should earn Grade A", cowAudit.cashQualityGrade.startsWith("A"))
        assertEquals(0.0, cowAudit.waccRiskPenaltyPercent, 0.01)
        assertTrue("Cash cow ROIC spread must be positive", cowAudit.moatRoicSpreadPercent > 15.0)
        assertNotEquals(1480.0 * 1.15, cowDcf.probabilityWeightedPrice, 1.0)
        assertTrue("Cash cow DCF (${cowDcf.probabilityWeightedPrice}) should sit well above 1480", cowDcf.probabilityWeightedPrice > 1480.0)
        assertTrue("Terminal growth capped <= Rf (2.2%)", cowDcf.terminalGrowthPercent <= 2.20)

        // 2. 财务排雷预警股 (纸面富贵 + 吞金兽高Capex + 大存大贷 + 高商誉 + 95倍PE泡沫)
        val bubbleQuote = cashCowQuote.copy(
            code = "300999",
            symbol = "sz300999",
            name = "某高估概念",
            price = 100.0,
            peTtm = 95.0,
            pb = 8.5,
            totalMarketCapHundredMillion = 950.0,
            amplitude = 6.2
        )
        val redFlagF10 = FinancialStatementSnapshot(
            symbol = "sz300999",
            reportPeriod = "2025年报",
            sourceLabel = "东方财富F10财报实盘",
            isLiveAuditedData = true,
            revenueBillion = 60.0,
            netProfitBillion = 10.0,
            deductedNetProfitBillion = 6.0,
            operatingCashFlowBillion = 5.0,
            capexBillion = 4.4,
            maintenanceCapexBillion = 2.6,
            freeCashFlowBillion = 0.6,
            monetaryCashBillion = 40.0,
            interestBearingDebtBillion = 55.0,
            netCashBillion = -15.0,
            accountsReceivableBillion = 22.0,
            inventoryBillion = 15.0,
            contractLiabilityBillion = 0.5,
            goodwillBillion = 35.0,
            equityBillion = 112.0,
            roicPercent = 5.2,
            roePercent = 8.9,
            grossMarginPercent = 21.0,
            debtToAssetPercent = 68.5,
            cashToEarningsRatio = 0.50,
            capexToOcfPercent = 88.0,
            workingCapitalQualityNote = "应收账款占比过高"
        )

        val (_, flagAudit, flagDcf) = DcfValuationEngine.evaluateProfessionalDcf(
            quote = bubbleQuote,
            kLines = kLines,
            liveSnapshot = redFlagF10
        )

        assertTrue("Red-flag stock should earn Grade D", flagAudit.cashQualityGrade.startsWith("D"))
        assertTrue("Red-flag stock must incur WACC governance penalty >= 2.0%", flagAudit.waccRiskPenaltyPercent >= 2.0)
        assertTrue("Red-flag stock must apply cash conversion haircut < 0.85", flagAudit.cashConversionHaircut < 0.85)
        assertTrue("Red-flag list must have >= 3 alerts", flagAudit.redFlags.count { it.isAlert } >= 3)
        assertTrue("Bubble 95x PE intrinsic price (${flagDcf.probabilityWeightedPrice}) must be far below 100.0", flagDcf.probabilityWeightedPrice < 65.0)
        assertTrue("Bubble safety margin (${flagDcf.marginOfSafetyPercent}%) must be negative", flagDcf.marginOfSafetyPercent < -30.0)
    }

    @Test
    fun `wacc monotonicity and reverse dcf round-trip precision`() {
        val kLines = sampleKLines()
        val quote = StockQuote(
            code = "000333",
            market = "sz",
            symbol = "sz000333",
            name = "美的集团",
            price = 72.0,
            yesterdayClose = 71.0,
            open = 71.5,
            change = 1.0,
            changePercent = 1.41,
            high = 72.8,
            low = 70.9,
            volumeLots = 22000,
            turnoverTenThousand = 158000.0,
            peTtm = 13.8,
            pb = 2.8,
            totalMarketCapHundredMillion = 5400.0,
            amplitude = 2.68
        )

        val (_, _, dcfBase) = DcfValuationEngine.evaluateProfessionalDcf(quote, kLines)
        val (_, _, dcfHigherWacc) = DcfValuationEngine.evaluateProfessionalDcf(
            quote = quote,
            kLines = kLines,
            waccAdjustmentPercent = 2.0
        )

        assertTrue(
            "Raising WACC (+2.0%) must strictly decrease weighted fair price (${dcfHigherWacc.probabilityWeightedPrice} < ${dcfBase.probabilityWeightedPrice})",
            dcfHigherWacc.probabilityWeightedPrice < dcfBase.probabilityWeightedPrice
        )
        assertTrue("Bear < Base < Bull ordering must hold", dcfBase.bearIntrinsicPrice < dcfBase.baseIntrinsicPrice && dcfBase.baseIntrinsicPrice < dcfBase.bullIntrinsicPrice)

        // Reverse DCF closed-loop verification:
        val impliedG = dcfBase.impliedReverseGrowthPercent
        assertNotNull("Reasonable PE=13.8 stock must solve finite Reverse DCF growth", impliedG)
        if (impliedG != null && impliedG > -11.5 && impliedG < 44.5) {
            // Re-evaluate Base DCF when stage-1 growth matches impliedG (via growthAdjustmentPercent)
            val delta = impliedG - dcfBase.stage1GrowthPercent
            val (_, _, roundTrip) = DcfValuationEngine.evaluateProfessionalDcf(
                quote = quote,
                kLines = kLines,
                growthAdjustmentPercent = delta
            )
            val relErr = abs(roundTrip.baseIntrinsicPrice - quote.price) / quote.price
            assertTrue(
                "Reverse DCF round-trip error ($relErr) should be < 2% (price=${quote.price}, roundTrip=${roundTrip.baseIntrinsicPrice})",
                relErr < 0.02
            )
        }
    }

    @Test
    fun `financial sector auto-switches to Excess Return Residual Income model`() {
        val bankQuote = StockQuote(
            code = "600036",
            market = "sh",
            symbol = "sh600036",
            name = "招商银行",
            price = 38.0,
            yesterdayClose = 37.8,
            open = 37.9,
            change = 0.2,
            changePercent = 0.53,
            high = 38.2,
            low = 37.6,
            volumeLots = 48000,
            turnoverTenThousand = 182000.0,
            peTtm = 6.4,
            pb = 0.94,
            totalMarketCapHundredMillion = 9580.0,
            amplitude = 1.59
        )
        val report = QuantEngine.generateWallStreetReport(bankQuote, sampleKLines())
        assertTrue("Bank must trigger Excess Return model", report.accountingAudit?.isFinancialSector == true)
        assertTrue("Model label must state Excess Return", report.professionalDcf?.modelTypeLabel?.contains("超额ROE") == true)
        assertEquals(0.0, report.professionalDcf?.netCashAdjustmentBillion ?: -1.0, 0.001)
        assertTrue("Profitable high-ROE bank trading at PB=0.94 should have DCF fair price > current price", (report.professionalDcf?.probabilityWeightedPrice ?: 0.0) > bankQuote.price)
        assertFalse("Bank should not falsely trigger industrial Capex red flag", report.accountingAudit?.redFlags?.any { it.title.contains("吞金兽") } == true)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h900dp-hdpi")
    fun `expanded WallStreetReportView renders unclipped inside 320dp portrait at 1_3x fontScale`() {
        val quote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "贵州茅台",
            price = 1480.0,
            yesterdayClose = 1450.0,
            open = 1460.0,
            change = 30.0,
            changePercent = 2.07,
            high = 1488.0,
            low = 1455.0,
            volumeLots = 31000,
            turnoverTenThousand = 450000.0,
            peTtm = 22.5,
            pb = 6.8,
            totalMarketCapHundredMillion = 18590.0,
            amplitude = 2.28
        )
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
                            .height(900.dp)
                            .background(TerminalBg)
                            .verticalScroll(rememberScrollState())
                            .testTag("dcf_portrait_root")
                    ) {
                        WallStreetReportView(
                            quote = quote,
                            kLines = sampleKLines(),
                            initiallyExpanded = true
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onNodeWithTag("dcf_portrait_root").fetchSemanticsNode().boundsInRoot
        listOf(
            "wall_street_report_card",
            "mao_accounting_audit_box",
            "reverse_dcf_card",
            "dcf_sensitivity_matrix",
            "dcf_interactive_sandbox",
            "dcf_cashflow_projection_table"
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag left clipped (${bounds.left} < ${rootBounds.left})", bounds.left >= rootBounds.left - 2f)
            assertTrue("$tag right overflow (${bounds.right} > ${rootBounds.right})", bounds.right <= rootBounds.right + 2f)
        }
    }

    @Test
    fun `custom dcf calculator satisfies textbook analytical identity in perpetual and exit multiple modes`() {
        val quote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "测试标的",
            price = 100.0,
            yesterdayClose = 99.0,
            open = 99.5,
            change = 1.0,
            changePercent = 1.01,
            high = 101.0,
            low = 98.5,
            volumeLots = 10000,
            turnoverTenThousand = 10000.0,
            peTtm = 10.0,
            pb = 2.5,
            totalMarketCapHundredMillion = 1000.0,
            amplitude = 2.5
        )

        // 1. 永续增长率法解析恒等式: FCF0=100, g=r=10% => 每年折现现值均为100, 5年总现值=500;
        //    PV(TV) = 100 * 1.02 / (0.10 - 0.02) = 1275.00; EV = 1775; NetCash +50 => Equity = 1825
        val perpetualInput = CustomDcfInput(
            baseFcfBillion = 100.0,
            stage1GrowthPercent = 10.0,
            discountRatePercent = 10.0,
            projectionYears = 5,
            terminalMethod = DcfTerminalMethod.PERPETUAL_GROWTH,
            terminalGrowthPercent = 2.0,
            netCashBillion = 50.0,
            safetyDiscountPercent = 30.0
        )
        val perpRes = DcfValuationEngine.computeCustomDcf(quote, perpetualInput)
        assertEquals(5, perpRes.yearlyRows.size)
        assertEquals(500.00, perpRes.pvExplicitFcfBillion, 0.01)
        assertEquals(2053.40, perpRes.terminalValueUndiscountedBillion, 0.01)
        assertEquals(1275.00, perpRes.pvTerminalValueBillion, 0.01)
        assertEquals(1775.00, perpRes.enterpriseValueBillion, 0.01)
        assertEquals(1825.00, perpRes.equityValueBillion, 0.01)
        assertEquals(182.50, perpRes.intrinsicPrice, 0.01)
        assertEquals(127.75, perpRes.maoStrikePrice, 0.01)
        assertEquals(45.2, perpRes.marginOfSafetyPercent, 0.1)

        // 2. 退出倍数法解析恒等式: g=r=10%, Multiple=15x => PV(TV) = 100 * 15 = 1500; EV = 2000
        val multipleInput = CustomDcfInput(
            baseFcfBillion = 100.0,
            stage1GrowthPercent = 10.0,
            discountRatePercent = 10.0,
            projectionYears = 5,
            terminalMethod = DcfTerminalMethod.EXIT_MULTIPLE,
            terminalMultiple = 15.0,
            netCashBillion = 0.0,
            safetyDiscountPercent = 20.0
        )
        val multRes = DcfValuationEngine.computeCustomDcf(quote, multipleInput)
        assertEquals(500.00, multRes.pvExplicitFcfBillion, 0.01)
        assertEquals(1500.00, multRes.pvTerminalValueBillion, 0.01)
        assertEquals(2000.00, multRes.enterpriseValueBillion, 0.01)
        assertEquals(200.00, multRes.intrinsicPrice, 0.01)
        assertEquals(160.00, multRes.maoStrikePrice, 0.01)
    }

    @Test
    fun `custom dcf calculator triggers mao accounting warnings and guards perpetual divergence`() {
        val quote = StockQuote(
            code = "300888",
            market = "sz",
            symbol = "sz300888",
            name = "预警测试",
            price = 50.0,
            yesterdayClose = 50.0,
            open = 50.0,
            change = 0.0,
            changePercent = 0.0,
            high = 51.0,
            low = 49.0,
            volumeLots = 5000,
            turnoverTenThousand = 2500.0,
            peTtm = 40.0,
            pb = 4.0,
            totalMarketCapHundredMillion = 500.0,
            amplitude = 4.0
        )

        // r <= g∞ 防溢出保护 + 高永续增速(>3%) + 激进增长(>20%)
        val aggressive = CustomDcfInput(
            baseFcfBillion = 10.0,
            stage1GrowthPercent = 25.0,
            discountRatePercent = 6.0,
            projectionYears = 5,
            terminalMethod = DcfTerminalMethod.PERPETUAL_GROWTH,
            terminalGrowthPercent = 7.0,
            netCashBillion = 0.0
        )
        val aggRes = DcfValuationEngine.computeCustomDcf(quote, aggressive)
        assertTrue("Intrinsic price must stay finite under r <= g∞", aggRes.intrinsicPrice.isFinite() && aggRes.intrinsicPrice > 0.0)
        assertTrue("Should warn r <= g∞ spread", aggRes.maoWarnings.any { it.contains("防溢出保护") })
        assertTrue("Should warn g∞ > 3%", aggRes.maoWarnings.any { it.contains("永续增速") })
        assertTrue("Should warn g1 > 20%", aggRes.maoWarnings.any { it.contains("显性期复合增速") })

        // 负自由现金流警告
        val negativeFcf = CustomDcfInput(
            baseFcfBillion = -8.0,
            stage1GrowthPercent = 10.0,
            discountRatePercent = 9.0
        )
        val negRes = DcfValuationEngine.computeCustomDcf(quote, negativeFcf)
        assertTrue("Negative FCF must emit Mao cash warning", negRes.maoWarnings.any { it.contains("基期自由现金流 ≤ 0") })
        assertTrue("Negative FCF conclusion must warn exhaustion", negRes.maoConclusion.contains("现金流枯竭"))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h900dp-hdpi")
    fun `DcfCalculatorCard interactive calculation and narrow portrait layout verify`() {
        val quote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "贵州茅台",
            price = 1480.0,
            yesterdayClose = 1450.0,
            open = 1460.0,
            change = 30.0,
            changePercent = 2.07,
            high = 1488.0,
            low = 1455.0,
            volumeLots = 31000,
            turnoverTenThousand = 450000.0,
            peTtm = 22.5,
            pb = 6.8,
            totalMarketCapHundredMillion = 18590.0,
            amplitude = 2.28
        )
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
                            .height(900.dp)
                            .background(TerminalBg)
                            .verticalScroll(rememberScrollState())
                            .testTag("dcf_calc_portrait_root")
                    ) {
                        DcfCalculatorCard(
                            quote = quote,
                            kLines = sampleKLines()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onNodeWithTag("dcf_calc_portrait_root").fetchSemanticsNode().boundsInRoot

        fun readIntrinsicPriceText(): String {
            val node = composeRule.onNodeWithTag("dcf_calc_intrinsic_price").fetchSemanticsNode()
            return node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
        }

        composeRule.onNodeWithTag("dcf_calc_intrinsic_price").performScrollTo()
        composeRule.waitForIdle()
        val initialIntrinsic = readIntrinsicPriceText()
        assertTrue("Initial intrinsic price should start with ¥: $initialIntrinsic", initialIntrinsic.startsWith("¥"))

        // Edit Free Cash Flow input -> Intrinsic value must update proportionally
        composeRule.onNodeWithTag("dcf_input_fcf").performScrollTo()
        composeRule.onNodeWithTag("dcf_input_fcf").performTextReplacement("2000")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dcf_calc_intrinsic_price").performScrollTo()
        composeRule.waitForIdle()
        val updatedIntrinsic = readIntrinsicPriceText()
        assertNotEquals("Editing FCF0 should recalculate intrinsic price", initialIntrinsic, updatedIntrinsic)

        // Toggle Terminal Method to Exit Multiple + Horizon 10Y + Safety 40%
        composeRule.onNodeWithTag("dcf_terminal_mode_multiple").performScrollTo()
        composeRule.onNodeWithTag("dcf_terminal_mode_multiple").performClick()
        composeRule.onNodeWithTag("dcf_years_10").performClick()
        composeRule.onNodeWithTag("dcf_mos_40").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dcf_calc_intrinsic_price").performScrollTo()
        composeRule.waitForIdle()
        val exitMultipleIntrinsic = readIntrinsicPriceText()
        assertNotEquals("Switching to Exit Multiple 10Y should change intrinsic price", updatedIntrinsic, exitMultipleIntrinsic)

        // Expand Year-by-Year Schedule Table
        composeRule.onNodeWithTag("dcf_calc_schedule_toggle").performScrollTo()
        composeRule.onNodeWithTag("dcf_calc_schedule_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dcf_calc_schedule_table").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dcf_calc_schedule_table").assertIsDisplayed()

        // Verify no horizontal clipping at 320dp + 1.3x fontScale
        listOf(
            "dcf_calculator_card",
            "dcf_calc_result_panel",
            "dcf_calc_intrinsic_price",
            "dcf_calc_strike_price",
            "dcf_calc_verdict",
            "dcf_calc_schedule_table"
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag left clipped (${bounds.left} < ${rootBounds.left})", bounds.left >= rootBounds.left - 2f)
            assertTrue("$tag right overflow (${bounds.right} > ${rootBounds.right})", bounds.right <= rootBounds.right + 2f)
        }
    }

    @Test
    fun `evaluateScreenerDcf separates strike-zone cash cows from overvalued stocks`() {
        val kLines = sampleKLines()
        val deepCowQuote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "优质现金牛",
            price = 100.0,
            yesterdayClose = 99.0,
            open = 99.5,
            change = 1.0,
            changePercent = 1.01,
            high = 101.0,
            low = 98.8,
            volumeLots = 25000,
            turnoverTenThousand = 250000.0,
            peTtm = 8.2,
            pb = 2.0,
            totalMarketCapHundredMillion = 1200.0,
            amplitude = 2.2
        )
        val bubbleQuote = deepCowQuote.copy(
            code = "688256",
            symbol = "sh688256",
            name = "高估题材股",
            peTtm = 98.0,
            pb = 14.5
        )

        val cowDcf = DcfValuationEngine.evaluateScreenerDcf(deepCowQuote, kLines)
        val bubbleDcf = DcfValuationEngine.evaluateScreenerDcf(bubbleQuote, kLines)

        assertTrue("Deep cash cow MOS (${cowDcf.marginOfSafetyPercent}%) should qualify for Mao 30% strike zone", cowDcf.isMaoStrikeZone)
        assertTrue(cowDcf.isDcfUndervalued)
        assertEquals("七折击球区", cowDcf.statusLabel)
        assertTrue("Cash cow DCF score (${cowDcf.dcfScreenerScore}) should be high (>=70)", cowDcf.dcfScreenerScore >= 70)

        assertFalse("Bubble stock must NOT qualify for DCF strike zone", bubbleDcf.isMaoStrikeZone)
        assertFalse("Bubble stock must NOT qualify for DCF undervalued", bubbleDcf.isDcfUndervalued)
        assertTrue("Bubble MOS (${bubbleDcf.marginOfSafetyPercent}%) should be negative", bubbleDcf.marginOfSafetyPercent < 0.0)
        assertTrue("Cow DCF score must exceed bubble DCF score", cowDcf.dcfScreenerScore > bubbleDcf.dcfScreenerScore)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h900dp-hdpi")
    fun `screener DCF filter pills, Margin-of-Safety sort, and card DCF strip work at 320dp portrait`() {
        val kLines = sampleKLines()
        val cowQuote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "贵州茅台",
            price = 100.0,
            yesterdayClose = 99.0,
            open = 99.5,
            change = 1.0,
            changePercent = 1.01,
            high = 101.0,
            low = 98.8,
            volumeLots = 25000,
            turnoverTenThousand = 250000.0,
            peTtm = 8.2,
            pb = 2.0,
            totalMarketCapHundredMillion = 1200.0,
            amplitude = 2.2
        )
        val bubbleQuote = cowQuote.copy(
            code = "688256",
            symbol = "sh688256",
            name = "寒武纪",
            peTtm = 98.0,
            pb = 14.5
        )
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StockTerminalViewModel(app)
        val compactSizeClass = WindowSizeClass.calculateFromSize(DpSize(320.dp, 900.dp))
        val compactSpec = AdaptiveLayoutSpec.fromWindowSizeClass(compactSizeClass, fontScale = 1.3f, screenDensity = 1.5f)

        composeRule.setContent {
            val vmState by vm.uiState.collectAsState()
            val testUiState = vmState.copy(
                screenerQuotes = listOf(cowQuote, bubbleQuote),
                retailUniverseKLines = mapOf(cowQuote.symbol to kLines, bubbleQuote.symbol to kLines)
            )
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.5f, fontScale = 1.3f),
                LocalAdaptiveLayout provides compactSpec
            ) {
                Theme {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .height(900.dp)
                            .background(TerminalBg)
                            .testTag("screener_dcf_root")
                    ) {
                        ScreenerScreen(
                            viewModel = vm,
                            uiState = testUiState,
                            onNavigateToTerminal = {},
                            windowSizeClass = compactSizeClass
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onNodeWithTag("screener_dcf_root").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("screener_lazy_column").performScrollToNode(hasTestTag("screener_item_600519"))
        composeRule.waitForIdle()
        val stripBounds = composeRule.onNodeWithTag("screener_dcf_strip_600519", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("DCF strip left clipped", stripBounds.left >= rootBounds.left - 2f)
        assertTrue("DCF strip right overflowed", stripBounds.right <= rootBounds.right + 2f)

        // Filter by standalone DCF 30% Margin-of-Safety strike zone
        composeRule.onNodeWithTag("screener_filter_DCF_STRIKE_ZONE").performScrollTo()
        composeRule.onNodeWithTag("screener_filter_DCF_STRIKE_ZONE").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screener_item_600519").assertIsDisplayed()
        composeRule.onNodeWithTag("screener_item_688256").assertDoesNotExist()

        // Reset to ALL and sort by DCF Margin of Safety descending
        composeRule.onNodeWithTag("screener_filter_ALL").performScrollTo()
        composeRule.onNodeWithTag("screener_filter_ALL").performClick()
        composeRule.onNodeWithTag("sort_DCF").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screener_item_600519").assertIsDisplayed()
        composeRule.onNodeWithTag("screener_lazy_column").performScrollToNode(hasTestTag("screener_item_688256"))
        composeRule.onNodeWithTag("screener_item_688256").assertIsDisplayed()

        // Switch Historical DCF Curve target via card button and verify zero horizontal clipping
        composeRule.onNodeWithTag("screener_dcf_curve_btn_688256").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screener_lazy_column").performScrollToNode(hasTestTag("screener_dcf_history_card"))
        composeRule.waitForIdle()
        val chartBounds = composeRule.onNodeWithTag("screener_dcf_history_card").fetchSemanticsNode().boundsInRoot
        assertTrue("Historical DCF chart card left clipped", chartBounds.left >= rootBounds.left - 2f)
        assertTrue("Historical DCF chart card right overflowed", chartBounds.right <= rootBounds.right + 2f)
        composeRule.onNodeWithTag("screener_dcf_history_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("dcf_history_inspector_bar").assertIsDisplayed()
    }

    @Test
    fun `computeHistoricalDcfCurve anchors latest DCF bands and tracks real K-line prices`() {
        val kLines = sampleKLines()
        val quote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "贵州茅台",
            price = 100.0,
            yesterdayClose = 99.0,
            open = 99.5,
            change = 1.0,
            changePercent = 1.01,
            high = 101.0,
            low = 98.8,
            volumeLots = 25000,
            turnoverTenThousand = 250000.0,
            peTtm = 8.5,
            pb = 2.0,
            totalMarketCapHundredMillion = 1200.0,
            amplitude = 2.2
        )

        val (_, _, proDcf) = DcfValuationEngine.evaluateProfessionalDcf(quote, kLines)
        val series = DcfValuationEngine.computeHistoricalDcfCurve(quote, kLines)

        assertTrue("Expected real K-line flag = true", series.isUsingRealKLines)
        assertEquals(kLines.size, series.points.size)
        val lastPt = series.points.last()
        assertEquals(quote.price, lastPt.closePrice, 0.01)
        assertEquals(proDcf.probabilityWeightedPrice, lastPt.fairPrice, 0.01)
        assertEquals(proDcf.bullIntrinsicPrice, lastPt.bullPrice, 0.01)
        assertEquals(proDcf.bearIntrinsicPrice, lastPt.bearPrice, 0.01)
        assertEquals(lastPt.fairPrice * 0.70, lastPt.strikePrice, 0.02)
        assertTrue(series.valuationPercentileInHistory in 0.0..100.0)

        // Insufficient K-lines gracefully falls back without fabricating points
        val emptySeries = DcfValuationEngine.computeHistoricalDcfCurve(quote, emptyList())
        assertFalse(emptySeries.isUsingRealKLines)
        assertTrue(emptySeries.points.isEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h900dp-xxhdpi")
    fun `switching index category strictly isolates DCF screener constituents without leaking prior index stocks`() {
        val kLines = sampleKLines()
        // CSI_300 only (NOT in STAR_50): 600519, 600036
        val csiOnlyMoutai = StockQuote(
            code = "600519", market = "sh", symbol = "sh600519", name = "贵州茅台",
            price = 100.0, yesterdayClose = 99.0, open = 99.5, change = 1.0, changePercent = 1.01,
            high = 101.0, low = 98.8, volumeLots = 25000, turnoverTenThousand = 250000.0,
            peTtm = 8.2, pb = 2.0, totalMarketCapHundredMillion = 1200.0, amplitude = 2.2
        )
        val csiOnlyCmb = csiOnlyMoutai.copy(
            code = "600036", symbol = "sh600036", name = "招商银行",
            peTtm = 5.6, pb = 0.85
        )
        // STAR_50 only (NOT in CSI_300): 688036 (传音控股)
        val starOnlyTranssion = csiOnlyMoutai.copy(
            code = "688036", symbol = "sh688036", name = "传音控股",
            peTtm = 9.0, pb = 2.1
        )

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StockTerminalViewModel(app)
        val sizeClass = WindowSizeClass.calculateFromSize(DpSize(390.dp, 900.dp))
        val spec = AdaptiveLayoutSpec.fromWindowSizeClass(sizeClass, fontScale = 1.0f, screenDensity = 3.0f)

        composeRule.setContent {
            var activeCategory by remember { mutableStateOf(StockIndexCategory.CSI_300) }
            // Intentionally feed a mixed list containing stale CSI_300 + STAR_50 quotes to verify ScreenerScreen
            // strictly enforces constituent membership of activeCategory on every switch.
            val mixedState = TerminalUiState(
                selectedIndexCategory = activeCategory,
                selectedScreenerFilter = "DCF_STRIKE_ZONE",
                screenerQuotes = listOf(csiOnlyMoutai, csiOnlyCmb, starOnlyTranssion),
                retailUniverseKLines = mapOf(
                    csiOnlyMoutai.symbol to kLines,
                    csiOnlyCmb.symbol to kLines,
                    starOnlyTranssion.symbol to kLines
                )
            )
            CompositionLocalProvider(LocalAdaptiveLayout provides spec) {
                Theme {
                    Column(modifier = Modifier.width(390.dp).height(900.dp).background(TerminalBg)) {
                        androidx.compose.material3.Button(
                            onClick = { activeCategory = StockIndexCategory.STAR_50 },
                            modifier = Modifier.testTag("test_switch_to_star50")
                        ) { androidx.compose.material3.Text("SwitchSTAR50") }
                        androidx.compose.material3.Button(
                            onClick = { activeCategory = StockIndexCategory.CHINEXT },
                            modifier = Modifier.testTag("test_switch_to_chinext")
                        ) { androidx.compose.material3.Text("SwitchCHINEXT") }
                        ScreenerScreen(
                            viewModel = vm,
                            uiState = mixedState,
                            onNavigateToTerminal = {},
                            windowSizeClass = sizeClass
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Under CSI_300: 600519 & 600036 pass DCF_STRIKE_ZONE, while STAR_50-only 688036 must NOT appear
        composeRule.onNodeWithTag("screener_lazy_column").performScrollToNode(hasTestTag("screener_item_600519"))
        composeRule.onNodeWithTag("screener_item_600519").assertIsDisplayed()
        composeRule.onNodeWithTag("screener_item_688036").assertDoesNotExist()

        // Switch to STAR_50 -> 688036 appears, while CSI_300-only 600519 & 600036 must be filtered out
        composeRule.onNodeWithTag("test_switch_to_star50").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screener_lazy_column").performScrollToNode(hasTestTag("screener_item_688036"))
        composeRule.onNodeWithTag("screener_item_688036").assertIsDisplayed()
        composeRule.onNodeWithTag("screener_item_600519").assertDoesNotExist()
        composeRule.onNodeWithTag("screener_item_600036").assertDoesNotExist()

        // Switch to CHINEXT -> none of those 3 belong to CHINEXT, so 0 hits instead of stale CSI_300 13 hits
        composeRule.onNodeWithTag("test_switch_to_chinext").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screener_item_600519").assertDoesNotExist()
        composeRule.onNodeWithTag("screener_item_688036").assertDoesNotExist()

        // ViewModel constituent scoping check: selectIndexCategory immediately purges non-constituent quotes
        vm.selectIndexCategory(StockIndexCategory.STAR_50)
        val starSet = StockIndexConstituents.getConstituentSet(StockIndexCategory.STAR_50)
        assertTrue(
            "ViewModel screenerQuotes must only contain STAR_50 constituents after switch",
            vm.uiState.value.screenerQuotes.all { it.symbol.lowercase() in starSet }
        )
    }
}
