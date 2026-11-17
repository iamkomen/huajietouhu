package com.example

import com.example.data.model.CustomDcfInput
import com.example.data.model.KLinePoint
import com.example.data.model.SignalDirection
import com.example.data.model.StockQuote
import com.example.data.remote.StockApiService
import com.example.data.repository.DcfValuationEngine
import com.example.data.repository.QuantEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AuditRegressionTest {

    private fun buildQuote(
        symbol: String = "sh600519",
        code: String = "600519",
        market: String = "sh",
        name: String = "贵州茅台",
        price: Double = 1480.0,
        yesterdayClose: Double = 1470.0,
        peTtm: Double = 20.0,
        pb: Double = 6.0,
        capB: Double = 18500.0,
        turnoverRate: Double = 0.8,
        amplitude: Double = 1.5,
        changePercent: Double = 0.68
    ) = StockQuote(
        code = code,
        market = market,
        symbol = symbol,
        name = name,
        price = price,
        yesterdayClose = yesterdayClose,
        open = yesterdayClose,
        change = price - yesterdayClose,
        changePercent = changePercent,
        high = price * 1.01,
        low = price * 0.99,
        volumeLots = 30000L,
        turnoverTenThousand = 440000.0,
        turnoverRate = turnoverRate,
        peTtm = peTtm,
        pb = pb,
        totalMarketCapHundredMillion = capB,
        circMarketCapHundredMillion = capB,
        amplitude = amplitude
    )

    @Test
    fun quantRecommendation_noDoubleCurrencySignOnStrongBuy() {
        val quote = buildQuote(
            symbol = "sh600519",
            code = "600519",
            name = "贵州茅台",
            price = 1480.0,
            yesterdayClose = 1450.0,
            peTtm = 18.0,
            pb = 3.2,
            capB = 18500.0,
            turnoverRate = 1.2,
            amplitude = 1.4,
            changePercent = 2.07
        )
        val kLines = (1..30).map { idx ->
            val p = 1350.0 + idx * 4.2
            KLinePoint(
                date = "2026-09-%02d".format(Locale.US, idx),
                open = p - 2.0,
                close = p,
                high = p + 3.0,
                low = p - 3.0,
                volume = 20000.0 + idx * 100.0,
                ma5 = p - 5.0,
                ma10 = p - 12.0,
                ma20 = p - 25.0,
                ma60 = p - 40.0,
                bollUpper = p + 60.0,
                bollMid = p - 25.0,
                bollLower = p - 90.0
            )
        }
        val scores = QuantEngine.evaluateFactors(quote, kLines)
        val rec = QuantEngine.generateTradeRecommendation(quote, scores, dynamicScore = 85, kLines = kLines)

        assertEquals(SignalDirection.BUY, rec.action)
        assertFalse("Risk warning must never contain ¥¥: ${rec.riskWarning}", rec.riskWarning.contains("¥¥"))
        assertTrue(rec.riskWarning.contains("¥"))
    }

    @Test
    fun t1StressTest_enforcesAShareLimitBandsForStStarBseAndMainBoard() {
        val stStock = buildQuote(symbol = "sh600111", code = "600111", name = "*ST示例", price = 10.0, yesterdayClose = 10.0)
        val mainStock = buildQuote(symbol = "sh600036", code = "600036", name = "招商银行", price = 40.0, yesterdayClose = 40.0)
        val starStock = buildQuote(symbol = "sh688981", code = "688981", name = "中芯国际", price = 100.0, yesterdayClose = 100.0)
        val bseStock = buildQuote(symbol = "bj830799", code = "830799", market = "bj", name = "艾融软件", price = 50.0, yesterdayClose = 50.0)

        assertEquals(10.50, QuantEngine.computeT1StressTest(stStock).limitUpPrice, 0.001)
        assertEquals(9.50, QuantEngine.computeT1StressTest(stStock).limitDownPrice, 0.001)

        assertEquals(44.00, QuantEngine.computeT1StressTest(mainStock).limitUpPrice, 0.001)
        assertEquals(36.00, QuantEngine.computeT1StressTest(mainStock).limitDownPrice, 0.001)

        assertEquals(120.00, QuantEngine.computeT1StressTest(starStock).limitUpPrice, 0.001)
        assertEquals(80.00, QuantEngine.computeT1StressTest(starStock).limitDownPrice, 0.001)

        assertEquals(65.00, QuantEngine.computeT1StressTest(bseStock).limitUpPrice, 0.001)
        assertEquals(35.00, QuantEngine.computeT1StressTest(bseStock).limitDownPrice, 0.001)
    }

    @Test
    fun inferMarketFromCode_mapsEtfBondsAndBseAccurately() {
        assertEquals("sh", StockApiService.inferMarketFromCode("510300"))
        assertEquals("sh", StockApiService.inferMarketFromCode("110059"))
        assertEquals("sh", StockApiService.inferMarketFromCode("688981"))
        assertEquals("sz", StockApiService.inferMarketFromCode("300750"))
        assertEquals("sz", StockApiService.inferMarketFromCode("000858"))
        assertEquals("bj", StockApiService.inferMarketFromCode("830799"))
        assertEquals("bj", StockApiService.inferMarketFromCode("920001"))
        assertEquals("bj", StockApiService.inferMarketFromCode("430047"))
    }

    @Test
    fun parseTencentQuotes_computesFallbackChangePercentWhenField32Missing() {
        val fields = MutableList(48) { "0" }
        fields[1] = "中国海油 "
        fields[2] = "600938 "
        fields[3] = "31.50"
        fields[4] = "30.00"
        fields[5] = "30.20"
        fields[6] = "12000"
        fields[31] = ""
        fields[32] = ""
        fields[33] = "31.80"
        fields[34] = "29.90"
        fields[38] = "1.25"
        fields[39] = "9.5"
        fields[43] = ""
        fields[44] = "14000.0"
        fields[45] = "14500.0"
        fields[46] = "2.1"

        val payload = """v_sh600938="${fields.joinToString("~")}";"""
        val parsed = StockApiService.parseTencentQuotes(payload)

        assertEquals(1, parsed.size)
        val q = parsed.first()
        assertEquals("中国海油", q.name)
        assertEquals("600938", q.code)
        assertEquals(5.0, q.changePercent, 0.01)
        assertEquals(1.50, q.change, 0.01)
    }

    @Test
    fun localeSafety_formatsDotDecimalsUnderCommaDecimalDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val quote = buildQuote(price = 123.45, yesterdayClose = 120.00, changePercent = 2.88)
            assertEquals("123.45", quote.formattedPrice)
            assertEquals("+3.45", quote.formattedChange)
            assertEquals("+2.88%", quote.formattedChangePercent)
            assertEquals(123.45, quote.formattedPrice.toDouble(), 0.0001)

            val undervaluedQuote = buildQuote(price = 50.0, yesterdayClose = 49.0, capB = 500.0, changePercent = 2.04)
            val customDcf = DcfValuationEngine.computeCustomDcf(
                quote = undervaluedQuote,
                input = CustomDcfInput(
                    baseFcfBillion = 100.0,
                    stage1GrowthPercent = 8.0,
                    discountRatePercent = 9.0,
                    projectionYears = 5,
                    terminalGrowthPercent = 2.5,
                    netCashBillion = 20.0,
                    safetyDiscountPercent = 0.0
                )
            )
            assertTrue("Expected 全额无折价 in conclusion: ${customDcf.maoConclusion}", customDcf.maoConclusion.contains("全额无折价"))
            assertFalse("Must never show 10.0折", customDcf.maoConclusion.contains("10.0折"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun winRateLowVolThreshold_alignsWith2Point5PercentRule() {
        val quote = buildQuote(
            symbol = "sh601088",
            code = "601088",
            name = "中国神华",
            price = 40.0,
            yesterdayClose = 39.8,
            peTtm = 12.0,
            pb = 1.6,
            capB = 7900.0,
            turnoverRate = 0.6,
            amplitude = 2.4,
            changePercent = 0.5
        )
        val scores = QuantEngine.evaluateFactors(quote, emptyList())
        val stats = QuantEngine.computeHistoricalWinRateStats(quote, "5年全周期", scores, emptyList())
        val lowVol = stats.combinations.first { it.id == "low_vol_dividend" }

        assertTrue("Expected low_vol_dividend to hit when amp=2.4 <= 2.5 and riskControl=${scores.riskControlScore}", lowVol.isHit)
    }
}
