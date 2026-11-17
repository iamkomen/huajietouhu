package com.example.data.repository

import com.example.data.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * 顶级投行估值与「猫会计」财报穿透式 DCF 引擎
 *
 * 核心体系融合：
 * 1. 猫会计排雷与现金含金量穿透（净现比、维持性Capex扣减、大存大贷、商誉/应收账款压顶）
 * 2. 麦肯锡《价值评估》ROIC 驱动公式: FCFF_t = NOPAT_t * (1 - g_t / ROIC_t)，杜绝假增长虚高估值
 * 3. CAPM + 会计瑕疵治理溢价动态 WACC 与 10 年两阶段护城河衰减（Fade Rate）
 * 4. 银行/保险金融业自动切换为 超额ROE 剩余收益模型 (Excess Return on Equity)
 * 5. 买方核心实操武器：Reverse DCF（当前股价隐含复合增速反推）+ Bear/Base/Bull 情景加权 + 3×3 敏感性矩阵
 */
object DcfValuationEngine {

    private const val RISK_FREE_RATE_PCT = 2.20   // 中国10年期国债无风险利率 (%)
    private const val EQUITY_RISK_PREMIUM_PCT = 5.50 // A股实证股权风险溢价 ERP (%)
    private const val DEFAULT_TERMINAL_GROWTH_PCT = 2.10 // 永续增长率严格 <= 无风险利率/长期通胀锚

    private val financialNames = listOf("银行", "保险", "证券", "信托", "金控")
    private val financialCodes = setOf(
        "600036", "601318", "600030", "601398", "601288", "601988", "601939",
        "601166", "600000", "600016", "601601", "601628", "601336", "300059",
        "601688", "601211", "000001", "002142"
    )

    fun isFinancialInstitution(quote: StockQuote): Boolean {
        if (quote.code in financialCodes) return true
        return financialNames.any { quote.name.contains(it) }
    }

    /**
     * 若东方财富F10三表已同步则直接穿透采用；否则基于实时行情 PE(TTM)/PB/市值 通过杜邦恒等式透明反推，
     * 并明确标记数据来源，绝不使用固定比例“伪DCF”。
     */
    fun resolveFinancialSnapshot(
        quote: StockQuote,
        liveSnapshot: FinancialStatementSnapshot?
    ): FinancialStatementSnapshot {
        val marketCapB = quote.totalMarketCapHundredMillion.coerceAtLeast(10.0)
        val isFinancial = isFinancialInstitution(quote)

        if (liveSnapshot != null && liveSnapshot.symbol == quote.symbol) {
            val safeRoe = if (liveSnapshot.roePercent != 0.0) {
                liveSnapshot.roePercent
            } else if (liveSnapshot.equityBillion > 0.1) {
                (liveSnapshot.netProfitBillion / liveSnapshot.equityBillion) * 100.0
            } else if (quote.peTtm > 0.1 && quote.pb > 0.05) {
                (quote.pb / quote.peTtm) * 100.0
            } else 8.0

            val safeRoic = if (liveSnapshot.roicPercent != 0.0) {
                liveSnapshot.roicPercent
            } else {
                val investedCapital = (liveSnapshot.equityBillion + liveSnapshot.interestBearingDebtBillion - liveSnapshot.monetaryCashBillion * 0.5)
                    .coerceAtLeast(liveSnapshot.equityBillion * 0.65)
                    .coerceAtLeast(1.0)
                val estNopat = liveSnapshot.netProfitBillion + liveSnapshot.interestBearingDebtBillion * 0.026
                if (isFinancial) safeRoe else ((estNopat / investedCapital) * 100.0).coerceIn(-25.0, 55.0)
            }
            return liveSnapshot.copy(
                roePercent = round2(safeRoe),
                roicPercent = round2(safeRoic)
            )
        }

        // 实时杜邦反推口径（离线/等待F10回传期间的透明推导基准）
        val pb = quote.pb
        val pe = quote.peTtm
        val equityB = if (pb > 0.05) marketCapB / pb else marketCapB * 0.45
        val netProfitB = when {
            pe > 0.1 -> marketCapB / pe
            pe < -0.1 -> marketCapB / pe
            else -> -marketCapB * 0.02
        }
        val roePct = when {
            pe > 0.1 && pb > 0.05 -> (pb / pe) * 100.0
            pe < -0.1 -> -8.0
            else -> 4.0
        }

        val estNetMargin = when {
            isFinancial -> 0.28
            roePct >= 22.0 -> 0.26
            roePct >= 14.0 -> 0.14
            roePct >= 6.0 -> 0.08
            else -> 0.04
        }
        val revenueB = max(abs(netProfitB) / estNetMargin, equityB * 0.55)
        val estCashToEarnings = when {
            netProfitB <= 0.0 -> 0.35
            roePct >= 18.0 -> 1.08
            roePct >= 11.0 -> 0.96
            else -> 0.80
        }
        val ocfB = if (netProfitB > 0.0) netProfitB * estCashToEarnings else revenueB * 0.02
        val capexB = when {
            isFinancial -> 0.0
            roePct >= 20.0 -> revenueB * 0.032 // 高壁垒轻资产
            else -> revenueB * 0.068
        }
        val maintCapexB = if (isFinancial) 0.0 else min(capexB, revenueB * 0.030)
        val interestDebtB = if (isFinancial) 0.0 else equityB * (if (roePct >= 18.0) 0.12 else 0.32)
        val cashB = if (isFinancial) 0.0 else equityB * (if (roePct >= 18.0) 0.38 else 0.22)
        val netCashB = cashB - interestDebtB
        val roicPct = if (isFinancial) {
            roePct
        } else {
            val invCap = max(equityB + interestDebtB - cashB * 0.5, equityB * 0.7).coerceAtLeast(1.0)
            (netProfitB / invCap) * 100.0
        }

        return FinancialStatementSnapshot(
            symbol = quote.symbol,
            reportPeriod = "实时估值截面(T0)",
            sourceLabel = "行情PE/PB杜邦反推口径",
            isLiveAuditedData = false,
            revenueBillion = round2(revenueB),
            netProfitBillion = round2(netProfitB),
            deductedNetProfitBillion = round2(netProfitB * 0.94),
            operatingCashFlowBillion = round2(ocfB),
            capexBillion = round2(capexB),
            maintenanceCapexBillion = round2(maintCapexB),
            freeCashFlowBillion = round2(ocfB - capexB),
            monetaryCashBillion = round2(cashB),
            interestBearingDebtBillion = round2(interestDebtB),
            netCashBillion = round2(netCashB),
            accountsReceivableBillion = round2(revenueB * 0.09),
            inventoryBillion = round2(revenueB * 0.08),
            contractLiabilityBillion = round2(revenueB * 0.05),
            goodwillBillion = 0.0,
            equityBillion = round2(equityB),
            roicPercent = round2(roicPct.coerceIn(-25.0, 55.0)),
            roePercent = round2(roePct.coerceIn(-30.0, 55.0)),
            grossMarginPercent = if (roePct >= 18.0) 48.0 else 26.0,
            debtToAssetPercent = if (isFinancial) 86.0 else 42.0,
            cashToEarningsRatio = round2(estCashToEarnings),
            capexToOcfPercent = if (ocfB > 0.05) round1((capexB / ocfB) * 100.0) else 80.0,
            workingCapitalQualityNote = "基于实时 PE/PB 反推营运基准，等待F10财报同步校核"
        )
    }

