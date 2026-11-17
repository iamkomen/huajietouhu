package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun ObjectiveValidationCard(
    report: ObjectiveQuantAuditReport,
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("因子显著", "前视审计", "黑天鹅", "摩擦扣除", "数据勾稽")

    val isGradeWarning = report.overallReliabilityGrade.contains("⚠️")
    val isGradeTop = report.overallReliabilityGrade.startsWith("AAA") || report.overallReliabilityGrade.startsWith("A级")
    val gradeBg = when {
        isGradeWarning -> StockRedSoft
        isGradeTop -> SuccessGreenSoft
        else -> WallStreetGoldSoft
    }
    val gradeColor = when {
        isGradeWarning -> StockRed
        isGradeTop -> SuccessGreen
        else -> WallStreetGold
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = adaptive.cardHorizontalPadding, vertical = 6.dp)
            .testTag("objective_validation_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(adaptive.cardInnerPadding)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(WallStreetGoldSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FactCheck,
                            contentDescription = "外部客观实证审计",
                            tint = WallStreetGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "严苛数理质检与外部客观实证",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = report.empiricalModeLabel,
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(gradeBg)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = report.overallReliabilityGrade.substringBefore(" · "),
                        color = gradeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub Tabs Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) WallStreetGoldSoft else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) WallStreetGold else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTab = index }
                            .padding(horizontal = 2.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) WallStreetGold else TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> FactorHypothesisTab(report.hypothesisTests, report.empiricalModeLabel)
                1 -> WalkForwardTab(report.walkForwardProof)
                2 -> StressRegimesTab(report.extremeStressTests)
                3 -> FrictionAccountingTab(report.frictionAccounting)
                4 -> DataIntegrityTab(report.dataIntegrityAudit)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactorHypothesisTab(
    tests: List<FactorHypothesisTest>,
    empiricalModeLabel: String
) {
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useCompactRows = maxWidth < 380.dp || fontScale >= 1.15f

        Column(modifier = Modifier.fillMaxWidth()) {
            if (useCompactRows) {
                // Narrow / Large FontScale: crisp 2-line per-factor rows so t-stat & p-value never clip
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tests.forEach { test ->
                        val pFormatted = when {
                            test.pValue < 0.01 -> "p<0.01"
                            test.pValue < 0.05 -> "p<0.05"
                            else -> String.format(java.util.Locale.US, "p=%.2f", test.pValue)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(TerminalSurfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = test.factorName,
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (test.isStatisticallyValid) SuccessGreenSoft else WallStreetGoldSoft)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = test.significanceTag,
                                            color = if (test.isStatisticallyValid) SuccessGreen else WallStreetGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Pearson IC: ${String.format(java.util.Locale.US, "%+.3f", test.icMean)}",
                                        color = if (abs(test.icMean) >= 0.03) StockRed else TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Rank IC: ${String.format(java.util.Locale.US, "%+.3f", test.rankIcMean)}",
                                        color = if (abs(test.rankIcMean) >= 0.04) StockRed else TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "t=${test.tStatistic}($pFormatted)",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Wide / Tablet / Standard width: aligned 5-column institutional table
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "因子名称", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1.3f))
                    Text(text = "Pearson IC", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(0.9f))
                    Text(text = "Rank IC", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(0.9f))
                    Text(text = "t统计量(p值)", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1.25f))
                    Text(text = "显著性判定", color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1.0f))
                }

                HorizontalDivider(color = TerminalBorder, thickness = 0.5.dp)

                tests.forEach { test ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = test.factorName,
                            color = TextPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1.3f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%+.3f", test.icMean),
                            color = if (abs(test.icMean) >= 0.03) StockRed else TextPrimary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(0.9f),
                            maxLines = 1
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%+.3f", test.rankIcMean),
                            color = if (abs(test.rankIcMean) >= 0.04) StockRed else TextPrimary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(0.9f),
                            maxLines = 1
                        )
                        val pFormatted = if (test.pValue < 0.01) "p<0.01" else if (test.pValue < 0.05) "p<0.05" else String.format(java.util.Locale.US, "p=%.2f", test.pValue)
                        Text(
                            text = "t=${test.tStatistic}($pFormatted)",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1.25f),
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (test.isStatisticallyValid) SuccessGreenSoft else WallStreetGoldSoft)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = test.significanceTag,
                                color = if (test.isStatisticallyValid) SuccessGreen else WallStreetGold,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val validCount = tests.count { it.isStatisticallyValid }
            val avgIcIr = if (tests.isNotEmpty()) {
                Math.round((tests.map { abs(it.icIr) }.average()) * 100.0) / 100.0
            } else 0.0
            val sampleN = tests.maxOfOrNull { it.sampleSizeN } ?: 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(8.dp)
            ) {
                Text(
                    text = "📌 客观实证结论 ($empiricalModeLabel · N=$sampleN): ${validCount}/${tests.size} 项因子通过 Student's t-test 显著性检验 (|t|>1.96, p<0.05)，平均 |IC_IR| = $avgIcIr。",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun WalkForwardTab(proof: OutOfSampleWalkForwardProof) {
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = isNarrowPortrait(maxWidth, fontScale)

        Column(modifier = Modifier.fillMaxWidth()) {
            if (isNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuditMetricBox(
                            label = "样本内训练夏普",
                            value = String.format(java.util.Locale.US, "%.2f", proof.inSampleSharpe),
                            sub = proof.inSampleHorizon,
                            color = WallStreetGold,
                            modifier = Modifier.weight(1f)
                        )
                        AuditMetricBox(
                            label = "样本外滚测夏普",
                            value = String.format(java.util.Locale.US, "%.2f", proof.outOfSampleSharpe),
                            sub = proof.outOfSampleHorizon,
                            color = if (proof.outOfSampleSharpe >= 0.0) SuccessGreen else StockGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AuditMetricBox(
                        label = "衰减率 / 减水夏普",
                        value = "${proof.degradationRatioPercent}%",
                        sub = "DSR: ${proof.deflatedSharpeRatio}",
                        color = QuantCyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuditMetricBox(
                        label = "样本内训练夏普",
                        value = String.format(java.util.Locale.US, "%.2f", proof.inSampleSharpe),
                        sub = proof.inSampleHorizon,
                        color = WallStreetGold,
                        modifier = Modifier.weight(1f)
                    )
                    AuditMetricBox(
                        label = "严苛样本外滚测夏普",
                        value = String.format(java.util.Locale.US, "%.2f", proof.outOfSampleSharpe),
                        sub = proof.outOfSampleHorizon,
                        color = if (proof.outOfSampleSharpe >= 0.0) SuccessGreen else StockGreen,
                        modifier = Modifier.weight(1f)
                    )
                    AuditMetricBox(
                        label = "衰减率 / 减水夏普",
                        value = "${proof.degradationRatioPercent}%",
                        sub = "DSR: ${proof.deflatedSharpeRatio}",
                        color = QuantCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (proof.zeroLookaheadCertified) SuccessGreenSoft else WallStreetGoldSoft)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (proof.zeroLookaheadCertified) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (proof.zeroLookaheadCertified) SuccessGreen else WallStreetGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (proof.zeroLookaheadCertified) {
                        "防前视偏差审计通过: 100% 杜绝未来函数 · t日决策严格仅依赖t-1日已收盘数据"
                    } else {
                        "等待历史K线样本积累 (需≥30日K线方可执行前60%/后40%无前视滚测)"
                    },
                    color = if (proof.zeroLookaheadCertified) SuccessGreen else WallStreetGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "📌 ${proof.auditSummary}",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StressRegimesTab(stressTests: List<ExtremeStressTestAudit>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        stressTests.forEach { test ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = test.regimeName,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (test.passed) SuccessGreenSoft else WallStreetGoldSoft)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "相对超额 +${test.alphaOutperformance}%",
                                color = if (test.passed) SuccessGreen else WallStreetGold,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "沪深300最大跌幅: ${test.marketBenchmarkDrawdown}%",
                            color = StockGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "本模型最大回撤: ${test.modelPortfolioDrawdown}%",
                            color = WallStreetGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "状态判定: ${test.liquiditySurvivalStatus}",
                        color = TextMuted,
                        fontSize = 9.5.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrictionAccountingTab(friction: AShareFrictionAccounting) {
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = isNarrowPortrait(maxWidth, fontScale)

        Column(modifier = Modifier.fillMaxWidth()) {
            if (isNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AuditMetricBox(
                            label = "卖出印花税",
                            value = "${friction.stampDutyPercent}%",
                            sub = "单边征收",
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        AuditMetricBox(
                            label = "双边佣金规费",
                            value = "${friction.commissionAndFeesPercent}%",
                            sub = "全额扣除",
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AuditMetricBox(
                            label = "冲击滑点成本",
                            value = "${friction.marketSlippageBps} bps",
                            sub = "Almgren测算",
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        AuditMetricBox(
                            label = "T+1隔夜跳空",
                            value = "-${friction.t1LiquidityLockDiscount}%",
                            sub = "90分位实测",
                            color = StockGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AuditMetricBox(
                        label = "卖出印花税",
                        value = "${friction.stampDutyPercent}%",
                        sub = "单边征收",
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    AuditMetricBox(
                        label = "双边佣金规费",
                        value = "${friction.commissionAndFeesPercent}%",
                        sub = "全额扣除",
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    AuditMetricBox(
                        label = "冲击滑点成本",
                        value = "${friction.marketSlippageBps} bps",
                        sub = "Almgren测算",
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    AuditMetricBox(
                        label = "T+1隔夜跳空",
                        value = "-${friction.t1LiquidityLockDiscount}%",
                        sub = "90分位实测",
                        color = StockGreen,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column {
                    Text(text = "样本外名义毛收益: ${friction.grossReturnAnnualized}%", color = TextMuted, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "全摩擦扣除净收益: ${String.format(java.util.Locale.US, "%+.1f%%", friction.netReturnAnnualized)}",
                        color = if (friction.netReturnAnnualized >= 0) SuccessGreen else StockGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(WallStreetGoldSoft)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "半凯利上限: ${friction.halfKellyPositionLimitPercent}%",
                        color = WallStreetGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "📌 ${friction.frictionVerdict}",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun DataIntegrityTab(audit: ExternalDataIntegrityAudit) {
    val isVwapReasonable = audit.vwapReconciliationErrorBps < 80.0
    val isSplitClean = !audit.dividendSplitAdjustmentCheck.contains("⚠️")
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalSurfaceElevated)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IntegrityItemRow(
                    label = "行情源网关认证",
                    status = audit.exchangeFeedSource,
                    isSuccess = true
                )
                IntegrityItemRow(
                    label = "实时均价VWAP偏差",
                    status = "${audit.vwapReconciliationErrorBps} bps (${if (isVwapReasonable) "处于日内均值合理带" else "盘口瞬时偏离"})",
                    isSuccess = isVwapReasonable
                )
                IntegrityItemRow(
                    label = "前复权(QFQ)连续性",
                    status = audit.dividendSplitAdjustmentCheck,
                    isSuccess = isSplitClean
                )
                IntegrityItemRow(
                    label = "高低价与VWAP约束",
                    status = if (audit.highLowPriceConstraintCheck) "最高 ≥ 现价 ≥ 最低 & VWAP∈[低,高] 成立" else "⚠️ 物理区间越界异常",
                    isSuccess = audit.highLowPriceConstraintCheck
                )
                IntegrityItemRow(
                    label = "K线时间序单调检验",
                    status = if (audit.kLineChronologicalCheck) "时间戳严格递增 · 零乱序/零零价" else "⚠️ K线序列存在乱序或零价",
                    isSuccess = audit.kLineChronologicalCheck
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "📌 ${audit.dataIntegrityVerdict}",
            color = if (audit.highLowPriceConstraintCheck && audit.kLineChronologicalCheck) TextSecondary else StockRed,
            fontSize = 10.5.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun IntegrityItemRow(label: String, status: String, isSuccess: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isSuccess) SuccessGreen else WallStreetGold,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status,
                color = if (isSuccess) TextPrimary else WallStreetGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AuditMetricBox(
    label: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalSurfaceElevated)
            .padding(6.dp)
    ) {
        Column {
            Text(text = label, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = sub, color = TextMuted, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
