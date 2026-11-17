package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KLinePoint
import com.example.data.model.StockQuote
import com.example.ui.theme.*

@Composable
fun TradingSignalsSection(
    quote: StockQuote,
    kLines: List<KLinePoint>,
    modifier: Modifier = Modifier,
    horizontalMargin: androidx.compose.ui.unit.Dp = LocalAdaptiveLayout.current.cardHorizontalPadding
) {
    val adaptive = LocalAdaptiveLayout.current
    val latestK = kLines.lastOrNull()
    val isMacdBullish = latestK != null && latestK.macdDif > latestK.macdDea
    val isBollUpper = latestK != null && latestK.close > latestK.bollMid
    val isRsiStrong = latestK != null && latestK.rsi6 in 50.0..75.0
    val isMaBullish = latestK != null && latestK.close > latestK.ma5 && latestK.ma5 > latestK.ma20

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = 6.dp)
            .testTag("trading_signals_card"),
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
                            .background(QuantCyanSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = QuantCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "技术面与微观量化信号雷达",
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "实盘毫秒运算",
                    color = QuantCyan,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (latestK == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalSurface)
                        .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                        .padding(vertical = 20.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = QuantCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "正在同步 ${quote.name}(${quote.code}) 历史日K线序列以精确计算 MACD / BOLL / RSI(6) / MA5/MA20...",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalIndicatorRow(
                        indicatorName = "MACD 指标共振",
                        signal = if (isMacdBullish) "零轴上方多头金叉放量" else "DIF下穿DEA死叉或粘合",
                        status = if (isMacdBullish) "偏多" else "中性/偏空",
                        isBullish = isMacdBullish,
                        basis = "DIF: ${String.format(java.util.Locale.US, "%.3f", latestK.macdDif)} / DEA: ${String.format(java.util.Locale.US, "%.3f", latestK.macdDea)}"
                    )

                    SignalIndicatorRow(
                        indicatorName = "布林通道 BOLL",
                        signal = if (isBollUpper) "运行于中轨与上轨强势通道" else "跌破中轨向下轨寻底",
                        status = if (isBollUpper) "强势通道" else "震荡弱势",
                        isBullish = isBollUpper,
                        basis = "上轨: ¥${String.format(java.util.Locale.US, "%.2f", latestK.bollUpper)} / 下轨: ¥${String.format(java.util.Locale.US, "%.2f", latestK.bollLower)}"
                    )

                    SignalIndicatorRow(
                        indicatorName = "RSI 相对强弱",
                        signal = if (isRsiStrong) "处于 50~75 健康做多扩张区间" else if (latestK.rsi6 > 80) "RSI>80 严重短线超买" else "RSI<45 动能偏弱",
                        status = if (isRsiStrong) "健康扩张" else "注意背离",
                        isBullish = isRsiStrong,
                        basis = "RSI(6): ${String.format(java.util.Locale.US, "%.1f", latestK.rsi6)}"
                    )

                    SignalIndicatorRow(
                        indicatorName = "均线排列 MA5/MA20",
                        signal = if (isMaBullish) "MA5金叉MA20标准多头多层推进" else "MA5处于MA20下方或破位",
                        status = if (isMaBullish) "多头排列" else "整理防守",
                        isBullish = isMaBullish,
                        basis = "MA5: ¥${String.format(java.util.Locale.US, "%.2f", latestK.ma5)} / MA20: ¥${String.format(java.util.Locale.US, "%.2f", latestK.ma20)}"
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalIndicatorRow(
    indicatorName: String,
    signal: String,
    status: String,
    isBullish: Boolean,
    basis: String
) {
    val statusColor = if (isBullish) StockRed else TextSecondary
    val statusBg = if (isBullish) StockRedSoft else TerminalSurfaceElevated

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = indicatorName,
                    color = WallStreetGold,
                    fontSize = 12.sp,
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
                        .background(statusBg)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = signal,
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = basis,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
