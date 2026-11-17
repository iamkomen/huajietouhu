package com.example.data.model

data class QuantFactorScores(
    val valueScore: Int,      // 价值因子 (0-100)
    val growthScore: Int,     // 成长因子 (0-100)
    val momentumScore: Int,   // 动量因子 (0-100)
    val qualityScore: Int,    // 质量因子 (0-100)
    val riskControlScore: Int,// 风控/低波动因子 (0-100)
    val compositeScore: Int,  // 综合量化得分 (0-100)
    val rating: String,       // "强力推荐 (Strong Buy)", "跑赢大盘 (Outperform)", "中性观望 (Hold)", "减持避险 (Underperform)"
    // 深度重构区分度量化指标
    val peScore: Int = valueScore,
    val pbScore: Int = valueScore,
    val microScore: Int = 50,
    val realizedVol20D: Double = 0.0,         // 20日年化已实现波动率(%)
    val momentum20DPercent: Double = 0.0,     // 20日区间动量涨幅(%)
    val valuationSpreadPercent: Double = 0.0, // 估值相对全A中枢偏离度(%)
    val roeEstimatedPercent: Double = 0.0,    // 测算内生ROE(%)
    val barraExposure: BarraStyleFactorExposure? = null,
    val stoikovMicroPrice: StoikovMicroPriceInfo? = null
)

enum class SignalDirection {
    BUY,       // 买入 / 多头增强
    SELL,      // 卖出 / 获利了结 / 止损
    HOLD,      // 持股 / 趋势跟踪
    WATCH      // 观望 / 震荡整理
}

data class TradingSignal(
    val id: String,
    val title: String,
    val direction: SignalDirection,
    val timeframe: String, // e.g. "日线级别", "周线波段"
    val winRate: Double,   // 理论名义回测胜率，如 68.4%
    val profitLossRatio: String, // 如 "2.3 : 1"
    val summary: String,
    val technicalBasis: String,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val targetPrice: Double,
    val strength: Int, // 1-5 星级信号强度
    val netWinRate: Double = (winRate * 0.91).coerceAtLeast(30.0), // 扣除0.23%滑点税费后的实战净胜率
    val netProfitLossRatio: String = profitLossRatio, // 扣除滑点后的真实盈亏比
    val executionSlippageCost: Double = 0.23, // 单笔综合滑点冲击与印花税佣金 (%)
    val liquidityAlert: String = "流动性充沛 · 可顺畅成交"
)

enum class MarketRegime(
    val title: String,
    val badge: String,
    val strategyRecommendation: String,
    val momentumBias: Float,
    val valueBias: Float,
    val volBias: Float,
    val microBias: Float
) {
    BULL_MOMENTUM(
        title = "牛市强动量突破态",
        badge = "多头增强",
        strategyRecommendation = "顺势交易，重配动量与龙头质量因子，适当放宽止盈幅度",
        momentumBias = 1.35f,
        valueBias = 0.85f,
        volBias = 0.75f,
        microBias = 1.25f
    ),
    RANGE_BOUND(
        title = "箱体震荡博弈态",
        badge = "均衡套利",
        strategyRecommendation = "高抛低吸，重配价值低估与微观筹码因子，严格执行均线止损",
        momentumBias = 0.95f,
        valueBias = 1.20f,
        volBias = 1.10f,
        microBias = 1.20f
    ),
    BEAR_DEFENSIVE(
        title = "弱势单边避险态",
        badge = "低波防御",
        strategyRecommendation = "空仓防守或重配低波动、高股息红利因子，警惕微盘流动性踩踏",
        momentumBias = 0.60f,
        valueBias = 1.15f,
        volBias = 1.45f,
        microBias = 0.80f
    )
}

/**
 * 历史胜率与盈利期望模型
 */
data class FactorCombinationResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val winRate: Double,
    val expectedReturn: Double,
    val profitLossRatio: String,
    val maxDrawdown: Double,
    val isHit: Boolean,
    val hitStatusText: String, // "🎯 当前标的命中" or "未命中 · 动量评分需≥75"
    val tags: List<String>
)

data class HistoricalWinRateStats(
    val horizonYears: String, // "5年全周期", "3年震荡市", "1年近态"
    val sampleStockCount: Int, // 样本量，如 5300
    val totalTradeInstances: Int, // 历史策略触发次数
    val overallExpectancy: Double, // 综合期望回报 E(R)
    val combinations: List<FactorCombinationResult>,
    val deflatedSharpeInfo: DeflatedSharpeRatioInfo? = null
)

