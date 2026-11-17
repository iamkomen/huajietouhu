package com.example.data.repository

import com.example.data.local.TradeJournalEntity
import com.example.data.model.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * 盈利闭环与纪律风控引擎（吸收并本土化适配 VCP二阶段筛选 / 市场广度门控 / 1R以损定仓 / 五重开仓闸门 / R倍数期望复盘）
 */
object ProfitabilityEngine {

    /**
     * 1. 实盘市场广度与仓位敞口门控 (Empirical Market Breadth & Exposure Coach)
     */
    fun computeEmpiricalMarketBreadth(
        indexQuotes: List<StockQuote>,
        universeQuotes: List<StockQuote>,
        universeKLines: Map<String, List<KLinePoint>>,
        fallbackRegime: MarketRegime = MarketRegime.RANGE_BOUND
    ): MarketBreadthSnapshot {
        val validUniverse = universeQuotes.filter { it.price > 0.0 }
        val validIndices = indexQuotes.filter { it.price > 0.0 }

        if (validUniverse.isEmpty() && validIndices.isEmpty()) {
            val mappedRegime = when (fallbackRegime) {
                MarketRegime.BULL_MOMENTUM -> MarketBreadthRegime.OFFENSIVE
                MarketRegime.RANGE_BOUND -> MarketBreadthRegime.SELECTIVE
                MarketRegime.BEAR_DEFENSIVE -> MarketBreadthRegime.DEFENSIVE
            }
            return MarketBreadthSnapshot(
                breadthScore = 50,
                regime = mappedRegime,
                sampleCount = 0,
                advancePercent = 50.0,
                aboveMa20Percent = 50.0,
                crashFragilityPercent = 0.0,
                indexUpCount = 0,
                indexTotalCount = 0,
                maxPermittedExposurePercent = mappedRegime.maxStockExposurePercent,
                diagnosticSummary = "等待同步全市场指数与样本股实盘数据，暂按保守基准门控。"
            )
        }

        val indexUpCount = validIndices.count { it.changePercent > 0.0 }
        val avgIndexChg = if (validIndices.isNotEmpty()) validIndices.map { it.changePercent }.average() else 0.0

        val advancePct = if (validUniverse.isNotEmpty()) {
            (validUniverse.count { it.changePercent > 0.0 }.toDouble() / validUniverse.size) * 100.0
        } else {
            if (avgIndexChg >= 0.3) 62.0 else if (avgIndexChg <= -0.5) 35.0 else 50.0
        }

        val withKLines = validUniverse.mapNotNull { q ->
            val k = universeKLines[q.symbol]
            if (k != null && k.size >= 20) q to k else null
        }
        val aboveMa20Pct = if (withKLines.isNotEmpty()) {
            val aboveCount = withKLines.count { (q, k) ->
                val ma20 = k.last().ma20
                ma20 > 0.0 && q.price >= ma20
            }
            (aboveCount.toDouble() / withKLines.size) * 100.0
        } else {
            advancePct
        }

        val crashFragilityPct = if (validUniverse.isNotEmpty()) {
            (validUniverse.count { it.changePercent <= -4.0 }.toDouble() / validUniverse.size) * 100.0
        } else 0.0

        val indexScore = if (validIndices.isNotEmpty()) {
            ((indexUpCount.toDouble() / validIndices.size) * 30.0) + (avgIndexChg * 5.0).coerceIn(-10.0, 10.0)
        } else 15.0

        val breadthScore = (
            indexScore +
                (advancePct * 0.35) +
                (aboveMa20Pct * 0.35) -
                (crashFragilityPct * 0.8)
            ).toInt().coerceIn(5, 98)

        val regime = when {
            breadthScore >= 68 && crashFragilityPct < 8.0 -> MarketBreadthRegime.OFFENSIVE
            breadthScore >= 48 && crashFragilityPct < 15.0 -> MarketBreadthRegime.SELECTIVE
            breadthScore >= 30 -> MarketBreadthRegime.DEFENSIVE
            else -> MarketBreadthRegime.CASH_PRESERVATION
        }

        val summary = buildString {
            append("样本 ${validUniverse.size} 只 · 上涨广度 ${round1(advancePct)}% · ")
            append("站上MA20占比 ${round1(aboveMa20Pct)}% · ")
            if (crashFragilityPct >= 10.0) {
                append("⚠️高脆性跌超4%占比 ${round1(crashFragilityPct)}%，严控开仓。")
            } else {
                append("建议总股票仓位上限 ≤ ${regime.maxStockExposurePercent}%，单笔风险 ${regime.recommendedSingleRiskPercent}R。")
            }
        }

        return MarketBreadthSnapshot(
            breadthScore = breadthScore,
            regime = regime,
            sampleCount = validUniverse.size,
            advancePercent = round1(advancePct),
            aboveMa20Percent = round1(aboveMa20Pct),
            crashFragilityPercent = round1(crashFragilityPct),
            indexUpCount = indexUpCount,
            indexTotalCount = validIndices.size,
            maxPermittedExposurePercent = regime.maxStockExposurePercent,
            diagnosticSummary = summary
        )
    }