    /**
     * 猫会计核心排雷审校：先看商业模式造血含金量与资产负债表排雷，再定折现惩罚溢价
     */
    fun performMaoAccountingAudit(
        quote: StockQuote,
        snapshot: FinancialStatementSnapshot,
        rawWaccPct: Double,
        stage1GrowthPct: Double
    ): AccountingForensicAudit {
        val isFinancial = isFinancialInstitution(quote)
        val flags = mutableListOf<AccountingRedFlag>()
        var penaltyPct = 0.0
        var cashHaircut = 1.0

        // 1. 净现比（利润含金量）排雷
        if (!isFinancial) {
            when {
                snapshot.netProfitBillion <= 0.0 -> {
                    penaltyPct += 2.0
                    cashHaircut = 0.60
                    flags.add(
                        AccountingRedFlag(
                            title = "归母净利润亏损 · 失去盈利锚",
                            severity = "高危红灯",
                            detail = "净利润为负(${snapshot.netProfitBillion}亿)，按保守清算/远期正常化利润率折价估值并加收2.0%风控溢价",
                            isAlert = true
                        )
                    )
                }
                snapshot.cashToEarningsRatio < 0.70 -> {
                    penaltyPct += 1.2
                    cashHaircut = 0.78
                    flags.add(
                        AccountingRedFlag(
                            title = "净现比过低 · 纸面富贵预警",
                            severity = "高危红灯",
                            detail = "净现比仅 ${snapshot.cashToEarningsRatio} (<0.70)，利润未能转化为真实经营现金流入，现金流按0.78x挤水分",
                            isAlert = true
                        )
                    )
                }
                snapshot.cashToEarningsRatio < 0.95 -> {
                    cashHaircut = 0.91
                    flags.add(
                        AccountingRedFlag(
                            title = "净现比偏弱 · 关注回款节奏",
                            severity = "审慎关注",
                            detail = "净现比 ${snapshot.cashToEarningsRatio}，略低于1.0理想健康线，需追踪应收账款周转变化",
                            isAlert = false
                        )
                    )
                }
                else -> {
                    flags.add(
                        AccountingRedFlag(
                            title = "净现比优异 · 利润真金白银",
                            severity = "绿灯通过",
                            detail = "净现比达 ${snapshot.cashToEarningsRatio} (≥0.95)，每1元利润均有扎实经营现金流兑现",
                            isAlert = false
                        )
                    )
                }
            }
        } else {
            flags.add(
                AccountingRedFlag(
                    title = "金融业口径切换 · 超额ROE模型",
                    severity = "绿灯通过",
                    detail = "银行/保险资产负债即主营业务，自动屏蔽FCFF并采用剩余收益(Excess Return)贴现净资产与ROE利差",
                    isAlert = false
                )
            )
        }

        // 2. 资本开支吞噬（商业模式轻重穿透）
        if (!isFinancial) {
            when {
                snapshot.capexToOcfPercent > 75.0 -> {
                    penaltyPct += 0.8
                    flags.add(
                        AccountingRedFlag(
                            title = "高资本开支吞噬（吞金兽警告）",
                            severity = "高危红灯",
                            detail = "Capex消耗了 ${snapshot.capexToOcfPercent}% 的经营现金流，自由现金流长期承压",
                            isAlert = true
                        )
                    )
                }
                snapshot.capexToOcfPercent > 45.0 -> {
                    flags.add(
                        AccountingRedFlag(
                            title = "中等扩产开支 · 需区分维持与扩张",
                            severity = "审慎关注",
                            detail = "Capex/OCF 为 ${snapshot.capexToOcfPercent}%，维持性投入约 ${snapshot.maintenanceCapexBillion}亿",
                            isAlert = false
                        )
                    )
                }
                else -> {
                    flags.add(
                        AccountingRedFlag(
                            title = "低耗资护城河 · 高自由现金转化",
                            severity = "绿灯通过",
                            detail = "Capex 仅占经营现金流 ${snapshot.capexToOcfPercent}%，利润大比例可转化为股东自由现金",
                            isAlert = false
                        )
                    )
                }
            }
        }

        // 3. 大存大贷 & 资产负债表洁净度排雷
        val cashB = snapshot.monetaryCashBillion
        val debtB = snapshot.interestBearingDebtBillion
        val eqB = snapshot.equityBillion.coerceAtLeast(1.0)
        if (!isFinancial) {
            if (cashB > eqB * 0.25 && debtB > eqB * 0.38 && snapshot.debtToAssetPercent >= 62.0) {
                penaltyPct += 1.5
                flags.add(
                    AccountingRedFlag(
                        title = "疑似「大存大贷」结构风险",
                        severity = "高危红灯",
                        detail = "账面货币资金 ${cashB}亿 与有息负债 ${debtB}亿 双高，加收1.5%流动性与受限资金折现惩罚",
                        isAlert = true
                    )
                )
            } else if (snapshot.netCashBillion >= 0.0) {
                flags.add(
                    AccountingRedFlag(
                        title = "净现金充沛 · 无有息债压顶",
                        severity = "绿灯通过",
                        detail = "货币资金(${cashB}亿)覆盖全部有息负债(${debtB}亿)，真实净现金 +${snapshot.netCashBillion}亿",
                        isAlert = false
                    )
                )
            } else {
                flags.add(
                    AccountingRedFlag(
                        title = "温和有息杠杆 · 净负债 ${round2(-snapshot.netCashBillion)}亿",
                        severity = "审慎关注",
                        detail = "EV 转股权价值已严格扣减净有息负债，资产负债率 ${snapshot.debtToAssetPercent}%",
                        isAlert = false
                    )
                )
            }
        }

        // 4. ROIC vs WACC 护城河 EVA 创造审计（麦肯锡第一铁律）
        val roicSpread = snapshot.roicPercent - rawWaccPct
        if (roicSpread <= 0.0) {
            penaltyPct += 0.5
            flags.add(
                AccountingRedFlag(
                    title = "ROIC 低于资金成本 · 警惕毁灭价值增长",
                    severity = "高危红灯",
                    detail = "ROIC (${snapshot.roicPercent}%) ≤ WACC (${round2(rawWaccPct)}%)，高增长消耗资本反降低每股内在价值",
                    isAlert = true
                )
            )
        } else if (roicSpread >= 8.0) {
            flags.add(
                AccountingRedFlag(
                    title = "高ROIC护城河验证 (EVA利差 +${round1(roicSpread)}%)",
                    severity = "绿灯通过",
                    detail = "投入资本回报率 ${snapshot.roicPercent}% 显著超越资本成本，少量再投资即可撬动内生复利增长",
                    isAlert = false
                )
            )
        }

        // 5. 商誉/应收/合同负债辅助核验
        if (!isFinancial && snapshot.goodwillBillion > eqB * 0.22) {
            penaltyPct += 0.8
            flags.add(
                AccountingRedFlag(
                    title = "商誉悬顶 · 并购减值隐患",
                    severity = "高危红灯",
                    detail = "商誉 ${snapshot.goodwillBillion}亿 占归母净资产 ${round1((snapshot.goodwillBillion / eqB) * 100.0)}%，警惕减值吞噬利润",
                    isAlert = true
                )
            )
        }

        val cappedPenalty = penaltyPct.coerceIn(0.0, 3.2)
        val roicForRate = snapshot.roicPercent.coerceAtLeast(3.5)
        val reinvestRatePct = ((stage1GrowthPct / roicForRate) * 100.0).coerceIn(0.0, 95.0)

        val grade = when {
            penaltyPct >= 2.0 -> "D·财务瑕疵/红灯预警"
            penaltyPct >= 0.8 -> "C·重资产或回款承压"
            !isFinancial && snapshot.cashToEarningsRatio >= 1.0 && roicSpread >= 6.0 && snapshot.capexToOcfPercent <= 45.0 ->
                "A·真现金奶牛(顶级造血)"
            isFinancial && snapshot.roePercent >= rawWaccPct + 2.0 ->
                "A·高ROE优质金融资产"
            else -> "B·稳健现金流质地"
        }

        val maoVerdict = when {
            penaltyPct >= 2.0 ->
                "猫会计穿透结论：${quote.name} 存在${flags.count { it.isAlert }}项高危红灯，账面利润与现金流/负债结构存在明显错配，已自动加收 ${round1(cappedPenalty)}% 折现惩罚并将可分配现金流按 ${round2(cashHaircut)}x 挤水分。"
            isFinancial ->
                "猫会计穿透结论：${quote.name} 属金融特许经营业态，核心锚点在于净资产(PB=${quote.pb})与可持续ROE(${snapshot.roePercent}%)之差，当ROE持续高于股权成本即具长期复利价值。"
            roicSpread >= 8.0 && snapshot.cashToEarningsRatio >= 0.95 ->
                "猫会计穿透结论：${quote.name} 属于典型「高ROIC(${snapshot.roicPercent}%) + 低Capex消耗(${snapshot.capexToOcfPercent}%) + 高净现比(${snapshot.cashToEarningsRatio})」顶级商业模式，${snapshot.workingCapitalQualityNote}。"
            else ->
                "猫会计穿透结论：${quote.name} 现金造血与资本投入基本匹配（再投资率约 ${round1(reinvestRatePct)}%），需结合当前股价隐含增速判断安全边际是否充足。"
        }

        return AccountingForensicAudit(
            isFinancialSector = isFinancial,
            cashQualityGrade = grade,
            moatRoicSpreadPercent = round2(roicSpread),
            reinvestmentRatePercent = round1(reinvestRatePct),
            cashConversionHaircut = round2(cashHaircut),
            waccRiskPenaltyPercent = round2(cappedPenalty),
            redFlags = flags,
            maoAccountingVerdict = maoVerdict
        )
    }

