package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.DcfValuationEngine
import com.example.ui.theme.*
import kotlin.math.round

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DcfCalculatorCard(
    quote: StockQuote,
    kLines: List<KLinePoint>,
    liveFinancialSnapshot: FinancialStatementSnapshot? = null,
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = LocalAdaptiveLayout.current.cardHorizontalPadding
) {
    val adaptive = LocalAdaptiveLayout.current

    val snapshotAndAudit = remember(quote, kLines, liveFinancialSnapshot) {
        DcfValuationEngine.evaluateProfessionalDcf(quote, kLines, liveFinancialSnapshot)
    }
    val snapshot = snapshotAndAudit.first
    val audit = snapshotAndAudit.second
    val defaultInput = remember(quote.symbol, liveFinancialSnapshot?.reportPeriod, quote.price) {
        DcfValuationEngine.defaultCustomDcfInput(quote, kLines, liveFinancialSnapshot)
    }

    // Editable text state keyed on stock symbol & live F10 period so switching stocks re-seeds cleanly
    var fcfText by remember(quote.symbol, liveFinancialSnapshot?.reportPeriod) {
        mutableStateOf(formatInputNumber(defaultInput.baseFcfBillion))
    }
    var growthText by remember(quote.symbol, liveFinancialSnapshot?.reportPeriod) {
        mutableStateOf(formatInputNumber(defaultInput.stage1GrowthPercent))
    }
    var discountText by remember(quote.symbol, liveFinancialSnapshot?.reportPeriod) {
        mutableStateOf(formatInputNumber(defaultInput.discountRatePercent))
    }
    var terminalMethod by remember(quote.symbol) {
        mutableStateOf(defaultInput.terminalMethod)
    }
    var terminalGrowthText by remember(quote.symbol, liveFinancialSnapshot?.reportPeriod) {
        mutableStateOf(formatInputNumber(defaultInput.terminalGrowthPercent))
    }
    var terminalMultipleText by remember(quote.symbol) {
        mutableStateOf(formatInputNumber(defaultInput.terminalMultiple))
    }
    var netCashText by remember(quote.symbol, liveFinancialSnapshot?.reportPeriod) {
        mutableStateOf(formatInputNumber(defaultInput.netCashBillion))
    }
    var projectionYears by remember(quote.symbol) {
        mutableIntStateOf(defaultInput.projectionYears)
    }
    var safetyDiscountPct by remember(quote.symbol) {
        mutableDoubleStateOf(defaultInput.safetyDiscountPercent)
    }
    var isScheduleExpanded by remember(quote.symbol) {
        mutableStateOf(false)
    }

    val activeInput = remember(
        fcfText,
        growthText,
        discountText,
        terminalMethod,
        terminalGrowthText,
        terminalMultipleText,
        netCashText,
        projectionYears,
        safetyDiscountPct,
        defaultInput
    ) {
        CustomDcfInput(
            baseFcfBillion = fcfText.toDoubleOrNull() ?: 0.0,
            stage1GrowthPercent = (growthText.toDoubleOrNull() ?: defaultInput.stage1GrowthPercent).coerceIn(-50.0, 100.0),
            discountRatePercent = (discountText.toDoubleOrNull() ?: defaultInput.discountRatePercent).coerceIn(1.0, 50.0),
            projectionYears = projectionYears,
            terminalMethod = terminalMethod,
            terminalGrowthPercent = (terminalGrowthText.toDoubleOrNull() ?: defaultInput.terminalGrowthPercent).coerceIn(-2.0, 10.0),
            terminalMultiple = (terminalMultipleText.toDoubleOrNull() ?: defaultInput.terminalMultiple).coerceIn(1.0, 60.0),
            netCashBillion = netCashText.toDoubleOrNull() ?: 0.0,
            safetyDiscountPercent = safetyDiscountPct
        )
    }

    val result = remember(quote, activeInput) {
        DcfValuationEngine.computeCustomDcf(quote, activeInput)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = 6.dp)
            .testTag("dcf_calculator_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(adaptive.cardInnerPadding)) {
            // 1. Header + Sync Baseline Button
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
                            .background(QuantCyanSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "DCF计算器",
                            tint = QuantCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "「猫会计」DCF 估值计算器",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${quote.name} · 自定义FCF/增速/折现率/终值测算",
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalSurfaceElevated)
                        .border(1.dp, WallStreetGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            fcfText = formatInputNumber(defaultInput.baseFcfBillion)
                            growthText = formatInputNumber(defaultInput.stage1GrowthPercent)
                            discountText = formatInputNumber(defaultInput.discountRatePercent)
                            terminalMethod = defaultInput.terminalMethod
                            terminalGrowthText = formatInputNumber(defaultInput.terminalGrowthPercent)
                            terminalMultipleText = formatInputNumber(defaultInput.terminalMultiple)
                            netCashText = formatInputNumber(defaultInput.netCashBillion)
                            projectionYears = defaultInput.projectionYears
                            safetyDiscountPct = defaultInput.safetyDiscountPercent
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("dcf_calc_sync_baseline_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "同步实盘基准",
                            tint = WallStreetGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "同步基准",
                            color = WallStreetGold,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Mao Accounting Context Pill Bar
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CalcContextChip("净现比", "${snapshot.cashToEarningsRatio}", if (snapshot.cashToEarningsRatio >= 0.95) SuccessGreen else WarningAmber)
                CalcContextChip("Capex吞噬", "${snapshot.capexToOcfPercent}%", if (snapshot.capexToOcfPercent <= 45.0) QuantCyan else WarningAmber)
                CalcContextChip("账面净现金", "${snapshot.netCashBillion}亿", if (snapshot.netCashBillion >= 0) SuccessGreen else StockGreen)
                CalcContextChip("当前市值", "${round1Val(quote.totalMarketCapHundredMillion)}亿", TextPrimary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Mao Accounting Quick Preset Anchors
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetAnchorChip(
                    label = "挤水分FCF(${formatInputNumber(defaultInput.baseFcfBillion)}亿)",
                    onClick = { fcfText = formatInputNumber(defaultInput.baseFcfBillion) },
                    accent = QuantCyan,
                    testTag = "dcf_calc_preset_mao_fcf"
                )
                PresetAnchorChip(
                    label = "财报原始FCF(${formatInputNumber(snapshot.freeCashFlowBillion)}亿)",
                    onClick = { fcfText = formatInputNumber(snapshot.freeCashFlowBillion) },
                    accent = WallStreetGold,
                    testTag = "dcf_calc_preset_raw_fcf"
                )
                PresetAnchorChip(
                    label = "价值锚8%折现",
                    onClick = { discountText = "8.0" },
                    accent = SuccessGreen,
                    testTag = "dcf_calc_preset_8pct"
                )
                PresetAnchorChip(
                    label = "严苛10%折现",
                    onClick = { discountText = "10.0" },
                    accent = WarningAmber,
                    testTag = "dcf_calc_preset_10pct"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Core Editable Inputs (Responsive 2-col or 1-col on ultra-narrow portrait)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val singleCol = isUltraNarrowPortrait(maxWidth, LocalDensity.current.fontScale)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (singleCol) {
                        DcfNumericField(
                            value = fcfText,
                            onValueChange = { fcfText = sanitizeDecimalInput(it, allowNegative = true) },
                            label = "基期自由现金流 FCF₀ (亿元)",
                            suffix = "亿",
                            testTag = "dcf_input_fcf"
                        )
                        DcfNumericField(
                            value = growthText,
                            onValueChange = { growthText = sanitizeDecimalInput(it, allowNegative = true) },
                            label = "预测期年增速 g₁ (%)",
                            suffix = "%",
                            testTag = "dcf_input_growth"
                        )
                        DcfNumericField(
                            value = discountText,
                            onValueChange = { discountText = sanitizeDecimalInput(it, allowNegative = false) },
                            label = "折现率 r / WACC (%)",
                            suffix = "%",
                            testTag = "dcf_input_discount_rate"
                        )
                        DcfNumericField(
                            value = netCashText,
                            onValueChange = { netCashText = sanitizeDecimalInput(it, allowNegative = true) },
                            label = "净现金调整 (亿元, 可负)",
                            suffix = "亿",
                            testTag = "dcf_input_net_cash"
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DcfNumericField(
                                value = fcfText,
                                onValueChange = { fcfText = sanitizeDecimalInput(it, allowNegative = true) },
                                label = "自由现金流 FCF₀ (亿)",
                                suffix = "亿",
                                modifier = Modifier.weight(1f),
                                testTag = "dcf_input_fcf"
                            )
                            DcfNumericField(
                                value = growthText,
                                onValueChange = { growthText = sanitizeDecimalInput(it, allowNegative = true) },
                                label = "预期增速 g₁ (%)",
                                suffix = "%",
                                modifier = Modifier.weight(1f),
                                testTag = "dcf_input_growth"
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DcfNumericField(
                                value = discountText,
                                onValueChange = { discountText = sanitizeDecimalInput(it, allowNegative = false) },
                                label = "折现率 r (%)",
                                suffix = "%",
                                modifier = Modifier.weight(1f),
                                testTag = "dcf_input_discount_rate"
                            )
                            DcfNumericField(
                                value = netCashText,
                                onValueChange = { netCashText = sanitizeDecimalInput(it, allowNegative = true) },
                                label = "净现金调整 (亿)",
                                suffix = "亿",
                                modifier = Modifier.weight(1f),
                                testTag = "dcf_input_net_cash"
                            )
                        }
                    }

                    // Terminal Value Method Toggle + Parameter Input
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TerminalSurfaceElevated)
                            .border(0.75.dp, TerminalBorder, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DcfTerminalMethod.values().forEach { method ->
                                val selected = terminalMethod == method
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) WallStreetGoldSoft else TerminalCard)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) WallStreetGold else TerminalBorder,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { terminalMethod = method }
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                        .testTag("dcf_terminal_mode_${method.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = method.label,
                                        color = if (selected) WallStreetGold else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DcfNumericField(
                                value = if (terminalMethod == DcfTerminalMethod.PERPETUAL_GROWTH) terminalGrowthText else terminalMultipleText,
                                onValueChange = { v ->
                                    val clean = sanitizeDecimalInput(v, allowNegative = false)
                                    if (terminalMethod == DcfTerminalMethod.PERPETUAL_GROWTH) {
                                        terminalGrowthText = clean
                                    } else {
                                        terminalMultipleText = clean
                                    }
                                },
                                label = if (terminalMethod == DcfTerminalMethod.PERPETUAL_GROWTH) {
                                    "永续增长率 g∞ (%)"
                                } else {
                                    "终值退出倍数 (×FCF)"
                                },
                                suffix = if (terminalMethod == DcfTerminalMethod.PERPETUAL_GROWTH) "%" else "x",
                                modifier = Modifier.weight(1.2f),
                                testTag = "dcf_input_terminal_param"
                            )

                            Column(
                                modifier = Modifier.weight(0.8f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "预测期", color = TextMuted, fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(5, 10).forEach { yrs ->
                                        val sel = projectionYears == yrs
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (sel) QuantCyanSoft else TerminalCard)
                                                .border(
                                                    1.dp,
                                                    if (sel) QuantCyan else TerminalBorder,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { projectionYears = yrs }
                                                .testTag("dcf_years_$yrs"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${yrs}年",
                                                color = if (sel) QuantCyan else TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Mao Accounting Safety Margin Discount Chips
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "击球安全边际:",
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            listOf(20.0 to "八折(-20%)", 30.0 to "七折(-30%)", 40.0 to "六折(-40%)").forEach { (pct, label) ->
                                val sel = safetyDiscountPct == pct
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (sel) SuccessGreen.copy(alpha = 0.16f) else TerminalCard)
                                        .border(
                                            0.8.dp,
                                            if (sel) SuccessGreen else TerminalBorder,
                                            RoundedCornerShape(5.dp)
                                        )
                                        .clickable { safetyDiscountPct = pct }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("dcf_mos_${pct.toInt()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (sel) SuccessGreen else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Real-time Calculated Valuation Result Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalSurfaceElevated)
                    .border(1.dp, WallStreetGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("dcf_calc_result_panel"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "DCF 测算每股内在价值", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "¥${String.format(java.util.Locale.US, "%.2f", result.intrinsicPrice)}",
                            color = WallStreetGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("dcf_calc_intrinsic_price")
                        )
                        Text(
                            text = "相对现价安全边际 ${if (result.marginOfSafetyPercent >= 0) "+" else ""}${result.marginOfSafetyPercent}%",
                            color = if (result.marginOfSafetyPercent >= 0) SuccessGreen else StockGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "猫会计击球买点 (${(100 - safetyDiscountPct).toInt()}%)",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "¥${String.format(java.util.Locale.US, "%.2f", result.maoStrikePrice)}",
                            color = if (quote.price <= result.maoStrikePrice) SuccessGreen else QuantCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("dcf_calc_strike_price")
                        )
                        Text(
                            text = "当前现价 ¥${quote.formattedPrice}",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                HorizontalDivider(color = TerminalBorder.copy(alpha = 0.7f), thickness = 0.75.dp)

                // Value Breakdown Grid
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcStatItem(
                        label = "${projectionYears}年FCF现值",
                        value = "${result.pvExplicitFcfBillion}亿",
                        tag = "dcf_calc_pv_explicit"
                    )
                    CalcStatItem(
                        label = "终值折现PV (未折现${result.terminalValueUndiscountedBillion}亿)",
                        value = "${result.pvTerminalValueBillion}亿",
                        tag = "dcf_calc_pv_terminal"
                    )
                    CalcStatItem(
                        label = "终值现值占比",
                        value = "${result.terminalValueSharePercent}%",
                        valueColor = if (result.terminalValueSharePercent > 75.0) WarningAmber else TextPrimary,
                        tag = "dcf_calc_tv_share"
                    )
                    CalcStatItem(
                        label = "归母内在股权价值",
                        value = "${result.equityValueBillion}亿 (隐含${result.impliedMultipleOnBaseFcf}x)",
                        tag = "dcf_calc_equity_value"
                    )
                }

                // Mao Accounting Warnings
                result.maoWarnings.forEach { warn ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(WarningAmber.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "风险提示",
                            tint = WarningAmber,
                            modifier = Modifier
                                .size(13.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = warn,
                            color = WarningAmber,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Final Verdict
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TerminalBg.copy(alpha = 0.65f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = result.maoConclusion,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("dcf_calc_verdict")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 6. Expandable Year-by-Year Discount Schedule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .clickable { isScheduleExpanded = !isScheduleExpanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("dcf_calc_schedule_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isScheduleExpanded) "收起逐年现金流与终值折现明细" else "展开 ${projectionYears} 年自由现金流逐年折现明细表",
                    color = QuantCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (isScheduleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "展开折现明细",
                    tint = QuantCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isScheduleExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalSurfaceElevated)
                        .border(0.75.dp, TerminalBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .testTag("dcf_calc_schedule_table"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("期数", color = TextMuted, fontSize = 9.5.sp, modifier = Modifier.weight(0.7f))
                        Text("预期FCF", color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("折现因子", color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                        Text("折现现值PV", color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = TerminalBorder.copy(alpha = 0.5f), thickness = 0.75.dp)
                    result.yearlyRows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("第${row.year}年", color = WallStreetGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
                            Text("${row.projectedFcfBillion}亿", color = TextPrimary, fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            Text(String.format(java.util.Locale.US, "%.3f", row.discountFactor), color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.9f))
                            Text("${row.presentValueBillion}亿", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider(color = TerminalBorder.copy(alpha = 0.5f), thickness = 0.75.dp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("终值TV", color = QuantCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                        Text("${result.terminalValueUndiscountedBillion}亿", color = TextPrimary, fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text("(${result.terminalValueSharePercent}%)", color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.9f))
                        Text("${result.pvTerminalValueBillion}亿", color = WallStreetGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcContextChip(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = "$label:", color = TextMuted, fontSize = 10.sp, maxLines = 1)
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
private fun PresetAnchorChip(
    label: String,
    onClick: () -> Unit,
    accent: Color,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(0.75.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DcfNumericField(
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
        label = {
            Text(
                text = label,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        suffix = {
            Text(
                text = suffix,
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        },
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
            focusedLabelColor = WallStreetGold,
            unfocusedLabelColor = TextMuted,
            focusedContainerColor = TerminalSurfaceElevated,
            unfocusedContainerColor = TerminalSurfaceElevated
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun CalcStatItem(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    tag: String
) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 9.5.sp, maxLines = 1)
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.testTag(tag)
        )
    }
}

private fun sanitizeDecimalInput(raw: String, allowNegative: Boolean): String {
    val sb = StringBuilder(raw.length)
    var hasDot = false
    raw.forEachIndexed { idx, ch ->
        when {
            ch in '0'..'9' -> sb.append(ch)
            ch == '.' && !hasDot -> {
                hasDot = true
                sb.append(ch)
            }
            ch == '-' && allowNegative && idx == 0 && sb.isEmpty() -> sb.append(ch)
        }
    }
    return sb.toString()
}

private fun formatInputNumber(v: Double): String {
    val rounded = round(v * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
}

private fun round1Val(v: Double): Double = round(v * 10.0) / 10.0
