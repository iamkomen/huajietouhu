package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChartPeriod
import com.example.data.model.KLinePoint
import com.example.data.model.MinutePoint
import com.example.data.model.StockQuote
import com.example.ui.theme.*

@Composable
fun TradingChartSection(
    quote: StockQuote,
    minutePoints: List<MinutePoint>,
    dailyKLines: List<KLinePoint>,
    weeklyKLines: List<KLinePoint>,
    monthlyKLines: List<KLinePoint>,
    selectedPeriod: ChartPeriod = ChartPeriod.INTRADAY,
    onSelectPeriod: (ChartPeriod) -> Unit = {},
    isKLineLoading: Boolean = false,
    kLineErrorMessage: String? = null,
    onRetryKLines: () -> Unit = {},
    modifier: Modifier = Modifier,
    horizontalMargin: androidx.compose.ui.unit.Dp = LocalAdaptiveLayout.current.cardHorizontalPadding
) {
    val adaptive = LocalAdaptiveLayout.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = 6.dp)
            .testTag("trading_chart_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(adaptive.cardInnerPadding)) {
            // Chart Period Switcher (分时 / 日K / 周K / 月K)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChartPeriod.values().forEach { period ->
                    val isSelected = selectedPeriod == period
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) WallStreetGoldSoft else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) WallStreetGold else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectPeriod(period) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("chart_period_${period.key}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = period.label,
                            color = if (isSelected) WallStreetGold else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedPeriod) {
                ChartPeriod.INTRADAY -> {
                    IntradayMinuteChart(
                        quote = quote,
                        minutePoints = minutePoints
                    )
                }
                ChartPeriod.DAY -> {
                    KLineChart(
                        kLines = dailyKLines,
                        periodTitle = "日K",
                        quote = quote,
                        isLoading = isKLineLoading && dailyKLines.isEmpty(),
                        errorMessage = kLineErrorMessage,
                        onRetry = onRetryKLines
                    )
                }
                ChartPeriod.WEEK -> {
                    KLineChart(
                        kLines = weeklyKLines,
                        periodTitle = "周K",
                        quote = quote,
                        isLoading = isKLineLoading && weeklyKLines.isEmpty(),
                        errorMessage = kLineErrorMessage,
                        onRetry = onRetryKLines
                    )
                }
                ChartPeriod.MONTH -> {
                    KLineChart(
                        kLines = monthlyKLines,
                        periodTitle = "月K",
                        quote = quote,
                        isLoading = isKLineLoading && monthlyKLines.isEmpty(),
                        errorMessage = kLineErrorMessage,
                        onRetry = onRetryKLines
                    )
                }
            }
        }
    }
}
