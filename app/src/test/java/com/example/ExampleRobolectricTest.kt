package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.KLinePoint
import com.example.data.model.SignalDirection
import com.example.data.model.StockQuote
import com.example.data.repository.QuantEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("花街投壶", appName)
    }

    @Test
    fun `test loss-making stock receives SELL recommendation and low value score`() {
        val lossQuote = StockQuote(
            code = "688000",
            market = "sh",
            symbol = "sh688000",
            name = "亏损科技",
            price = 15.0,
            yesterdayClose = 15.5,
            open = 15.2,
            change = -0.5,
            changePercent = -3.2,
            high = 15.4,
            low = 14.8,
            volumeLots = 50000,
            turnoverTenThousand = 7500.0,
            turnoverRate = 4.5,
            peTtm = -12.5, // 亏损股
            pb = 4.2,
            totalMarketCapHundredMillion = 80.0,
            circMarketCapHundredMillion = 60.0,
            amplitude = 3.8
        )
        val factors = QuantEngine.evaluateFactors(lossQuote, emptyList())
        assertTrue("Loss-making stock must have low value score", factors.valueScore <= 25)

        val tradeRec = QuantEngine.generateTradeRecommendation(lossQuote, factors)
        assertEquals("Loss-making stock must trigger SELL", SignalDirection.SELL, tradeRec.action)
    }

    @Test
    fun `test significant quantitative dispersion between low-vol dividend and high-PE speculative tech`() {
        // 1. 低波红利防守标的 (如长江电力)
        val defensiveQuote = StockQuote(
            code = "600900",
            market = "sh",
            symbol = "sh600900",
            name = "长江电力",
            price = 28.5,
            yesterdayClose = 28.4,
            open = 28.45,
            change = 0.1,
            changePercent = 0.35,
            high = 28.6,
            low = 28.3,
            volumeLots = 300000,
            turnoverTenThousand = 85000.0,
            turnoverRate = 0.65,
            peTtm = 17.5, // 稳健低估值
            pb = 2.6,
            totalMarketCapHundredMillion = 6900.0,
            circMarketCapHundredMillion = 6900.0,
            amplitude = 1.05 // 极窄振幅
        )

        // 2. 高估值高波动概念炒作标的 (如高位概念股)
        val speculativeQuote = StockQuote(
            code = "688999",
            market = "sh",
            symbol = "sh688999",
            name = "题材炒作",
            price = 120.0,
            yesterdayClose = 126.0,
            open = 125.0,
            change = -6.0,
            changePercent = -4.76,
            high = 128.0,
            low = 118.0,
            volumeLots = 80000,
            turnoverTenThousand = 98000.0,
            turnoverRate = 16.5, // 严重过热换手
            peTtm = 95.0, // PE 95倍严重透支
            pb = 11.5,
            totalMarketCapHundredMillion = 150.0,
            circMarketCapHundredMillion = 120.0,
            amplitude = 7.9 // 极端高振幅
        )

        val defensiveFactors = QuantEngine.evaluateFactors(defensiveQuote, emptyList())
        val speculativeFactors = QuantEngine.evaluateFactors(speculativeQuote, emptyList())

        // 验证估值因子的显著跨度 (差异 >= 35分)
        val valueSpread = defensiveFactors.valueScore - speculativeFactors.valueScore
        assertTrue("Value score must have significant dispersion (was $valueSpread)", valueSpread >= 35)
        assertTrue("Defensive PE score should be high (was ${defensiveFactors.peScore})", defensiveFactors.peScore >= 75)
        assertTrue("Speculative PE score should be low (was ${speculativeFactors.peScore})", speculativeFactors.peScore <= 35)

        // 验证风控因子的显著跨度 (差异 >= 30分)
        val riskSpread = defensiveFactors.riskControlScore - speculativeFactors.riskControlScore
        assertTrue("Risk control score must have wide dispersion (was $riskSpread)", riskSpread >= 30)
        assertTrue("Defensive stock should have high risk score (was ${defensiveFactors.riskControlScore})", defensiveFactors.riskControlScore >= 80)
        assertTrue("Speculative stock should have punished risk score (was ${speculativeFactors.riskControlScore})", speculativeFactors.riskControlScore <= 45)

        // 验证估值偏离度符号与区分度
        assertTrue("Defensive stock has positive valuation cushion", defensiveFactors.valuationSpreadPercent > 0.0)
        assertTrue("Speculative stock has negative valuation cushion", speculativeFactors.valuationSpreadPercent < -100.0)
    }

    @Test
    fun `test factor combinations differentiation`() {
        val highPeQuote = StockQuote(
            code = "300000",
            market = "sz",
            symbol = "sz300000",
            name = "高估值概念股",
            price = 50.0,
            yesterdayClose = 52.0,
            open = 51.0,
            change = -2.0,
            changePercent = -3.85,
            high = 51.5,
            low = 49.0,
            volumeLots = 100000,
            turnoverTenThousand = 50000.0,
            turnoverRate = 18.0,
            peTtm = 88.0,
            pb = 12.0,
            totalMarketCapHundredMillion = 120.0,
            circMarketCapHundredMillion = 100.0,
            amplitude = 4.8
        )
        val factors = QuantEngine.evaluateFactors(highPeQuote, emptyList())
        val winRateStats = QuantEngine.computeHistoricalWinRateStats(
            quote = highPeQuote,
            horizonYears = "5年全周期",
            factorScores = factors,
            kLines = emptyList()
        )

        // Alpha King combo must NOT be hit
        val kingCombo = winRateStats.combinations.first { it.id == "alpha_king" }
        assertFalse("High PE stock should not hit Alpha King", kingCombo.isHit)
        assertTrue("Status text should explain unhit reason", kingCombo.hitStatusText.contains("未命中"))

        // Value combo must NOT be hit due to high PE
        val valueCombo = winRateStats.combinations.first { it.id == "value_quality" }
        assertFalse("High PE stock should not hit Value combination", valueCombo.isHit)
        assertTrue("Status text should explain high PE", valueCombo.hitStatusText.contains("估值偏高") || valueCombo.hitStatusText.contains("未命中"))
    }
}
