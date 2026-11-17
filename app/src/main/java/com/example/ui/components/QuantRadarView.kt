package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KLinePoint
import com.example.data.model.QuantFactorScores
import com.example.data.model.QuantTradeRecommendation
import com.example.data.model.SignalDirection
import com.example.data.model.StockQuote
import com.example.data.repository.QuantEngine
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantRadarView(
    factorScores: QuantFactorScores,
    quote: StockQuote? = null,
    kLines: List<KLinePoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current
    val fontScale = LocalDensity.current.fontScale
    var showBenchmarkOverlay by remember { mutableStateOf(true) }
    var selectedFactorIndex by remember { mutableStateOf(0) }
    var isSlidersExpanded by remember { mutableStateOf(true) }

    // 动态因子权重滑块状态 (默认: PE=20, PB=15, 动量=20, 质量=20, 风控=15, 筹码=10)
    var weightPe by remember { mutableFloatStateOf(20f) }
    var weightPb by remember { mutableFloatStateOf(15f) }
    var weightMom by remember { mutableFloatStateOf(20f) }
    var weightRoe by remember { mutableFloatStateOf(20f) }
    var weightVol by remember { mutableFloatStateOf(15f) }
    var weightMicro by remember { mutableFloatStateOf(10f) }

    val totalWeight = (weightPe + weightPb + weightMom + weightRoe + weightVol + weightMicro).coerceAtLeast(1f)
    val normPe = weightPe / totalWeight
    val normPb = weightPb / totalWeight
    val normMom = weightMom / totalWeight
    val normRoe = weightRoe / totalWeight
    val normVol = weightVol / totalWeight
    val normMicro = weightMicro / totalWeight

    val peValue = quote?.peTtm ?: 24.5
    val pbValue = quote?.pb ?: 3.8
    val turnover = quote?.turnoverRate ?: 1.8
    val amp = quote?.amplitude ?: 2.4

    // 直接采用底层量化引擎深度计算的高区分度指标
    val peScore = factorScores.peScore
    val pbScore = factorScores.pbScore
    val microScore = factorScores.microScore

    // 实时根据滑动条加权计算新综合得分
    val dynamicCompositeScore = remember(
        peScore, pbScore, factorScores.momentumScore, factorScores.qualityScore, factorScores.riskControlScore, microScore,
        normPe, normPb, normMom, normRoe, normVol, normMicro
    ) {
        val raw = (peScore * normPe) +
                (pbScore * normPb) +
                (factorScores.momentumScore * normMom) +
                (factorScores.qualityScore * normRoe) +
                (factorScores.riskControlScore * normVol) +
                (microScore * normMicro)
        raw.toInt().coerceIn(12, 99)
    }

    val isCustomWeighted = remember(weightPe, weightPb, weightMom, weightRoe, weightVol, weightMicro) {
        kotlin.math.abs(weightPe - 20f) > 0.5f ||
                kotlin.math.abs(weightPb - 15f) > 0.5f ||
                kotlin.math.abs(weightMom - 20f) > 0.5f ||
                kotlin.math.abs(weightRoe - 20f) > 0.5f ||
                kotlin.math.abs(weightVol - 15f) > 0.5f ||
                kotlin.math.abs(weightMicro - 10f) > 0.5f
    }

    val dynamicRating = when {
        dynamicCompositeScore >= 80 -> "强力推荐 (Strong Buy)"
        dynamicCompositeScore >= 70 -> "跑赢大盘 (Outperform)"
        dynamicCompositeScore >= 52 -> "中性持有 (Hold)"
        else -> "减持避险 (Underperform)"
    }

    val factorItems = listOf(
        FactorDimensionItem(
            name = "估值偏差 (PE)",
            shortLabel = "市盈率 PE",
            score = peScore,
            color = WallStreetGold,
            currentValueStr = if (peValue <= 0.0) "亏损(PE<0)" else "PE: ${String.format(java.util.Locale.US, "%.1f", peValue)}倍 · 偏离中枢: ${if (factorScores.valuationSpreadPercent >= 0) "+" else ""}${factorScores.valuationSpreadPercent}%",
            percentileStr = if (peScore >= 80) "全A估值前20%优势洼地 · 盈利收益率利差充沛" else if (peScore >= 60) "估值处于合理偏优中枢 · 安全垫良好" else if (peScore >= 40) "估值溢价中等 · 需成长消化" else "估值严重透支泡沫区间",
            thesisExplanation = "衡量净利润变现价值比率。深度挖掘E/P收益率利差与全A中枢偏离度，高估值在系统性杀估值时面临极大约束。",
            benchmarkScore = 52
        ),
        FactorDimensionItem(
            name = "资产溢价 (PB)",
            shortLabel = "市净率 PB",
            score = pbScore,
            color = InstitutionalIndigo,
            currentValueStr = "PB: ${String.format(java.util.Locale.US, "%.2f", pbValue)}倍",
            percentileStr = if (pbScore >= 80) "账面破净/极低PB折价 · 底层资产安全边际高" else if (pbScore >= 60) "资产溢价率稳健健康" else "账面溢价较高 · 依赖轻资产溢价",
            thesisExplanation = "衡量市值相对账面净资产溢价。高质量核心重资产与高ROE企业赋予PB强力底层托底。",
            benchmarkScore = 50
        ),
        FactorDimensionItem(
            name = "动量分布 (MOM)",
            shortLabel = "动量分布",
            score = factorScores.momentumScore,
            color = QuantCyan,
            currentValueStr = "20日动量: ${if (factorScores.momentum20DPercent >= 0) "+" else ""}${factorScores.momentum20DPercent}% · 日涨跌: ${quote?.formattedChangePercent ?: "--"}",
            percentileStr = if (factorScores.momentumScore >= 75) "多周期动量共振 · MA5/MA20主升突破态" else if (factorScores.momentumScore >= 50) "区间中性震荡蓄势 · 动量多空平衡" else "破位下行态势 · 动量持续流出",
            thesisExplanation = "跨越5日/20日多周期动量分布与均线通道斜率，右侧跟踪主力资金建仓主升浪，杜绝左侧阴跌抄底踩雷。",
            benchmarkScore = 54
        ),
        FactorDimensionItem(
            name = "盈利质量 (ROE)",
            shortLabel = "盈利质量",
            score = factorScores.qualityScore,
            color = StockRed,
            currentValueStr = "测算内生ROE: ${factorScores.roeEstimatedPercent}% · 市值: ${String.format(java.util.Locale.US, "%.0f", quote?.totalMarketCapHundredMillion ?: 0.0)}亿",
            percentileStr = if (factorScores.qualityScore >= 80) "顶级垄断护城河 · 巴菲特特许权高资本回报" else if (factorScores.qualityScore >= 65) "大盘核心白马 · 盈利质量优良" else "资本回报平庸或微盘抗风险脆弱",
            thesisExplanation = "结合杜邦拆解PB/PE内生净资产收益率与千亿市值护城河壁垒，评估核心资产抗周期抗风险硬实力。",
            benchmarkScore = 55
        ),
        FactorDimensionItem(
            name = "波动风控 (VOL)",
            shortLabel = "波动风控",
            score = factorScores.riskControlScore,
            color = SuccessGreen,
            currentValueStr = "年化已实现HV20: ${factorScores.realizedVol20D}% · 日振幅: ${String.format(java.util.Locale.US, "%.1f", amp)}%",
            percentileStr = if (factorScores.riskControlScore >= 80) "低波异象顶级防守堡垒 · 下行回撤浅" else if (factorScores.riskControlScore >= 60) "大盘适度良性波幅 · 风险受控" else "高波动高Beta · 游资剧烈博弈踩踏隐患",
            thesisExplanation = "量化低波动率（Low-Volatility）异象模型，HV20年化波动越低复利胜率越高，严控极端黑天鹅下行风险。",
            benchmarkScore = 50
        ),
        FactorDimensionItem(
            name = "微观筹码 (DEPTH)",
            shortLabel = "微观筹码",
            score = microScore,
            color = WarningAmber,
            currentValueStr = "换手率: ${String.format(java.util.Locale.US, "%.2f", turnover)}% · 盘口五档承接",
            percentileStr = if (microScore >= 80) "机构黄金健康换手 · 盘口深度与流动性充沛" else if (turnover > 14.0) "⚠️游资过度换手 · 筹码松动踩踏风险" else "筹码锁定良好 · 流动性平稳",
            thesisExplanation = "微观挂单厚度、日内买卖承接与换手率Z-score，测算大资金调仓冲击成本与流动性陷阱。",
            benchmarkScore = 50
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = adaptive.cardHorizontalPadding, vertical = 6.dp)
            .testTag("quant_radar_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(adaptive.cardInnerPadding)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(QuantCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "多因子分析仪表盘",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "雷达图谱 · 实时动态权重评分",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dynamic Composite Score Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCustomWeighted) WallStreetGoldSoft else TerminalSurfaceElevated)
                        .border(
                            1.dp,
                            if (isCustomWeighted) WallStreetGold else TerminalBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${dynamicCompositeScore}分",
                            color = if (isCustomWeighted) WallStreetGold else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = if (isCustomWeighted) "调权实时" else factorScores.rating.take(4),
                            color = if (isCustomWeighted) WallStreetGold else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dual Series Legend & Toggle Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(QuantCyan)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "当前标的", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (showBenchmarkOverlay) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(WallStreetGold.copy(alpha = 0.7f))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "沪深300基准 (50分)", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showBenchmarkOverlay) WallStreetGoldSoft else TerminalBorder)
                        .clickable { showBenchmarkOverlay = !showBenchmarkOverlay }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (showBenchmarkOverlay) "隐藏基准" else "+ 叠加基准",
                        color = if (showBenchmarkOverlay) WallStreetGold else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hexagonal Radar Canvas (adaptive height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adaptive.radarHeight),
                contentAlignment = Alignment.Center
            ) {
                RechartsD3RadarCanvas(
                    factorItems = factorItems,
                    showBenchmark = showBenchmarkOverlay,
                    selectedIndex = selectedFactorIndex,
                    onSelectFactor = { selectedFactorIndex = it }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6-Factor Interactive Selection Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                factorItems.forEachIndexed { idx, item ->
                    val isSelected = selectedFactorIndex == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) item.color.copy(alpha = 0.22f) else TerminalSurfaceElevated)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) item.color else TerminalBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedFactorIndex = idx }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("factor_pill_$idx")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(item.color)
                            )
                            Text(
                                text = item.shortLabel,
                                color = if (isSelected) item.color else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "${item.score}",
                                color = if (isSelected) TextPrimary else TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Spotlight Card for Selected Factor
            val activeItem = factorItems[selectedFactorIndex]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("factor_spotlight_card"),
                colors = CardDefaults.cardColors(containerColor = TerminalSurfaceElevated),
                shape = RoundedCornerShape(10.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(activeItem.color.copy(alpha = 0.4f)))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Row 1: Weighted factor name + guaranteed unclipped score
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "【${activeItem.name}】",
                            color = activeItem.color,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "${activeItem.score}分",
                            color = activeItem.color,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 2: Full-width live factor readout (never collides with score!)
                    Text(
                        text = activeItem.currentValueStr,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "行业分位",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        LinearProgressIndicator(
                            progress = { (activeItem.score / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = activeItem.color,
                            trackColor = TerminalBorder
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${activeItem.score}%",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "🎯 ${activeItem.percentileStr}",
                        color = SuccessGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "💡 ${activeItem.thesisExplanation}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==============================================================
            // 🔬 Stoikov 微观真实定价与 Barra 风格中性化面板 (Tier-1 对冲基金专有模型)
            // ==============================================================
            factorScores.stoikovMicroPrice?.let { stoikov ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, QuantCyan.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stoikov (2018) 微观中间价",
                                color = QuantCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = "OFI: ${if (stoikov.orderFlowImbalance >= 0) "+" else ""}${stoikov.orderFlowImbalance}",
                                color = if (stoikov.orderFlowImbalance >= 0) StockRed else StockGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
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
                                text = "中位价: ¥${stoikov.midPrice} ➔ 微观公允: ¥${stoikov.microPrice}",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${if (stoikov.microPriceSpreadBps >= 0) "+" else ""}${stoikov.microPriceSpreadBps} bps",
                                color = if (stoikov.microPriceSpreadBps >= 0) StockRed else StockGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stoikov.buyerUrgencyVerdict,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            factorScores.barraExposure?.let { barra ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, WallStreetGold.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Barra 正交中性化 (CNE6)",
                                color = WallStreetGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "纯Alpha: ${barra.pureResidualAlphaScore}分 (原始 ${barra.rawAlphaScore})",
                                color = WallStreetGold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "规模 Size", color = TextMuted, fontSize = 9.5.sp, maxLines = 1, softWrap = false)
                                Text(text = "${if (barra.sizeExposureZ >= 0) "+" else ""}${barra.sizeExposureZ}σ", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "贝塔 Beta", color = TextMuted, fontSize = 9.5.sp, maxLines = 1, softWrap = false)
                                Text(text = "${if (barra.betaExposureZ >= 0) "+" else ""}${barra.betaExposureZ}σ", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "动量 Mom", color = TextMuted, fontSize = 9.5.sp, maxLines = 1, softWrap = false)
                                Text(text = "${if (barra.momentumExposureZ >= 0) "+" else ""}${barra.momentumExposureZ}σ", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "波动 Vol", color = TextMuted, fontSize = 9.5.sp, maxLines = 1, softWrap = false)
                                Text(text = "${if (barra.volatilityExposureZ >= 0) "+" else ""}${barra.volatilityExposureZ}σ", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "风格裁决: ${barra.principalRiskFactor}",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ==============================================================
            // 🎛️ 动态多因子权重调节滑块组
            // ==============================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("factor_weight_sliders_card"),
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isSlidersExpanded = !isSlidersExpanded }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = WallStreetGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "动态多因子权重滑块调节",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "拖动实时重新计算综合评分",
                                    color = TextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCustomWeighted) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TerminalSurfaceElevated)
                                        .clickable {
                                            weightPe = 20f
                                            weightPb = 15f
                                            weightMom = 20f
                                            weightRoe = 20f
                                            weightVol = 15f
                                            weightMicro = 10f
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                        .testTag("reset_sliders_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = "重置",
                                            tint = WarningAmber,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = "重置", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            IconButton(
                                onClick = { isSlidersExpanded = !isSlidersExpanded },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSlidersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TerminalSurfaceElevated)
                            .border(1.dp, if (isCustomWeighted) WallStreetGoldSoft else TerminalBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "动态重构量化评分", color = TextMuted, fontSize = 11.sp, maxLines = 1)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$dynamicCompositeScore",
                                    color = if (dynamicCompositeScore >= 80) StockRed else if (dynamicCompositeScore >= 52) WallStreetGold else StockGreen,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(text = " / 100", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
                                if (isCustomWeighted) {
                                    val delta = dynamicCompositeScore - factorScores.compositeScore
                                    val deltaSign = if (delta >= 0) "+$delta" else "$delta"
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "($deltaSign)",
                                        color = if (delta >= 0) StockRed else StockGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "当前策略裁定", color = TextMuted, fontSize = 11.sp, maxLines = 1)
                            Text(
                                text = dynamicRating,
                                color = if (dynamicCompositeScore >= 70) WallStreetGold else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    AnimatedVisibility(visible = isSlidersExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(text = "一键预设权重策略：", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickWeightPresetPill(
                                    title = "均衡标准",
                                    onClick = {
                                        weightPe = 20f; weightPb = 15f; weightMom = 20f; weightRoe = 20f; weightVol = 15f; weightMicro = 10f
                                    }
                                )
                                QuickWeightPresetPill(
                                    title = "深度价值(高PE/PB)",
                                    onClick = {
                                        weightPe = 35f; weightPb = 30f; weightMom = 10f; weightRoe = 15f; weightVol = 5f; weightMicro = 5f
                                    }
                                )
                                QuickWeightPresetPill(
                                    title = "动量主升(高动量)",
                                    onClick = {
                                        weightPe = 10f; weightPb = 5f; weightMom = 40f; weightRoe = 15f; weightVol = 10f; weightMicro = 20f
                                    }
                                )
                                QuickWeightPresetPill(
                                    title = "巴菲特质选(高ROE)",
                                    onClick = {
                                        weightPe = 20f; weightPb = 10f; weightMom = 5f; weightRoe = 40f; weightVol = 20f; weightMicro = 5f
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            FactorWeightSliderRow("市盈率估值 (PE)", (normPe * 100).toInt(), weightPe, { weightPe = it }, WallStreetGold, peScore)
                            Spacer(modifier = Modifier.height(10.dp))
                            FactorWeightSliderRow("市净率弹性 (PB)", (normPb * 100).toInt(), weightPb, { weightPb = it }, InstitutionalIndigo, pbScore)
                            Spacer(modifier = Modifier.height(10.dp))
                            FactorWeightSliderRow("动量指标趋势 (MOM)", (normMom * 100).toInt(), weightMom, { weightMom = it }, QuantCyan, factorScores.momentumScore)
                            Spacer(modifier = Modifier.height(10.dp))
                            FactorWeightSliderRow("盈利质量壁垒 (ROE)", (normRoe * 100).toInt(), weightRoe, { weightRoe = it }, StockRed, factorScores.qualityScore)
                            Spacer(modifier = Modifier.height(10.dp))
                            FactorWeightSliderRow("波动回撤风控 (VOL)", (normVol * 100).toInt(), weightVol, { weightVol = it }, SuccessGreen, factorScores.riskControlScore)
                            Spacer(modifier = Modifier.height(10.dp))
                            FactorWeightSliderRow("微观筹码流动性 (DEPTH)", (normMicro * 100).toInt(), weightMicro, { weightMicro = it }, WarningAmber, microScore)
                        }
                    }
                }
            }

            // ==============================================================
            // 🎯 量化交易建议卡片
            // ==============================================================
            if (quote != null) {
                val tradeRec = remember(quote, dynamicCompositeScore, kLines) {
                    QuantEngine.generateTradeRecommendation(
                        quote = quote,
                        factorScores = factorScores,
                        dynamicScore = dynamicCompositeScore,
                        kLines = kLines
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                QuantTradeAdviceCard(recommendation = tradeRec)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantTradeAdviceCard(
    recommendation: QuantTradeRecommendation,
    modifier: Modifier = Modifier
) {
    val (actionColor, actionIcon, actionBg) = when (recommendation.action) {
        SignalDirection.BUY -> Triple(StockRed, Icons.AutoMirrored.Filled.TrendingUp, StockRedSoft)
        SignalDirection.SELL -> Triple(CriticalRed, Icons.AutoMirrored.Filled.TrendingDown, CriticalRed.copy(alpha = 0.15f))
        else -> Triple(WarningAmber, Icons.Default.HourglassEmpty, WarningAmber.copy(alpha = 0.15f))
    }

    val fontScale = LocalDensity.current.fontScale

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quant_trade_advice_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(actionColor.copy(alpha = 0.45f)))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            val isNarrow = isNarrowPortrait(maxWidth, fontScale)

            Column(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(actionBg)
                            .border(1.dp, actionColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = actionColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (recommendation.action) {
                                    SignalDirection.BUY -> "量化指令: 【买入 BUY】"
                                    SignalDirection.SELL -> "量化指令: 【卖出 SELL】"
                                    else -> "量化指令: 【持有 HOLD】"
                                },
                                color = actionColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TerminalSurfaceElevated)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = recommendation.confidenceLevel,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = recommendation.actionTitle,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TradeParamBox(
                        label = "预估实战净胜率",
                        value = "${recommendation.winRateEstimate}%",
                        color = if (recommendation.winRateEstimate >= 65.0) StockRed else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TradeParamBox(
                        label = "风险收益盈亏比",
                        value = recommendation.profitLossRatio,
                        color = WallStreetGold,
                        modifier = Modifier.weight(1f)
                    )
                    TradeParamBox(
                        label = "建议半凯利仓位",
                        value = recommendation.suggestedPositionRange.take(8),
                        color = QuantCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (isNarrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "建议建仓挂单区间", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    text = recommendation.entryPriceRange,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "动态目标价", color = TextMuted, fontSize = 10.5.sp)
                                    Text(
                                        text = "¥${String.format(java.util.Locale.US, "%.2f", recommendation.targetPrice)}",
                                        color = StockRed,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "保底止损价", color = TextMuted, fontSize = 10.5.sp)
                                    Text(
                                        text = "¥${String.format(java.util.Locale.US, "%.2f", recommendation.stopLossFloorPrice)}",
                                        color = StockGreen,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(text = "建议建仓挂单区间", color = TextMuted, fontSize = 11.sp, maxLines = 1)
                                Text(
                                    text = recommendation.entryPriceRange,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "动态目标价", color = TextMuted, fontSize = 10.5.sp, maxLines = 1)
                                    Text(
                                        text = "¥${String.format(java.util.Locale.US, "%.2f", recommendation.targetPrice)}",
                                        color = StockRed,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "保底止损价", color = TextMuted, fontSize = 10.5.sp, maxLines = 1)
                                    Text(
                                        text = "¥${String.format(java.util.Locale.US, "%.2f", recommendation.stopLossFloorPrice)}",
                                        color = StockGreen,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "📌 核心驱动: ",
                    color = WallStreetGold,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = recommendation.primaryFactorDriver,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = recommendation.quantitativeThesis,
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalBorder.copy(alpha = 0.5f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recommendation.riskWarning,
                    color = WarningAmber,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            }
        }
    }
}

@Composable
private fun TradeParamBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalSurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(text = label, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickWeightPresetPill(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("preset_$title")
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FactorWeightSliderRow(
    label: String,
    currentWeightPercent: Int,
    rawValue: Float,
    onValueChange: (Float) -> Unit,
    color: Color,
    score: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "得分 $score",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "权重 $currentWeightPercent%",
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Slider(
            value = rawValue,
            onValueChange = onValueChange,
            valueRange = 0f..50f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = TerminalBorder,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
    }
}

data class FactorDimensionItem(
    val name: String,
    val shortLabel: String,
    val score: Int,
    val color: Color,
    val currentValueStr: String,
    val percentileStr: String,
    val thesisExplanation: String,
    val benchmarkScore: Int = 50
)

@Composable
private fun RechartsD3RadarCanvas(
    factorItems: List<FactorDimensionItem>,
    showBenchmark: Boolean,
    selectedIndex: Int,
    onSelectFactor: (Int) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recharts_radar_canvas")
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = (size.minDimension / 2f) * 0.72f

        val numAxes = 6
        val angleStep = (2 * Math.PI / numAxes).toFloat()
        val startAngle = -Math.PI.toFloat() / 2f

        val webLevels = 4
        for (w in 1..webLevels) {
            val r = radius * (w / webLevels.toFloat())
            val webPath = Path()
            for (i in 0 until numAxes) {
                val angle = startAngle + i * angleStep
                val x = centerX + r * cos(angle)
                val y = centerY + r * sin(angle)
                if (i == 0) webPath.moveTo(x, y) else webPath.lineTo(x, y)
            }
            webPath.close()

            val strokeColor = if (w == 2) TerminalBorderLight.copy(alpha = 0.8f) else TerminalBorder.copy(alpha = 0.45f)
            val strokeWidth = if (w == 2) 1.2f else 0.8f
            drawPath(webPath, color = strokeColor, style = Stroke(width = strokeWidth))
        }

        for (i in 0 until numAxes) {
            val angle = startAngle + i * angleStep
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            drawLine(
                color = TerminalBorderLight.copy(alpha = 0.5f),
                start = Offset(centerX, centerY),
                end = Offset(x, y),
                strokeWidth = 1f
            )
        }

        if (showBenchmark) {
            val benchPath = Path()
            for (i in 0 until numAxes) {
                val angle = startAngle + i * angleStep
                val benchFactor = factorItems[i].benchmarkScore / 100f
                val r = radius * benchFactor
                val x = centerX + r * cos(angle)
                val y = centerY + r * sin(angle)
                if (i == 0) benchPath.moveTo(x, y) else benchPath.lineTo(x, y)
            }
            benchPath.close()

            drawPath(benchPath, color = WallStreetGold.copy(alpha = 0.08f), style = Fill)
            drawPath(
                benchPath,
                color = WallStreetGold.copy(alpha = 0.70f),
                style = Stroke(
                    width = 1.6f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
            )
        }

        val stockPath = Path()
        val vertexOffsets = mutableListOf<Offset>()

        for (i in 0 until numAxes) {
            val angle = startAngle + i * angleStep
            val factorValue = (factorItems[i].score / 100f).coerceIn(0.15f, 1.0f)
            val r = radius * factorValue
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)
            vertexOffsets.add(Offset(x, y))
            if (i == 0) stockPath.moveTo(x, y) else stockPath.lineTo(x, y)
        }
        stockPath.close()

        drawPath(stockPath, color = QuantCyan.copy(alpha = 0.32f), style = Fill)
        drawPath(stockPath, color = QuantCyan, style = Stroke(width = 2.4f))

        vertexOffsets.forEachIndexed { i, offset ->
            val isSelected = selectedIndex == i
            val dotColor = factorItems[i].color

            if (isSelected) {
                drawCircle(color = dotColor.copy(alpha = 0.35f), radius = 9f, center = offset)
                drawCircle(color = dotColor, radius = 5.5f, center = offset)
                drawCircle(color = Color.White, radius = 2.5f, center = offset)
            } else {
                drawCircle(color = QuantCyan.copy(alpha = 0.5f), radius = 5f, center = offset)
                drawCircle(color = QuantCyan, radius = 3.5f, center = offset)
            }
        }
    }
}