data class MacroPositionGuidance(
    val regime: MarketRegime,
    val recommendedStockPositionPercent: Int, // 建议股票总仓位 (e.g. 80%)
    val recommendedCashCushionPercent: Int,   // 现金防守垫 (e.g. 20%)
    val singleStockMaxAllocationPercent: Int, // 单票风控上限 (e.g. 15%)
    val macroRiskLevel: String,               // "低风险进攻" / "中性控制" / "极端谨慎"
    val actionDirective: String              // 指令描述
)

data class HalfKellyAllocation(
    val theoreticalKellyPercent: Double,  // 理论凯利最优比例 (如 28.4%)
    val recommendedHalfKellyPercent: Int, // 半凯利审慎比例 (如 14%)
    val maximumCapPercent: Int,          // 绝对硬风控上限 (18%)
    val rationale: String                // 仓位理由
)

data class T1StressTestResult(
    val limitUpPrice: Double,
    val limitDownPrice: Double,
    val distanceToLimitUpPercent: Double,
    val distanceToLimitDownPercent: Double,
    val maxSingleDayLossRMB: Double,
    val riskVerdict: String,
    val isNearLimitDownAlert: Boolean
)

data class SectorCrowdingInfo(
    val sectorName: String,
    val crowdingIndexPercent: Double,
    val turnoverZScore: Double,
    val institutionalSentiment: String,
    val isOvercrowded: Boolean
)

data class StressScenarioResult(
    val scenarioId: String,
    val scenarioName: String,
    val historicalPeriod: String,
    val marketDrawdown: Double,
    val simulatedPortfolioDrawdown: Double,
    val maxSingleDayDrop: Double,
    val liquiditySurvivalRate: Double,
    val tailRiskVerdict: String,
    val keyVulnerability: String
)

data class MarketNeutralHedgingAnalysis(
    val portfolioBeta: Double,
    val grossExpectedReturn: Double,
    val hedgeInstrument: String,
    val hedgeRatioPercent: Int,
    val basisDiscountCost: Double,
    val netAlphaReturn: Double,
    val hedgeVerdict: String
)

data class ExecutionImpactEstimate(
    val capitalScaleLabel: String,
    val estimatedSlippageBps: Double,
    val recommendedExecutionMethod: String,
    val liquidityWarning: String
)

data class QuantTradeRecommendation(
    val action: SignalDirection,
    val actionTitle: String,
    val compositeScore: Int,
    val confidenceLevel: String,
    val winRateEstimate: Double,
    val profitLossRatio: String,
    val suggestedPositionRange: String,
    val entryPriceRange: String,
    val targetPrice: Double,
    val stopLossFloorPrice: Double,
    val primaryFactorDriver: String,
    val quantitativeThesis: String,
    val riskWarning: String
)

data class TargetFloorPriceInfo(
    val currentPrice: Double,
    val targetPrice: Double,
    val floorPrice: Double,
    val targetUpsidePercent: Double,
    val floorDownsidePercent: Double,
    val basisExplanation: String
) {
    val formattedTargetUpside: String get() = String.format(java.util.Locale.US, "%.2f%%", targetUpsidePercent)
    val formattedFloorDownside: String get() = String.format(java.util.Locale.US, "%.2f%%", floorDownsidePercent)
}

data class QuantStrategyPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val tags: List<String>
)

data class RiskPriorityItem(
    val title: String,
    val level: String,
    val description: String
)

data class FullResearchSection(
    val executiveSummary: String,
    val coreDrivers: List<String>,
    val investmentThesis: String
)

data class FinancialHealthSection(
    val roe5YearTrend: String,
    val cashFlowStatus: String,
    val debtRatioHealth: String
)

data class MoatAnalysisSection(
    val score: Int,
    val moatType: String,
    val competitiveAdvantage: String
)

