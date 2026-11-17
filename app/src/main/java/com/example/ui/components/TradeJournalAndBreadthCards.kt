package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.TradeJournalEntity
import com.example.data.model.JournalPerformanceStats
import com.example.data.model.MarketBreadthRegime
import com.example.data.model.MarketBreadthSnapshot
import com.example.ui.theme.*
import kotlin.math.round

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketBreadthCoachCard(
    breadth: MarketBreadthSnapshot,
    modifier: Modifier = Modifier
) {
    val regimeColor = when (breadth.regime) {
        MarketBreadthRegime.OFFENSIVE -> SuccessGreen
        MarketBreadthRegime.SELECTIVE -> WallStreetGold
        MarketBreadthRegime.DEFENSIVE -> WarningAmber
        MarketBreadthRegime.CASH_PRESERVATION -> CriticalRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("market_breadth_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(regimeColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = "市场广度门控", tint = regimeColor, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "全市场实盘广度与仓位门控",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = breadth.regime.title,
                            color = regimeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(regimeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("market_breadth_score")
                ) {
                    Text(
                        text = "${breadth.breadthScore}分",
                        color = regimeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BreadthStat("允许总仓位上限", "${breadth.maxPermittedExposurePercent}%", regimeColor)
                BreadthStat("建议单笔风险", "${breadth.regime.recommendedSingleRiskPercent}R", QuantCyan)
                BreadthStat("样本上涨广度", "${breadth.advancePercent}%", if (breadth.advancePercent >= 50.0) StockRed else StockGreen)
                BreadthStat("站上MA20占比", "${breadth.aboveMa20Percent}%", TextPrimary)
                BreadthStat("跌超4%脆性", "${breadth.crashFragilityPercent}%", if (breadth.crashFragilityPercent >= 10.0) CriticalRed else TextSecondary)
            }

            Text(
                text = "📌 ${breadth.regime.guidance}",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.5.sp
            )
            Text(
                text = breadth.diagnosticSummary,
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradeJournalExpectancyCard(
    entries: List<TradeJournalEntity>,
    stats: JournalPerformanceStats,
    onMarkOpened: (Long) -> Unit,
    onCloseTrade: (id: Long, entryPrice: Double, stopPrice: Double, shares: Int, exitPrice: Double) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingCloseEntry by remember { mutableStateOf<TradeJournalEntity?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_journal_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(QuantCyanSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Book, contentDescription = "实盘交易账本", tint = QuantCyan, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "实盘交易账本与 R 倍数期望复盘",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "已记录 ${stats.totalEntries} 笔 · 进行中 ${stats.openOrPlannedCount} · 已平仓 ${stats.closedCount}",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BreadthStat(
                    label = "实测数学期望 E(R)",
                    value = "${if (stats.expectancyR >= 0) "+" else ""}${stats.expectancyR}R",
                    color = if (stats.expectancyR > 0) SuccessGreen else if (stats.closedCount == 0) TextSecondary else CriticalRed,
                    tag = "trade_journal_expectancy_r"
                )
                BreadthStat("实盘胜率", "${stats.winRatePercent}%", WallStreetGold)
                BreadthStat("盈利因子 PF", "${stats.profitFactor}", QuantCyan)
                BreadthStat("累计已结算盈亏", "${if (stats.totalRealizedPnlRmb >= 0) "+" else ""}¥${stats.totalRealizedPnlRmb.toInt()}", if (stats.totalRealizedPnlRmb >= 0) StockRed else StockGreen)
                BreadthStat("闸门守纪率", "${stats.disciplineCompliancePercent}%", if (stats.disciplineCompliancePercent >= 80.0) SuccessGreen else WarningAmber)
            }

            Text(
                text = stats.reviewVerdict,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.5.sp
            )

            if (entries.isNotEmpty()) {
                HorizontalDivider(color = TerminalBorder.copy(alpha = 0.6f), thickness = 0.75.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.take(8).forEach { entry ->
                        TradeJournalRow(
                            entry = entry,
                            onMarkOpened = { onMarkOpened(entry.id) },
                            onOpenCloseDialog = { pendingCloseEntry = entry },
                            onDelete = { onDeleteEntry(entry.id) }
                        )
                    }
                }
            }
        }
    }

    pendingCloseEntry?.let { entry ->
        CloseTradeDialog(
            entry = entry,
            onDismiss = { pendingCloseEntry = null },
            onConfirm = { exitPrice ->
                onCloseTrade(entry.id, entry.entryPrice, entry.stopPrice, entry.shares, exitPrice)
                pendingCloseEntry = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TradeJournalRow(
    entry: TradeJournalEntity,
    onMarkOpened: () -> Unit,
    onOpenCloseDialog: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (entry.status) {
        "OPEN" -> QuantCyan
        "CLOSED" -> if ((entry.realizedRMultiple ?: 0.0) >= 0.0) SuccessGreen else CriticalRed
        else -> WallStreetGold
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalSurfaceElevated)
            .border(0.75.dp, TerminalBorder, RoundedCornerShape(8.dp))
            .padding(9.dp)
            .testTag("trade_journal_row_${entry.symbol}"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.stockName, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = when (entry.status) {
                            "OPEN" -> "持仓中"
                            "CLOSED" -> "已平仓 ${if ((entry.realizedRMultiple ?: 0.0) >= 0) "+" else ""}${entry.realizedRMultiple}R"
                            else -> "计划单"
                        },
                        color = statusColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除记录", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("买入 ¥${entry.entryPrice}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("止损 ¥${entry.stopPrice}", color = StockGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("2R目标 ¥${entry.targetPrice}", color = QuantCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("${entry.shares}股", color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            if (entry.status == "CLOSED" && entry.realizedPnlRmb != null) {
                val pnlColor = if (entry.realizedPnlRmb >= 0) StockRed else StockGreen
                Text(
                    text = "结算 ${if (entry.realizedPnlRmb >= 0) "+" else ""}¥${round(entry.realizedPnlRmb).toInt()}",
                    color = pnlColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Text(entry.thesis, color = TextMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

        if (entry.status != "CLOSED") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.status == "PLANNED") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(QuantCyanSoft)
                            .clickable(onClick = onMarkOpened)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text("标记已买入", color = QuantCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(WallStreetGoldSoft)
                        .clickable(onClick = onOpenCloseDialog)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("trade_journal_close_btn_${entry.symbol}")
                ) {
                    Text("平仓结算 R倍数", color = WallStreetGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CloseTradeDialog(
    entry: TradeJournalEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var exitText by remember(entry.id) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", entry.targetPrice)) }
    val exitVal = exitText.toDoubleOrNull()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = TerminalCard,
            border = BorderStroke(1.dp, TerminalBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("平仓结算 · ${entry.stockName}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "计划买入 ¥${entry.entryPrice} · 硬止损 ¥${entry.stopPrice} · ${entry.shares}股",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = exitText,
                    onValueChange = { raw ->
                        val sb = StringBuilder()
                        var dot = false
                        raw.forEach { c ->
                            if (c in '0'..'9') sb.append(c)
                            else if (c == '.' && !dot) {
                                dot = true
                                sb.append(c)
                            }
                        }
                        exitText = sb.toString()
                    },
                    label = { Text("实际卖出成交价 (元)", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trade_close_exit_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = WallStreetGold,
                        unfocusedBorderColor = TerminalBorder
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (exitVal != null && exitVal > 0.0) onConfirm(exitVal) },
                        enabled = exitVal != null && exitVal > 0.0,
                        modifier = Modifier.testTag("trade_close_confirm_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = WallStreetGoldSoft, contentColor = WallStreetGold)
                    ) {
                        Text("确认结算", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadthStat(label: String, value: String, color: Color, tag: String? = null) {
    Column {
        Text(label, color = TextMuted, fontSize = 9.5.sp, maxLines = 1)
        Text(
            text = value,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier
        )
    }
}
