package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HistoricalDcfCurvePoint
import com.example.data.model.HistoricalDcfCurveSeries
import com.example.data.model.StockQuote
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * 量化选股中枢：目标公司历史 DCF 贴现现金流估值曲线与安全区间图
 * 原生 Jetpack Compose Canvas 实现多维估值通道填充 + 真实股价叠加 + 十字线交互检视
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenerHistoricalDcfChartCard(
    curveSeries: HistoricalDcfCurveSeries,
    candidateQuotes: List<StockQuote>,
    selectedSymbol: String,
    onSelectTarget: (String) -> Unit,
    onOpenTerminal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    var inspectedIdx by remember(curveSeries.symbol, curveSeries.points.size) {
        mutableStateOf<Int?>(null)
    }

    val zoneColor = when (curveSeries.currentZoneLabel) {
        "七折击球区" -> SuccessGreen
        "DCF低估区" -> QuantCyan
        "合理价值带" -> WallStreetGold
        else -> CriticalRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("screener_dcf_history_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header row
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
                            .clip(RoundedCornerShape(7.dp))
                            .background(QuantCyanSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = "历史DCF估值曲线",
                            tint = QuantCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "历史 DCF 估值曲线与安全区间",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${curveSeries.stockName}(${curveSeries.symbol.takeLast(6)}) · 历史分位 ${curveSeries.valuationPercentileInHistory}%",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(zoneColor.copy(alpha = 0.16f))
                            .border(0.8.dp, zoneColor.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("dcf_history_zone_badge")
                    ) {
                        Text(
                            text = curveSeries.currentZoneLabel,
                            color = zoneColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TerminalSurfaceElevated)
                            .clickable { expanded = !expanded }
                            .testTag("dcf_history_chart_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "收起估值曲线" else "展开估值曲线",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Target selector strip (quick switch among top screener candidates)
            if (candidateQuotes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidateQuotes.take(10).forEach { q ->
                        val isTarget = q.symbol == selectedSymbol
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTarget) WallStreetGoldSoft else TerminalSurfaceElevated)
                                .border(
                                    width = if (isTarget) 1.dp else 0.75.dp,
                                    color = if (isTarget) WallStreetGold else TerminalBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectTarget(q.symbol) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("dcf_chart_target_${q.code}")
                        ) {
                            Text(
                                text = "${q.name} ${q.code}",
                                color = if (isTarget) WallStreetGold else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val activePoint: HistoricalDcfCurvePoint? = inspectedIdx?.let {
                        curveSeries.points.getOrNull(it)
                    } ?: curveSeries.points.lastOrNull()

                    // Crosshair inspection readout bar
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(TerminalSurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("dcf_history_inspector_bar"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val dispDate = activePoint?.date ?: "当前截面"
                        val dispPrice = activePoint?.closePrice ?: curveSeries.currentPrice
                        val dispFair = activePoint?.fairPrice ?: curveSeries.currentFairPrice
                        val dispStrike = activePoint?.strikePrice ?: curveSeries.currentStrikePrice
                        val dispMos = activePoint?.safetyMarginPercent ?: curveSeries.currentMarginOfSafetyPercent
                        val mosText = "${if (dispMos >= 0) "+" else ""}${dispMos}%"

                        CurveStatPill("日期", dispDate, TextSecondary)
                        CurveStatPill("股价", "¥${String.format(java.util.Locale.US, "%.2f", dispPrice)}", WallStreetGold)
                        CurveStatPill("DCF公允", "¥${String.format(java.util.Locale.US, "%.2f", dispFair)}", QuantCyan)
                        CurveStatPill("七折击球", "¥${String.format(java.util.Locale.US, "%.2f", dispStrike)}", SuccessGreen)
                        CurveStatPill("安全边际", mosText, if (dispMos >= 15.0) SuccessGreen else if (dispMos >= 0) WallStreetGold else CriticalRed)
                    }

                    if (curveSeries.points.size >= 2) {
                        val pts = curveSeries.points
                        val minVal = pts.minOf { minOf(it.closePrice, it.strikePrice, it.bearPrice) } * 0.94
                        val maxVal = pts.maxOf { maxOf(it.closePrice, it.bullPrice, it.fairPrice) } * 1.04
                        val span = (maxVal - minVal).coerceAtLeast(0.05)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TerminalBg.copy(alpha = 0.72f))
                                .border(0.75.dp, TerminalBorder.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    // Y-axis ticks
                                    Column(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(end = 4.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", maxVal),
                                            color = TextMuted,
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", (maxVal + minVal) / 2.0),
                                            color = TextMuted,
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", minVal),
                                            color = TextMuted,
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("screener_dcf_history_canvas")
                                            .pointerInput(pts.size) {
                                                detectTapGestures { offset ->
                                                    val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                                    inspectedIdx = (ratio * (pts.size - 1)).roundToInt().coerceIn(0, pts.lastIndex)
                                                }
                                            }
                                            .pointerInput(pts.size) {
                                                detectDragGestures { change, _ ->
                                                    change.consume()
                                                    val ratio = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                                    inspectedIdx = (ratio * (pts.size - 1)).roundToInt().coerceIn(0, pts.lastIndex)
                                                }
                                            }
                                    ) {
                                        val w = size.width
                                        val h = size.height
                                        fun xAt(i: Int): Float = if (pts.size <= 1) 0f else (i.toFloat() / (pts.size - 1)) * w
                                        fun yAt(v: Double): Float = (h - ((v - minVal) / span).toFloat() * h).coerceIn(0f, h)

                                        // Horizontal grid lines
                                        val dashGrid = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                        listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                                            drawLine(
                                                color = TerminalBorder.copy(alpha = 0.45f),
                                                start = Offset(0f, h * frac),
                                                end = Offset(w, h * frac),
                                                strokeWidth = 1f,
                                                pathEffect = dashGrid
                                            )
                                        }

                                        // 1. Shaded Strike-Zone area (bottom -> 0.7x strike curve)
                                        val strikeZonePath = Path().apply {
                                            moveTo(xAt(0), h)
                                            pts.forEachIndexed { i, pt -> lineTo(xAt(i), yAt(pt.strikePrice)) }
                                            lineTo(xAt(pts.lastIndex), h)
                                            close()
                                        }
                                        drawPath(
                                            path = strikeZonePath,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(SuccessGreen.copy(alpha = 0.22f), SuccessGreen.copy(alpha = 0.04f))
                                            )
                                        )

                                        // 2. Shaded Reasonable Valuation Channel (Bull <-> Bear/Strike)
                                        val channelPath = Path().apply {
                                            pts.forEachIndexed { i, pt ->
                                                val px = xAt(i)
                                                val py = yAt(pt.bullPrice)
                                                if (i == 0) moveTo(px, py) else lineTo(px, py)
                                            }
                                            for (i in pts.lastIndex downTo 0) {
                                                lineTo(xAt(i), yAt(pts[i].bearPrice))
                                            }
                                            close()
                                        }
                                        drawPath(
                                            path = channelPath,
                                            color = QuantCyan.copy(alpha = 0.13f)
                                        )

                                        // 3. Valuation boundary & fair lines
                                        val bullPath = Path()
                                        val fairPath = Path()
                                        val strikePath = Path()
                                        val pricePath = Path()
                                        val priceAreaPath = Path()

                                        pts.forEachIndexed { i, pt ->
                                            val px = xAt(i)
                                            val yBull = yAt(pt.bullPrice)
                                            val yFair = yAt(pt.fairPrice)
                                            val yStrike = yAt(pt.strikePrice)
                                            val yClose = yAt(pt.closePrice)
                                            if (i == 0) {
                                                bullPath.moveTo(px, yBull)
                                                fairPath.moveTo(px, yFair)
                                                strikePath.moveTo(px, yStrike)
                                                pricePath.moveTo(px, yClose)
                                                priceAreaPath.moveTo(px, h)
                                                priceAreaPath.lineTo(px, yClose)
                                            } else {
                                                bullPath.lineTo(px, yBull)
                                                fairPath.lineTo(px, yFair)
                                                strikePath.lineTo(px, yStrike)
                                                pricePath.lineTo(px, yClose)
                                                priceAreaPath.lineTo(px, yClose)
                                            }
                                        }
                                        priceAreaPath.lineTo(xAt(pts.lastIndex), h)
                                        priceAreaPath.close()

                                        drawPath(
                                            path = bullPath,
                                            color = WarningAmber.copy(alpha = 0.65f),
                                            style = Stroke(width = 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
                                        )
                                        drawPath(
                                            path = strikePath,
                                            color = SuccessGreen.copy(alpha = 0.85f),
                                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f))
                                        )
                                        drawPath(
                                            path = fairPath,
                                            color = QuantCyan,
                                            style = Stroke(width = 2.dp.toPx())
                                        )

                                        // 4. Actual stock price gradient + foreground line
                                        drawPath(
                                            path = priceAreaPath,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(WallStreetGold.copy(alpha = 0.24f), Color.Transparent)
                                            )
                                        )
                                        drawPath(
                                            path = pricePath,
                                            color = WallStreetGold,
                                            style = Stroke(width = 2.4.dp.toPx())
                                        )

                                        // 5. Inspected or latest crosshair marker
                                        val markerIdx = (inspectedIdx ?: pts.lastIndex).coerceIn(0, pts.lastIndex)
                                        val mx = xAt(markerIdx)
                                        val myClose = yAt(pts[markerIdx].closePrice)
                                        val myFair = yAt(pts[markerIdx].fairPrice)

                                        drawLine(
                                            color = TextPrimary.copy(alpha = 0.4f),
                                            start = Offset(mx, 0f),
                                            end = Offset(mx, h),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                        drawCircle(color = QuantCyan, radius = 3.5.dp.toPx(), center = Offset(mx, myFair))
                                        drawCircle(color = WallStreetGold, radius = 4.5.dp.toPx(), center = Offset(mx, myClose))
                                    }
                                }

                                // X-axis start / mid / end dates
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 30.dp, top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(pts.first().date, color = TextMuted, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                    Text(pts[pts.size / 2].date, color = TextMuted, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                    Text(pts.last().date, color = TextMuted, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TerminalSurfaceElevated)
                                .padding(12.dp)
                                .testTag("dcf_history_sync_placeholder")
                        ) {
                            Text(
                                text = "正在同步 ${curveSeries.stockName} 真实历史日K线以绘制 DCF 估值轨迹… 当前参考公允价 ¥${String.format(java.util.Locale.US, "%.2f", curveSeries.currentFairPrice)}，七折击球线 ¥${String.format(java.util.Locale.US, "%.2f", curveSeries.currentStrikePrice)}。",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.5.sp
                            )
                        }
                    }

                    // Legend + Quick Action Footer
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            LegendDot("股价走势", WallStreetGold)
                            LegendDot("DCF公允", QuantCyan)
                            LegendDot("七折击球(0.7×)", SuccessGreen)
                            LegendDot("乐观上轨", WarningAmber)
                        }

                        Text(
                            text = "进入深度DCF算器 →",
                            color = QuantCyan,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onOpenTerminal(curveSeries.symbol) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("dcf_history_open_terminal")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurveStatPill(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("$label:", color = TextMuted, fontSize = 9.5.sp, maxLines = 1)
        Text(
            text = value,
            color = valueColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, color = TextMuted, fontSize = 9.5.sp, maxLines = 1)
    }
}