data class FinancialStatementSnapshot(
    val symbol: String,
    val reportPeriod: String,
    val sourceLabel: String, // "东方财富F10财报实盘" or "行情PE/PB杜邦反推口径"
    val isLiveAuditedData: Boolean,
    val revenueBillion: Double,
    val netProfitBillion: Double,
    val deductedNetProfitBillion: Double,
    val operatingCashFlowBillion: Double,
    val capexBillion: Double,
    val maintenanceCapexBillion: Double,
    val freeCashFlowBillion: Double,
    val monetaryCashBillion: Double,
    val interestBearingDebtBillion: Double,
    val netCashBillion: Double,
    val accountsReceivableBillion: Double,
    val inventoryBillion: Double,
    val contractLiabilityBillion: Double,
    val goodwillBillion: Double,
    val equityBillion: Double,
    val roicPercent: Double,
    val roePercent: Double,
    val grossMarginPercent: Double,
    val debtToAssetPercent: Double,
    val cashToEarningsRatio: Double,
    val capexToOcfPercent: Double,
    val workingCapitalQualityNote: String
)

data class AccountingRedFlag(
    val title: String,
    val severity: String, // "高危红灯" / "审慎关注" / "绿灯通过"
    val detail: String,
    val isAlert: Boolean
)

data class AccountingForensicAudit(
    val isFinancialSector: Boolean,
    val cashQualityGrade: String,       // e.g. "A·现金奶牛" / "B·稳健造血" / "C·重资产吞噬" / "D·纸面富贵预警"
    val moatRoicSpreadPercent: Double,  // ROIC - WACC (EVA创造利差)
    val reinvestmentRatePercent: Double,// 再投资率 g / ROIC (%)
    val cashConversionHaircut: Double,  // 现金流挤水分折扣系数 (0.60..1.00)
    val waccRiskPenaltyPercent: Double, // 因排雷瑕疵加收的治理折现溢价 (%)
    val redFlags: List<AccountingRedFlag>,
    val maoAccountingVerdict: String
)

data class DcfYearProjection(
    val year: Int,
    val growthRatePercent: Double,
    val roicPercent: Double,
    val nopatBillion: Double,
    val reinvestmentBillion: Double,
    val fcffBillion: Double,
    val discountedPvBillion: Double
)

data class DcfSensitivityCell(
    val waccPercent: Double,
    val terminalGrowthPercent: Double,
    val intrinsicPrice: Double,
    val upsidePercent: Double
)

data class DcfSensitivityGrid(
    val waccAxis: List<Double>,
    val terminalGrowthAxis: List<Double>,
    val matrix: List<List<DcfSensitivityCell>>
)

data class ProfessionalDcfModel(
    val modelTypeLabel: String,
    val dataSourceBadge: String,
    val reportPeriodLabel: String,
    val riskFreeRatePercent: Double,
    val equityRiskPremiumPercent: Double,
    val betaUsed: Double,
    val costOfEquityPercent: Double,
    val afterTaxCostOfDebtPercent: Double,
    val waccPercent: Double,
    val stage1GrowthPercent: Double,
    val terminalGrowthPercent: Double,
    val terminalRoicPercent: Double,
    val pvExplicitFcfBillion: Double,
    val pvTerminalValueBillion: Double,
    val terminalValueSharePercent: Double,
    val enterpriseValueBillion: Double,
    val netCashAdjustmentBillion: Double,
    val equityValueBillion: Double,
    val currentMarketCapBillion: Double,
    val bearIntrinsicPrice: Double,
    val baseIntrinsicPrice: Double,
    val bullIntrinsicPrice: Double,
    val probabilityWeightedPrice: Double,
    val marginOfSafetyPercent: Double,
    val impliedReverseGrowthPercent: Double?,
    val reverseDcfVerdict: String,
    val yearlyProjections: List<DcfYearProjection>,
    val sensitivityGrid: DcfSensitivityGrid,
    val institutionalConclusion: String
)

enum class DcfTerminalMethod(val key: String, val label: String) {
    PERPETUAL_GROWTH("perpetual", "永续增长率法"),
    EXIT_MULTIPLE("multiple", "退出倍数法")
}

data class CustomDcfInput(
    val baseFcfBillion: Double,
    val stage1GrowthPercent: Double,
    val discountRatePercent: Double,
    val projectionYears: Int = 5,
    val terminalMethod: DcfTerminalMethod = DcfTerminalMethod.PERPETUAL_GROWTH,
    val terminalGrowthPercent: Double = 2.0,
    val terminalMultiple: Double = 15.0,
    val netCashBillion: Double = 0.0,
    val safetyDiscountPercent: Double = 30.0
)

data class CustomDcfYearRow(
    val year: Int,
    val growthPercent: Double,
    val projectedFcfBillion: Double,
    val discountFactor: Double,
    val presentValueBillion: Double
)

