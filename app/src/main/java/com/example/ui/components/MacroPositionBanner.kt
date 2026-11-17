package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
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
import com.example.data.model.KLinePoint
import com.example.data.model.MarketRegime
import com.example.data.model.QuantFactorScores
import com.example.data.model.StockQuote
import com.example.data.repository.QuantEngine
import com.example.ui.theme.*

@Composable
fun MacroPositionBanner(
    quote: StockQuote,
    marketRegime: MarketRegime = MarketRegime.BULL_MOMENTUM,
    factorScores: QuantFactorScores? = null,
    kLines: List<KLinePoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current
    val fontScale = LocalDensity.current.fontScale

    val macroGuidance = QuantEngine.computeMacroPositionGuidance(marketRegime)
    val resolvedScores = factorScores ?: QuantEngine.evaluateFactors(quote, kLines)
    val tradeRec = QuantEngine.generateTradeRecommendation(
        quote = quote,
        factorScores = resolvedScores,
        dynamicScore = resolvedScores.compositeScore,
        kLines = kLines
    )
    val kellyAllocation = QuantEngine.computeHalfKellyAllocation(tradeRec.winRateEstimate, tradeRec.profitLossRatio)
    val t1Test = QuantEngine.computeT1StressTest(quote)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = adaptive.cardHorizontalPadding, vertical = 6.dp)
            .testTag("macro_position_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(adaptive.cardInnerPadding)
        ) {
            val isNarrow = isNarrowPortrait(maxWidth, fontScale)

            Column(modifier = Modifier.fillMaxWidth()) {
                // Header: Macro Risk Control & Kelly Formula
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
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = WallStreetGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "宏观仓位指引与半凯利风控",
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "当前市场状态: ${marketRegime.title}",
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(WallStreetGoldSoft)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = marketRegime.badge,
                            color = WallStreetGold,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Responsive 2+1 on narrow portrait/high fontScale, 3 columns otherwise
                if (isNarrow) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PositionMetricBox(
                                label = "推荐总股票仓位",
                                value = "${macroGuidance.recommendedStockPositionPercent}%",
                                sub = "现金防守垫 ${macroGuidance.recommendedCashCushionPercent}%",
                                color = WallStreetGold,
                                modifier = Modifier.weight(1f)
                            )
                            PositionMetricBox(
                                label = "单票半凯利上限",
                                value = "${kellyAllocation.recommendedHalfKellyPercent}%",
                                sub = "硬风控顶格 ${kellyAllocation.maximumCapPercent}%",
                                color = QuantCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TerminalSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "T+1极端跌停测算",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "跌停参考价 ¥${String.format(java.util.Locale.US, "%.2f", t1Test.limitDownPrice)}",
                                        color = TextMuted,
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "-¥${String.format(java.util.Locale.US, "%.2f", t1Test.maxSingleDayLossRMB)}",
                                    color = StockGreen,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PositionMetricBox(
                            label = "推荐总股票仓位",
                            value = "${macroGuidance.recommendedStockPositionPercent}%",
                            sub = "现金防守垫 ${macroGuidance.recommendedCashCushionPercent}%",
                            color = WallStreetGold,
                            modifier = Modifier.weight(1f)
                        )
                        PositionMetricBox(
                            label = "单票半凯利上限",
                            value = "${kellyAllocation.recommendedHalfKellyPercent}%",
                            sub = "硬风控顶格 ${kellyAllocation.maximumCapPercent}%",
                            color = QuantCyan,
                            modifier = Modifier.weight(1f)
                        )
                        PositionMetricBox(
                            label = "T+1极端跌停测算",
                            value = "-¥${String.format(java.util.Locale.US, "%.2f", t1Test.maxSingleDayLossRMB)}",
                            sub = "跌停价 ¥${String.format(java.util.Locale.US, "%.2f", t1Test.limitDownPrice)}",
                            color = StockGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "📌 操盘纲领: ${macroGuidance.actionDirective}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun PositionMetricBox(
    label: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalSurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = sub,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
