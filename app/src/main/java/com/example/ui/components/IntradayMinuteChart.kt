package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MinutePoint
import com.example.data.model.StockQuote
import com.example.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntradayMinuteChart(
    quote: StockQuote,
    minutePoints: List<MinutePoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current
    val yesterdayClose = quote.yesterdayClose.coerceAtLeast(0.01)
    val currentPrice = quote.price.coerceAtLeast(0.01)

    // Prepare active minute data (either real from network or continuous fallback from quote)
    val points = remember(minutePoints, quote) {
        if (minutePoints.isNotEmpty()) {
            minutePoints
        } else {
            val fallback = mutableListOf<MinutePoint>()
            val totalMinutes = 241
            val open = if (quote.open > 0) quote.open else yesterdayClose
            val high = if (quote.high > 0) quote.high else maxOf(open, currentPrice)
            val low = if (quote.low > 0) quote.low else minOf(open, currentPrice)

            var runningVol = 0L
            var runningTurnover = 0.0

            for (i in 0 until totalMinutes) {
                val progress = i.toFloat() / (totalMinutes - 1)
                val hour = if (i <= 120) 9 + (30 + i) / 60 else 13 + (i - 120) / 60
                val minute = if (i <= 120) (30 + i) % 60 else (i - 120) % 60
                val timeStr = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)

                val base = when {
                    progress < 0.25f -> open + (low - open) * (progress / 0.25f)
                    progress < 0.70f -> low + (high - low) * ((progress - 0.25f) / 0.45f)
                    else -> high + (currentPrice - high) * ((progress - 0.70f) / 0.30f)
                }
                val wave = Math.sin(i * 0.18) * (yesterdayClose * 0.0012)
                val p = (base + wave).coerceIn(low * 0.999, high * 1.001)

                val incLots = (50 + (Math.sin(i * 0.3) * 30).toInt()).coerceAtLeast(10).toLong()
                runningVol += incLots
                runningTurnover += incLots * 100 * p
                val vwap = runningTurnover / (runningVol * 100.0)

                fallback.add(
                    MinutePoint(
                        time = timeStr,
                        price = p,
                        averagePrice = vwap,
                        volumeLots = incLots,
                        turnover = runningTurnover
                    )
                )
            }
            fallback
        }
    }

    val highestPrice = maxOf(quote.high, points.maxOfOrNull { it.price } ?: currentPrice, yesterdayClose * 1.003)
    val lowestPrice = minOf(quote.low.takeIf { it > 0 } ?: (yesterdayClose * 0.997), points.minOfOrNull { it.price } ?: currentPrice, yesterdayClose * 0.997)
    val maxDelta = maxOf(abs(highestPrice - yesterdayClose), abs(lowestPrice - yesterdayClose), yesterdayClose * 0.005)

    val upperLimit = yesterdayClose + maxDelta
    val lowerLimit = yesterdayClose - maxDelta
    val priceSpan = (upperLimit - lowerLimit).coerceAtLeast(0.01)
    val maxPct = (maxDelta / yesterdayClose) * 100.0

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val inspectedPoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .padding(10.dp)
            .testTag("intraday_minute_chart_container")
    ) {
        // Top Header: Responsive FlowRow so price & VWAP/Vol never collide in narrow portrait
        if (inspectedPoint != null) {
            val ptPrice = inspectedPoint.price
            val ptChangePct = ((ptPrice - yesterdayClose) / yesterdayClose) * 100.0
            val ptColor = when {
                ptPrice > yesterdayClose -> StockRed
                ptPrice < yesterdayClose -> StockGreen
                else -> TextMuted
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = inspectedPoint.time,
                        color = WallStreetGold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "价: ¥${String.format(java.util.Locale.US, "%.2f", ptPrice)}",
                        color = ptColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "${if (ptChangePct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f%%", ptChangePct)}",
                        color = ptColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "均: ¥${String.format(java.util.Locale.US, "%.2f", inspectedPoint.averagePrice)}",
                        color = WallStreetGold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "量: ${inspectedPoint.volumeLots}手",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            Text(text = "分时走势", color = TextSecondary, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Price Axis Percent Labels Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "+${String.format(java.util.Locale.US, "%.2f%%", maxPct)}",
                color = StockRed,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "昨收 ¥${String.format(java.util.Locale.US, "%.2f", yesterdayClose)}",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            Text(
                text = "-${String.format(java.util.Locale.US, "%.2f%%", maxPct)}",
                color = StockGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Combined Canvas for Price Trend and Volume Bars (adaptive height via WindowSizeClass)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(adaptive.chartHeight)
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val idx = ((offset.x / size.width) * (points.size - 1)).toInt()
                            selectedIndex = idx.coerceIn(0, points.size - 1)
                        },
                        onDrag = { change, _ ->
                            val idx = ((change.position.x / size.width) * (points.size - 1)).toInt()
                            selectedIndex = idx.coerceIn(0, points.size - 1)
                        },
                        onDragEnd = { selectedIndex = null },
                        onDragCancel = { selectedIndex = null }
                    )
                }
                .pointerInput(points) {
                    detectTapGestures(
                        onTap = { offset ->
                            val idx = ((offset.x / size.width) * (points.size - 1)).toInt()
                            selectedIndex = if (selectedIndex == idx) null else idx.coerceIn(0, points.size - 1)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val totalH = size.height
                val priceH = totalH * 0.72f
                val volH = totalH * 0.24f
                val volTopY = totalH - volH

                val midY = priceH / 2f

                drawLine(
                    color = TerminalBorder,
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                )

                drawLine(
                    color = TerminalBorder.copy(alpha = 0.5f),
                    start = Offset(0f, volTopY),
                    end = Offset(w, volTopY),
                    strokeWidth = 1f
                )

                if (points.isEmpty()) return@Canvas

                val count = points.size
                val stepX = if (count > 1) w / (count - 1) else w

                val pricePath = Path()
                val vwapPath = Path()
                val fillPath = Path()

                val maxVol = points.maxOfOrNull { it.volumeLots }?.coerceAtLeast(10L) ?: 100L

                points.forEachIndexed { i, pt ->
                    val x = i * stepX
                    val py = priceH - ((pt.price - lowerLimit) / priceSpan).toFloat() * priceH
                    val vy = priceH - ((pt.averagePrice - lowerLimit) / priceSpan).toFloat() * priceH

                    val safePy = py.coerceIn(2f, priceH - 2f)
                    val safeVy = vy.coerceIn(2f, priceH - 2f)

                    if (i == 0) {
                        pricePath.moveTo(x, safePy)
                        vwapPath.moveTo(x, safeVy)
                        fillPath.moveTo(x, priceH)
                        fillPath.lineTo(x, safePy)
                    } else {
                        pricePath.lineTo(x, safePy)
                        vwapPath.lineTo(x, safeVy)
                        fillPath.lineTo(x, safePy)
                    }

                    if (i == points.size - 1) {
                        fillPath.lineTo(x, priceH)
                        fillPath.close()
                    }

                    val barH = (pt.volumeLots.toFloat() / maxVol.toFloat()) * volH
                    val barTop = totalH - barH
                    val barColor = if (pt.price >= (points.getOrNull(i - 1)?.price ?: yesterdayClose)) StockRed else StockGreen
                    val barWidth = maxOf(stepX * 0.85f, 1.2f)

                    drawRect(
                        color = barColor.copy(alpha = 0.85f),
                        topLeft = Offset(x - barWidth / 2f, barTop),
                        size = Size(barWidth, barH)
                    )
                }

                val currentPriceVal = points.lastOrNull()?.price ?: currentPrice
                val isUp = currentPriceVal >= yesterdayClose
                val fillBrush = Brush.verticalGradient(
                    colors = listOf(
                        (if (isUp) StockRed else StockGreen).copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = priceH
                )
                drawPath(path = fillPath, brush = fillBrush)
                drawPath(path = vwapPath, color = WallStreetGold, style = Stroke(width = 1.8f))
                drawPath(path = pricePath, color = if (isUp) StockRed else StockGreen, style = Stroke(width = 2.2f))

                selectedIndex?.let { idx ->
                    val clampedIdx = idx.coerceIn(0, points.size - 1)
                    val pt = points[clampedIdx]
                    val crossX = clampedIdx * stepX
                    val crossY = priceH - ((pt.price - lowerLimit) / priceSpan).toFloat() * priceH

                    drawLine(
                        color = WallStreetGold.copy(alpha = 0.8f),
                        start = Offset(crossX, 0f),
                        end = Offset(crossX, totalH),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                    drawLine(
                        color = WallStreetGold.copy(alpha = 0.8f),
                        start = Offset(0f, crossY),
                        end = Offset(w, crossY),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                    drawCircle(color = WallStreetGold, radius = 4.5f, center = Offset(crossX, crossY))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Time axis standard A-share labels (adapts tick density on ultra-narrow / large fontScale)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val compactTicks = isUltraNarrowPortrait(maxWidth, fontScale)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "09:30", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                if (!compactTicks) {
                    Text(text = "10:30", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                }
                Text(text = "11:30/13:00", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                if (!compactTicks) {
                    Text(text = "14:00", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                }
                Text(text = "15:00", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            }
        }
    }
}