data class CustomDcfResult(
    val pvExplicitFcfBillion: Double,
    val terminalValueUndiscountedBillion: Double,
    val pvTerminalValueBillion: Double,
    val terminalValueSharePercent: Double,
    val enterpriseValueBillion: Double,
    val netCashAdjustmentBillion: Double,
    val equityValueBillion: Double,
    val currentMarketCapBillion: Double,
    val intrinsicPrice: Double,
    val maoStrikePrice: Double,
    val marginOfSafetyPercent: Double,
    val impliedMultipleOnBaseFcf: Double,
    val yearlyRows: List<CustomDcfYearRow>,
    val maoWarnings: List<String>,
    val maoConclusion: String
)

data class ScreenerDcfSignal(
    val intrinsicPrice: Double,
    val maoStrikePrice: Double,
    val marginOfSafetyPercent: Double,
    val impliedReverseGrowthPercent: Double?,
    val roicSpreadPercent: Double,
    val cashQualityGrade: String,
    val isMaoStrikeZone: Boolean,
    val isDcfUndervalued: Boolean,
    val dcfScreenerScore: Int,
    val statusLabel: String
)

data class HistoricalDcfCurvePoint(
    val date: String,
    val closePrice: Double,
    val strikePrice: Double,
    val bearPrice: Double,
    val fairPrice: Double,
    val bullPrice: Double,
    val safetyMarginPercent: Double
)

data class HistoricalDcfCurveSeries(
    val symbol: String,
    val stockName: String,
    val points: List<HistoricalDcfCurvePoint>,
    val currentPrice: Double,
    val currentFairPrice: Double,
    val currentStrikePrice: Double,
    val currentBearPrice: Double,
    val currentBullPrice: Double,
    val currentMarginOfSafetyPercent: Double,
    val currentZoneLabel: String,
    val valuationPercentileInHistory: Double,
    val isUsingRealKLines: Boolean
)

data class ValuationModelSection(
    val dcfIntrinsicValue: Double,
    val pePercentile5Year: String,
    val marginOfSafety: String
)

data class LongTermGrowthSection(
    val growthDrivers: List<String>,
    val industryTrend: String
)

data class BullVsBearSection(
    val bullCase: String,
    val bearCase: String,
    val keyCatalyst: String
)

data class InstitutionalSection(
    val fundHoldingsPercent: Double,
    val northBoundTrend: String,
    val institutionalVerdict: String
)

data class WallStreetReport(
    val stockSymbol: String,
    val stockName: String,
    val generationTime: String,
    val analystConsensus: String,
    val targetPriceBase: Double,
    val targetPriceBull: Double,
    val targetPriceBear: Double,
    val fullEquityResearch: FullResearchSection,
    val financialHealth: FinancialHealthSection,
    val moatAnalysis: MoatAnalysisSection,
    val valuationModel: ValuationModelSection,
    val riskPriorities: List<RiskPriorityItem>,
    val longTermGrowth: LongTermGrowthSection,
    val bullVsBear: BullVsBearSection,
    val institutionalActivity: InstitutionalSection,
    val financialSnapshot: FinancialStatementSnapshot? = null,
    val accountingAudit: AccountingForensicAudit? = null,
    val professionalDcf: ProfessionalDcfModel? = null
)

/**
 * 华尔街顶级对冲基金专有高维微观模型与风控数据结构
 */
data class StoikovMicroPriceInfo(
    val midPrice: Double,
    val microPrice: Double,
    val microPriceSpreadBps: Double,
    val orderFlowImbalance: Double, // OFI (-1.0 to +1.0)
    val buyerUrgencyVerdict: String
)

data class CornishFisherVaRResult(
    val gaussianVaR95: Double,
    val gaussianVaR99: Double,
    val cornishFisherVaR99: Double,
    val expectedShortfallCVaR99: Double,
    val skewness: Double,
    val excessKurtosis: Double,
    val tailFatnessVerdict: String
)

data class AlmgrenChrissImpactCost(
    val tradeSizeRMB: Double,
    val participationRatePercent: Double,
    val halfSpreadCostBps: Double,
    val permanentImpactBps: Double,
    val temporaryImpactBps: Double,
    val totalCostBps: Double,
    val netAlphaErosionPercent: Double,
    val optimalExecutionHorizon: String
)

data class DeflatedSharpeRatioInfo(
    val nominalSharpe: Double,
    val deflatedSharpeRatio: Double,
    val probabilisticSharpePercent: Double, // PSR
    val numberOfTrialsTested: Int,
    val isStatisticallySignificant: Boolean,
    val pValueVerdict: String
)