    /**
     * 执行完整机构级 DCF + Reverse DCF + 情景权重 + 3×3 敏感性矩阵计算
     */
    fun evaluateProfessionalDcf(
        quote: StockQuote,
        kLines: List<KLinePoint>,
        liveSnapshot: FinancialStatementSnapshot? = null,
        waccAdjustmentPercent: Double = 0.0,
        growthAdjustmentPercent: Double = 0.0
    ): Triple<FinancialStatementSnapshot, AccountingForensicAudit, ProfessionalDcfModel> {
        val snapshot = resolveFinancialSnapshot(quote, liveSnapshot)
        val isFinancial = isFinancialInstitution(quote)
        val marketCapB = quote.totalMarketCapHundredMillion.coerceAtLeast(10.0)
        val price = quote.price.coerceAtLeast(0.01)

        // 1. CAPM 股权成本 Ke 与 税后债务成本 Kd 核算
        val realizedVol = QuantEngine.computeAnnualizedLogVolatility(kLines, 20)
            ?: QuantEngine.computeParkinsonVolatility(quote.high, quote.low)
            ?: (quote.amplitude * 5.8).coerceIn(12.0, 65.0)
        val beta = (0.62 + (realizedVol - 16.0) / 36.0).coerceIn(0.65, 1.55)
        val costOfEquityPct = RISK_FREE_RATE_PCT + beta * EQUITY_RISK_PREMIUM_PCT
        val afterTaxKdPct = if (snapshot.debtToAssetPercent > 65.0) 3.15 else 2.55

        val debtWeight = if (isFinancial) {
            0.0
        } else {
            val totalCap = marketCapB + snapshot.interestBearingDebtBillion.coerceAtLeast(0.0)
            (snapshot.interestBearingDebtBillion / totalCap).coerceIn(0.0, 0.45)
        }
        val rawWaccPct = costOfEquityPct * (1.0 - debtWeight) + afterTaxKdPct * debtWeight

        // 2. 确定客观显性期增速锚点 (ROIC × 合理再投资率，杜绝拍脑袋给 25% 增长)
        val baseStage1GrowthPct = when {
            isFinancial -> (snapshot.roePercent * 0.48).coerceIn(2.5, 11.5)
            snapshot.netProfitBillion <= 0.0 -> 4.0 // 亏损标的按行业保守复苏增速假设
            snapshot.roicPercent >= 22.0 -> (7.5 + (snapshot.roicPercent - 22.0) * 0.15).coerceIn(7.5, 12.5)
            snapshot.roicPercent >= 12.0 -> (5.0 + (snapshot.roicPercent - 12.0) * 0.25).coerceIn(5.0, 8.5)
            else -> 3.8
        } + growthAdjustmentPercent
        val clampedStage1GrowthPct = baseStage1GrowthPct.coerceIn(-12.0, 35.0)

        // 3. 执行猫会计穿透排雷
        val audit = performMaoAccountingAudit(quote, snapshot, rawWaccPct, clampedStage1GrowthPct)
        val effectiveWaccPct = (rawWaccPct + audit.waccRiskPenaltyPercent + waccAdjustmentPercent).coerceIn(6.5, 16.5)
        val terminalGrowthPct = DEFAULT_TERMINAL_GROWTH_PCT.coerceAtMost(effectiveWaccPct - 2.0)

        // 4. 计算 Base / Bear / Bull 估值与 10年明细
        val baseResult = runDcfScenario(
            quote = quote,
            snapshot = snapshot,
            audit = audit,
            waccPct = effectiveWaccPct,
            stage1GrowthPct = clampedStage1GrowthPct,
            terminalGrowthPct = terminalGrowthPct
        )
        val bearResult = runDcfScenario(
            quote = quote,
            snapshot = snapshot,
            audit = audit,
            waccPct = (effectiveWaccPct + 1.0).coerceAtMost(18.0),
            stage1GrowthPct = (clampedStage1GrowthPct - 2.5).coerceAtLeast(-6.0),
            terminalGrowthPct = (terminalGrowthPct - 0.5).coerceAtLeast(1.0)
        )
        val bullResult = runDcfScenario(
            quote = quote,
            snapshot = snapshot,
            audit = audit,
            waccPct = (effectiveWaccPct - 0.7).coerceAtLeast(6.0),
            stage1GrowthPct = (clampedStage1GrowthPct + 2.2).coerceAtMost(24.0),
            terminalGrowthPct = (terminalGrowthPct + 0.3).coerceAtMost(2.5)
        )

        val weightedPrice = round2(
            bearResult.intrinsicPrice * 0.25 +
                baseResult.intrinsicPrice * 0.50 +
                bullResult.intrinsicPrice * 0.25
        )
        val marginOfSafetyPct = if (weightedPrice > 0.01) {
            round1(((weightedPrice - price) / weightedPrice) * 100.0)
        } else -99.9

        // 5. 逆向 DCF 求解：当前股价隐含未来10年什么水平的前5年复合增速？
        val impliedGrowth = solveImpliedReverseGrowth(
            quote = quote,
            snapshot = snapshot,
            audit = audit,
            waccPct = effectiveWaccPct,
            terminalGrowthPct = terminalGrowthPct
        )
        val reverseVerdict = buildReverseDcfVerdict(
            impliedGrowthPct = impliedGrowth,
            price = price,
            weightedPrice = weightedPrice,
            roicPct = snapshot.roicPercent,
            waccPct = effectiveWaccPct
        )

        // 6. 构建 3×3 (WACC × Terminal Growth) 敏感性矩阵
        val waccAxis = listOf(
            round1((effectiveWaccPct - 1.0).coerceAtLeast(6.0)),
            round1(effectiveWaccPct),
            round1((effectiveWaccPct + 1.0).coerceAtMost(18.0))
        )
        val gInfAxis = listOf(
            round1((terminalGrowthPct - 0.5).coerceAtLeast(1.0)),
            round1(terminalGrowthPct),
            round1((terminalGrowthPct + 0.5).coerceAtMost(2.5))
        )
        val matrix = waccAxis.map { w ->
            gInfAxis.map { g ->
                val cellVal = runDcfScenario(
                    quote = quote,
                    snapshot = snapshot,
                    audit = audit,
                    waccPct = w,
                    stage1GrowthPct = clampedStage1GrowthPct,
                    terminalGrowthPct = g
                ).intrinsicPrice
                val upside = ((cellVal - price) / price) * 100.0
                DcfSensitivityCell(
                    waccPercent = w,
                    terminalGrowthPercent = g,
                    intrinsicPrice = round2(cellVal),
                    upsidePercent = round1(upside)
                )
            }
        }

        val tvSharePct = if (baseResult.evBillion > 0.01) {
            round1((baseResult.pvTerminalBillion / baseResult.evBillion) * 100.0)
        } else 0.0

        val modelLabel = if (isFinancial) {
            "金融业超额ROE剩余收益模型 (Excess Return)"
        } else {
            "ROIC驱动两阶段FCFF贴现模型"
        }

        val conclusion = when {
            marginOfSafetyPct >= 25.0 ->
                "具备显著深度安全边际(+${marginOfSafetyPct}%)，概率加权公允价 ¥${weightedPrice} 高于现价，现金流支撑扎实"
            marginOfSafetyPct >= 5.0 ->
                "估值具备温和安全边际(+${marginOfSafetyPct}%)，处于合理偏低配置区间"
            marginOfSafetyPct >= -12.0 ->
                "现价基本合理定价（安全边际 ${marginOfSafetyPct}%），赚取企业未来内生ROE/ROIC复利为主"
            else ->
                "⚠️ 现价透支未来现金流（安全边际 ${marginOfSafetyPct}%），市场预期过高，谨防杀估值回撤"
        }

        val dcfModel = ProfessionalDcfModel(
            modelTypeLabel = modelLabel,
            dataSourceBadge = snapshot.sourceLabel,
            reportPeriodLabel = snapshot.reportPeriod,
            riskFreeRatePercent = RISK_FREE_RATE_PCT,
            equityRiskPremiumPercent = EQUITY_RISK_PREMIUM_PCT,
            betaUsed = round2(beta),
            costOfEquityPercent = round2(costOfEquityPct),
            afterTaxCostOfDebtPercent = round2(afterTaxKdPct),
            waccPercent = round2(effectiveWaccPct),
            stage1GrowthPercent = round1(clampedStage1GrowthPct),
            terminalGrowthPercent = round2(terminalGrowthPct),
            terminalRoicPercent = round2(baseResult.terminalRoicPct),
            pvExplicitFcfBillion = round2(baseResult.pvExplicitBillion),
            pvTerminalValueBillion = round2(baseResult.pvTerminalBillion),
            terminalValueSharePercent = tvSharePct,
            enterpriseValueBillion = round2(baseResult.evBillion),
            netCashAdjustmentBillion = round2(baseResult.netCashAdjBillion),
            equityValueBillion = round2(baseResult.equityValueBillion),
            currentMarketCapBillion = round2(marketCapB),
            bearIntrinsicPrice = round2(bearResult.intrinsicPrice),
            baseIntrinsicPrice = round2(baseResult.intrinsicPrice),
            bullIntrinsicPrice = round2(bullResult.intrinsicPrice),
            probabilityWeightedPrice = weightedPrice,
            marginOfSafetyPercent = marginOfSafetyPct,
            impliedReverseGrowthPercent = impliedGrowth,
            reverseDcfVerdict = reverseVerdict,
            yearlyProjections = baseResult.projections,
            sensitivityGrid = DcfSensitivityGrid(waccAxis, gInfAxis, matrix),
            institutionalConclusion = conclusion
        )

        return Triple(snapshot, audit, dcfModel)
    }

