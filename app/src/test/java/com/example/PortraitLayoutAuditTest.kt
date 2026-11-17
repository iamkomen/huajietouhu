package com.example

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WatchlistEntity
import com.example.data.model.ChartPeriod
import com.example.data.model.KLinePoint
import com.example.data.model.MarketRegime
import com.example.data.model.OrderBook
import com.example.data.model.OrderBookTier
import com.example.data.model.StockQuote
import com.example.data.repository.QuantEngine
import com.example.ui.components.MacroPositionBanner
import com.example.ui.components.ObjectiveValidationCard
import com.example.ui.components.OrderBookView
import com.example.ui.components.QuantRadarView
import com.example.ui.components.StockHeaderBar
import com.example.ui.components.TradingChartSection
import com.example.ui.components.VolatilityHeatmapCard
import com.example.ui.screens.ScreenerScreen
import com.example.ui.screens.WatchlistScreen
import com.example.ui.theme.AdaptiveLayoutSpec
import com.example.ui.theme.LocalAdaptiveLayout
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.Theme
import com.example.ui.theme.UiDensityTier
import com.example.ui.theme.isNarrowPortrait
import com.example.ui.theme.isUltraNarrowPortrait
import com.example.ui.viewmodel.StockTerminalViewModel
import com.example.ui.viewmodel.TerminalUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi")
class PortraitLayoutAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var viewModel: StockTerminalViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = StockTerminalViewModel(application)
    }

    private fun sampleQuote(
        symbol: String = "sh600519",
        code: String = "600519",
        market: String = "sh",
        name: String = "贵州茅台",
        price: Double = 1480.00
    ) = StockQuote(
        code = code,
        market = market,
        symbol = symbol,
        name = name,
        price = price,
        yesterdayClose = 1439.10,
        open = 1450.00,
        change = 40.90,
        changePercent = 2.84,
        high = price + 6.00,
        low = price - 15.00,
        volumeLots = 31614,
        turnoverTenThousand = 468000.0,
        turnoverRate = 0.25,
        peTtm = 22.5,
        pb = 6.82,
        totalMarketCapHundredMillion = 18590.0,
        circMarketCapHundredMillion = 18590.0,
        amplitude = 3.12,
        orderBook = OrderBook(
            bids = listOf(OrderBookTier(price - 0.20, 12)),
            asks = listOf(OrderBookTier(price + 0.10, 8))
        ),
        timestamp = "14:58:00"
    )

    private fun sampleKLines(count: Int = 75): List<KLinePoint> {
        return (0 until count).map { i ->
            val base = 1380.0 + i * 1.3 + kotlin.math.sin(i * 0.35) * 18.0
            KLinePoint(
                date = "2026-08-${(i % 28 + 1).toString().padStart(2, '0')}",
                open = base - 4.0,
                close = base + 3.5,
                high = base + 9.0,
                low = base - 8.5,
                volume = 28000.0 + i * 210.0,
                ma5 = base - 1.0,
                ma10 = base - 4.0,
                ma20 = base - 9.0,
                ma60 = base - 18.0
            )
        }
    }

    private fun assertInsideContainerHorizontally(
        node: SemanticsNodeInteraction,
        containerTag: String,
        label: String,
        scrollIntoView: Boolean = false
    ) {
        if (scrollIntoView) {
            node.performScrollTo()
            composeRule.waitForIdle()
        }
        node.assertIsDisplayed()
        val containerBounds = composeRule.onNodeWithTag(containerTag).fetchSemanticsNode().boundsInRoot
        val nodeBounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "$label left (${nodeBounds.left}) clipped outside container (${containerBounds.left})",
            nodeBounds.left >= containerBounds.left - 2f
        )
        assertTrue(
            "$label right (${nodeBounds.right}) overflows container (${containerBounds.right})",
            nodeBounds.right <= containerBounds.right + 2f
        )
        assertTrue(
            "$label height (${nodeBounds.height}) must be positive",
            nodeBounds.height > 0f
        )
    }

    @Test
    fun `test AdaptiveLayoutSpec maps Compact Medium and Expanded WindowSizeClasses correctly`() {
        val compactSpec = AdaptiveLayoutSpec.fromWindowSizeClass(
            WindowSizeClass.calculateFromSize(DpSize(320.dp, 640.dp))
        )
        assertEquals(WindowWidthSizeClass.Compact, compactSpec.widthSizeClass)
        assertTrue(compactSpec.isCompactWidth)
        assertFalse(compactSpec.useNavigationRail)
        assertEquals(1, compactSpec.gridColumns)
        assertEquals(14.dp, compactSpec.screenHorizontalPadding)
        assertEquals(UiDensityTier.STANDARD, compactSpec.densityTier)

        val denseCompactSpec = AdaptiveLayoutSpec.fromWindowSizeClass(
            WindowSizeClass.calculateFromSize(DpSize(360.dp, 720.dp)),
            fontScale = 1.3f,
            screenDensity = 3.5f
        )
        assertEquals(UiDensityTier.COMPACT, denseCompactSpec.densityTier)
        assertEquals(12.dp, denseCompactSpec.screenHorizontalPadding)

        val mediumSpec = AdaptiveLayoutSpec.fromWindowSizeClass(
            WindowSizeClass.calculateFromSize(DpSize(650.dp, 960.dp))
        )
        assertEquals(WindowWidthSizeClass.Medium, mediumSpec.widthSizeClass)
        assertTrue(mediumSpec.isMediumWidth)
        assertTrue(mediumSpec.useNavigationRail)
        assertEquals(2, mediumSpec.gridColumns)
        assertEquals(840.dp, mediumSpec.maxContentWidth)
        assertEquals(UiDensityTier.SPACIOUS, mediumSpec.densityTier)

        val expandedSpec = AdaptiveLayoutSpec.fromWindowSizeClass(
            WindowSizeClass.calculateFromSize(DpSize(900.dp, 1280.dp))
        )
        assertEquals(WindowWidthSizeClass.Expanded, expandedSpec.widthSizeClass)
        assertEquals(WindowHeightSizeClass.Expanded, expandedSpec.heightSizeClass)
        assertTrue(expandedSpec.isExpandedWidth)
        assertTrue(expandedSpec.useNavigationRail)
        assertEquals(2, expandedSpec.gridColumns)
        assertEquals(1140.dp, expandedSpec.maxContentWidth)

        assertTrue(isUltraNarrowPortrait(320.dp, 1.0f))
        assertTrue(isUltraNarrowPortrait(360.dp, 1.3f))
        assertTrue(isNarrowPortrait(390.dp, 1.2f))
        assertFalse(isNarrowPortrait(420.dp, 1.0f))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h840dp-xxhdpi")
    fun `test MainTerminalApp switches NavigationBar on Compact portrait`() {
        composeRule.setContent {
            val compactSizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 840.dp))
            Theme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainTerminalApp(windowSizeClass = compactSizeClass, viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("main_bottom_nav_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("main_nav_rail").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_watchlist").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("volatility_heatmap_card").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w720dp-h1024dp-xhdpi")
    fun `test MainTerminalApp renders NavigationRail and switches all 5 tabs on Medium portrait`() {
        composeRule.setContent {
            val mediumSizeClass = WindowSizeClass.calculateFromSize(DpSize(720.dp, 1024.dp))
            CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 1.15f)) {
                Theme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainTerminalApp(windowSizeClass = mediumSizeClass, viewModel = viewModel)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("main_nav_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("main_bottom_nav_bar").assertDoesNotExist()

        composeRule.onNodeWithTag("tab_watchlist").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("retail_top10_lazy_column").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_screener").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("index_tab_CSI_300").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_risk").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("risk_monitor_lazy_column").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_storage").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("factor_storage_lazy_column").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_terminal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("terminal_lazy_column").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h900dp-hdpi")
    fun `test StockHeaderBar MacroPositionBanner QuantRadar and ObjectiveAudit at 320dp large fontScale`() {
        val quote = sampleQuote()
        val kLines = sampleKLines()
        val factorScores = QuantEngine.evaluateFactors(quote, kLines)
        val auditReport = QuantEngine.computeObjectiveQuantAuditReport(quote, kLines, factorScores)
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
                            .testTag("narrow_audit_root")
                    ) {
                        StockHeaderBar(
                            quote = quote,
                            isWatchlisted = true,
                            onToggleWatchlist = {},
                            onSearchClick = {},
                            onRefreshClick = {}
                        )
                        MacroPositionBanner(
                            quote = quote,
                            marketRegime = MarketRegime.BULL_MOMENTUM,
                            factorScores = factorScores,
                            kLines = kLines
                        )
                        QuantRadarView(
                            factorScores = factorScores,
                            quote = quote,
                            kLines = kLines
                        )
                        ObjectiveValidationCard(report = auditReport)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("stock_header_bar"),
            "narrow_audit_root",
            "StockHeaderBar",
            scrollIntoView = true
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("header_search_button"),
            "narrow_audit_root",
            "HeaderSearchButton"
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("header_watchlist_toggle_button"),
            "narrow_audit_root",
            "HeaderWatchlistToggleButton"
        )
        assertInsideContainerHorizontally(
            composeRule.onAllNodesWithText("1480.00")[0],
            "narrow_audit_root",
            "HeaderPrice"
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("macro_position_card"),
            "narrow_audit_root",
            "MacroPositionBanner",
            scrollIntoView = true
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("factor_spotlight_card"),
            "narrow_audit_root",
            "FactorSpotlightCard",
            scrollIntoView = true
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("quant_trade_advice_card"),
            "narrow_audit_root",
            "QuantTradeAdviceCard",
            scrollIntoView = true
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("objective_validation_card"),
            "narrow_audit_root",
            "ObjectiveValidationCard",
            scrollIntoView = true
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h1000dp-hdpi")
    fun `test VolatilityHeatmapCard switches to unclipped compact 2-column layout at 320dp large fontScale`() {
        val portfolio = QuantEngine.generateRetailTop10Portfolio("BALANCED")
        val compactSizeClass = WindowSizeClass.calculateFromSize(DpSize(320.dp, 1000.dp))
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
                            .height(1000.dp)
                            .background(TerminalBg)
                            .verticalScroll(rememberScrollState())
                            .testTag("heatmap_narrow_root")
                    ) {
                        VolatilityHeatmapCard(
                            portfolio = portfolio,
                            selectedFilterLevel = null,
                            onSelectFilterLevel = {},
                            onSelectStockSlot = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("volatility_heatmap_card"),
            "heatmap_narrow_root",
            "VolatilityHeatmapCard"
        )
        portfolio.stocks.forEach { slot ->
            assertInsideContainerHorizontally(
                composeRule.onNodeWithTag("heatmap_cell_${slot.quote.code}"),
                "heatmap_narrow_root",
                "HeatmapCell(${slot.quote.code})",
                scrollIntoView = true
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h900dp-hdpi")
    fun `test WatchlistScreen Top10 and MyWatchlist tabs stay within 320dp bounds at 1_3x fontScale`() {
        val portfolio = QuantEngine.generateRetailTop10Portfolio("BALANCED")
        val sampleWatchlistQuote = sampleQuote(
            symbol = "sz300750",
            code = "300750",
            market = "sz",
            name = "宁德时代",
            price = 240.00
        ).copy(changePercent = -2.70, change = -6.66)
        val fakeState = TerminalUiState(
            quote = sampleWatchlistQuote,
            retailTop10Portfolio = portfolio,
            selectedRetailStrategy = "BALANCED",
            watchlistEntities = listOf(
                WatchlistEntity(
                    symbol = "sz300750",
                    name = "宁德时代",
                    addTimestamp = 1_700_000_000_000L,
                    notifyThresholdDelta = 5,
                    isNotificationEnabled = true
                )
            ),
            watchlistQuotes = listOf(sampleWatchlistQuote),
            retailUniverseKLines = mapOf("sz300750" to sampleKLines())
        )
        val compactSizeClass = WindowSizeClass.calculateFromSize(DpSize(320.dp, 900.dp))
        val compactSpec = AdaptiveLayoutSpec.fromWindowSizeClass(compactSizeClass, fontScale = 1.3f, screenDensity = 1.5f)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.5f, fontScale = 1.3f),
                LocalAdaptiveLayout provides compactSpec
            ) {
                Theme {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .height(900.dp)
                            .background(TerminalBg)
                            .testTag("watchlist_narrow_root")
                    ) {
                        WatchlistScreen(
                            viewModel = viewModel,
                            uiState = fakeState,
                            onNavigateToTerminal = {},
                            windowSizeClass = compactSizeClass
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("watchlist_add_stock_button"),
            "watchlist_narrow_root",
            "WatchlistAddButton"
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("tab_retail_top10_screener"),
            "watchlist_narrow_root",
            "TabRetailTop10"
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("tab_my_watchlist"),
            "watchlist_narrow_root",
            "TabMyWatchlist"
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("volatility_heatmap_card"),
            "watchlist_narrow_root",
            "Top10Heatmap"
        )
        val firstCode = portfolio.stocks.first().quote.code
        composeRule.onNodeWithTag("retail_top10_lazy_column")
            .performScrollToNode(hasTestTag("retail_stock_card_$firstCode"))
        composeRule.waitForIdle()
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("retail_stock_card_$firstCode"),
            "watchlist_narrow_root",
            "RetailStockCard($firstCode)"
        )

        // Switch to View 1: 我的自选池
        composeRule.onNodeWithTag("tab_my_watchlist").performClick()
        composeRule.waitForIdle()
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("watchlist_stock_card_300750"),
            "watchlist_narrow_root",
            "WatchlistStockCard(300750)"
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h1000dp-xxxhdpi")
    fun `test TradingChartSection OrderBookView and ScreenerScreen prevent clipping at 320dp high density`() {
        val quote = sampleQuote()
        val kLines = sampleKLines()
        val compactSizeClass = WindowSizeClass.calculateFromSize(DpSize(320.dp, 1000.dp))
        val compactSpec = AdaptiveLayoutSpec.fromWindowSizeClass(
            compactSizeClass,
            fontScale = 1.3f,
            screenDensity = 4.0f
        )
        val fakeScreenerState = TerminalUiState(
            quote = quote,
            screenerQuotes = listOf(quote),
            marketIndexQuotes = listOf(quote.copy(code = "000001", symbol = "sh000001", name = "上证指数"))
        )

        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 4.0f, fontScale = 1.3f),
                LocalAdaptiveLayout provides compactSpec
            ) {
                Theme {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .height(1000.dp)
                            .background(TerminalBg)
                            .verticalScroll(rememberScrollState())
                            .testTag("charts_tables_narrow_root")
                    ) {
                        TradingChartSection(
                            quote = quote,
                            minutePoints = emptyList(),
                            dailyKLines = kLines,
                            weeklyKLines = kLines,
                            monthlyKLines = kLines,
                            selectedPeriod = ChartPeriod.DAY,
                            onSelectPeriod = {}
                        )
                        OrderBookView(
                            orderBook = quote.orderBook,
                            yesterdayClose = quote.yesterdayClose
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(700.dp)
                                .testTag("screener_screen_box")
                        ) {
                            ScreenerScreen(
                                viewModel = viewModel,
                                uiState = fakeScreenerState,
                                onNavigateToTerminal = {},
                                windowSizeClass = compactSizeClass
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("trading_chart_card"),
            "charts_tables_narrow_root",
            "TradingChartCard",
            scrollIntoView = true
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("kline_chart_container"),
            "charts_tables_narrow_root",
            "KLineChartContainer"
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("kline_span_30"),
            "charts_tables_narrow_root",
            "KLineSpan30"
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("kline_span_all"),
            "charts_tables_narrow_root",
            "KLineSpanAll"
        )
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("order_book_card"),
            "charts_tables_narrow_root",
            "OrderBookCard",
            scrollIntoView = true
        )
        composeRule.onNodeWithTag("screener_screen_box").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screener_lazy_column")
            .performScrollToNode(hasTestTag("screener_item_600519"))
        composeRule.waitForIdle()
        assertInsideContainerHorizontally(
            composeRule.onNodeWithTag("screener_item_600519"),
            "charts_tables_narrow_root",
            "ScreenerStockCard(600519)"
        )
    }
}