    /**
     * 2. Stage-2 趋势模板 + VCP 波动与量能紧缩识别 + 结构性止损位计算
     */
    fun analyzeStageAndVcp(
        quote: StockQuote,
        kLines: List<KLinePoint>,
        benchmarkKLines: List<KLinePoint> = emptyList()
    ): StageVcpAnalysis {
        val price = quote.price.coerceAtLeast(0.01)
        val atr14 = computeAtr14(kLines, quote)
        val latest = kLines.lastOrNull()
        val ma20 = latest?.ma20?.takeIf { it > 0.0 } ?: price
        val ma60 = latest?.ma60?.takeIf { it > 0.0 } ?: ma20

        val extensionFromMa20 = round1(((price - ma20) / ma20) * 100.0)

        // 20日与近期5日波幅对比 (VCP 波动紧缩核心量化指标)
        val contractionRatio = if (kLines.size >= 20) {
            val recent5 = kLines.takeLast(5)
            val prior15 = kLines.dropLast(5).takeLast(15)
            val avgRange5 = recent5.map { if (it.close > 0) (it.high - it.low) / it.close else 0.0 }.average()
            val avgRange15 = prior15.map { if (it.close > 0) (it.high - it.low) / it.close else 0.0 }.average()
            if (avgRange15 > 1e-5) round2(avgRange5 / avgRange15) else 1.0
        } else 1.0

        val volumeDryUpRatio = if (kLines.size >= 20) {
            val vol5 = kLines.takeLast(5).map { it.volume }.average()
            val vol20 = kLines.takeLast(20).map { it.volume }.average()
            if (vol20 > 1e-5) round2(vol5 / vol20) else 1.0
        } else 1.0

        // 相对基准沪深300的 20日相对强弱 RS
        val stock20Ret = if (kLines.size >= 21 && kLines[kLines.size - 21].close > 0.0) {
            ((price - kLines[kLines.size - 21].close) / kLines[kLines.size - 21].close) * 100.0
        } else quote.changePercent
        val bench20Ret = if (benchmarkKLines.size >= 21 && benchmarkKLines[benchmarkKLines.size - 21].close > 0.0) {
            ((benchmarkKLines.last().close - benchmarkKLines[benchmarkKLines.size - 21].close) / benchmarkKLines[benchmarkKLines.size - 21].close) * 100.0
        } else 0.0
        val rsVsIndex = round1(stock20Ret - bench20Ret)

        val window60 = if (kLines.size >= 60) kLines.takeLast(60) else kLines
        val high60 = window60.maxOfOrNull { it.high }?.takeIf { it > 0.0 } ?: max(price, quote.high)
        val low60 = window60.minOfOrNull { it.low }?.takeIf { it > 0.0 } ?: min(price, quote.low)
        val swingLow10 = (if (kLines.size >= 10) kLines.takeLast(10) else kLines)
            .minOfOrNull { it.low }?.takeIf { it > 0.0 } ?: (price - 1.5 * atr14)
        val recentPivotHigh = (if (kLines.size >= 15) kLines.takeLast(15) else kLines)
            .maxOfOrNull { it.high }?.takeIf { it > 0.0 } ?: max(price * 1.02, quote.high)

        val aboveMa20 = price >= ma20
        val aboveMa60 = price >= ma60
        val maAscending = ma20 >= ma60 * 0.99
        val nearHigh = price >= high60 * 0.80
        val offLowStrong = price >= low60 * 1.10
        val isVolatilityTight = contractionRatio <= 0.82
        val isVolumeDryUp = volumeDryUpRatio <= 0.90
        val hasPositiveRs = rsVsIndex >= 0.0

        val checklist = listOf(
            "现价站上20日与60日均线" to (aboveMa20 && aboveMa60),
            "中期均线多头 (MA20 ≥ MA60)" to maAscending,
            "距60日低点已脱底 ≥ 10%" to offLowStrong,
            "处于60日高点 20% 强支撑带内" to nearHigh,
            "近5日波幅较前15日收敛 (≤0.82)" to isVolatilityTight,
            "成交量温和萎缩蓄势 (≤0.90)" to isVolumeDryUp,
            "跑赢沪深300相对强弱 (RS ≥ 0)" to hasPositiveRs
        )
        val passedCount = checklist.count { it.second }

        val stage = when {
            !aboveMa20 && !aboveMa60 && ma20 < ma60 -> TrendStageLabel.STAGE_4_DECLINE
            aboveMa20 && aboveMa60 && maAscending && nearHigh -> TrendStageLabel.STAGE_2_UPTREND
            !aboveMa20 && aboveMa60 -> TrendStageLabel.STAGE_3_TOPPING
            else -> TrendStageLabel.STAGE_1_BASING
        }

        val isVcpTight = stage == TrendStageLabel.STAGE_2_UPTREND &&
            (isVolatilityTight || isVolumeDryUp) &&
            extensionFromMa20 in -3.0..9.0

        var vcpScore = passedCount * 12
        if (isVcpTight) vcpScore += 14
        if (extensionFromMa20 > 12.0) vcpScore -= 16
        if (stage == TrendStageLabel.STAGE_4_DECLINE) vcpScore = vcpScore.coerceAtMost(28)
        vcpScore = vcpScore.coerceIn(10, 99)

        // 结构性硬止损：取 10日低点(微留0.3%防毛刺) 与 1.6倍ATR止损 的较紧但合理者，并限制在 [2.5%, 10%] 区间内
        val atrStop = price - (atr14 * 1.6)
        val swingStop = swingLow10 * 0.997
        val rawStop = max(atrStop, swingStop)
        val minAllowedStop = price * 0.90
        val maxAllowedStop = price * 0.975
        val suggestedStop = round2(rawStop.coerceIn(minAllowedStop, maxAllowedStop))

        val verdict = when {
            stage == TrendStageLabel.STAGE_4_DECLINE ->
                "处于 Stage-4 下行空头期，历史数据显示逆势抄底期望为负，严禁开新仓。"
            extensionFromMa20 > 12.0 ->
                "股价偏离 MA20 达 +${extensionFromMa20}%，已远离低风险紧缩枢轴，追高止损过宽。"
            isVcpTight ->
                "命中 Stage-2 VCP 波动量能双紧缩形态，突破枢轴 ¥${round2(recentPivotHigh)} 具备非对称盈亏比。"
            stage == TrendStageLabel.STAGE_2_UPTREND ->
                "处于二阶段上升结构，建议耐心等待回踩缩量紧缩后再择机入场。"
            else ->
                "处于趋势整固/筑底过渡期，需确认放量突破并站稳中期均线。"
        }

        return StageVcpAnalysis(
            stage = stage,
            vcpScore = vcpScore,
            isVcpTightSetup = isVcpTight,
            volatilityContractionRatio = contractionRatio,
            volumeDryUpRatio = volumeDryUpRatio,
            extensionFromMa20Percent = extensionFromMa20,
            relativeStrengthVsIndexPercent = rsVsIndex,
            pivotBreakoutPrice = round2(recentPivotHigh),
            suggestedStopPrice = suggestedStop,
            atr14 = round2(atr14),
            checklistPassedCount = passedCount,
            checklistItems = checklist,
            setupVerdict = verdict
        )
    }