    private data class ScenarioValuationOutput(
        val intrinsicPrice: Double,
        val equityValueBillion: Double,
        val evBillion: Double,
        val pvExplicitBillion: Double,
        val pvTerminalBillion: Double,
        val netCashAdjBillion: Double,
        val terminalRoicPct: Double,
        val projections: List<DcfYearProjection>
    )

    private fun runDcfScenario(
        quote: StockQuote,
        snapshot: FinancialStatementSnapshot,
        audit: AccountingForensicAudit,
        waccPct: Double,
        stage1GrowthPct: Double,
        terminalGrowthPct: Double
    ): ScenarioValuationOutput {
        val marketCapB = quote.totalMarketCapHundredMillion.coerceAtLeast(10.0)
        val price = quote.price.coerceAtLeast(0.01)
        val r = (waccPct / 100.0).coerceAtLeast(0.055)
        val g1 = stage1GrowthPct / 100.0
        val gInf = (terminalGrowthPct / 100.0).coerceAtMost(r - 0.022)

        if (audit.isFinancialSector) {
            // 金融业剩余收益模型 (Excess Return on Equity)
            val bv0 = snapshot.equityBillion.coerceAtLeast(marketCapB * 0.25)
            val roe0 = (snapshot.roePercent / 100.0).coerceIn(0.03, 0.22)
            val terminalRoe = if (roe0 > r) r + (roe0 - r) * 0.35 else roe0
            var prevBv = bv0
            var pvExplicit = 0.0
            val projections = mutableListOf<DcfYearProjection>()

            for (t in 1..10) {
                val fadeWeight = if (t <= 5) 0.0 else (t - 5) / 5.0
                val gT = g1 * (1.0 - fadeWeight) + gInf * fadeWeight
                val roeT = roe0 * (1.0 - fadeWeight) + terminalRoe * fadeWeight
                val excessReturn = (roeT - r) * prevBv
                val pvT = excessReturn / (1.0 + r).pow(t.toDouble())
                pvExplicit += pvT

                val netIncT = prevBv * roeT
                val reinvestT = prevBv * gT
                prevBv *= (1.0 + gT)

                projections.add(
                    DcfYearProjection(
                        year = t,
                        growthRatePercent = round1(gT * 100.0),
                        roicPercent = round1(roeT * 100.0),
                        nopatBillion = round2(netIncT),
                        reinvestmentBillion = round2(reinvestT),
                        fcffBillion = round2(excessReturn),
                        discountedPvBillion = round2(pvT)
                    )
                )
            }
            val terminalExcess = ((terminalRoe - r) * prevBv) / (r - gInf).coerceAtLeast(0.022)
            val pvTerminal = terminalExcess / (1.0 + r).pow(10.0)
            val equityVal = max(bv0 * 0.40, bv0 + pvExplicit + pvTerminal)
            val fairPrice = price * (equityVal / marketCapB)

            return ScenarioValuationOutput(
                intrinsicPrice = fairPrice,
                equityValueBillion = equityVal,
                evBillion = equityVal,
                pvExplicitBillion = pvExplicit,
                pvTerminalBillion = pvTerminal,
                netCashAdjBillion = 0.0,
                terminalRoicPct = terminalRoe * 100.0,
                projections = projections
            )
        }

        // 非金融业：ROIC 驱动两阶段 FCFF 模型
        val startingNopatB = computeStartingNopatBillion(snapshot, audit, marketCapB)
        val rawRoicPct = snapshot.roicPercent.coerceIn(3.5, 48.0)
        // 竞争衰减（Competitive Fade Rate）：强护城河在永续期仅部分向 WACC 均值回归，普通企业回归 WACC
        val terminalRoicPct = when {
            rawRoicPct >= waccPct + 10.0 -> max(waccPct + 3.0, rawRoicPct * 0.58 + waccPct * 0.42)
            rawRoicPct >= waccPct + 2.0 -> max(waccPct + 0.5, rawRoicPct * 0.35 + waccPct * 0.65)
            else -> min(rawRoicPct, waccPct)
        }

        var prevNopat = startingNopatB
        var pvExplicit = 0.0
        val projections = mutableListOf<DcfYearProjection>()

        for (t in 1..10) {
            val fade = if (t <= 5) 0.0 else (t - 5) / 5.0
            val gT = g1 * (1.0 - fade) + gInf * fade
            val roicT = ((rawRoicPct * (1.0 - fade) + terminalRoicPct * fade) / 100.0).coerceAtLeast(0.035)
            val nopatT = prevNopat * (1.0 + gT)
            val reinvestRatio = if (gT > 0.0) (gT / roicT).coerceIn(0.0, 0.85) else 0.0
            val reinvestT = nopatT * reinvestRatio
            val fcffT = nopatT - reinvestT
            val pvT = fcffT / (1.0 + r).pow(t.toDouble())
            pvExplicit += pvT
            prevNopat = nopatT

            projections.add(
                DcfYearProjection(
                    year = t,
                    growthRatePercent = round1(gT * 100.0),
                    roicPercent = round1(roicT * 100.0),
                    nopatBillion = round2(nopatT),
                    reinvestmentBillion = round2(reinvestT),
                    fcffBillion = round2(fcffT),
                    discountedPvBillion = round2(pvT)
                )
            )
        }

        val termRoicDec = (terminalRoicPct / 100.0).coerceAtLeast(0.035)
        val terminalReinvestRatio = (gInf / termRoicDec).coerceIn(0.0, 0.78)
        val nopatYear11 = prevNopat * (1.0 + gInf)
        val terminalFcff11 = nopatYear11 * (1.0 - terminalReinvestRatio)
        val terminalValue10 = terminalFcff11 / (r - gInf).coerceAtLeast(0.022)
        val pvTerminal = terminalValue10 / (1.0 + r).pow(10.0)

        val ev = max(0.0, pvExplicit + pvTerminal)
        val netCashAdj = snapshot.netCashBillion
        val equityVal = max(marketCapB * 0.05, ev + netCashAdj)
        val fairPrice = price * (equityVal / marketCapB)

        return ScenarioValuationOutput(
            intrinsicPrice = fairPrice,
            equityValueBillion = equityVal,
            evBillion = ev,
            pvExplicitBillion = pvExplicit,
            pvTerminalBillion = pvTerminal,
            netCashAdjBillion = netCashAdj,
            terminalRoicPct = terminalRoicPct,
            projections = projections
        )
    }

