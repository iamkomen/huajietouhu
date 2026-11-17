package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.QuantEngine
import com.example.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallStreetReportView(
    quote: StockQuote,
    kLines: List<KLinePoint>,
    liveFinancialSnapshot: FinancialStatementSnapshot? = null,
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = LocalAdaptiveLayout.current.cardHorizontalPadding,
    initiallyExpanded: Boolean = false
) {
    val adaptive = LocalAdaptiveLayout.current
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var waccDeltaPct by remember(quote.symbol) { mutableFloatStateOf(0f) }
    var growthDeltaPct by remember(quote.symbol) { mutableFloatStateOf(0f) }

    val report = remember(quote, kLines, liveFinancialSnapshot, waccDeltaPct, growthDeltaPct) {
        QuantEngine.generateWallStreetReport(
            quote = quote,
            kLines = kLines,
            liveSnapshot = liveFinancialSnapshot,
            waccAdjustmentPercent = (waccDeltaPct * 2f).roundToInt() / 2.0,
            growthAdjustmentPercent = (growthDeltaPct * 2f).roundToInt() / 2.0
        )
    }

    val dcf = report.professionalDcf
    val audit = report.accountingAudit
    val snapshot = report.financialSnapshot

    val consensusColor = when {
        report.analystConsensus.contains("UNDERWEIGHT") || report.analystConsensus.contains("CAUTIOUS") -> StockGreen
        report.analystConsensus.contains("STRONG BUY") || report.analystConsensus.contains("BUY") -> StockRed
        else -> WallStreetGold
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = 6.dp)
            .testTag("wall_street_report_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(adaptive.cardInnerPadding)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { isExpanded = !isExpanded }
                    .testTag("dcf_report_header_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WallStreetGoldSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "DCF估值研报",
                            tint = WallStreetGold,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "专业机构 DCF 与「猫会计」穿透研报",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = report.generationTime,
                            color = if (snapshot?.isLiveAuditedData == true) QuantCyan else TextMuted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(consensusColor.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = report.analystConsensus.substringBefore(" "),
                            color = consensusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起研报" else "展开研报",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Top Summary Badges (Always Visible Before Expansion)
            if (dcf != null && audit != null) {
                val mosColor = when {
                    dcf.marginOfSafetyPercent >= 10.0 -> SuccessGreen
                    dcf.marginOfSafetyPercent >= -5.0 -> WallStreetGold
                    else -> StockGreen
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SummaryPill(
                        label = "DCF公允锚 ¥${String.format(java.util.Locale.US, "%.2f", dcf.probabilityWeightedPrice)}",
                        accent = WallStreetGold
                    )
                    SummaryPill(
                        label = "安全边际 ${if (dcf.marginOfSafetyPercent >= 0) "+" else ""}${dcf.marginOfSafetyPercent}%",
                        accent = mosColor
                    )
                    SummaryPill(
                        label = audit.cashQualityGrade,
                        accent = if (audit.cashQualityGrade.startsWith("A")) QuantCyan else if (audit.cashQualityGrade.startsWith("D")) StockRed else WarningAmber
                    )
                    val revStr = dcf.impliedReverseGrowthPercent?.let { "现价隐含增速 ${it}%" } ?: "现价隐含增速 >45%预警"
                    SummaryPill(
                        label = revStr,
                        accent = if ((dcf.impliedReverseGrowthPercent ?: 99.0) <= 10.0) SuccessGreen else WarningAmber
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = report.fullEquityResearch.executiveSummary,
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            AnimatedVisibility(visible = isExpanded && dcf != null && audit != null && snapshot != null) {
                if (dcf != null && audit != null && snapshot != null) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // ---------------------------------------------------------
                        // 1. Institutional Multi-Scenario DCF Intrinsic Value
                        // ---------------------------------------------------------
                        DcfValuationOverviewSection(
                            quote = quote,
                            dcf = dcf,
                            hasSandboxOverride = (waccDeltaPct != 0f || growthDeltaPct != 0f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ---------------------------------------------------------
                        // 2. 「猫会计说投资」财务穿透、净现比与排雷审计
                        // ---------------------------------------------------------
                        MaoAccountingAuditSection(
                            snapshot = snapshot,
                            audit = audit
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ---------------------------------------------------------
                        // 3. CAPM / WACC / Reverse DCF 逆向预期拆解
                        // ---------------------------------------------------------
                        ReverseDcfAndWaccSection(dcf = dcf)

                        Spacer(modifier = Modifier.height(12.dp))

                        // ---------------------------------------------------------
                        // 4. 3x3 WACC × 永续增长率 敏感性热力矩阵
                        // ---------------------------------------------------------
                        DcfSensitivityMatrixSection(
                            currentPrice = quote.price,
                            grid = dcf.sensitivityGrid
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ---------------------------------------------------------
                        // 5. 交互式买方估值压力测试沙盒 (WACC & 增速微调)
                        // ---------------------------------------------------------
                        InteractiveDcfSandbox(
                            waccDeltaPct = waccDeltaPct,
                            growthDeltaPct = growthDeltaPct,
                            effectiveWacc = dcf.waccPercent,
                            effectiveGrowth = dcf.stage1GrowthPercent,
                            onWaccDeltaChange = { waccDeltaPct = it },
                            onGrowthDeltaChange = { growthDeltaPct = it },
                            onReset = {
                                waccDeltaPct = 0f
                                growthDeltaPct = 0f
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ---------------------------------------------------------
                        // 6. 10年显性折现明细抽样 (Y1 / Y3 / Y5 / Y10)
                        // ---------------------------------------------------------
                        ExplicitCashFlowTableSection(
                            isFinancial = audit.isFinancialSector,
                            projections = dcf.yearlyProjections
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.13f))
            .border(0.75.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DcfValuationOverviewSection(
    quote: StockQuote,
    dcf: ProfessionalDcfModel,
    hasSandboxOverride: Boolean
) {
    Text(
        text = "1. ${dcf.modelTypeLabel}" + if (hasSandboxOverride) " (沙盒推演中)" else "",
        color = WallStreetGold,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(6.dp))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        val isNarrow = isNarrowPortrait(maxWidth, LocalDensity.current.fontScale)
        val mosColor = if (dcf.marginOfSafetyPercent >= 0) SuccessGreen else StockGreen
        val revText = dcf.impliedReverseGrowthPercent?.let { "${it}%" } ?: ">45%"

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isNarrow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "概率加权内在价", color = TextMuted, fontSize = 10.5.sp)
                    Text(
                        text = "¥${String.format(java.util.Locale.US, "%.2f", dcf.probabilityWeightedPrice)}",
                        color = WallStreetGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBlock(
                        label = "安全边际",
                        value = "${if (dcf.marginOfSafetyPercent >= 0) "+" else ""}${dcf.marginOfSafetyPercent}%",
                        valueColor = mosColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBlock(
                        label = "逆向隐含增速",
                        value = revText,
                        valueColor = QuantCyan,
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBlock(
                        label = "概率加权内在价",
                        value = "¥${String.format(java.util.Locale.US, "%.2f", dcf.probabilityWeightedPrice)}",
                        valueColor = WallStreetGold,
                        modifier = Modifier.weight(1.2f)
                    )
                    MetricBlock(
                        label = "安全边际",
                        value = "${if (dcf.marginOfSafetyPercent >= 0) "+" else ""}${dcf.marginOfSafetyPercent}%",
                        valueColor = mosColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBlock(
                        label = "逆向隐含增速",
                        value = revText,
                        valueColor = QuantCyan,
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }
            }

            HorizontalDivider(color = TerminalBorder.copy(alpha = 0.7f), thickness = 0.8.dp)

            // Bear / Base / Bull Scenario Cards
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScenarioChip(
                    title = "悲观 Bear (25%)",
                    price = dcf.bearIntrinsicPrice,
                    currentPrice = quote.price,
                    accent = StockGreen
                )
                ScenarioChip(
                    title = "基准 Base (50%)",
                    price = dcf.baseIntrinsicPrice,
                    currentPrice = quote.price,
                    accent = WallStreetGold
                )
                ScenarioChip(
                    title = "乐观 Bull (25%)",
                    price = dcf.bullIntrinsicPrice,
                    currentPrice = quote.price,
                    accent = StockRed
                )
            }

            // EV to Equity Bridge
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalBg.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BridgeStat("10年FCF现值", "${dcf.pvExplicitFcfBillion}亿")
                BridgeStat("终值现值", "${dcf.pvTerminalValueBillion}亿(${dcf.terminalValueSharePercent}%)")
                BridgeStat("净现金调整", "${if (dcf.netCashAdjustmentBillion >= 0) "+" else ""}${dcf.netCashAdjustmentBillion}亿")
                BridgeStat("归母股权估值", "${dcf.equityValueBillion}亿 (现市值${dcf.currentMarketCapBillion}亿)")
            }

            if (dcf.terminalValueSharePercent > 75.0) {
                Text(
                    text = "⚠️ 机构预警：终值现值占比达 ${dcf.terminalValueSharePercent}% (>75%)，估值高度依赖远期稳态假设，务必留足安全边际。",
                    color = WarningAmber,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ScenarioChip(
    title: String,
    price: Double,
    currentPrice: Double,
    accent: Color
) {
    val diffPct = if (currentPrice > 0.0) ((price - currentPrice) / currentPrice) * 100.0 else 0.0
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(0.75.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Column {
            Text(text = title, color = TextMuted, fontSize = 9.5.sp, maxLines = 1)
            Text(
                text = "¥${String.format(java.util.Locale.US, "%.2f", price)} (${if (diffPct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", diffPct)}%)",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BridgeStat(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 9.5.sp, maxLines = 1)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(text = label, color = TextMuted, fontSize = 10.sp, maxLines = 1)
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaoAccountingAuditSection(
    snapshot: FinancialStatementSnapshot,
    audit: AccountingForensicAudit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = null,
            tint = QuantCyan,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "2. 「猫会计说投资」财务穿透与排雷体检",
            color = QuantCyan,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(modifier = Modifier.height(6.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("mao_accounting_audit_box"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Core Accounting Quality Metrics Grid
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BridgeStat("净现比(OCF/净利)", "${snapshot.cashToEarningsRatio}")
            BridgeStat("ROIC / ROE", "${snapshot.roicPercent}% / ${snapshot.roePercent}%")
            BridgeStat("EVA护城河利差", "${if (audit.moatRoicSpreadPercent >= 0) "+" else ""}${audit.moatRoicSpreadPercent}%")
            BridgeStat("Capex吞噬率", "${snapshot.capexToOcfPercent}%")
            BridgeStat("再投资率", "${audit.reinvestmentRatePercent}%")
            BridgeStat("账面资金 / 有息债", "${snapshot.monetaryCashBillion} / ${snapshot.interestBearingDebtBillion}亿")
        }

        HorizontalDivider(color = TerminalBorder.copy(alpha = 0.6f), thickness = 0.75.dp)

        audit.redFlags.forEach { flag ->
            val flagColor = when {
                flag.isAlert -> StockRed
                flag.severity.contains("审慎") -> WarningAmber
                else -> SuccessGreen
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(flagColor.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (flag.isAlert) Icons.Default.Warning else Icons.Default.VerifiedUser,
                    contentDescription = flag.severity,
                    tint = flagColor,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "[${flag.severity}] ${flag.title}",
                        color = flagColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = flag.detail,
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Text(
            text = audit.maoAccountingVerdict,
            color = TextPrimary,
            fontSize = 11.sp,
            lineHeight = 15.5.sp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReverseDcfAndWaccSection(dcf: ProfessionalDcfModel) {
    Text(
        text = "3. CAPM/WACC 折现中枢与 逆向DCF 预期拆解",
        color = QuantCyan,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(6.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("reverse_dcf_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BridgeStat("无风险利率 Rf", "${dcf.riskFreeRatePercent}%")
            BridgeStat("CAPM β / Ke", "${dcf.betaUsed} / ${dcf.costOfEquityPercent}%")
            BridgeStat("税后债务 Kd", "${dcf.afterTaxCostOfDebtPercent}%")
            BridgeStat("有效 WACC", "${dcf.waccPercent}%")
            BridgeStat("永续增速 g∞", "${dcf.terminalGrowthPercent}%")
            BridgeStat("终值ROIC回归", "${dcf.terminalRoicPercent}%")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(QuantCyanSoft)
                .border(0.75.dp, QuantCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text(
                text = dcf.reverseDcfVerdict,
                color = QuantCyan,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DcfSensitivityMatrixSection(
    currentPrice: Double,
    grid: DcfSensitivityGrid
) {
    Text(
        text = "4. WACC × 永续增长率(g∞) 3×3 敏感性热力矩阵",
        color = WarningAmber,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(6.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
            .padding(8.dp)
            .testTag("dcf_sensitivity_matrix"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WACC \\ g∞",
                color = TextMuted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.95f)
            )
            grid.terminalGrowthAxis.forEach { g ->
                Text(
                    text = "${g}%",
                    color = WallStreetGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        grid.matrix.forEachIndexed { rowIdx, rowCells ->
            val waccVal = grid.waccAxis.getOrNull(rowIdx) ?: 0.0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${waccVal}%",
                    color = if (rowIdx == 1) WallStreetGold else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (rowIdx == 1) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(0.95f)
                )
                rowCells.forEachIndexed { colIdx, cell ->
                    val isCenter = rowIdx == 1 && colIdx == 1
                    val isUndervalued = cell.intrinsicPrice >= currentPrice
                    val cellBg = when {
                        cell.upsidePercent >= 15.0 -> SuccessGreen.copy(alpha = 0.20f)
                        isUndervalued -> WallStreetGold.copy(alpha = 0.16f)
                        else -> StockGreen.copy(alpha = 0.14f)
                    }
                    val textColor = when {
                        cell.upsidePercent >= 15.0 -> SuccessGreen
                        isUndervalued -> WallStreetGold
                        else -> StockGreen
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(5.dp))
                            .background(cellBg)
                            .border(
                                width = if (isCenter) 1.dp else 0.dp,
                                color = if (isCenter) WallStreetGold else Color.Transparent,
                                shape = RoundedCornerShape(5.dp)
                            )
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "¥${String.format(java.util.Locale.US, "%.1f", cell.intrinsicPrice)}",
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Text(
                                text = "${if (cell.upsidePercent >= 0) "+" else ""}${cell.upsidePercent}%",
                                color = textColor.copy(alpha = 0.85f),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveDcfSandbox(
    waccDeltaPct: Float,
    growthDeltaPct: Float,
    effectiveWacc: Double,
    effectiveGrowth: Double,
    onWaccDeltaChange: (Float) -> Unit,
    onGrowthDeltaChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalBg.copy(alpha = 0.65f))
            .border(1.dp, WallStreetGold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("dcf_interactive_sandbox"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = WallStreetGold,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "5. 买方假设压力测试沙盒",
                    color = WallStreetGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalSurfaceElevated)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("dcf_sandbox_reset_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重置客观基准",
                    tint = TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = "重置基准", color = TextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }

        // Slider 1: WACC Override
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "要求回报率 WACC 微调", color = TextSecondary, fontSize = 10.5.sp)
            Text(
                text = "当前 ${effectiveWacc}% (${if (waccDeltaPct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", waccDeltaPct)}%)",
                color = WallStreetGold,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = waccDeltaPct,
            onValueChange = onWaccDeltaChange,
            valueRange = -1.5f..2.5f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = WallStreetGold,
                activeTrackColor = WallStreetGold,
                inactiveTrackColor = TerminalBorder
            ),
            modifier = Modifier.testTag("dcf_wacc_slider")
        )

        // Slider 2: Stage-1 Growth Override
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "前5年复合增速假设微调", color = TextSecondary, fontSize = 10.5.sp)
            Text(
                text = "当前 ${effectiveGrowth}% (${if (growthDeltaPct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", growthDeltaPct)}%)",
                color = QuantCyan,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = growthDeltaPct,
            onValueChange = onGrowthDeltaChange,
            valueRange = -5.0f..5.0f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = QuantCyan,
                activeTrackColor = QuantCyan,
                inactiveTrackColor = TerminalBorder
            ),
            modifier = Modifier.testTag("dcf_growth_slider")
        )
    }
}

@Composable
private fun ExplicitCashFlowTableSection(
    isFinancial: Boolean,
    projections: List<DcfYearProjection>
) {
    val sampled = remember(projections) {
        projections.filter { it.year in setOf(1, 3, 5, 10) }
    }
    if (sampled.isEmpty()) return

    Text(
        text = if (isFinancial) "6. 10年超额ROE剩余收益折现路径 (亿元)" else "6. 10年 ROIC 驱动现金流折现路径 (亿元)",
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(6.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
            .padding(8.dp)
            .testTag("dcf_cashflow_projection_table"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("年份", color = TextMuted, fontSize = 9.5.sp, modifier = Modifier.weight(0.6f))
            Text("增速", color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
            Text(if (isFinancial) "ROE" else "ROIC", color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
            Text(if (isFinancial) "超额收益" else "FCFF", color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("折现PV", color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = TerminalBorder.copy(alpha = 0.5f), thickness = 0.75.dp)
        sampled.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("T+${row.year}", color = WallStreetGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.6f))
                Text("${row.growthRatePercent}%", color = TextPrimary, fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.8f))
                Text("${row.roicPercent}%", color = QuantCyan, fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.8f))
                Text("${row.fcffBillion}", color = TextPrimary, fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text("${row.discountedPvBillion}", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            }
        }
    }
}