    /**
     * 3. 1R 以损定仓 + A股100股整手取整 + 五重交易前铁律闸门
     */
    fun buildPreTradeExecutionPlan(
        quote: StockQuote,
        kLines: List<KLinePoint>,
        accountCapitalRmb: Double,
        riskPerTradePercent: Double,
        customStopPrice: Double? = null,
        breadthSnapshot: MarketBreadthSnapshot,
        vcpAnalysis: StageVcpAnalysis,
        liveFinancialSnapshot: FinancialStatementSnapshot? = null
    ): PositionSizePlan {
        val safeCapital = accountCapitalRmb.coerceAtLeast(1000.0)
        val safeRiskPct = riskPerTradePercent.coerceIn(0.1, 5.0)
        val riskBudget = round2(safeCapital * (safeRiskPct / 100.0))
        val entryPrice = quote.price.coerceAtLeast(0.01)
        val stopPrice = round2((customStopPrice ?: vcpAnalysis.suggestedStopPrice).coerceAtLeast(0.01))

        val isValidStop = stopPrice < entryPrice
        val riskPerShare = if (isValidStop) (entryPrice - stopPrice).coerceAtLeast(0.01) else 0.0
        val stopDistancePct = if (isValidStop) round2((riskPerShare / entryPrice) * 100.0) else 0.0

        val rawShares = if (isValidStop && riskPerShare > 0.0) {
            floor(riskBudget / riskPerShare).toInt()
        } else 0

        // 受市场广度总仓位上限和单票最高 25% 集中度约束
        val maxSinglePosPct = min(25, breadthSnapshot.maxPermittedExposurePercent)
        val maxPositionRmb = safeCapital * (maxSinglePosPct / 100.0)
        val maxSharesByCap = floor(maxPositionRmb / entryPrice).toInt()
        val cappedRawShares = min(rawShares, maxSharesByCap).coerceAtLeast(0)
        val boardLotShares = (cappedRawShares / 100) * 100

        val positionValue = round2(boardLotShares * entryPrice)
        val positionPct = round1((positionValue / safeCapital) * 100.0)

        val target1R = round2(entryPrice + riskPerShare)
        val target2R = round2(entryPrice + riskPerShare * 2.0)
        val target3R = round2(entryPrice + riskPerShare * 3.0)

        val t1Test = QuantEngine.computeT1StressTest(quote)
        val targetFloor = QuantEngine.computeTargetAndFloor(quote, kLines)
        val structuralRewardR = if (isValidStop && riskPerShare > 0.0) {
            round2(max(targetFloor.targetPrice - entryPrice, vcpAnalysis.atr14 * 3.0) / riskPerShare)
        } else 0.0

        // 五重交易前纪律闸门
        val (_, audit, _) = DcfValuationEngine.evaluateProfessionalDcf(quote, kLines, liveFinancialSnapshot)
        val gate1Breadth = breadthSnapshot.regime != MarketBreadthRegime.CASH_PRESERVATION
        val gate2Trend = vcpAnalysis.stage != TrendStageLabel.STAGE_4_DECLINE
        val gate3NotExtended = vcpAnalysis.extensionFromMa20Percent <= 12.0 && structuralRewardR >= 1.6
        val gate4Fundamental = quote.peTtm > 0.0 && !audit.cashQualityGrade.startsWith("D")
        val gate5StopValid = isValidStop && stopDistancePct in 1.5..12.0 && !t1Test.isNearLimitDownAlert

        val gateItems = listOf(
            PreTradeGateItem(
                gateName = "1. 市场广度气候门控",
                passed = gate1Breadth,
                detail = "${breadthSnapshot.regime.title} (广度${breadthSnapshot.breadthScore}分，上限${breadthSnapshot.maxPermittedExposurePercent}%)"
            ),
            PreTradeGateItem(
                gateName = "2. 趋势阶段过滤门控",
                passed = gate2Trend,
                detail = "${vcpAnalysis.stage.label} (VCP紧缩评分 ${vcpAnalysis.vcpScore})"
            ),
            PreTradeGateItem(
                gateName = "3. 不追高与盈亏比门控",
                passed = gate3NotExtended,
                detail = "MA20偏离 ${if (vcpAnalysis.extensionFromMa20Percent >= 0) "+" else ""}${vcpAnalysis.extensionFromMa20Percent}% · 结构盈亏比 ${structuralRewardR}R"
            ),
            PreTradeGateItem(
                gateName = "4. 基本面现金流排雷门控",
                passed = gate4Fundamental,
                detail = if (quote.peTtm <= 0.0) "PE为负处于亏损" else "猫会计现金流评级 ${audit.cashQualityGrade}"
            ),
            PreTradeGateItem(
                gateName = "5. 止损有效性与T+1门控",
                passed = gate5StopValid,
                detail = if (!isValidStop) "止损价必须低于现价" else "止损幅度 -${stopDistancePct}% · 距跌停 ${t1Test.distanceToLimitDownPercent}%"
            )
        )

        val failCount = gateItems.count { !it.passed }
        val verdict = when {
            !gate1Breadth || !gate2Trend || !gate5StopValid -> GateVerdict.BLOCK
            failCount == 0 -> GateVerdict.PASS
            failCount == 1 -> GateVerdict.CAUTION
            else -> GateVerdict.BLOCK
        }

        val warning = when {
            !isValidStop -> "止损价 ¥$stopPrice 必须严格低于买入价 ¥$entryPrice，否则无法计算 1R 头寸！"
            boardLotShares == 0 && verdict != GateVerdict.BLOCK ->
                "按 1R 风险预算 ¥${riskBudget.toInt()} 与每股风险 ¥${round2(riskPerShare)} 计算不足1手(100股)，需提高资金或收窄止损至紧缩形态再出手。"
            verdict == GateVerdict.BLOCK ->
                "触发硬性交易纪律拦截（未过 ${failCount} 项闸门），强行重仓极易产生非必要回撤。"
            stopDistancePct > 10.0 ->
                "止损距离 -${stopDistancePct}% 偏宽，系统已自动按以损定仓公式大幅缩减可买股数。"
            else -> null
        }

        return PositionSizePlan(
            accountCapitalRmb = round2(safeCapital),
            riskPerTradePercent = safeRiskPct,
            riskBudgetRmb = riskBudget,
            entryPrice = round2(entryPrice),
            stopPrice = stopPrice,
            stopDistancePercent = stopDistancePct,
            riskPerShareRmb = round2(riskPerShare),
            rawSharesByRisk = rawShares,
            finalBoardLotShares = if (verdict == GateVerdict.BLOCK) 0 else boardLotShares,
            positionValueRmb = if (verdict == GateVerdict.BLOCK) 0.0 else positionValue,
            positionPercentOfAccount = if (verdict == GateVerdict.BLOCK) 0.0 else positionPct,
            maxPermittedPositionPercent = maxSinglePosPct,
            target1RPrice = target1R,
            target2RPrice = target2R,
            target3RPrice = target3R,
            rewardToStructuralTargetR = structuralRewardR,
            gateVerdict = verdict,
            gateItems = gateItems,
            executionWarning = warning
        )
    }