    private fun computeStartingNopatBillion(
        snapshot: FinancialStatementSnapshot,
        audit: AccountingForensicAudit,
        marketCapB: Double
    ): Double {
        if (snapshot.netProfitBillion <= 0.0) {
            // 亏损企业：不允许输出虚假高估值，采用远期正常化保守营业利润（营收*3.5%或市值*1.2%）按执行风险折价
            val normalizedBase = max(snapshot.revenueBillion * 0.035, marketCapB * 0.012)
            return normalizedBase * 0.55
        }
        val unleveredNopat = snapshot.netProfitBillion + snapshot.interestBearingDebtBillion * 0.026
        val adjustedAccountingNopat = unleveredNopat * audit.cashConversionHaircut
        return if (snapshot.isLiveAuditedData && snapshot.operatingCashFlowBillion > 0.0) {
            val ownerEarnings = max(0.0, snapshot.operatingCashFlowBillion - snapshot.maintenanceCapexBillion)
            0.60 * adjustedAccountingNopat + 0.40 * max(ownerEarnings, adjustedAccountingNopat * 0.50)
        } else {
            adjustedAccountingNopat
        }
    }

    /**
     * 逆向 DCF 二分数值求解：反推当前市场总市值/股价隐含了未来 10 年多少的 1-5 年复合增速
     */
    private fun solveImpliedReverseGrowth(
        quote: StockQuote,
        snapshot: FinancialStatementSnapshot,
        audit: AccountingForensicAudit,
        waccPct: Double,
        terminalGrowthPct: Double
    ): Double? {
        val targetCap = quote.totalMarketCapHundredMillion.coerceAtLeast(10.0)
        var low = -12.0
        var high = 45.0

        val eqLow = runDcfScenario(quote, snapshot, audit, waccPct, low, terminalGrowthPct).equityValueBillion
        val eqHigh = runDcfScenario(quote, snapshot, audit, waccPct, high, terminalGrowthPct).equityValueBillion

        if (eqHigh <= eqLow) return null
        if (eqLow >= targetCap) return low
        if (eqHigh <= targetCap) return null // 连 +45% 复合增速都无法支撑当前股价

        repeat(34) {
            val mid = (low + high) / 2.0
            val eqMid = runDcfScenario(quote, snapshot, audit, waccPct, mid, terminalGrowthPct).equityValueBillion
            if (eqMid < targetCap) {
                low = mid
            } else {
                high = mid
            }
        }
        return round1((low + high) / 2.0)
    }

