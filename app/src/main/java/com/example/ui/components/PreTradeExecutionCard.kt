package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.ProfitabilityEngine
import com.example.ui.theme.*
import kotlin.math.round

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreTradeExecutionCard(
    quote: StockQuote,
    kLines: List<KLinePoint>,
    benchmarkKLines: List<KLinePoint>,
    marketBreadth: MarketBreadthSnapshot?,
    marketRegime: MarketRegime,
    liveFinancialSnapshot: FinancialStatementSnapshot? = null,
    onSaveTradePlan: (
        symbol: String,
        stockName: String,
        entryPrice: Double,
        stopPrice: Double,
        targetPrice: Double,
        shares: Int,
        plannedRiskRmb: Double,
        gateVerdict: String,
        thesis: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = LocalAdaptiveLayout.current.cardHorizontalPadding
) {
    val adaptive = LocalAdaptiveLayout.current
    val fontScale = LocalDensity.current.fontScale

    val vcp = remember(quote, kLines, benchmarkKLines) {
        ProfitabilityEngine.analyzeStageAndVcp(quote, kLines, benchmarkKLines)
    }

    val effectiveBreadth = marketBreadth ?: remember(marketRegime) {
        ProfitabilityEngine.computeEmpiricalMarketBreadth(
            indexQuotes = emptyList(),
            universeQuotes = emptyList(),
            universeKLines = emptyMap(),
            fallbackRegime = marketRegime
        )
    }

    var capitalText by remember { mutableStateOf("100000") }
    var hasCustomRisk by remember { mutableStateOf(false) }
    var riskPct by remember {
        mutableDoubleStateOf(effectiveBreadth.regime.recommendedSingleRiskPercent)
    }
    LaunchedEffect(effectiveBreadth.regime) {
        if (!hasCustomRisk) {
            riskPct = effectiveBreadth.regime.recommendedSingleRiskPercent
        }
    }
    var hasCustomStop by remember(quote.symbol) { mutableStateOf(false) }
    var stopText by remember(quote.symbol) {
        mutableStateOf(formatPriceInput(vcp.suggestedStopPrice))
    }
    LaunchedEffect(quote.symbol, vcp.suggestedStopPrice) {
        if (!hasCustomStop) {
            stopText = formatPriceInput(vcp.suggestedStopPrice)
        }
    }
    var thesisText by remember(quote.symbol) { mutableStateOf("") }
    var showVcpChecklist by remember { mutableStateOf(false) }
    var savedBannerVisible by remember(quote.symbol) { mutableStateOf(false) }

    val capitalVal = capitalText.toDoubleOrNull()?.coerceAtLeast(1000.0) ?: 100000.0
    val stopVal = stopText.toDoubleOrNull() ?: vcp.suggestedStopPrice

    val plan = remember(quote, kLines, capitalVal, riskPct, stopVal, effectiveBreadth, vcp, liveFinancialSnapshot) {
        ProfitabilityEngine.buildPreTradeExecutionPlan(
            quote = quote,
            kLines = kLines,
            accountCapitalRmb = capitalVal,
            riskPerTradePercent = riskPct,
            customStopPrice = stopVal,
            breadthSnapshot = effectiveBreadth,
            vcpAnalysis = vcp,
            liveFinancialSnapshot = liveFinancialSnapshot
        )
    }

    val verdictColor = when (plan.gateVerdict) {
        GateVerdict.PASS -> SuccessGreen
        GateVerdict.CAUTION -> WarningAmber
        GateVerdict.BLOCK -> CriticalRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = 6.dp)
            .testTag("pre_trade_execution_card"),
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
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WallStreetGoldSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GppGood,
                            contentDescription = "交易前风控闸门与以损定仓",
                            tint = WallStreetGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "VCP 形态 · 以损定仓 · 开仓铁律闸门",
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${vcp.stage.label} · 紧缩评分 ${vcp.vcpScore}/100",
                            color = if (vcp.isVcpTightSetup) SuccessGreen else TextMuted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(verdictColor.copy(alpha = 0.16f))
                        .border(1.dp, verdictColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("pre_trade_gate_verdict")
                ) {
                    Text(
                        text = plan.gateVerdict.name,
                        color = verdictColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1. VCP & Stage-2 Metrics Strip
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MetricPill("波幅收敛", "${vcp.volatilityContractionRatio}x", if (vcp.volatilityContractionRatio <= 0.82) SuccessGreen else TextPrimary)
                MetricPill("量能萎缩", "${vcp.volumeDryUpRatio}x", if (vcp.volumeDryUpRatio <= 0.90) QuantCyan else TextPrimary)
                MetricPill(
                    "MA20偏离",
                    "${if (vcp.extensionFromMa20Percent >= 0) "+" else ""}${vcp.extensionFromMa20Percent}%",
                    if (vcp.extensionFromMa20Percent > 10.0) WarningAmber else TextPrimary
                )
                MetricPill(
                    "相对沪深300",
                    "${if (vcp.relativeStrengthVsIndexPercent >= 0) "+" else ""}${vcp.relativeStrengthVsIndexPercent}%",
                    if (vcp.relativeStrengthVsIndexPercent >= 0.0) StockRed else StockGreen
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expandable VCP 7-point checklist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .clickable { showVcpChecklist = !showVcpChecklist }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("pre_trade_vcp_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stage-2 / VCP 买点体检 (${vcp.checklistPassedCount}/${vcp.checklistItems.size}项达标 · 枢轴 ¥${vcp.pivotBreakoutPrice})",
                    color = QuantCyan,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (showVcpChecklist) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "展开VCP体检明细",
                    tint = QuantCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showVcpChecklist) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalBg.copy(alpha = 0.65f))
                        .padding(8.dp)
                        .testTag("pre_trade_vcp_checklist"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    vcp.checklistItems.forEach { (itemLabel, passed) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (passed) SuccessGreen else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = itemLabel,
                                color = if (passed) TextPrimary else TextMuted,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                    Text(
                        text = vcp.setupVerdict,
                        color = WallStreetGold,
                        fontSize = 10.5.sp,
                        lineHeight = 14.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 1R Fixed-Risk Position Sizer Inputs
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val singleCol = isUltraNarrowPortrait(maxWidth, fontScale)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (singleCol) {
                        SizerOutlinedField(
                            value = capitalText,
                            onValueChange = { capitalText = sanitizePositiveNum(it, allowDecimal = false) },
                            label = "账户总资金 (元)",
                            suffix = "元",
                            testTag = "pre_trade_capital_input"
                        )
                        SizerOutlinedField(
                            value = stopText,
                            onValueChange = {
                                hasCustomStop = true
                                stopText = sanitizePositiveNum(it, allowDecimal = true)
                            },
                            label = "硬止损价 (建议 ¥${vcp.suggestedStopPrice})",
                            suffix = "元",
                            testTag = "pre_trade_stop_input"
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SizerOutlinedField(
                                value = capitalText,
                                onValueChange = { capitalText = sanitizePositiveNum(it, allowDecimal = false) },
                                label = "账户总资金 (元)",
                                suffix = "元",
                                modifier = Modifier.weight(1f),
                                testTag = "pre_trade_capital_input"
                            )
                            SizerOutlinedField(
                                value = stopText,
                                onValueChange = {
                                    hasCustomStop = true
                                    stopText = sanitizePositiveNum(it, allowDecimal = true)
                                },
                                label = "硬止损价 (元)",
                                suffix = "元",
                                modifier = Modifier.weight(1f),
                                testTag = "pre_trade_stop_input"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Risk per trade (1R) selector + Reset Stop
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "单笔最大亏损(1R):",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                listOf(0.5, 0.75, 1.0, 1.5).forEach { r ->
                    val sel = kotlin.math.abs(riskPct - r) < 0.01
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) QuantCyanSoft else TerminalSurfaceElevated)
                            .border(1.dp, if (sel) QuantCyan else TerminalBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                hasCustomRisk = true
                                riskPct = r
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("pre_trade_risk_${r}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${r}%",
                            color = if (sel) QuantCyan else TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TerminalSurfaceElevated)
                        .border(0.8.dp, WallStreetGold.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .clickable {
                            hasCustomStop = false
                            stopText = formatPriceInput(vcp.suggestedStopPrice)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("pre_trade_reset_stop_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = "还原建议止损", tint = WallStreetGold, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("建议止损", color = WallStreetGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Calculated 1R Sizing Output Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalSurfaceElevated)
                    .border(1.dp, verdictColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("pre_trade_sizing_panel"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("A股整手可买上限 (以损定仓)", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "${plan.finalBoardLotShares} 股 (${plan.finalBoardLotShares / 100}手)",
                            color = if (plan.finalBoardLotShares > 0) WallStreetGold else CriticalRed,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("pre_trade_board_lot_shares")
                        )
                        Text(
                            text = "仓位 ¥${plan.positionValueRmb.toInt()} (${plan.positionPercentOfAccount}% · 单票限额${plan.maxPermittedPositionPercent}%)",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(0.85f), horizontalAlignment = Alignment.End) {
                        Text("1R 允许最大亏损", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "-¥${plan.riskBudgetRmb.toInt()}",
                            color = StockGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("pre_trade_risk_budget")
                        )
                        Text(
                            text = "止损距 -${plan.stopDistancePercent}%",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 1R / 2R / 3R Asymmetric Profit Targets
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MetricPill("1R保本推损位", "¥${plan.target1RPrice}", TextPrimary)
                    MetricPill("2R标准兑现位", "¥${plan.target2RPrice}", QuantCyan)
                    MetricPill("3R趋势主升位", "¥${plan.target3RPrice}", StockRed)
                }

                HorizontalDivider(color = TerminalBorder.copy(alpha = 0.6f), thickness = 0.75.dp)

                // 4. Five Pre-Trade Discipline Gates
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pre_trade_gate_list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "交易前五重铁律闸门: ${plan.gateVerdict.label}",
                        color = verdictColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    plan.gateItems.forEach { gate ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (gate.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (gate.passed) SuccessGreen else CriticalRed,
                                modifier = Modifier
                                    .size(13.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${gate.gateName} · ${gate.detail}",
                                    color = if (gate.passed) TextSecondary else WarningAmber,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                if (plan.executionWarning != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(verdictColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = plan.executionWarning,
                            color = verdictColor,
                            fontSize = 10.5.sp,
                            lineHeight = 14.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Optional Trade Thesis & Save to Journal Button
            OutlinedTextField(
                value = thesisText,
                onValueChange = { thesisText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pre_trade_thesis_input"),
                label = { Text("买入逻辑备忘 (如: VCP紧缩突破 / 七折击球买点)", fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = WallStreetGold,
                    unfocusedBorderColor = TerminalBorder,
                    focusedContainerColor = TerminalSurfaceElevated,
                    unfocusedContainerColor = TerminalSurfaceElevated
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val sharesToSave = if (plan.finalBoardLotShares > 0) plan.finalBoardLotShares else 100
                    onSaveTradePlan(
                        quote.symbol,
                        quote.name,
                        plan.entryPrice,
                        plan.stopPrice,
                        plan.target2RPrice,
                        sharesToSave,
                        plan.riskBudgetRmb,
                        plan.gateVerdict.name,
                        thesisText.ifBlank { "${vcp.stage.label} · 止损-${plan.stopDistancePercent}% · 目标2R" }
                    )
                    savedBannerVisible = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("pre_trade_save_plan_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (plan.gateVerdict == GateVerdict.BLOCK) WarningAmber.copy(alpha = 0.2f) else WallStreetGoldSoft,
                    contentColor = if (plan.gateVerdict == GateVerdict.BLOCK) WarningAmber else WallStreetGold
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (plan.gateVerdict == GateVerdict.BLOCK) {
                        "记录观察计划 (触发闸门拦截 · 请勿实盘重仓)"
                    } else {
                        "记入实盘交易账本 (${if (plan.finalBoardLotShares > 0) plan.finalBoardLotShares else 100}股 · 1R=¥${plan.riskBudgetRmb.toInt()})"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (savedBannerVisible) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "✓ 已写入交易账本！请前往「风险监控」页面跟踪实盘持仓与平仓 R 倍数复盘。",
                    color = SuccessGreen,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("pre_trade_saved_banner")
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("$label:", color = TextMuted, fontSize = 10.sp, maxLines = 1)
        Text(
            text = value,
            color = valueColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun SizerOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        label = { Text(label, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        suffix = { Text(suffix, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = WallStreetGold,
            unfocusedBorderColor = TerminalBorder,
            focusedContainerColor = TerminalSurfaceElevated,
            unfocusedContainerColor = TerminalSurfaceElevated
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

private fun sanitizePositiveNum(raw: String, allowDecimal: Boolean): String {
    val sb = StringBuilder(raw.length)
    var hasDot = false
    raw.forEach { ch ->
        when {
            ch in '0'..'9' -> sb.append(ch)
            ch == '.' && allowDecimal && !hasDot -> {
                hasDot = true
                sb.append(ch)
            }
        }
    }
    return sb.toString()
}

private fun formatPriceInput(v: Double): String {
    val r = round(v * 100.0) / 100.0
    return String.format(java.util.Locale.US, "%.2f", r)
}