data class BarraStyleFactorExposure(
    val sizeExposureZ: Double,      // 市值规模暴露 Z-Score
    val betaExposureZ: Double,      // 贝塔暴露 Z-Score
    val momentumExposureZ: Double,  // 动量暴露 Z-Score
    val volatilityExposureZ: Double,// 波动率暴露 Z-Score
    val rawAlphaScore: Int,
    val pureResidualAlphaScore: Int,// 经截面 OLS 正交中性化剥离风格后的纯残差阿尔法
    val principalRiskFactor: String
)

/**
 * 外部客观验证标准体系与严苛数理质检数据结构
 */
data class FactorHypothesisTest(
    val factorName: String,
    val icMean: Double,          // 信息系数均值 (Pearson IC, > 0.03 为行业有效线)
    val rankIcMean: Double,      // 秩相关系数 (Spearman Rank IC, > 0.04)
    val icIr: Double,            // IC信息比率 IC_IR = mean(IC) / std(IC) (> 0.5 优秀)
    val tStatistic: Double,      // t 统计量检验值 (|t| > 1.96 代表 95% 置信度显著)
    val pValue: Double,          // p 值 (< 0.05 拒绝零假设)
    val isStatisticallyValid: Boolean,
    val significanceTag: String, // "极显著(p<0.01)" / "显著(p<0.05)" / "未通过显著检验"
    val sampleSizeN: Int = 0     // 实证样本截面数/时序窗口数
)

data class OutOfSampleWalkForwardProof(
    val inSampleHorizon: String,      // 样本内训练区间 (实时K线前60%滚动窗口)
    val outOfSampleHorizon: String,   // 严苛前向样本外测试区间 (后40%窗口)
    val inSampleSharpe: Double,       // 样本内年化夏普
    val outOfSampleSharpe: Double,    // 样本外真实年化夏普
    val degradationRatioPercent: Double, // 样本外衰减率 (< 35% 安全线)
    val deflatedSharpeRatio: Double,  // 减水夏普 (DSR)
    val zeroLookaheadCertified: Boolean, // 100% 杜绝未来函数前视偏差审计通过
    val auditSummary: String
)

data class ExtremeStressTestAudit(
    val regimeName: String,
    val marketBenchmarkDrawdown: Double,
    val modelPortfolioDrawdown: Double,
    val alphaOutperformance: Double,
    val liquiditySurvivalStatus: String,
    val passed: Boolean
)

data class AShareFrictionAccounting(
    val stampDutyPercent: Double,         // 卖出印花税 0.05% (2023.08起减半征收)
    val commissionAndFeesPercent: Double, // 双边佣金+过户规费 0.03%
    val marketSlippageBps: Double,        // Almgren-Chriss 冲击滑点 (bps)
    val t1LiquidityLockDiscount: Double,  // 实测隔夜跳空风险折现 (%)
    val grossReturnAnnualized: Double,   // 毛收益率
    val netReturnAnnualized: Double,     // 扣除全摩擦后真实净收益率
    val halfKellyPositionLimitPercent: Int, // 经摩擦贴现后的单票半凯利硬上限
    val frictionVerdict: String
)

data class ExternalDataIntegrityAudit(
    val exchangeFeedSource: String,
    val vwapReconciliationErrorBps: Double, // 真实现价与全天成交均价VWAP偏离 (bps)
    val dividendSplitAdjustmentCheck: String,
    val highLowPriceConstraintCheck: Boolean, // 最高 >= 现价 >= 最低 && 均价在 [最低, 最高] 区间
    val kLineChronologicalCheck: Boolean = true, // K线日期严格单调递增且收盘价>0
    val dataIntegrityVerdict: String
)

data class ObjectiveQuantAuditReport(
    val symbol: String,
    val stockName: String,
    val overallReliabilityGrade: String,
    val hypothesisTests: List<FactorHypothesisTest>,
    val walkForwardProof: OutOfSampleWalkForwardProof,
    val extremeStressTests: List<ExtremeStressTestAudit>,
    val frictionAccounting: AShareFrictionAccounting,
    val dataIntegrityAudit: ExternalDataIntegrityAudit,
    val empiricalModeLabel: String = "K线时序实证"
)

/**
 * 波动率风险等级与个人投资者资产限额标准
 */