    private fun buildReverseDcfVerdict(
        impliedGrowthPct: Double?,
        price: Double,
        weightedPrice: Double,
        roicPct: Double,
        waccPct: Double
    ): String {
        if (impliedGrowthPct == null) {
            return if (roicPct <= waccPct) {
                "逆向DCF警示：因 ROIC(${round1(roicPct)}%) ≤ WACC(${round1(waccPct)}%)，单纯依靠增长无法创造估值溢价，当前估值显著偏高"
            } else {
                "逆向DCF红牌：当前股价即便按未来前5年 >45% 超高速复合增长也无法算平，属于极端估值透支"
            }
        }
        return when {
            impliedGrowthPct <= 0.0 ->
                "逆向DCF极度低估：当前股价仅隐含负增长/零增长(${impliedGrowthPct}%)，市场悲观定价已给出厚实安全垫"
            impliedGrowthPct <= 6.5 && weightedPrice >= price ->
                "逆向DCF稳健可控：现价仅隐含未来增速 ${impliedGrowthPct}%，低于历史长期复利中枢，买方赔率极佳"
            impliedGrowthPct <= 16.0 ->
                "逆向DCF中性略高：当前股价隐含未来1-5年复合增速约 ${impliedGrowthPct}%，需紧盯季度业绩兑现度"
            else ->
                "逆向DCF高预期预警：现价已计入 ${impliedGrowthPct}% 的激进高增长预期，容错率较低"
        }
    }

    /**
     * 生成「猫会计说投资」交互式 DCF 计算器的实盘默认参数锚点
     */
    fun defaultCustomDcfInput(
        quote: StockQuote,
        kLines: List<KLinePoint>,
        liveSnapshot: FinancialStatementSnapshot? = null
    ): CustomDcfInput {
        val (snapshot, audit, dcf) = evaluateProfessionalDcf(quote, kLines, liveSnapshot)
        val rawOwnerFcf = (snapshot.operatingCashFlowBillion - snapshot.maintenanceCapexBillion)
        val maoConservativeFcf = if (snapshot.netProfitBillion > 0.0 && rawOwnerFcf > 0.0) {
            rawOwnerFcf * audit.cashConversionHaircut
        } else {
            snapshot.freeCashFlowBillion
        }
        return CustomDcfInput(
            baseFcfBillion = round2(maoConservativeFcf),
            stage1GrowthPercent = round1(dcf.stage1GrowthPercent),
            discountRatePercent = round2(dcf.waccPercent),
            projectionYears = 5,
            terminalMethod = DcfTerminalMethod.PERPETUAL_GROWTH,
            terminalGrowthPercent = round2(dcf.terminalGrowthPercent),
            terminalMultiple = 15.0,
            netCashBillion = if (audit.isFinancialSector) 0.0 else round2(snapshot.netCashBillion),
            safetyDiscountPercent = 30.0
        )
    }

