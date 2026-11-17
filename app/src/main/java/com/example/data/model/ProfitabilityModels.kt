package com.example.data.model

enum class MarketBreadthRegime(
    val title: String,
    val maxStockExposurePercent: Int,
    val recommendedSingleRiskPercent: Double,
    val guidance: String
) {
    OFFENSIVE(
        title = "进攻窗口 (强广度多头)",
        maxStockExposurePercent = 85,
        recommendedSingleRiskPercent = 1.0,
        guidance = "指数与个股广度共振走强，允许积极参与二阶段突破，单笔风险按 1.0R 执行。"
    ),
    SELECTIVE(
        title = "精选试错 (结构分化)",
        maxStockExposurePercent = 60,
        recommendedSingleRiskPercent = 0.75,
        guidance = "市场结构分化，仅允许买点紧缩且盈亏比 ≥ 2:1 的龙头回踩，禁止追高。"
    ),
    DEFENSIVE(
        title = "防御收缩 (广度转弱)",
        maxStockExposurePercent = 35,
        recommendedSingleRiskPercent = 0.5,
        guidance = "多数个股跌破中期均线，严格压缩总仓位至 35% 以内，只守高股息/低波防御。"
    ),
    CASH_PRESERVATION(
        title = "空仓避险 (冰点退潮)",
        maxStockExposurePercent = 15,
        recommendedSingleRiskPercent = 0.25,
        guidance = "市场处于高脆性下行阶段，新开仓极易触发连续止损，优先保留现金等待冰点拐点。"
    )
}

data class MarketBreadthSnapshot(
    val breadthScore: Int,
    val regime: MarketBreadthRegime,
    val sampleCount: Int,
    val advancePercent: Double,
    val aboveMa20Percent: Double,
    val crashFragilityPercent: Double,
    val indexUpCount: Int,
    val indexTotalCount: Int,
    val maxPermittedExposurePercent: Int,
    val diagnosticSummary: String
)

enum class TrendStageLabel(val label: String) {
    STAGE_2_UPTREND("Stage-2 主升上升趋势"),
    STAGE_1_BASING("Stage-1 筑底蓄势期"),
    STAGE_3_TOPPING("Stage-3 高位派发震荡"),
    STAGE_4_DECLINE("Stage-4 空头下行阶段")
}

data class StageVcpAnalysis(
    val stage: TrendStageLabel,
    val vcpScore: Int,
    val isVcpTightSetup: Boolean,
    val volatilityContractionRatio: Double,
    val volumeDryUpRatio: Double,
    val extensionFromMa20Percent: Double,
    val relativeStrengthVsIndexPercent: Double,
    val pivotBreakoutPrice: Double,
    val suggestedStopPrice: Double,
    val atr14: Double,
    val checklistPassedCount: Int,
    val checklistItems: List<Pair<String, Boolean>>,
    val setupVerdict: String
)

enum class GateVerdict(val label: String) {
    PASS("PASS · 准许按计划执行"),
    CAUTION("CAUTION · 减半仓试错"),
    BLOCK("BLOCK · 禁止冲动开仓")
}

data class PreTradeGateItem(
    val gateName: String,
    val passed: Boolean,
    val detail: String
)

data class PositionSizePlan(
    val accountCapitalRmb: Double,
    val riskPerTradePercent: Double,
    val riskBudgetRmb: Double,
    val entryPrice: Double,
    val stopPrice: Double,
    val stopDistancePercent: Double,
    val riskPerShareRmb: Double,
    val rawSharesByRisk: Int,
    val finalBoardLotShares: Int,
    val positionValueRmb: Double,
    val positionPercentOfAccount: Double,
    val maxPermittedPositionPercent: Int,
    val target1RPrice: Double,
    val target2RPrice: Double,
    val target3RPrice: Double,
    val rewardToStructuralTargetR: Double,
    val gateVerdict: GateVerdict,
    val gateItems: List<PreTradeGateItem>,
    val executionWarning: String?
)

data class JournalPerformanceStats(
    val totalEntries: Int,
    val openOrPlannedCount: Int,
    val closedCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val winRatePercent: Double,
    val avgWinR: Double,
    val avgLossR: Double,
    val expectancyR: Double,
    val profitFactor: Double,
    val totalRealizedPnlRmb: Double,
    val disciplineCompliancePercent: Double,
    val reviewVerdict: String
)