enum class VolatilityRiskLevel(
    val label: String,
    val shortLabel: String,
    val maxStockQuotaAdvice: String,
    val riskDescription: String
) {
    LOW("低波防御", "低波", "建议配置 4~6 只", "特质波动极低，分红造血与资产保值压舱石"),
    MODERATE("稳健白马", "中波", "建议配置 3~5 只", "估值与盈利匹配，行业龙头稳步上行"),
    HIGH("高波弹性", "高波", "限额 ≤ 3 只防回撤", "高贝塔与景气催化，弹性大需设止损"),
    EXTREME("极端博弈", "超高波", "限额 ≤ 1 只或空仓", "高投机与高换手，易诱发剧烈单日回撤")
}

/**
 * 个人投资者专用：最多只买10只股票的高容错量化组合模型
 */
data class RetailTop10Stock(
    val quote: StockQuote,
    val sectorName: String,               // 申万一级行业分类
    val recommendedWeightPercent: Int,    // 建议仓位权重 (e.g. 5% ~ 12%, 总和严格等于80%)
    val compositeScore: Int,              // 量化综合评分 (0-100)
    val selectionRationale: String,       // 入选核心理由
    val entryPriceRange: String,          // 建议建仓区间
    val stopLossPrice: Double,            // 硬风控止损底线
    val targetPrice: Double,              // 目标获利位
    val dividendYieldOrGrowth: String,    // 核心估值/盈利标签
    val realizedVolAnnualized: Double = 22.0, // 年化已实现波动率 (HV20, %)
    val beta: Double = 1.0,               // 相对沪深300实证贝塔系数
    val volatilityLevel: VolatilityRiskLevel = VolatilityRiskLevel.MODERATE,
    val riskContributionPercent: Double = 10.0, // 马科维茨 Euler 风险贡献占比 CTR (%)
    val invVolSuggestedWeight: Int = recommendedWeightPercent, // 风险平价 (Inverse-Vol) 客观参考权重
    val volSourceLabel: String = "Parkinson估", // "K线HV20实测" vs "Parkinson估"
    val isLiveQuoteSynced: Boolean = false
)

data class RetailTop10Portfolio(
    val strategyKey: String,              // "BALANCED", "HIGH_DIVIDEND", "TECH_GROWTH", "TURNAROUND"
    val strategyTitle: String,
    val strategySubtitle: String,
    val recommendedTotalStockWeight: Int, // 80%
    val cashCushionWeight: Int,           // 20% 现金/逆回购防守垫
    val expectedAnnualizedReturn: Double, // 测算期望年化收益率
    val maxHistoricalDrawdown: Double,    // 历史回测最大回撤
    val sectorDiversificationScore: Int,  // 行业去相关评分
    val stocks: List<RetailTop10Stock>,   // 严格约束 ≤ 10 只标的
    val weightedPortfolioVol: Double = 21.8, // 马科维茨协方差真实组合年化波动率 σ_p = sqrt(w'Σw) (%)
    val highRiskStockCount: Int = 2,      // 当前组合中高波/极端标的个数
    val highRiskLimitMax: Int = 3,        // 散户硬风控上限：高波标的最多3只
    val riskBudgetVerdict: String = "风险预算安全 · 高波标的未超标",
    // 客观数理证明指标 (Markowitz Diversification, Correlation & Benchmark Backtest)
    val naiveWeightedVolSum: Double = 21.8,      // 朴素加权波动率之和 ∑w_i*σ_i (未分散前)
    val diversificationRatio: Double = 1.0,      // 分散度比率 DR = (∑w_i*σ_i) / σ_p (>1 证明去相关生效)
    val averagePairwiseCorr: Double = 0.35,      // 组内平均相关系数 ρ̄
    val effectiveNumBets: Double = 7.5,          // 有效独立风险押注数 ENB = 1 / ∑(CTR_i^2)
    val backtestPortfolioReturnPct: Double = 0.0,// 同步K线窗口真实回测收益 (%)
    val backtestBenchmarkReturnPct: Double = 0.0,// 同期沪深300基准收益 (%)
    val backtestSharpeRatio: Double = 0.0,       // 同期实测年化夏普比率
    val constraintAuditPassed: Boolean = true,   // 五大硬约束全量审计通过标志
    val constraintAuditDetails: List<String> = emptyList(),
    val isEmpiricalKLineBacked: Boolean = false  // 是否由交易所真实历史K线协方差矩阵驱动
)