    /**
     * 4. 实盘交易账本期望值统计 (Expectancy E = W*AvgWinR - L*AvgLossR)
     */
    fun computeJournalStats(entries: List<TradeJournalEntity>): JournalPerformanceStats {
        val closed = entries.filter { it.status == "CLOSED" && it.realizedRMultiple != null }
        val openOrPlanned = entries.count { it.status != "CLOSED" }
        val compliantCount = entries.count { !it.gateVerdict.startsWith("BLOCK") }
        val compliancePct = if (entries.isNotEmpty()) {
            round1((compliantCount.toDouble() / entries.size) * 100.0)
        } else 100.0

        if (closed.isEmpty()) {
            return JournalPerformanceStats(
                totalEntries = entries.size,
                openOrPlannedCount = openOrPlanned,
                closedCount = 0,
                winCount = 0,
                lossCount = 0,
                winRatePercent = 0.0,
                avgWinR = 0.0,
                avgLossR = 0.0,
                expectancyR = 0.0,
                profitFactor = 0.0,
                totalRealizedPnlRmb = 0.0,
                disciplineCompliancePercent = compliancePct,
                reviewVerdict = if (entries.isEmpty()) {
                    "尚未记录实盘计划，建议每笔买入前先通过五重闸门并保存 1R 交易计划。"
                } else {
                    "已有 $openOrPlanned 笔跟踪中计划，平仓结算后将自动统计真实胜率与 R 倍数数学期望。"
                }
            )
        }

        val wins = closed.filter { (it.realizedRMultiple ?: 0.0) > 0.0 }
        val losses = closed.filter { (it.realizedRMultiple ?: 0.0) <= 0.0 }
        val winRate = wins.size.toDouble() / closed.size
        val lossRate = 1.0 - winRate

        val avgWinR = if (wins.isNotEmpty()) wins.mapNotNull { it.realizedRMultiple }.average() else 0.0
        val avgLossR = if (losses.isNotEmpty()) abs(losses.mapNotNull { it.realizedRMultiple }.average()) else 0.0
        val expectancyR = (winRate * avgWinR) - (lossRate * avgLossR)

        val grossGain = closed.mapNotNull { it.realizedPnlRmb }.filter { it > 0.0 }.sum()
        val grossLoss = abs(closed.mapNotNull { it.realizedPnlRmb }.filter { it < 0.0 }.sum())
        val profitFactor = when {
            grossLoss > 0.01 -> round2(grossGain / grossLoss)
            grossGain > 0.0 -> 9.99
            else -> 0.0
        }
        val totalPnl = round2(closed.mapNotNull { it.realizedPnlRmb }.sum())

        val verdict = when {
            expectancyR >= 0.5 && compliancePct >= 80.0 ->
                "🟢 正期望复利轨道：实测单笔期望 +${round2(expectancyR)}R，盈亏比与纪律执行俱佳。"
            expectancyR > 0.0 ->
                "🟡 弱正期望区间 (+${round2(expectancyR)}R)：继续截断亏损、让盈利奔跑至 2R~3R。"
            else ->
                "🔴 负期望警报 (${round2(expectancyR)}R)：实盘亏损大于盈利，请立刻检查是否逆大势交易或未守 1R 硬止损。"
        }

        return JournalPerformanceStats(
            totalEntries = entries.size,
            openOrPlannedCount = openOrPlanned,
            closedCount = closed.size,
            winCount = wins.size,
            lossCount = losses.size,
            winRatePercent = round1(winRate * 100.0),
            avgWinR = round2(avgWinR),
            avgLossR = round2(avgLossR),
            expectancyR = round2(expectancyR),
            profitFactor = profitFactor,
            totalRealizedPnlRmb = totalPnl,
            disciplineCompliancePercent = compliancePct,
            reviewVerdict = verdict
        )
    }

    private fun computeAtr14(kLines: List<KLinePoint>, quote: StockQuote): Double {
        if (kLines.size < 2) {
            return ((quote.amplitude.coerceAtLeast(1.5) / 100.0) * quote.price.coerceAtLeast(0.01)).coerceAtLeast(0.01)
        }
        val window = kLines.takeLast(15)
        var sumTr = 0.0
        var count = 0
        for (i in 1 until window.size) {
            val prevClose = window[i - 1].close
            val cur = window[i]
            val tr = maxOf(
                cur.high - cur.low,
                abs(cur.high - prevClose),
                abs(cur.low - prevClose)
            )
            sumTr += tr
            count++
        }
        return if (count > 0) (sumTr / count).coerceAtLeast(0.01) else 0.01
    }

    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
