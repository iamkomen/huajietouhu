package com.example

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.data.model.KLinePoint
import com.example.data.model.StockQuote
import com.example.data.remote.StockApiService
import com.example.ui.components.KLineChart
import com.example.ui.theme.LocalAdaptiveLayout
import com.example.ui.theme.Theme
import com.example.ui.theme.rememberAdaptiveLayoutSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi")
class KLineDailyStatusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleQuote(
        price: Double = 104.0,
        yesterdayClose: Double = 100.0,
        open: Double = 105.0,
        high: Double = 106.0,
        low: Double = 103.0,
        volumeLots: Long = 12500L,
        timestamp: String = "20261113150000"
    ) = StockQuote(
        code = "300750",
        market = "sz",
        symbol = "sz300750",
        name = "宁德时代",
        price = price,
        yesterdayClose = yesterdayClose,
        open = open,
        change = price - yesterdayClose,
        changePercent = ((price - yesterdayClose) / yesterdayClose) * 100.0,
        high = high,
        low = low,
        volumeLots = volumeLots,
        turnoverTenThousand = 13000.0,
        timestamp = timestamp
    )

    @Test
    fun sanitizeKLines_filtersInvalidBars_repairsOhlc_andSortsChronologically() {
        val dirty = listOf(
            KLinePoint(date = "2026-11-12", open = 102.0, close = 100.0, high = 101.0, low = 103.0, volume = 500.0),
            KLinePoint(date = "", open = 10.0, close = 10.0, high = 10.0, low = 10.0, volume = 10.0),
            KLinePoint(date = "2026-11-11", open = 98.0, close = Double.NaN, high = 99.0, low = 97.0, volume = 100.0),
            KLinePoint(date = "2026-11-10", open = 99.0, close = 99.0, high = 99.5, low = 98.5, volume = -50.0),
            KLinePoint(date = "2026-11-12", open = 101.0, close = 103.0, high = 102.0, low = 104.0, volume = 800.0)
        )

        val cleaned = StockApiService.sanitizeKLines(dirty)
        assertEquals(2, cleaned.size)
        assertEquals("2026-11-10", cleaned[0].date)
        assertEquals(0.0, cleaned[0].volume, 1e-6)

        val nov12 = cleaned[1]
        assertEquals("2026-11-12", nov12.date)
        assertEquals(103.0, nov12.close, 1e-6)
        assertTrue(nov12.high >= maxOf(nov12.open, nov12.close))
        assertTrue(nov12.low <= minOf(nov12.open, nov12.close))
    }

    @Test
    fun reconcileDailyKLinesWithLiveQuote_updatesSameDayAndAppendsNewTradingDay() {
        val baseHistory = listOf(
            KLinePoint(date = "2026-11-10", open = 96.0, close = 98.0, high = 98.5, low = 95.5, volume = 1000.0),
            KLinePoint(date = "2026-11-11", open = 98.0, close = 99.0, high = 100.0, low = 97.5, volume = 1100.0),
            KLinePoint(date = "2026-11-12", open = 99.0, close = 100.0, high = 101.0, low = 98.5, volume = 1200.0),
            KLinePoint(date = "2026-11-13", open = 101.0, close = 102.0, high = 102.5, low = 100.5, volume = 1300.0)
        )

        // Same day intraday update should overwrite last bar's close/high/low/volume
        val sameDayQuote = sampleQuote(
            price = 104.5,
            yesterdayClose = 100.0,
            open = 101.0,
            high = 105.0,
            low = 100.2,
            volumeLots = 5400L,
            timestamp = "20261113144500"
        )
        val sameDayReconciled = StockApiService.reconcileDailyKLinesWithLiveQuote(baseHistory, sameDayQuote)
        assertEquals(4, sameDayReconciled.size)
        assertEquals(104.5, sameDayReconciled.last().close, 1e-6)
        assertEquals(105.0, sameDayReconciled.last().high, 1e-6)
        assertEquals(100.2, sameDayReconciled.last().low, 1e-6)
        assertEquals(5400.0, sameDayReconciled.last().volume, 1e-6)
        assertTrue(sameDayReconciled.last().ma5 > 0.0)

        // Next trading day with positive volume appends a fresh daily bar
        val nextDayQuote = sampleQuote(
            price = 106.0,
            yesterdayClose = 104.5,
            open = 105.0,
            high = 107.0,
            low = 104.0,
            volumeLots = 3200L,
            timestamp = "2026-11-14 10:15:00"
        )
        val nextDayReconciled = StockApiService.reconcileDailyKLinesWithLiveQuote(sameDayReconciled, nextDayQuote)
        assertEquals(5, nextDayReconciled.size)
        assertEquals("2026-11-14", nextDayReconciled.last().date)
        assertEquals(106.0, nextDayReconciled.last().close, 1e-6)

        // Pre-market / halted 0 volume quote should NOT append a synthetic bar
        val zeroVolNextDay = nextDayQuote.copy(volumeLots = 0L, timestamp = "2026-11-15 09:05:00")
        val afterZeroVol = StockApiService.reconcileDailyKLinesWithLiveQuote(nextDayReconciled, zeroVolNextDay)
        assertEquals(5, afterZeroVol.size)
    }

    @Test
    fun kLineChart_displaysLoadingAndRetryErrorStates() {
        var retryTapped = 0
        val isLoadingState = androidx.compose.runtime.mutableStateOf(true)

        composeRule.setContent {
            val wsc = WindowSizeClass.calculateFromSize(DpSize(360.dp, 800.dp))
            val spec = rememberAdaptiveLayoutSpec(wsc)
            Theme {
                CompositionLocalProvider(LocalAdaptiveLayout provides spec) {
                    val loading = isLoadingState.value
                    KLineChart(
                        kLines = emptyList(),
                        periodTitle = "日K",
                        isLoading = loading,
                        errorMessage = if (!loading) "日线K线暂未返回数据，请点击重试" else null,
                        onRetry = { retryTapped++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("kline_loading_indicator").assertIsDisplayed()

        composeRule.runOnUiThread {
            isLoadingState.value = false
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("kline_error_text").assertIsDisplayed()
        composeRule.onNodeWithTag("kline_retry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("kline_retry_button").performClick()
        assertEquals(1, retryTapped)
    }

    @Test
    fun kLineChart_usesPrevCloseForDailyChangePct_andHandlesFlatPriceBars() {
        val gapUpDayHistory = StockApiService.calculateIndicators(
            listOf(
                KLinePoint(date = "2026-11-12", open = 99.0, close = 100.0, high = 100.5, low = 98.5, volume = 2000.0),
                KLinePoint(date = "2026-11-13", open = 105.0, close = 104.0, high = 106.0, low = 103.5, volume = 4500.0)
            )
        )
        val alignedQuote = sampleQuote(
            price = 104.0,
            yesterdayClose = 100.0,
            open = 105.0,
            high = 106.0,
            low = 103.5,
            volumeLots = 4500L,
            timestamp = "20261113150000"
        )

        composeRule.setContent {
            val wsc = WindowSizeClass.calculateFromSize(DpSize(360.dp, 800.dp))
            val spec = rememberAdaptiveLayoutSpec(wsc)
            Theme {
                CompositionLocalProvider(LocalAdaptiveLayout provides spec) {
                    KLineChart(
                        kLines = gapUpDayHistory,
                        periodTitle = "日K",
                        quote = alignedQuote,
                        isLoading = false
                    )
                }
            }
        }

        composeRule.onNodeWithTag("kline_chart_container").assertIsDisplayed()
        // PrevClose = 100.0, Close = 104.0 => +4.00% (not (104-105)/105 = -0.95%)
        composeRule.onNodeWithTag("kline_change_pct_text").assertTextContains("+4.00%")
        composeRule.onNodeWithTag("kline_status_badge").assertTextContains("盘口已对齐")
    }

    @Test
    fun kLineChart_flatLimitUpOrHaltedBars_renderWithoutNaN() {
        val flatBars = StockApiService.calculateIndicators(
            listOf(
                KLinePoint(date = "2026-11-11", open = 50.0, close = 50.0, high = 50.0, low = 50.0, volume = 1000.0),
                KLinePoint(date = "2026-11-12", open = 50.0, close = 50.0, high = 50.0, low = 50.0, volume = 1200.0),
                KLinePoint(date = "2026-11-13", open = 50.0, close = 50.0, high = 50.0, low = 50.0, volume = 800.0)
            )
        )

        composeRule.setContent {
            val wsc = WindowSizeClass.calculateFromSize(DpSize(360.dp, 800.dp))
            val spec = rememberAdaptiveLayoutSpec(wsc)
            Theme {
                CompositionLocalProvider(LocalAdaptiveLayout provides spec) {
                    KLineChart(
                        kLines = flatBars,
                        periodTitle = "日K",
                        isLoading = false
                    )
                }
            }
        }

        composeRule.onNodeWithTag("kline_chart_container").assertIsDisplayed()
        composeRule.onNodeWithTag("kline_change_pct_text").assertTextContains("+0.00%")
    }
}
