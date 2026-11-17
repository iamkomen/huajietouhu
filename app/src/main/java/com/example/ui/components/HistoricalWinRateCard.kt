package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.data.model.FactorCombinationResult
import com.example.data.model.HistoricalWinRateStats
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoricalWinRateCard(
    stats: HistoricalWinRateStats,
    onSelectHorizon: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = adaptive.cardHorizontalPadding, vertical = 6.dp)
            .testTag("historical_win_rate_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(adaptive.cardInnerPadding)) {
            // Header Row: Title (weight=1f) & Expectancy Badge
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
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WallStreetGoldSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = WallStreetGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "历史胜率统计与盈利期望",
                            color = TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "多因子组合数学期望 E(R) · 全A样本回测",
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(WallStreetGoldSoft)
                        .border(1.dp, WallStreetGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "期望 +${stats.overallExpectancy}%",
                        color = WallStreetGold,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizon Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("5年全周期", "3年震荡市", "1年近态").forEach { horizon ->
                    val isSelected = stats.horizonYears == horizon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) WallStreetGoldSoft else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) WallStreetGold else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectHorizon(horizon) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("horizon_tab_$horizon"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = horizon,
                            color = if (isSelected) WallStreetGold else TextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mathematical Formula Notice Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalSurfaceElevated.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = QuantCyan,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "数学期望公式: E = (胜率 × 平均盈利) - (败率 × 平均亏损) · 期望值越大长期复利越陡峭",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            stats.deflatedSharpeInfo?.let { dsr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (dsr.isStatisticallySignificant) SuccessGreen.copy(alpha = 0.5f) else WarningAmber.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "López de Prado 伪夏普 (DSR) 检验",
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = "PSR: ${dsr.probabilisticSharpePercent}%",
                                color = if (dsr.isStatisticallySignificant) SuccessGreen else WarningAmber,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "名义夏普: ${dsr.nominalSharpe} ➔ 实净夏普: ${dsr.deflatedSharpeRatio}",
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "多重检验校正 N=${dsr.numberOfTrialsTested}",
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dsr.pValueVerdict,
                            color = if (dsr.isStatisticallySignificant) QuantCyan else WarningAmber,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5 Factor Combination Cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.combinations.forEach { combo ->
                    FactorCombinationRowCard(combo = combo)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactorCombinationRowCard(combo: FactorCombinationResult) {
    val fontScale = LocalDensity.current.fontScale

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (combo.isHit) StockRed.copy(alpha = 0.5f) else TerminalBorder
            )
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val isNarrow = isNarrowPortrait(maxWidth, fontScale)

            Column(modifier = Modifier.fillMaxWidth()) {
                // Title (weight=1f) & Hit Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = combo.name,
                        color = if (combo.isHit) WallStreetGold else TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (combo.isHit) StockRedSoft else TerminalSurfaceElevated)
                            .border(
                                1.dp,
                                if (combo.isHit) StockRed.copy(alpha = 0.6f) else TerminalBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = combo.hitStatusText,
                            color = if (combo.isHit) StockRed else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (combo.isHit) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle Tag Strip (FlowRow wraps cleanly)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    combo.tags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TerminalSurfaceElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Responsive 2x2 on narrow/high fontScale, 4 weighted columns otherwise
                if (isNarrow) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ComboMetricCell("历史胜率", "${combo.winRate}%", StockRed, Modifier.weight(1f))
                            ComboMetricCell("期望回报E(R)", "+${combo.expectedReturn}%", WallStreetGold, Modifier.weight(1f), Alignment.End)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ComboMetricCell("盈亏比", combo.profitLossRatio, TextPrimary, Modifier.weight(1f))
                            ComboMetricCell("最大回撤", "${combo.maxDrawdown}%", SuccessGreen, Modifier.weight(1f), Alignment.End)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ComboMetricCell("历史胜率", "${combo.winRate}%", StockRed, Modifier.weight(1f))
                        ComboMetricCell("期望回报E(R)", "+${combo.expectedReturn}%", WallStreetGold, Modifier.weight(1f))
                        ComboMetricCell("盈亏比", combo.profitLossRatio, TextPrimary, Modifier.weight(1f))
                        ComboMetricCell("最大回撤", "${combo.maxDrawdown}%", SuccessGreen, Modifier.weight(1f), Alignment.End)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(TerminalSurfaceElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight((combo.winRate / 100.0).toFloat().coerceIn(0.1f, 0.9f))
                            .background(StockRed)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(((100.0 - combo.winRate) / 100.0).toFloat().coerceIn(0.1f, 0.9f))
                            .background(SuccessGreen)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComboMetricCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}
