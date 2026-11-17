package com.example

import com.example.data.model.KLinePoint
import com.example.data.model.StockQuote
import com.example.data.model.VolatilityRiskLevel
import com.example.data.remote.StockApiService
import com.example.data.repository.QuantEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class QuantObjectiveVerificationTest {

    private fun generateSyntheticKLines(
        days: Int,
        startPrice: Double,
        dailyDrift: Double,
        dailyShockPattern: (Int) -> Double
    ): List<KLinePoint> {
        val raw = ArrayList<KLinePoint>(days)
        var price = startPrice
        for (i in 0 until days) {
            val ret = dailyDrift + dailyShockPattern(i)
            val prev = price
            val close = prev * exp(ret)
            val open = prev * exp(ret * 0.25)
            val high = maxOf(open, close) * 1.01
            val low = minOf(open, close) * 0.99
            val dateStr = String.format("2026-%02d-%02d", 1 + (i / 28), 1 + (i % 28))
            raw.add(
                KLinePoint(
                    date = dateStr,
                    open = open,
                    close = close,
                    high = high,
                    low = low,
                    volume = 100000.0 + i * 500.0
                )
            )
            price = close
        }
        return StockApiService.calculateIndicators(raw)
    }

    @Test
    fun `external standard 1 - exact EMA12 EMA26 MACD(12,26,9) Wilder RSI6 and BOLL20 parity`() {
        val raw = (0 until 45).map { i ->
            val close = 100.0 + i * 0.6 + kotlin.math.sin(i * 0.4) * 2.5
            KLinePoint(
                date = String.format("2026-%02d-%02d", 1 + i / 28, 1 + i % 28),
                open = close - 0.3,
                close = close,
                high = close + 0.8,
                low = close - 0.8,
                volume = 50000.0
            )
        }
        val computed = StockApiService.calculateIndicators(raw)
        assertEquals(raw.size, computed.size)

        // Independent canonical TA-Lib / 同花顺 textbook calculation
        var ema12 = raw[0].close
        var ema26 = raw[0].close
        var dea = 0.0
        var avgGain = 0.0
        var avgLoss = 0.0
        val k12 = 2.0 / 13.0
        val k26 = 2.0 / 27.0
        val k9 = 2.0 / 10.0

        for (i in raw.indices) {
            val c = raw[i].close
            if (i > 0) {
                ema12 = c * k12 + ema12 * (1.0 - k12)
                ema26 = c * k26 + ema26 * (1.0 - k26)
            }
            val dif = ema12 - ema26
            dea = if (i == 0) dif else (dif * k9 + dea * (1.0 - k9))
            val bar = 2.0 * (dif - dea)

            assertEquals("MACD DIF mismatch at bar $i", dif, computed[i].macdDif, 1e-4)
            assertEquals("MACD DEA mismatch at bar $i", dea, computed[i].macdDea, 1e-4)
            assertEquals("MACD BAR mismatch at bar $i", bar, computed[i].macdBar, 1e-4)

            if (i > 0) {
                val delta = c - raw[i - 1].close
                val g = if (delta > 0) delta else 0.0
                val l = if (delta < 0) -delta else 0.0
                if (i <= 6) {
                    avgGain += g / 6.0
                    avgLoss += l / 6.0
                } else {
                    avgGain = (avgGain * 5.0 + g) / 6.0
                    avgLoss = (avgLoss * 5.0 + l) / 6.0
                }
                if (i >= 6) {
                    val expectedRsi = if (avgLoss < 1e-9) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
                    assertEquals("Wilder RSI(6) mismatch at bar $i", expectedRsi, computed[i].rsi6, 1e-4)
                }
            }

            if (i >= 19) {
                val win = raw.subList(i - 19, i + 1).map { it.close }
                val mid = win.average()
                val sd = sqrt(win.sumOf { (it - mid).pow(2) } / 20.0)
                assertEquals("BOLL Mid mismatch at bar $i", mid, computed[i].bollMid, 1e-4)
                assertEquals("BOLL Upper mismatch at bar $i", mid + 2.0 * sd, computed[i].bollUpper, 1e-4)
                assertEquals("BOLL Lower mismatch at bar $i", mid - 2.0 * sd, computed[i].bollLower, 1e-4)
            }
        }
    }

    @Test
    fun `external standard 2 - exact log volatility HV20 Parkinson vol and OLS CAPM Beta = 2_0`() {
        val bench = generateSyntheticKLines(40, 3500.0, 0.0005) { i ->
            if (i % 2 == 0) 0.012 else -0.008
        }
        // Construct stock whose daily log return is exactly 2.0x benchmark daily log return
        val doubleBetaStock = generateSyntheticKLines(40, 100.0, 0.0010) { i ->
            2.0 * (if (i % 2 == 0) 0.012 else -0.008)
        }

        val beta = QuantEngine.computeEmpiricalBeta(doubleBetaStock, bench)
        assertNotNull("Empirical OLS Beta must not be null", beta)
        assertEquals("CAPM Beta must equal 2.0 for 2x return asset", 2.0, beta!!, 0.05)

        val hv20 = QuantEngine.computeAnnualizedLogVolatility(bench, 20)
        assertNotNull("HV20 must not be null", hv20)
        // Independent sample std calculation
        val last20 = bench.takeLast(21)
        val rets = (1 until last20.size).map { ln(last20[it].close / last20[it - 1].close) }
        val mean = rets.average()
        val expectedHv = sqrt(rets.sumOf { (it - mean).pow(2) } / (rets.size - 1)) * sqrt(252.0) * 100.0
        assertEquals("HV20 must match Bessel-corrected annualized std", expectedHv, hv20!!, 0.2)

        val parkinson = QuantEngine.computeParkinsonVolatility(102.0, 98.0)
        assertNotNull(parkinson)
        val lnHl = ln(102.0 / 98.0)
        val expectedParkinson = sqrt((lnHl * lnHl) / (4.0 * ln(2.0))) * sqrt(252.0) * 100.0
        assertEquals(expectedParkinson, parkinson!!, 0.2)
    }

    @Test
    fun `external standard 3 - Spearman Rank IC with ties and Student t-test p-value bounds`() {
        val x = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0)
        val yPos = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val yNeg = yPos.reversed()

        assertEquals(1.0, QuantEngine.computeSpearmanRankCorrelation(x, yPos), 1e-6)
        assertEquals(-1.0, QuantEngine.computeSpearmanRankCorrelation(x, yNeg), 1e-6)

        val (tStrong, pStrong) = QuantEngine.computeStudentTAndPValue(0.85, 25)
        assertTrue("t-stat for r=0.85, N=25 must exceed 7.0", tStrong > 7.0)
        assertTrue("p-value for r=0.85, N=25 must be < 0.001", pStrong < 0.001)

        val (tZero, pZero) = QuantEngine.computeStudentTAndPValue(0.0, 25)
        assertEquals(0.0, tZero, 1e-6)
        assertEquals(1.0, pZero, 1e-6)
    }

    @Test
    fun `external standard 4 - Bailey and Lopez de Prado Deflated Sharpe Ratio penalizes snooping and negative skew`() {
        val dsrLowTrials = QuantEngine.computeDeflatedSharpeRatio(
            nominalSharpe = 1.35,
            nTrials = 5,
            sampleObservationsT = 120,
            skewness = 0.0,
            excessKurtosis = 0.0
        )
        val dsrManyTrialsNegSkew = QuantEngine.computeDeflatedSharpeRatio(
            nominalSharpe = 1.35,
            nTrials = 250,
            sampleObservationsT = 120,
            skewness = -1.2,
            excessKurtosis = 4.0
        )
        assertTrue(
            "More backtest trials must deflate DSR more heavily",
            dsrManyTrialsNegSkew.deflatedSharpeRatio < dsrLowTrials.deflatedSharpeRatio
        )
        assertTrue(
            "PSR must decrease under higher trials + tail risk",
            dsrManyTrialsNegSkew.probabilisticSharpePercent < dsrLowTrials.probabilisticSharpePercent
        )

        val dsrWeakStrategy = QuantEngine.computeDeflatedSharpeRatio(
            nominalSharpe = 0.65,
            nTrials = 100,
            sampleObservationsT = 120
        )
        assertFalse(
            "Weak Sharpe=0.65 over 100 trials must NOT be marked statistically significant",
            dsrWeakStrategy.isStatisticallySignificant
        )
    }

    @Test
    fun `external standard 5 - unfiltered physical data audit refuses to clamp fake VWAP or broken OHLC`() {
        val cleanQuote = StockQuote(
            code = "600519", market = "sh", symbol = "sh600519", name = "贵州茅台",
            price = 1500.0, yesterdayClose = 1490.0, open = 1495.0,
            change = 10.0, changePercent = 0.67, high = 1510.0, low = 1485.0,
            volumeLots = 10000L, turnoverTenThousand = 150000.0, // Exact VWAP = 1500.0
            turnoverRate = 0.5, peTtm = 22.0, pb = 6.5,
            totalMarketCapHundredMillion = 18800.0, circMarketCapHundredMillion = 18800.0, amplitude = 1.68
        )
        val cleanKLines = generateSyntheticKLines(60, 1450.0, 0.001) { i ->
            if (i % 3 == 0) 0.008 else -0.003
        }
        val scores = QuantEngine.evaluateFactors(cleanQuote, cleanKLines)
        val cleanReport = QuantEngine.computeObjectiveQuantAuditReport(cleanQuote, cleanKLines, scores)

        assertTrue(cleanReport.dataIntegrityAudit.highLowPriceConstraintCheck)
        assertTrue(cleanReport.dataIntegrityAudit.kLineChronologicalCheck)
        assertEquals(0.0, cleanReport.dataIntegrityAudit.vwapReconciliationErrorBps, 0.01)
        assertTrue(cleanReport.walkForwardProof.zeroLookaheadCertified)

        // Broken quote: price > high AND huge VWAP mismatch (must NOT clamp to 4.5 bps!)
        val corruptedQuote = cleanQuote.copy(
            price = 1600.0,
            high = 1510.0,
            turnoverTenThousand = 150000.0 // VWAP=1500 vs price=1600 -> 625 bps error!
        )
        val corruptedReport = QuantEngine.computeObjectiveQuantAuditReport(corruptedQuote, cleanKLines, scores)
        assertFalse(corruptedReport.dataIntegrityAudit.highLowPriceConstraintCheck)
        assertTrue(
            "VWAP reconciliation error must NOT be artificially clamped below 500 bps",
            corruptedReport.dataIntegrityAudit.vwapReconciliationErrorBps > 500.0
        )
        assertTrue(corruptedReport.overallReliabilityGrade.contains("⚠️"))
    }

    @Test
    fun `external standard 6 - Constrained Retail Top-10 Optimizer and Markowitz Covariance Invariants`() {
        val bench = generateSyntheticKLines(50, 3500.0, 0.0006) { i ->
            kotlin.math.sin(i * 0.45) * 0.011
        }
        val universeSymbols = QuantEngine.getRetailUniverseSymbols()
        val kLinesMap = universeSymbols.mapIndexed { idx, sym ->
            sym to generateSyntheticKLines(50, 20.0 + idx * 5.0, 0.0005 + idx * 0.00005) { i ->
                kotlin.math.sin(i * 0.5 + idx * 1.1) * (0.010 + (idx % 4) * 0.005)
            }
        }.toMap()

        for (strategy in listOf("BALANCED", "HIGH_DIVIDEND", "TECH_GROWTH", "TURNAROUND")) {
            val portfolio = QuantEngine.generateRetailTop10Portfolio(
                strategyKey = strategy,
                quotesMap = emptyMap(),
                kLinesMap = kLinesMap,
                benchmarkKLines = bench
            )

            assertEquals("$strategy must contain exactly 10 stocks", 10, portfolio.stocks.size)
            assertEquals(80, portfolio.recommendedTotalStockWeight)
            assertEquals(20, portfolio.cashCushionWeight)
            assertEquals(80, portfolio.stocks.sumOf { it.recommendedWeightPercent })
            assertTrue(
                "$strategy single-stock weight must be in [5, 12]",
                portfolio.stocks.all { it.recommendedWeightPercent in 5..12 }
            )

            val maxPerSector = portfolio.stocks.groupingBy { it.sectorName }.eachCount().values.maxOrNull() ?: 0
            assertTrue("$strategy must have <= 2 stocks per sector (got $maxPerSector)", maxPerSector <= 2)

            if (strategy != "TECH_GROWTH") {
                assertTrue(
                    "$strategy high-risk count ${portfolio.highRiskStockCount} must be <= ${portfolio.highRiskLimitMax}",
                    portfolio.highRiskStockCount <= portfolio.highRiskLimitMax
                )
            }
            if (strategy == "BALANCED" || strategy == "HIGH_DIVIDEND") {
                assertTrue(
                    "$strategy must exclude loss-making stocks (PE<=0)",
                    portfolio.stocks.none { it.quote.peTtm <= 0.0 }
                )
            }

            assertTrue("Portfolio must use empirical K-line covariance matrix", portfolio.isEmpiricalKLineBacked)
            assertTrue("Constraint self-audit must pass for $strategy", portfolio.constraintAuditPassed)
            assertTrue(
                "Markowitz diversification ratio must exceed 1.0 (got ${portfolio.diversificationRatio})",
                portfolio.diversificationRatio > 1.0
            )
            assertTrue(
                "Covariance σ_p (${portfolio.weightedPortfolioVol}) must be <= naive weighted sum (${portfolio.naiveWeightedVolSum})",
                portfolio.weightedPortfolioVol <= portfolio.naiveWeightedVolSum + 0.1
            )
            val ctrSum = portfolio.stocks.sumOf { it.riskContributionPercent }
            assertEquals("Euler risk contributions CTR% must sum to 100%", 100.0, ctrSum, 1.5)
        }
    }
}
