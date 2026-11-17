package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.data.model.RetailTop10Portfolio
import com.example.data.model.RetailTop10Stock
import com.example.data.model.VolatilityRiskLevel
import com.example.ui.theme.*

@Composable
fun VolatilityHeatmapCard(
    portfolio: RetailTop10Portfolio,
    selectedFilterLevel: VolatilityRiskLevel?,
    onSelectFilterLevel: (VolatilityRiskLevel?) -> Unit,
    onSelectStockSlot: (RetailTop10Stock) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAuditDetails by remember { mutableStateOf(false) }
    val fontScale = LocalDensity.current.fontScale

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("volatility_heatmap_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val isNarrow = isNarrowPortrait(maxWidth, fontScale)
            val useTwoColumnHeatmap = isUltraNarrowPortrait(maxWidth, fontScale)

            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row: Title & High Risk Budget Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(WallStreetGoldSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = "波动率热力矩阵",
                                tint = WallStreetGold,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(7.dp))
                        Column {
                            Text(
                                text = "十绝持仓波动率热力与协方差矩阵",
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (portfolio.isEmpiricalKLineBacked) {
                                    "实盘70日K线协方差实测 · 颜色分级预警 · CTR风险预算"
                                } else {
                                    "全市场实盘同步中 · 行业协方差结构 · 10股风险预算"
                                },
                                color = TextMuted,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val isSafe = portfolio.highRiskStockCount <= portfolio.highRiskLimitMax
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSafe) SuccessGreenSoft else StockRedSoft)
                            .border(
                                1.dp,
                                if (isSafe) SuccessGreen.copy(alpha = 0.4f) else StockRed.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "高波: ${portfolio.highRiskStockCount}/${portfolio.highRiskLimitMax}只",
                            color = if (isSafe) SuccessGreen else StockRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Responsive 10-Cell Visual Heatmap Matrix:
                // - 2 rows x 5 cols on standard/wide widths (>= 340dp, normal fontScale)
                // - 5 rows x 2 cols horizontal compact tiles on ultra-narrow (< 340dp) or high fontScale
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (useTwoColumnHeatmap) {
                        val pairs = portfolio.stocks.chunked(2)
                        pairs.forEachIndexed { rowIdx, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                pair.forEachIndexed { colIdx, stock ->
                                    val rank = rowIdx * 2 + colIdx + 1
                                    CompactHorizontalHeatmapCell(
                                        rank = rank,
                                        stock = stock,
                                        onClick = { onSelectStockSlot(stock) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        val row1 = portfolio.stocks.take(5)
                        val row2 = portfolio.stocks.drop(5).take(5)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            row1.forEachIndexed { idx, stock ->
                                HeatmapCell(
                                    rank = idx + 1,
                                    stock = stock,
                                    onClick = { onSelectStockSlot(stock) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (row2.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                row2.forEachIndexed { idx, stock ->
                                    HeatmapCell(
                                        rank = idx + 6,
                                        stock = stock,
                                        onClick = { onSelectStockSlot(stock) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Markowitz Covariance Risk Metrics Strip (2+1 on ultra-narrow, 3 cols otherwise)
                if (useTwoColumnHeatmap) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            CovarianceStatPill(
                                label = "组合实测σ_p",
                                value = "${portfolio.weightedPortfolioVol}%",
                                sub = "未分散${portfolio.naiveWeightedVolSum}%",
                                color = if (portfolio.weightedPortfolioVol <= 24.0) SuccessGreen else WallStreetGold,
                                modifier = Modifier.weight(1f)
                            )
                            CovarianceStatPill(
                                label = "分散比率DR",
                                value = "${portfolio.diversificationRatio}x",
                                sub = "均相关ρ̄=${portfolio.averagePairwiseCorr}",
                                color = QuantCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        CovarianceStatPill(
                            label = "有效押注ENB",
                            value = "${portfolio.effectiveNumBets}",
                            sub = "高波额度${portfolio.highRiskStockCount}/${portfolio.highRiskLimitMax}",
                            color = WallStreetGold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        CovarianceStatPill(
                            label = "组合实测σ_p",
                            value = "${portfolio.weightedPortfolioVol}%",
                            sub = "未分散${portfolio.naiveWeightedVolSum}%",
                            color = if (portfolio.weightedPortfolioVol <= 24.0) SuccessGreen else WallStreetGold,
                            modifier = Modifier.weight(1.1f)
                        )
                        CovarianceStatPill(
                            label = "分散比率DR",
                            value = "${portfolio.diversificationRatio}x",
                            sub = "均相关ρ̄=${portfolio.averagePairwiseCorr}",
                            color = QuantCyan,
                            modifier = Modifier.weight(1f)
                        )
                        CovarianceStatPill(
                            label = "有效押注ENB",
                            value = "${portfolio.effectiveNumBets}",
                            sub = "高波额度${portfolio.highRiskStockCount}/${portfolio.highRiskLimitMax}",
                            color = WallStreetGold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Realized K-Line Window Backtest vs CSI 300 & Constraint Audit Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TerminalSurfaceElevated)
                        .clickable { showAuditDetails = !showAuditDetails }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("heatmap_constraint_audit_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        val alphaVsBench = Math.round((portfolio.backtestPortfolioReturnPct - portfolio.backtestBenchmarkReturnPct) * 10.0) / 10.0
                        Text(
                            text = if (portfolio.isEmpiricalKLineBacked) {
                                "同步K线回测: 组合 ${formatSignedPct(portfolio.backtestPortfolioReturnPct)} vs 沪深300 ${formatSignedPct(portfolio.backtestBenchmarkReturnPct)} (超额 ${formatSignedPct(alphaVsBench)} · 夏普 ${portfolio.backtestSharpeRatio})"
                            } else {
                                "基准比对期望: 组合 ${formatSignedPct(portfolio.backtestPortfolioReturnPct)} vs 沪深300 ${formatSignedPct(portfolio.backtestBenchmarkReturnPct)}"
                            },
                            color = if (alphaVsBench >= 0) StockRed else StockGreen,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (portfolio.constraintAuditPassed) {
                                "✓ 五大硬约束实证自检全过 (点击展开约束明细)"
                            } else {
                                "⚠️ 存在仓位或高波超限警示 (点击查看明细)"
                            },
                            color = if (portfolio.constraintAuditPassed) SuccessGreen else WallStreetGold,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Icon(
                        imageVector = if (showAuditDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "展开或收起约束审计明细",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showAuditDetails) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TerminalSurfaceElevated)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        portfolio.constraintAuditDetails.forEach { line ->
                            Text(
                                text = line,
                                color = if (line.startsWith("✓")) TextSecondary else WallStreetGold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Heatmap Legend & Interactive Filter Bar (2x2 on narrow portrait, 1x4 otherwise)
                if (isNarrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterRiskChip(
                                label = "全部10只",
                                color = WallStreetGold,
                                isSelected = selectedFilterLevel == null,
                                onClick = { onSelectFilterLevel(null) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterRiskChip(
                                label = "低波 ≤20%",
                                color = SuccessGreen,
                                isSelected = selectedFilterLevel == VolatilityRiskLevel.LOW,
                                onClick = { onSelectFilterLevel(VolatilityRiskLevel.LOW) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterRiskChip(
                                label = "稳健 ~30%",
                                color = QuantCyan,
                                isSelected = selectedFilterLevel == VolatilityRiskLevel.MODERATE,
                                onClick = { onSelectFilterLevel(VolatilityRiskLevel.MODERATE) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterRiskChip(
                                label = "高波 >30%",
                                color = StockRed,
                                isSelected = selectedFilterLevel == VolatilityRiskLevel.HIGH || selectedFilterLevel == VolatilityRiskLevel.EXTREME,
                                onClick = { onSelectFilterLevel(VolatilityRiskLevel.HIGH) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterRiskChip(
                            label = "全部10只",
                            color = WallStreetGold,
                            isSelected = selectedFilterLevel == null,
                            onClick = { onSelectFilterLevel(null) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterRiskChip(
                            label = "低波 ≤20%",
                            color = SuccessGreen,
                            isSelected = selectedFilterLevel == VolatilityRiskLevel.LOW,
                            onClick = { onSelectFilterLevel(VolatilityRiskLevel.LOW) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterRiskChip(
                            label = "稳健 ~30%",
                            color = QuantCyan,
                            isSelected = selectedFilterLevel == VolatilityRiskLevel.MODERATE,
                            onClick = { onSelectFilterLevel(VolatilityRiskLevel.MODERATE) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterRiskChip(
                            label = "高波 >30%",
                            color = StockRed,
                            isSelected = selectedFilterLevel == VolatilityRiskLevel.HIGH || selectedFilterLevel == VolatilityRiskLevel.EXTREME,
                            onClick = { onSelectFilterLevel(VolatilityRiskLevel.HIGH) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CovarianceStatPill(
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
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Column {
            Text(text = label, color = TextMuted, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = sub,
                color = TextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun riskColorsFor(level: VolatilityRiskLevel): Triple<Color, Color, Color> = when (level) {
    VolatilityRiskLevel.LOW -> Triple(Color(0x2810B981), Color(0xFF10B981), Color(0xFF10B981))
    VolatilityRiskLevel.MODERATE -> Triple(Color(0x2838BDF8), Color(0xFF38BDF8), Color(0xFF38BDF8))
    VolatilityRiskLevel.HIGH -> Triple(Color(0x35F59E0B), Color(0xFFF59E0B), Color(0xFFF59E0B))
    VolatilityRiskLevel.EXTREME -> Triple(Color(0x3DF43F5E), Color(0xFFF43F5E), Color(0xFFF43F5E))
}

@Composable
private fun CompactHorizontalHeatmapCell(
    rank: Int,
    stock: RetailTop10Stock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (heatBg, heatBorder, heatText) = riskColorsFor(stock.volatilityLevel)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(heatBg)
            .border(1.dp, heatBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp)
            .testTag("heatmap_cell_${stock.quote.code}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(heatText)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "#$rank ${stock.quote.name}",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${stock.realizedVolAnnualized}%",
                    color = heatText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "风${stock.riskContributionPercent}%",
                    color = TextMuted,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    rank: Int,
    stock: RetailTop10Stock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (heatBg, heatBorder, heatText) = riskColorsFor(stock.volatilityLevel)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(heatBg)
            .border(1.dp, heatBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 5.dp)
            .testTag("heatmap_cell_${stock.quote.code}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(heatText)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "#$rank",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = stock.quote.name.take(3),
                color = TextPrimary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "HV ${stock.realizedVolAnnualized}%",
                color = heatText,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "风险${stock.riskContributionPercent}%",
                color = TextMuted,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun FilterRiskChip(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) color.copy(alpha = 0.2f) else TerminalSurfaceElevated)
            .border(
                1.dp,
                if (isSelected) color else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) color else TextMuted,
            fontSize = 9.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatSignedPct(v: Double): String {
    return String.format(java.util.Locale.US, "%+.1f%%", v)
}