    /**
     * 交互式 DCF 估值计算器核心引擎：
     * - 显性预测期逐年复利折现：FCF_t = FCF_0 * (1 + g)^t, PV_t = FCF_t / (1 + r)^t
     * - 终值双算法：永续增长法 TV_N = FCF_N * (1 + g∞) / (r - g∞) 或 退出倍数法 TV_N = FCF_N * Multiple
     * - 企业价值转归母股权价值：Equity = PV(FCF_1..N) + PV(TV_N) + NetCash
     * - 结合「猫会计说投资」心法输出安全边际、七折击球买点价及风险预警
     */
    fun computeCustomDcf(
        quote: StockQuote,
        input: CustomDcfInput
    ): CustomDcfResult {
        val marketCapB = quote.totalMarketCapHundredMillion.coerceAtLeast(10.0)
        val price = quote.price.coerceAtLeast(0.01)
        val nYears = input.projectionYears.coerceIn(1, 15)
        val r = (input.discountRatePercent / 100.0).coerceIn(0.01, 0.50)
        val g = (input.stage1GrowthPercent / 100.0).coerceIn(-0.50, 1.00)
        val fcf0 = input.baseFcfBillion

        var pvExplicit = 0.0
        var fcfN = fcf0
        val rows = ArrayList<CustomDcfYearRow>(nYears)
        for (t in 1..nYears) {
            val fcfT = fcf0 * (1.0 + g).pow(t.toDouble())
            val discountFactor = 1.0 / (1.0 + r).pow(t.toDouble())
            val pvT = fcfT * discountFactor
            pvExplicit += pvT
            fcfN = fcfT
            rows.add(
                CustomDcfYearRow(
                    year = t,
                    growthPercent = round1(g * 100.0),
                    projectedFcfBillion = round2(fcfT),
                    discountFactor = round(discountFactor * 1000.0) / 1000.0,
                    presentValueBillion = round2(pvT)
                )
            )
        }

        val warnings = mutableListOf<String>()
        if (fcf0 <= 0.0) {
            warnings.add("基期自由现金流 ≤ 0，持续烧钱阶段企业不具备正向DCF可算性，请改用重置成本或困境反转折价。")
        }

        val gInf = input.terminalGrowthPercent / 100.0
        val hasInvalidPerpetualSpread = input.terminalMethod == DcfTerminalMethod.PERPETUAL_GROWTH &&
            input.discountRatePercent <= input.terminalGrowthPercent

        val tvUndiscounted = when (input.terminalMethod) {
            DcfTerminalMethod.PERPETUAL_GROWTH -> {
                if (hasInvalidPerpetualSpread) {
                    warnings.add("折现率 r(${round2(input.discountRatePercent)}%) 必须严格大于永续增速 g∞(${round2(input.terminalGrowthPercent)}%)，已按最低利差 0.5% 防溢出保护。")
                }
                if (input.terminalGrowthPercent > 3.0) {
                    warnings.add("猫会计心法警示：永续增速 ${round2(input.terminalGrowthPercent)}% 高于长期国债与名义GDP锚(≤3%)，远期终值存在高估幻觉。")
                }
                val spread = (r - gInf).coerceAtLeast(0.005)
                max(0.0, fcfN * (1.0 + gInf) / spread)
            }
            DcfTerminalMethod.EXIT_MULTIPLE -> {
                if (input.terminalMultiple > 25.0) {
                    warnings.add("终值退出倍数 ${round1(input.terminalMultiple)}x 偏激进，价值派保守常取 10x~15x 自由现金流。")
                }
                max(0.0, fcfN * input.terminalMultiple.coerceAtLeast(0.0))
            }
        }

        if (input.stage1GrowthPercent > 20.0) {
            warnings.add("猫会计心法警示：显性期复合增速 ${round1(input.stage1GrowthPercent)}% >20%，线性外推高成长极易因竞争加剧而踩雷。")
        }

        val pvTerminal = tvUndiscounted / (1.0 + r).pow(nYears.toDouble())
        val ev = max(0.0, pvExplicit + pvTerminal)
        val equityVal = max(0.0, ev + input.netCashBillion)
        val tvSharePct = if (ev > 0.01) round1((pvTerminal / ev) * 100.0) else 0.0
        if (tvSharePct > 75.0 && fcf0 > 0.0) {
            warnings.add("终值现值占比达 ${tvSharePct}% (>75%)，估值高度依赖远期假设，务必守住安全边际。")
        }

        val intrinsicPrice = round2(price * (equityVal / marketCapB))
        val safetyRatio = 1.0 - (input.safetyDiscountPercent.coerceIn(0.0, 60.0) / 100.0)
        val strikePrice = round2(intrinsicPrice * safetyRatio)
        val mosPct = if (intrinsicPrice > 0.01) {
            round1(((intrinsicPrice - price) / intrinsicPrice) * 100.0)
        } else {
            -99.9
        }
        val impliedMultiple = if (fcf0 > 0.01) round1(equityVal / fcf0) else 0.0
        val discountLabel = if (input.safetyDiscountPercent <= 0.05) {
            "全额无折价"
        } else {
            "${round((safetyRatio * 10.0) * 10.0) / 10.0}折"
        }

        val conclusion = when {
            fcf0 <= 0.0 ->
                "⚠️ 现金流枯竭警告：基期FCF为负，难以用现金流折现支撑当前市值(现价 ¥${round2(price)})，建议优先核查应收与Capex吞噬。"
            price <= strikePrice ->
                "🟢 进入猫会计击球区：现价 ¥${round2(price)} 已低于${discountLabel}击球买点 ¥${strikePrice}（全额内在价 ¥${intrinsicPrice}，安全边际 +${mosPct}%），赔率极佳。"
            price <= intrinsicPrice ->
                "🟡 合理观察持有区：现价略低于内在价值 ¥${intrinsicPrice}（安全边际 +${mosPct}%），但尚未跌入${discountLabel}极佳击球价 ¥${strikePrice}。"
            else ->
                "🔴 高估透支警戒区：现价 ¥${round2(price)} 已高于测算内在价值 ¥${intrinsicPrice}（溢价 ${round1(-mosPct)}%），安全边际不足，谨防均值回归。"
        }

        return CustomDcfResult(
            pvExplicitFcfBillion = round2(pvExplicit),
            terminalValueUndiscountedBillion = round2(tvUndiscounted),
            pvTerminalValueBillion = round2(pvTerminal),
            terminalValueSharePercent = tvSharePct,
            enterpriseValueBillion = round2(ev),
            netCashAdjustmentBillion = round2(input.netCashBillion),
            equityValueBillion = round2(equityVal),
            currentMarketCapBillion = round2(marketCapB),
            intrinsicPrice = intrinsicPrice,
            maoStrikePrice = strikePrice,
            marginOfSafetyPercent = mosPct,
            impliedMultipleOnBaseFcf = impliedMultiple,
            yearlyRows = rows,
            maoWarnings = warnings,
            maoConclusion = conclusion
        )
    }

    /**
     * 策略选股专用独立 DCF 选股指标核算（复用机构级三情景 DCF + 猫会计排雷 + 逆向增速）：
     * - 独立输出 DCF 概率加权公允价、七折击球买点价、安全边际 (%)、隐含增长率与现金含金量评级
     */
    fun evaluateScreenerDcf(
        quote: StockQuote,
        kLines: List<KLinePoint> = emptyList(),
        liveSnapshot: FinancialStatementSnapshot? = null
    ): ScreenerDcfSignal {
        val (_, audit, dcf) = evaluateProfessionalDcf(quote, kLines, liveSnapshot)
        val intrinsic = round2(dcf.probabilityWeightedPrice)
        val maoStrike = round2(intrinsic * 0.70)
        val mos = round1(dcf.marginOfSafetyPercent)
        val isCleanProfitable = quote.peTtm > 0.0 && !audit.cashQualityGrade.startsWith("D")
        val isStrike = isCleanProfitable && mos >= 30.0
        val isUndervalued = isCleanProfitable && mos >= 15.0

        val mosPoints = (((mos + 20.0) / 70.0) * 55.0).coerceIn(0.0, 55.0)
        val moatPoints = (((audit.moatRoicSpreadPercent + 4.0) / 22.0) * 20.0).coerceIn(0.0, 20.0)
        val gradePoints = when {
            audit.cashQualityGrade.startsWith("A") -> 17.0
            audit.cashQualityGrade.startsWith("B") -> 12.0
            audit.cashQualityGrade.startsWith("C") -> 6.0
            else -> 0.0
        }
        val reverseBonus = when {
            dcf.impliedReverseGrowthPercent == null -> -10.0
            dcf.impliedReverseGrowthPercent <= 6.5 -> 5.0
            dcf.impliedReverseGrowthPercent <= 15.0 -> 2.0
            dcf.impliedReverseGrowthPercent > 25.0 -> -8.0
            else -> 0.0
        }
        var score = (mosPoints + moatPoints + gradePoints + reverseBonus).toInt()
        if (!isCleanProfitable) {
            score = score.coerceAtMost(35)
        }
        score = score.coerceIn(10, 99)

        val statusLabel = when {
            !isCleanProfitable && quote.peTtm <= 0.0 -> "亏损观望"
            !isCleanProfitable -> "现金流排雷"
            isStrike -> "七折击球区"
            isUndervalued -> "DCF低估"
            mos >= 0.0 -> "合理偏低"
            mos >= -15.0 -> "估值中性"
            else -> "DCF透支"
        }

        return ScreenerDcfSignal(
            intrinsicPrice = intrinsic,
            maoStrikePrice = maoStrike,
            marginOfSafetyPercent = mos,
            impliedReverseGrowthPercent = dcf.impliedReverseGrowthPercent,
            roicSpreadPercent = audit.moatRoicSpreadPercent,
            cashQualityGrade = audit.cashQualityGrade,
            isMaoStrikeZone = isStrike,
            isDcfUndervalued = isUndervalued,
            dcfScreenerScore = score,
            statusLabel = statusLabel
        )
    }

