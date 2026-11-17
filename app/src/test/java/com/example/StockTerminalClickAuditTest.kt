package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.FactorScoreAlertEntity
import com.example.data.local.WatchlistEntity
import com.example.data.model.KLinePoint
import com.example.data.model.MarketRegime
import com.example.data.model.SignalDirection
import com.example.data.model.StockQuote
import com.example.data.remote.StockApiService
import com.example.data.repository.QuantEngine
import com.example.data.repository.StockIndexCategory
import com.example.data.repository.StockRepository
import com.example.ui.viewmodel.StockTerminalViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StockTerminalClickAuditTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: StockRepository
    private lateinit var viewModel: StockTerminalViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(application)
        repository = StockRepository(database)
        viewModel = StockTerminalViewModel(application)
    }

    @Test
    fun `test watchlist add and delete specific stock penetration`() = runBlocking {
        val testSymbol = "sh601127"
        val testName = "赛力斯"

        // 1. Initially not watchlisted
        repository.removeFromWatchlist(testSymbol)
        assertFalse(repository.isWatchlisted(testSymbol))

        // 2. Add to watchlist
        repository.addToWatchlist(testSymbol, testName)
        assertTrue(repository.isWatchlisted(testSymbol))

        val entities = repository.getWatchlistEntities().first()
        val found = entities.find { it.symbol == testSymbol }
        assertNotNull("Watchlist entity must be persisted in Room", found)
        assertEquals(testName, found?.name)
        assertTrue(found?.isNotificationEnabled == true)
        assertEquals(5, found?.notifyThresholdDelta)

        // 3. Remove specific stock from watchlist (penetration audit for delete button click)
        viewModel.removeStockFromWatchlist(testSymbol)
        // Wait brief moment for async coroutine
        kotlinx.coroutines.delay(100)
        assertFalse("Stock must be deleted from watchlist after click", repository.isWatchlisted(testSymbol))
    }

    @Test
    fun `test watchlist notification toggle and threshold cycle penetration`() = runBlocking {
        val testSymbol = "sz300750"
        val testName = "宁德时代"

        repository.addToWatchlist(testSymbol, testName)

        // Toggle notification off
        viewModel.toggleWatchlistNotification(testSymbol, false)
        kotlinx.coroutines.delay(100)
        var stock = database.watchlistDao().getWatchlistStock(testSymbol)
        assertFalse("Notification should be disabled", stock?.isNotificationEnabled ?: true)

        // Toggle notification on
        viewModel.toggleWatchlistNotification(testSymbol, true)
        kotlinx.coroutines.delay(100)
        stock = database.watchlistDao().getWatchlistStock(testSymbol)
        assertTrue("Notification should be enabled", stock?.isNotificationEnabled ?: false)

        // Threshold cycle: 5 -> 8 -> 10 -> 3 -> 5
        viewModel.updateWatchlistThreshold(testSymbol, 8)
        kotlinx.coroutines.delay(50)
        stock = database.watchlistDao().getWatchlistStock(testSymbol)
        assertEquals(8, stock?.notifyThresholdDelta)

        viewModel.updateWatchlistThreshold(testSymbol, 10)
        kotlinx.coroutines.delay(50)
        stock = database.watchlistDao().getWatchlistStock(testSymbol)
        assertEquals(10, stock?.notifyThresholdDelta)

        viewModel.updateWatchlistThreshold(testSymbol, 3)
        kotlinx.coroutines.delay(50)
        stock = database.watchlistDao().getWatchlistStock(testSymbol)
        assertEquals(3, stock?.notifyThresholdDelta)

        // Cleanup
        repository.removeFromWatchlist(testSymbol)
    }

    @Test
    fun `test factor alerts insert mark read and clear all penetration`() = runBlocking {
        // Clear any prior alerts
        database.factorAlertDao().clearAllAlerts()
        assertEquals(0, database.factorAlertDao().getUnreadAlertCount().first())

        // Insert test alert
        val alert = FactorScoreAlertEntity(
            symbol = "sh600519",
            stockName = "贵州茅台",
            oldScore = 80,
            newScore = 86,
            delta = 6,
            oldRating = "跑赢大盘",
            newRating = "强力推荐",
            primaryFactorChanged = "估值回升",
            alertMessage = "综合评分由80分显著跃升至86分(+6分)",
            isRead = false
        )
        database.factorAlertDao().insertAlert(alert)
        assertEquals(1, database.factorAlertDao().getUnreadAlertCount().first())

        val alerts = database.factorAlertDao().getAllAlerts().first()
        assertEquals(1, alerts.size)
        assertEquals("贵州茅台", alerts.first().stockName)

        // Mark as read (simulating openAlertsDialog click)
        database.factorAlertDao().markAllAsRead()
        assertEquals(0, database.factorAlertDao().getUnreadAlertCount().first())

        // Clear all (simulating clearAllAlerts click)
        viewModel.clearAllAlerts()
        kotlinx.coroutines.delay(100)
        assertEquals(0, database.factorAlertDao().getAllAlerts().first().size)
    }

    @Test
    fun `test search dialog query and direct stock detection`() = runBlocking {
        // Open search dialog
        viewModel.openSearchDialog()
        assertTrue(viewModel.uiState.value.isSearchDialogOpen)

        // Search query
        viewModel.searchStocks("600519")
        kotlinx.coroutines.delay(200)
        val results = viewModel.uiState.value.searchResults
        assertTrue("Search should return 贵州茅台", results.any { it.code == "600519" || it.name.contains("茅台") })

        // Select stock and verify dialog closes
        viewModel.selectStock("sh600519")
        assertFalse(viewModel.uiState.value.isSearchDialogOpen)

        // Direct 6-digit code detection logic test
        val code6 = "601127"
        val directMarket = if (code6.startsWith("60") || code6.startsWith("688")) "sh" else "sz"
        assertEquals("sh", directMarket)
        val directSymbol = "$directMarket$code6"
        assertEquals("sh601127", directSymbol)
    }

    @Test
    fun `test screener index categories and strategy filter switching`() = runBlocking {
        // Test all 6 index categories
        StockIndexCategory.entries.forEach { category ->
            val constituents = repository.getIndexConstituents(category)
            assertTrue("Constituents of ${category.shortName} must not be empty", constituents.isNotEmpty())
        }

        // Test category switching in ViewModel
        viewModel.selectIndexCategory(StockIndexCategory.STAR_50)
        assertEquals(StockIndexCategory.STAR_50, viewModel.uiState.value.selectedIndexCategory)

        viewModel.selectIndexCategory(StockIndexCategory.CHINEXT)
        assertEquals(StockIndexCategory.CHINEXT, viewModel.uiState.value.selectedIndexCategory)

        // Test strategy filter selection
        listOf("ALL", "ALPHA_KING", "VALUE_QUALITY", "MOMENTUM", "LOW_VOL").forEach { filter ->
            viewModel.updateScreenerFilter(filter)
            assertEquals(filter, viewModel.uiState.value.selectedScreenerFilter)
        }
    }

    @Test
    fun `test market regime switching in risk monitor`() {
        // Default regime
        assertNotNull(viewModel.uiState.value.marketRegime)

        // Switch to Range Bound
        viewModel.setMarketRegime(MarketRegime.RANGE_BOUND)
        assertEquals(MarketRegime.RANGE_BOUND, viewModel.uiState.value.marketRegime)

        // Verify macro guidance adapts to regime
        val guidance = QuantEngine.computeMacroPositionGuidance(MarketRegime.RANGE_BOUND)
        assertEquals(60, guidance.recommendedStockPositionPercent)
        assertEquals(40, guidance.recommendedCashCushionPercent)

        // Switch to Bear Defensive
        viewModel.setMarketRegime(MarketRegime.BEAR_DEFENSIVE)
        assertEquals(MarketRegime.BEAR_DEFENSIVE, viewModel.uiState.value.marketRegime)

        val bearGuidance = QuantEngine.computeMacroPositionGuidance(MarketRegime.BEAR_DEFENSIVE)
        assertEquals(20, bearGuidance.recommendedStockPositionPercent)
        assertEquals(80, bearGuidance.recommendedCashCushionPercent)
    }

    @Test
    fun `test dynamic weights adjustment and trade recommendation generation`() {
        val testQuote = StockQuote(
            code = "600519",
            market = "sh",
            symbol = "sh600519",
            name = "贵州茅台",
            price = 1580.0,
            yesterdayClose = 1570.0,
            open = 1575.0,
            change = 10.0,
            changePercent = 0.64,
            high = 1590.0,
            low = 1565.0,
            volumeLots = 30000,
            turnoverTenThousand = 474000.0,
            turnoverRate = 0.24,
            peTtm = 24.5,
            pb = 7.8,
            totalMarketCapHundredMillion = 19800.0,
            circMarketCapHundredMillion = 19800.0,
            amplitude = 1.59
        )

        val factors = QuantEngine.evaluateFactors(testQuote, emptyList())
        assertTrue("Moutai must have high quality score", factors.qualityScore >= 80)

        // Recommendation generation with standard dynamic score
        val recDefault = QuantEngine.generateTradeRecommendation(
            quote = testQuote,
            factorScores = factors,
            dynamicScore = 82,
            kLines = emptyList()
        )
        assertEquals(SignalDirection.BUY, recDefault.action)
        assertTrue(recDefault.winRateEstimate >= 60.0)

        // Recommendation generation with low dynamic score (penalized)
        val recBear = QuantEngine.generateTradeRecommendation(
            quote = testQuote,
            factorScores = factors,
            dynamicScore = 40,
            kLines = emptyList()
        )
        assertEquals(SignalDirection.SELL, recBear.action)
    }

    @Test
    fun `test bottom navigation tabs definition`() {
        val tabs = NavigationTab.entries
        assertEquals(5, tabs.size)
        assertEquals("行情中枢", tabs[0].title)
        assertEquals("自选标的", tabs[1].title)
        assertEquals("策略选股", tabs[2].title)
        assertEquals("风险监控", tabs[3].title)
        assertEquals("因子沉淀", tabs[4].title)
    }

    @Test
    fun `qa audit - watchlist Room entity scores must persist non-zero live factors instead of defaulting to 0`() = runBlocking {
        val catlSymbol = "sz300750"
        val catlName = "宁德时代"
        repository.removeFromWatchlist(catlSymbol)
        repository.addToWatchlist(catlSymbol, catlName)

        val initialEntity = database.watchlistDao().getWatchlistStock(catlSymbol)
        assertNotNull(initialEntity)
        assertEquals(0, initialEntity?.lastCompositeScore)

        val catlQuote = StockQuote(
            code = "300750",
            market = "sz",
            symbol = catlSymbol,
            name = catlName,
            price = 240.0,
            yesterdayClose = 246.66,
            open = 244.0,
            change = -6.66,
            changePercent = -2.70,
            high = 245.5,
            low = 238.5,
            volumeLots = 280000,
            turnoverTenThousand = 672000.0,
            turnoverRate = 1.45,
            peTtm = 21.8,
            pb = 4.1,
            totalMarketCapHundredMillion = 10560.0,
            circMarketCapHundredMillion = 9800.0,
            amplitude = 2.84
        )
        val evaluated = QuantEngine.evaluateFactors(catlQuote, emptyList())
        assertTrue("Composite score must be > 0", evaluated.compositeScore > 0)

        repository.updateWatchlistScores(
            symbol = catlSymbol,
            composite = evaluated.compositeScore,
            value = evaluated.valueScore,
            growth = evaluated.growthScore,
            momentum = evaluated.momentumScore,
            quality = evaluated.qualityScore,
            risk = evaluated.riskControlScore,
            rating = evaluated.rating
        )

        val updated = database.watchlistDao().getWatchlistStock(catlSymbol)
        assertNotNull(updated)
        assertEquals(evaluated.compositeScore, updated?.lastCompositeScore)
        assertTrue("Persisted compositeScore must not remain 0", (updated?.lastCompositeScore ?: 0) > 0)
        assertTrue("Persisted valueScore must not remain 0", (updated?.lastValueScore ?: 0) > 0)
        assertTrue("Persisted growthScore must not remain 0", (updated?.lastGrowthScore ?: 0) > 0)
        assertTrue("Persisted momentumScore must not remain 0", (updated?.lastMomentumScore ?: 0) > 0)
        assertTrue("Persisted qualityScore must not remain 0", (updated?.lastQualityScore ?: 0) > 0)
        assertTrue("Persisted rating must be populated", !updated?.lastRating.isNullOrBlank())

        repository.removeFromWatchlist(catlSymbol)
    }

    @Test
    fun `qa audit - T1 stress test supports 20pct ChiNext STAR, 30pct BSE and zero price safety`() {
        val chiNextQuote = StockQuote(
            code = "301001", market = "sz", symbol = "sz301001", name = "创业板测试",
            price = 100.0, yesterdayClose = 100.0, open = 100.0, change = 0.0, changePercent = 0.0,
            high = 101.0, low = 99.0, volumeLots = 1000, turnoverTenThousand = 1000.0,
            turnoverRate = 1.0, peTtm = 20.0, pb = 2.0, totalMarketCapHundredMillion = 50.0,
            circMarketCapHundredMillion = 40.0, amplitude = 2.0
        )
        val t1ChiNext = QuantEngine.computeT1StressTest(chiNextQuote)
        assertEquals(80.0, t1ChiNext.limitDownPrice, 0.01)

        val star689Quote = chiNextQuote.copy(code = "689009", market = "sh", symbol = "sh689009")
        assertEquals(80.0, QuantEngine.computeT1StressTest(star689Quote).limitDownPrice, 0.01)

        val bjQuote = chiNextQuote.copy(code = "830799", market = "bj", symbol = "bj830799")
        assertEquals(70.0, QuantEngine.computeT1StressTest(bjQuote).limitDownPrice, 0.01)

        val zeroPriceQuote = chiNextQuote.copy(price = 0.0)
        val t1Zero = QuantEngine.computeT1StressTest(zeroPriceQuote)
        assertFalse(t1Zero.maxSingleDayLossRMB.isNaN())
        assertFalse(t1Zero.maxSingleDayLossRMB.isInfinite())
        assertFalse(t1Zero.distanceToLimitDownPercent.isNaN())
        assertFalse(t1Zero.distanceToLimitDownPercent.isInfinite())
    }

    @Test
    fun `qa audit - HalfKelly sizing dynamically adapts to stock quality instead of fixed constant`() {
        val highConviction = QuantEngine.computeHalfKellyAllocation(70.0, "2.5 : 1")
        val poorExpectation = QuantEngine.computeHalfKellyAllocation(35.0, "1.1 : 1")
        assertTrue("High conviction Half-Kelly must exceed 0", highConviction.recommendedHalfKellyPercent > 0)
        assertEquals("Negative Kelly expectation must recommend 0% position", 0, poorExpectation.recommendedHalfKellyPercent)
        assertNotEquals(highConviction.recommendedHalfKellyPercent, poorExpectation.recommendedHalfKellyPercent)
    }

    @Test
    fun `qa audit - daily K-lines aggregate into valid weekly and monthly bars with indicators`() {
        val daily = (0 until 45).map { idx ->
            val month = if (idx < 22) "2026-09" else "2026-10"
            val day = String.format("%02d", (idx % 22) + 1)
            val close = 100.0 + idx * 0.5
            KLinePoint(
                date = "$month-$day",
                open = close - 0.5,
                close = close,
                high = close + 1.0,
                low = close - 1.0,
                volume = 10000.0 + idx * 100.0
            )
        }
        val weekly = StockApiService.aggregateDailyToPeriod(daily, "week")
        val monthly = StockApiService.aggregateDailyToPeriod(daily, "month")

        assertTrue("Weekly aggregation must produce multiple bars", weekly.size >= 5)
        assertEquals("Monthly aggregation across 2 months must produce 2 bars", 2, monthly.size)
        assertEquals(daily.first().open, monthly.first().open, 0.001)
        assertEquals(daily.last().close, monthly.last().close, 0.001)
        assertTrue("Weekly MA5 must be computed > 0", weekly.last().ma5 > 0.0)
    }

    @Test
    fun `qa audit - sector mapping resolves common A-shares and indices distinctly`() {
        assertEquals("高端制造/汽配", QuantEngine.getSectorNameForSymbol("sz002050", "三花智控"))
        assertEquals("宽基指数", QuantEngine.getSectorNameForSymbol("sh000001", "上证指数"))
        assertEquals("电力设备", QuantEngine.getSectorNameForSymbol("sz300750", "宁德时代"))
    }
}
