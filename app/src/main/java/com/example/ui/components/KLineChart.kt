package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KLinePoint
import com.example.data.model.StockQuote
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.hypot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KLineChart(
    kLines: List<KLinePoint>,
    periodTitle: String = "日K",
    quote: StockQuote? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current

    val safeKLines = remember(kLines) {
        kLines.filter {
            it.close.isFinite() && it.close > 0.0 &&
                it.open.isFinite() && it.open > 0.0 &&
                it.high.isFinite() && it.high > 0.0 &&
                it.low.isFinite() && it.low > 0.0 &&
                it.volume.isFinite() && it.volume >= 0.0
        }
    }

    if (safeKLines.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(adaptive.chartHeight + 45.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TerminalSurfaceElevated)
                .testTag("kline_empty_state"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("kline_loading_indicator"),
                        strokeWidth = 2.5.dp,
                        color = WallStreetGold
                    )
                    Text(
                        text = "正在同步 $periodTitle 真实历史行情...",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = errorMessage ?: "${periodTitle}线暂未返回历史K线数据",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("kline_error_text")
                    )
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("kline_retry_button"),
                        border = BorderStroke(1.dp, WallStreetGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WallStreetGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "重新同步$periodTitle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        return
    }

    val minBars = safeKLines.size.coerceAtMost(15).coerceAtLeast(1)
    val maxBars = safeKLines.size.coerceAtLeast(minBars)
    var visibleCount by remember(periodTitle, safeKLines.firstOrNull()?.date) { mutableIntStateOf(40) }
    val clampedCount = visibleCount.coerceIn(minBars, maxBars)

    val visiblePoints = remember(safeKLines, clampedCount) {
        safeKLines.takeLast(clampedCount)
    }

    val rawMaxPrice = visiblePoints.maxOf { it.high }.coerceAtLeast(0.01)
    val rawMinPrice = visiblePoints.minOf { it.low }.coerceAtLeast(0.01)
    val rawSpan = rawMaxPrice - rawMinPrice
    val minSpan = (rawMaxPrice * 0.002).coerceAtLeast(0.01)
    val (maxPrice, minPrice, priceSpan) = if (rawSpan < minSpan) {
        val mid = (rawMaxPrice + rawMinPrice) / 2.0
        val half = minSpan / 2.0
        Triple(mid + half, (mid - half).coerceAtLeast(0.01), minSpan)
    } else {
        Triple(rawMaxPrice, rawMinPrice, rawSpan)
    }
    val maxVolume = visiblePoints.maxOfOrNull { it.volume }?.coerceAtLeast(1.0) ?: 100.0

    var selectedIndex by remember(periodTitle, safeKLines.size, safeKLines.firstOrNull()?.date, clampedCount) {
        mutableStateOf<Int?>(null)
    }
    val inspectedVisibleIdx = selectedIndex?.coerceIn(0, visiblePoints.lastIndex) ?: visiblePoints.lastIndex
    val currentCandle = visiblePoints.getOrNull(inspectedVisibleIdx)
    val fullIdx = (safeKLines.size - visiblePoints.size) + inspectedVisibleIdx
    val prevCandle = if (fullIdx > 0) safeKLines.getOrNull(fullIdx - 1) else null

    val latestDate = safeKLines.lastOrNull()?.date.orEmpty()
    val quoteDigits = quote?.timestamp.orEmpty().filter { it.isDigit() }
    val quoteDateNormalized = if (quoteDigits.length >= 8) {
        "${quoteDigits.substring(0, 4)}-${quoteDigits.substring(4, 6)}-${quoteDigits.substring(6, 8)}"
    } else ""
    val isLiveAligned = periodTitle == "日K" && quoteDateNormalized.isNotEmpty() && latestDate == quoteDateNormalized

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .padding(10.dp)
            .testTag("kline_chart_container")
    ) {
        // 1. Top Header: Inspected Candle Details or Summary
        if (currentCandle != null) {
            val prevCloseRef = when {
                prevCandle != null && prevCandle.close > 0.0 -> prevCandle.close
                inspectedVisibleIdx == visiblePoints.lastIndex && periodTitle == "日K" && quote != null && quote.yesterdayClose > 0.0 -> quote.yesterdayClose
                else -> currentCandle.open
            }
            val chgPct = if (prevCloseRef > 0.0) ((currentCandle.close - prevCloseRef) / prevCloseRef) * 100.0 else 0.0
            val headerChgColor = when {
                chgPct > 0.0001 -> StockRed
                chgPct < -0.0001 -> StockGreen
                else -> TextSecondary
            }
            val volText = when {
                currentCandle.volume >= 10000.0 -> "${String.format(java.util.Locale.US, "%.2f", currentCandle.volume / 10000.0)}万手"
                currentCandle.volume >= 1000.0 -> "${String.format(java.util.Locale.US, "%.1f", currentCandle.volume / 1000.0)}k手"
                else -> "${currentCandle.volume.toInt()}手"
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentCandle.date,
                            color = WallStreetGold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "收 ¥${String.format(java.util.Locale.US, "%.2f", currentCandle.close)}",
                            color = headerChgColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "${if (chgPct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f%%", chgPct)}",
                            color = headerChgColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("kline_change_pct_text")
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "量: $volText",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = if (isLiveAligned) "盘口已对齐" else "$periodTitle · ${safeKLines.size}根",
                            color = if (isLiveAligned) SuccessGreen else TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = if (isLiveAligned) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("kline_status_badge")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Detail OHLC Row (wraps cleanly on ultra-narrow / large fontScale)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = "开: ${String.format(java.util.Locale.US, "%.2f", currentCandle.open)}", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = "高: ${String.format(java.util.Locale.US, "%.2f", currentCandle.high)}", color = StockRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text(text = "低: ${String.format(java.util.Locale.US, "%.2f", currentCandle.low)}", color = StockGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // MA Indicator FlowRow (cleanly wraps to 2 rows if needed on ultra-narrow / large fontScale)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "MA5: ${if (currentCandle.ma5 > 0) String.format(java.util.Locale.US, "%.2f", currentCandle.ma5) else "--"}",
                        color = WallStreetGold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "MA10: ${if (currentCandle.ma10 > 0) String.format(java.util.Locale.US, "%.2f", currentCandle.ma10) else "--"}",
                        color = Color(0xFFEC4899),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "MA20: ${if (currentCandle.ma20 > 0) String.format(java.util.Locale.US, "%.2f", currentCandle.ma20) else "--"}",
                        color = QuantCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "MA60: ${if (currentCandle.ma60 > 0) String.format(java.util.Locale.US, "%.2f", currentCandle.ma60) else "--"}",
                        color = Color(0xFFA78BFA),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Candle Count Switcher & Zoom Toolbar
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "跨度:",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "${clampedCount}根",
                    color = WallStreetGold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            listOf(30, 60, 90).forEach { count ->
                val isSelected = clampedCount == count
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) WallStreetGoldSoft else TerminalSurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) WallStreetGold else TerminalBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { visibleCount = count }
                        .padding(horizontal = adaptive.chipHorizontalPadding, vertical = adaptive.chipVerticalPadding)
                        .testTag("kline_span_$count")
                ) {
                    Text(
                        text = "${count}根",
                        color = if (isSelected) WallStreetGold else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            val isAll = clampedCount >= safeKLines.size
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isAll) WallStreetGoldSoft else TerminalSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (isAll) WallStreetGold else TerminalBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { visibleCount = safeKLines.size }
                    .padding(horizontal = adaptive.chipHorizontalPadding, vertical = adaptive.chipVerticalPadding)
                    .testTag("kline_span_all")
            ) {
                Text(
                    text = "全部",
                    color = if (isAll) WallStreetGold else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalSurfaceElevated)
                    .border(1.dp, TerminalBorder, RoundedCornerShape(4.dp))
                    .clickable { visibleCount = (clampedCount - 10).coerceAtLeast(minBars) }
                    .padding(horizontal = adaptive.chipHorizontalPadding, vertical = adaptive.chipVerticalPadding)
            ) {
                Text(text = "＋放大", color = TextPrimary, fontSize = 9.5.sp, maxLines = 1, softWrap = false)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalSurfaceElevated)
                    .border(1.dp, TerminalBorder, RoundedCornerShape(4.dp))
                    .clickable { visibleCount = (clampedCount + 10).coerceAtMost(maxBars) }
                    .padding(horizontal = adaptive.chipHorizontalPadding, vertical = adaptive.chipVerticalPadding)
            ) {
                Text(text = "－缩小", color = TextPrimary, fontSize = 9.5.sp, maxLines = 1, softWrap = false)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Price Axis Bounds
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "高: ¥${String.format(java.util.Locale.US, "%.2f", maxPrice)}", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            Text(text = "中: ¥${String.format(java.util.Locale.US, "%.2f", (maxPrice + minPrice) / 2)}", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            Text(text = "低: ¥${String.format(java.util.Locale.US, "%.2f", minPrice)}", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Interactive Combined Candlestick & Volume Canvas (unified non-conflicting gesture loop)
        var zoomAccumulator by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(adaptive.chartHeight)
                .pointerInput(visiblePoints, minBars, maxBars) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val touchSlop = viewConfiguration.touchSlop
                        var isHorizontalScrub = false
                        var isVerticalScroll = false
                        var isPinching = false
                        var totalDx = 0f
                        var totalDy = 0f
                        var lastTapPos = firstDown.position

                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.isEmpty()) break

                            if (activeChanges.size >= 2) {
                                isPinching = true
                                val zoom = event.calculateZoom()
                                if (zoom.isFinite() && abs(zoom - 1f) > 0.005f) {
                                    zoomAccumulator += (1f - zoom) * 28f
                                    val deltaBars = zoomAccumulator.toInt()
                                    if (deltaBars != 0) {
                                        zoomAccumulator -= deltaBars
                                        visibleCount = (visibleCount + deltaBars).coerceIn(minBars, maxBars)
                                    }
                                }
                                activeChanges.forEach { it.consume() }
                                continue
                            }

                            val change = activeChanges.first()
                            lastTapPos = change.position
                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y

                            if (!isHorizontalScrub && !isVerticalScroll && !isPinching) {
                                val dist = hypot(totalDx, totalDy)
                                if (dist > touchSlop) {
                                    if (abs(totalDx) > abs(totalDy) * 1.15f) {
                                        isHorizontalScrub = true
                                    } else {
                                        isVerticalScroll = true
                                    }
                                }
                            }

                            if (isHorizontalScrub && size.width > 0 && visiblePoints.isNotEmpty()) {
                                val idx = ((change.position.x / size.width) * visiblePoints.size).toInt()
                                selectedIndex = idx.coerceIn(0, visiblePoints.size - 1)
                                change.consume()
                            } else if (isVerticalScroll) {
                                // Leave vertical scroll unconsumed so parent LazyColumn scrolls smoothly
                                break
                            }
                        }

                        if (isHorizontalScrub) {
                            selectedIndex = null
                        } else if (!isPinching && !isVerticalScroll && size.width > 0 && visiblePoints.isNotEmpty()) {
                            val idx = ((lastTapPos.x / size.width) * visiblePoints.size).toInt()
                                .coerceIn(0, visiblePoints.size - 1)
                            selectedIndex = if (selectedIndex == idx) null else idx
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val totalH = size.height
                val candleH = totalH * 0.72f
                val volH = totalH * 0.22f
                val volTopY = totalH - volH

                val count = visiblePoints.size
                if (count == 0) return@Canvas

                val step = w / count
                val candleBodyWidth = (step * 0.68f).coerceAtLeast(1.8f)

                drawLine(
                    color = TerminalBorder.copy(alpha = 0.5f),
                    start = Offset(0f, volTopY),
                    end = Offset(w, volTopY),
                    strokeWidth = 1f
                )

                val ma5Path = Path()
                val ma10Path = Path()
                val ma20Path = Path()
                val ma60Path = Path()

                visiblePoints.forEachIndexed { i, p ->
                    val centerX = i * step + step / 2f
                    val isUp = p.close >= p.open
                    val barColor = if (isUp) StockRed else StockGreen

                    val highY = candleH - ((p.high - minPrice) / priceSpan).toFloat() * candleH
                    val lowY = candleH - ((p.low - minPrice) / priceSpan).toFloat() * candleH
                    val openY = candleH - ((p.open - minPrice) / priceSpan).toFloat() * candleH
                    val closeY = candleH - ((p.close - minPrice) / priceSpan).toFloat() * candleH

                    drawLine(
                        color = barColor,
                        start = Offset(centerX, highY.coerceIn(0f, candleH)),
                        end = Offset(centerX, lowY.coerceIn(0f, candleH)),
                        strokeWidth = maxOf(1.2f, candleBodyWidth * 0.15f)
                    )

                    val bodyTop = minOf(openY, closeY).coerceIn(0f, candleH)
                    val bodyBottom = maxOf(openY, closeY).coerceIn(0f, candleH)
                    val bodyHeight = maxOf(bodyBottom - bodyTop, 1.8f)

                    drawRect(
                        color = barColor,
                        topLeft = Offset(centerX - candleBodyWidth / 2f, bodyTop),
                        size = Size(candleBodyWidth, bodyHeight)
                    )

                    val vBarH = ((p.volume / maxVolume) * volH).toFloat().coerceIn(1f, volH)
                    val vBarTop = totalH - vBarH
                    drawRect(
                        color = barColor.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - candleBodyWidth / 2f, vBarTop),
                        size = Size(candleBodyWidth, vBarH)
                    )

                    fun addMaPoint(path: Path, value: Double) {
                        if (value.isFinite() && value > 0) {
                            val y = candleH - ((value - minPrice) / priceSpan).toFloat() * candleH
                            val safeY = y.coerceIn(0f, candleH)
                            if (path.isEmpty) path.moveTo(centerX, safeY) else path.lineTo(centerX, safeY)
                        }
                    }

                    addMaPoint(ma5Path, p.ma5)
                    addMaPoint(ma10Path, p.ma10)
                    addMaPoint(ma20Path, p.ma20)
                    addMaPoint(ma60Path, p.ma60)
                }

                drawPath(ma5Path, color = WallStreetGold, style = Stroke(width = 1.6f))
                drawPath(ma10Path, color = Color(0xFFEC4899), style = Stroke(width = 1.6f))
                drawPath(ma20Path, color = QuantCyan, style = Stroke(width = 1.6f))
                drawPath(ma60Path, color = Color(0xFFA78BFA), style = Stroke(width = 1.6f))

                selectedIndex?.let { idx ->
                    val clamped = idx.coerceIn(0, visiblePoints.size - 1)
                    val pt = visiblePoints[clamped]
                    val crossX = clamped * step + step / 2f
                    val crossY = (candleH - ((pt.close - minPrice) / priceSpan).toFloat() * candleH).coerceIn(0f, candleH)

                    drawLine(
                        color = WallStreetGold.copy(alpha = 0.85f),
                        start = Offset(crossX, 0f),
                        end = Offset(crossX, totalH),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                    drawLine(
                        color = WallStreetGold.copy(alpha = 0.85f),
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

        // Date Axis (First, Middle, Last)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = visiblePoints.firstOrNull()?.date ?: "", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            Text(text = visiblePoints.getOrNull(visiblePoints.size / 2)?.date ?: "", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            Text(text = visiblePoints.lastOrNull()?.date ?: "", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
        }
    }
}