    /**
     * 计算目标公司历史 DCF 估值区间曲线：
     * - 严格使用真实历史日K线收盘价；
     * - 基于最新机构级三情景 DCF 内在价值锚点 + 隐含内生复利斜率 + 滚动20日实现波动率对 CAPM WACC 的动态扰动反推历史价值带；
     * - 不得凭空编造历史走势：若真实日K线不足，返回空点列并提示正在同步。
     */
    fun computeHistoricalDcfCurve(
        quote: StockQuote,
        kLines: List<KLinePoint>,
        liveSnapshot: FinancialStatementSnapshot? = null,
        maxPoints: Int = 75
    ): HistoricalDcfCurveSeries {
        val (_, audit, dcf) = evaluateProfessionalDcf(quote, kLines, liveSnapshot)
        val curFair = round2(dcf.probabilityWeightedPrice)
        val curBear = round2(dcf.bearIntrinsicPrice)
        val curBull = round2(dcf.bullIntrinsicPrice)
        val curStrike = round2(curFair * 0.70)
        val curPrice = round2(quote.price.coerceAtLeast(0.01))
        val curMos = round1(dcf.marginOfSafetyPercent)

        val zoneLabel = when {
            quote.peTtm <= 0.0 -> "亏损观望"
            audit.cashQualityGrade.startsWith("D") -> "现金流警示"
            curMos >= 30.0 -> "七折击球区"
            curMos >= 15.0 -> "DCF低估区"
            curMos >= -12.0 -> "合理价值带"
            else -> "高估透支区"
        }

        val window = if (kLines.size > maxPoints) kLines.takeLast(maxPoints) else kLines
        if (window.size < 2) {
            return HistoricalDcfCurveSeries(
                symbol = quote.symbol,
                stockName = quote.name,
                points = emptyList(),
                currentPrice = curPrice,
                currentFairPrice = curFair,
                currentStrikePrice = curStrike,
                currentBearPrice = curBear,
                currentBullPrice = curBull,
                currentMarginOfSafetyPercent = curMos,
                currentZoneLabel = zoneLabel,
                valuationPercentileInHistory = 50.0,
                isUsingRealKLines = false
            )
        }

        // 计算滚动20日年化波动率用于反映历史CAPM折现率温差（仅使用真实K线收盘）
        val fullSize = kLines.size
        val startOffset = fullSize - window.size
        val currentVol = QuantEngine.computeAnnualizedLogVolatility(kLines, 20)
            ?: QuantEngine.computeParkinsonVolatility(quote.high, quote.low)
            ?: 24.0
        val annualCompoundRate = (dcf.stage1GrowthPercent / 100.0).coerceIn(-0.04, 0.14)

        val lastIdx = window.lastIndex
        val curvePoints = window.mapIndexed { idx, pt ->
            val daysFromToday = (lastIdx - idx).toDouble()
            val timeGrowthFactor = (1.0 + annualCompoundRate).pow(-daysFromToday / 250.0)

            val volAdj = if (idx == lastIdx) {
                1.0
            } else {
                val endInFull = startOffset + idx + 1
                val subSlice = kLines.subList(max(0, endInFull - 22), endInFull)
                val volAtT = QuantEngine.computeAnnualizedLogVolatility(subSlice, 20) ?: currentVol
                val deltaVol = (volAtT - currentVol).coerceIn(-12.0, 12.0)
                (1.0 - deltaVol * 0.004).coerceIn(0.92, 1.08)
            }

            val factor = timeGrowthFactor * volAdj
            val fairAtT = if (idx == lastIdx) curFair else round2((curFair * factor).coerceAtLeast(0.01))
            val bearAtT = if (idx == lastIdx) curBear else round2((curBear * factor).coerceAtLeast(0.01))
            val bullAtT = if (idx == lastIdx) curBull else round2((curBull * factor).coerceAtLeast(0.01))
            val strikeAtT = if (idx == lastIdx) curStrike else round2((fairAtT * 0.70).coerceAtLeast(0.01))
            val closeAtT = if (idx == lastIdx && curPrice > 0.0) curPrice else round2(pt.close.coerceAtLeast(0.01))
            val mosAtT = if (fairAtT > 0.01) round1(((fairAtT - closeAtT) / fairAtT) * 100.0) else -99.9

            HistoricalDcfCurvePoint(
                date = pt.date,
                closePrice = closeAtT,
                strikePrice = strikeAtT,
                bearPrice = bearAtT,
                fairPrice = fairAtT,
                bullPrice = bullAtT,
                safetyMarginPercent = mosAtT
            )
        }

        val latestRatio = if (curFair > 0.01) curPrice / curFair else 1.0
        val cheaperOrEqualCount = curvePoints.count {
            val r = if (it.fairPrice > 0.01) it.closePrice / it.fairPrice else 1.0
            r <= latestRatio + 1e-6
        }
        val percentile = round1((cheaperOrEqualCount.toDouble() / curvePoints.size) * 100.0)

        return HistoricalDcfCurveSeries(
            symbol = quote.symbol,
            stockName = quote.name,
            points = curvePoints,
            currentPrice = curPrice,
            currentFairPrice = curFair,
            currentStrikePrice = curStrike,
            currentBearPrice = curBear,
            currentBullPrice = curBull,
            currentMarginOfSafetyPercent = curMos,
            currentZoneLabel = zoneLabel,
            valuationPercentileInHistory = percentile,
            isUsingRealKLines = true
        )
    }

    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
