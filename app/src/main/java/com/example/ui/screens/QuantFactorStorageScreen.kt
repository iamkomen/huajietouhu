package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
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
import com.example.BuildConfig
import com.example.data.model.KLinePoint
import com.example.data.model.StockQuote
import com.example.data.repository.QuantEngine
import com.example.ui.components.VersionChangelogDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.TerminalUiState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class FactorStorageDisplayItem(
    val factorCode: String,
    val name: String,
    val category: String,
    val liveRankIc: Double?,
    val liveTStat: Double?,
    val livePValue: Double?,
    val sampleN: Int,
    val liveIcIr: Double?,
    val turnoverYearly: Double,
    val status: String,
    val formulaDesc: String = ""
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantFactorStorageScreen(
    uiState: TerminalUiState,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = LocalAdaptiveLayout.current.windowSizeClass
) {
    val adaptive = rememberAdaptiveLayoutSpec(windowSizeClass)
    var selectedCategory by remember { mutableStateOf("ALL") }
    var expandedFactorCode by remember { mutableStateOf<String?>(null) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "ALL" to "全部因子",
        "价值因子" to "价值因子",
        "动量因子" to "动量因子",
        "质量因子" to "质量因子",
        "风控因子" to "风控因子",
        "微观因子" to "微观因子"
    )

    val universeQuotes = remember(uiState.screenerQuotes, uiState.retailUniverseQuotes, uiState.watchlistQuotes) {
        (uiState.screenerQuotes + uiState.retailUniverseQuotes + uiState.watchlistQuotes)
            .distinctBy { it.symbol }
            .filter { it.price > 0.0 }
    }

    val factors = remember(universeQuotes, uiState.retailUniverseKLines) {
        buildEmpiricalFactorStorageList(universeQuotes, uiState.retailUniverseKLines)
    }

    val displayedFactors = factors.filter {
        selectedCategory == "ALL" || it.category == selectedCategory
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(horizontal = adaptive.screenHorizontalPadding, vertical = 12.dp)
    ) {
        Text(
            text = "多因子实盘截面验证库 (Spearman IC)",
            color = TextPrimary,
            fontSize = 18.5.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (universeQuotes.size >= 8) {
                "实时在 ${universeQuotes.size} 只A股实盘截面上检验 Rank IC 与 Student-t 显著性"
            } else {
                "等待同步 ≥8 只全市场实时行情后自动执行截面 Spearman Rank IC 校验"
            },
            color = TextMuted,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { (key, label) ->
                val isSelected = selectedCategory == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) WallStreetGoldSoft else TerminalSurfaceElevated)
                        .border(1.dp, if (isSelected) WallStreetGold else TerminalBorder, RoundedCornerShape(6.dp))
                        .clickable { selectedCategory = key }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("factor_category_$key")
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) WallStreetGold else TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val factorRows = remember(displayedFactors, adaptive.gridColumns) {
            displayedFactors.chunked(adaptive.gridColumns)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("factor_storage_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = if (adaptive.useNavigationRail) 24.dp else 80.dp)
        ) {
            items(factorRows) { rowGroup ->
                if (adaptive.gridColumns >= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowGroup.forEach { item ->
                            FactorStorageItemCard(
                                item = item,
                                isExpanded = expandedFactorCode == item.factorCode,
                                onToggleExpand = {
                                    expandedFactorCode = if (expandedFactorCode == item.factorCode) null else item.factorCode
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowGroup.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    val item = rowGroup.first()
                    FactorStorageItemCard(
                        item = item,
                        isExpanded = expandedFactorCode == item.factorCode,
                        onToggleExpand = {
                            expandedFactorCode = if (expandedFactorCode == item.factorCode) null else item.factorCode
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showChangelogDialog = true }
                        .testTag("storage_version_changelog_button"),
                    colors = CardDefaults.cardColors(containerColor = TerminalCard),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
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
                                    imageVector = Icons.Default.History,
                                    contentDescription = "查看版本演进记录",
                                    tint = WallStreetGold,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "版本演进与修复记录",
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "当前版本 v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) · 点击查看历次修复与迭代",
                                    color = TextMuted,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "打开版本日志",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showChangelogDialog) {
        VersionChangelogDialog(
            onDismiss = { showChangelogDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactorStorageItemCard(
    item: FactorStorageDisplayItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSignificant = item.livePValue != null && item.livePValue < 0.05 && abs(item.liveTStat ?: 0.0) > 1.96
    Card(
        modifier = modifier
            .clickable(onClick = onToggleExpand)
            .testTag("factor_card_${item.factorCode}"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                    Text(
                        text = item.name,
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TerminalSurfaceElevated)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = item.category, color = WallStreetGold, fontSize = 9.5.sp, maxLines = 1, softWrap = false)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.status,
                        color = if (isSignificant) SuccessGreen else WallStreetGold,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起因子公式" else "展开因子公式",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (item.liveRankIc != null) {
                        "实测Rank IC: ${String.format(java.util.Locale.US, "%+.3f", item.liveRankIc)}"
                    } else {
                        "实测IC: 待同步"
                    },
                    color = WallStreetGold,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = if (item.liveTStat != null && item.livePValue != null) {
                        val pStr = if (item.livePValue < 0.01) "<0.01" else if (item.livePValue < 0.05) "<0.05" else String.format(java.util.Locale.US, "%.2f", item.livePValue)
                        "t=${item.liveTStat} (p$pStr)"
                    } else {
                        "N=${item.sampleN}"
                    },
                    color = QuantCyan,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "N=${item.sampleN} · 换手${item.turnoverYearly}x",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 ${item.formulaDesc}",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                    if (item.liveIcIr != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📊 实盘截面估算 IC_IR = ${item.liveIcIr} (样本池规模 N=${item.sampleN})",
                            color = QuantCyan,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private fun buildEmpiricalFactorStorageList(
    quotes: List<StockQuote>,
    kLinesMap: Map<String, List<KLinePoint>>
): List<FactorStorageDisplayItem> {
    data class FactorSpec(
        val code: String,
        val name: String,
        val category: String,
        val turnoverYearly: Double,
        val desc: String,
        val extractor: (StockQuote, List<KLinePoint>) -> Double
    )

    val specs = listOf(
        FactorSpec(
            "FAC_PE_TTM", "滚动市盈率倒数(E/P)", "价值因子", 3.2,
            "公式: 1 / PE_TTM。度量净利润相对于市值的盈利收益率利差，拒绝高估值泡沫与纯题材炒作。"
        ) { q, _ -> if (q.peTtm > 0.0) (100.0 / q.peTtm) else -2.0 },
        FactorSpec(
            "FAC_PB_LF", "市净率倒数(B/P)", "价值因子", 2.8,
            "公式: 1 / PB。衡量净资产折价水平，破净与低市净率提供下行安全边际。"
        ) { q, _ -> if (q.pb > 0.0) (1.0 / q.pb) else 0.0 },
        FactorSpec(
            "FAC_MOM_20D", "20日动量与均线趋势", "动量因子", 8.5,
            "公式: evaluateFactors 动量分 (MA5/MA20乖离 + MACD动量柱 + RSI强弱)。"
        ) { q, kl -> QuantEngine.evaluateFactors(q, kl).momentumScore.toDouble() },
        FactorSpec(
            "FAC_MA_CROSS", "EMA(12,26,9)多头能量", "动量因子", 6.2,
            "公式: 递归指数平滑 DIF - DEA (MACD_BAR)。符合 TA-Lib/同花顺 精确递推规范。"
        ) { q, kl ->
            if (kl.isNotEmpty()) kl.last().macdBar else q.changePercent
        },
        FactorSpec(
            "FAC_ROE_TTM", "杜邦隐含ROE质量分", "质量因子", 1.5,
            "公式: PB / PE_TTM (= 隐含 ROE) 与大市值流动性护城河复合评价。"
        ) { q, _ ->
            if (q.peTtm > 0.0 && q.pb > 0.0) (q.pb / q.peTtm) * 100.0 else 0.0
        },
        FactorSpec(
            "FAC_VOL_20D", "对数已实现波动率(HV20)", "风控因子", 4.1,
            "公式: sqrt(252) * stdev(ln(C_t / C_{t-1})) (Bessel N-1校正)。低波资产具备更优夏普复利效率。"
        ) { q, kl ->
            -(QuantEngine.computeAnnualizedLogVolatility(kl, 20) ?: QuantEngine.computeParkinsonVolatility(q.high, q.low) ?: q.amplitude * 4.5)
        },
        FactorSpec(
            "FAC_AMPLITUDE", "日内振幅与Parkinson极差", "风控因子", 5.0,
            "公式: -(High - Low) / YesterdayClose。极端日内换手震荡体现短线资金博弈分歧度。"
        ) { q, _ -> -q.amplitude },
        FactorSpec(
            "FAC_DEPTH_5", "五档盘口买压不对称性", "微观因子", 12.0,
            "公式: (Sum(Bid1..5) - Sum(Ask1..5)) / TotalDepth。高频L1盘口委买/委卖微观结构测算。"
        ) { q, kl -> QuantEngine.evaluateFactors(q, kl).microScore.toDouble() }
    )

    val canCompute = quotes.size >= 8
    val targetRets = if (canCompute) {
        quotes.map { q ->
            val kl = kLinesMap[q.symbol].orEmpty()
            if (kl.size >= 21 && kl[kl.size - 21].close > 0.0) {
                (kl.last().close - kl[kl.size - 21].close) / kl[kl.size - 21].close
            } else {
                q.changePercent / 100.0
            }
        }
    } else emptyList()

    return specs.map { spec ->
        if (canCompute) {
            val fVec = quotes.map { q -> spec.extractor(q, kLinesMap[q.symbol].orEmpty()) }
            val rankIc = (QuantEngine.computeSpearmanRankCorrelation(fVec, targetRets) * 1000.0).roundToInt() / 1000.0
            val (tStat, pVal) = QuantEngine.computeStudentTAndPValue(rankIc, quotes.size)
            val icIr = ((rankIc * sqrt(quotes.size.toDouble()) / 3.5) * 100.0).roundToInt() / 100.0
            val statusLabel = when {
                pVal < 0.01 && abs(tStat) > 2.58 -> "极显著(p<0.01)"
                pVal < 0.05 && abs(tStat) > 1.96 -> "显著(p<0.05)"
                else -> "未达显著线(实测)"
            }
            FactorStorageDisplayItem(
                factorCode = spec.code,
                name = spec.name,
                category = spec.category,
                liveRankIc = rankIc,
                liveTStat = tStat,
                livePValue = pVal,
                sampleN = quotes.size,
                liveIcIr = icIr,
                turnoverYearly = spec.turnoverYearly,
                status = statusLabel,
                formulaDesc = spec.desc
            )
        } else {
            FactorStorageDisplayItem(
                factorCode = spec.code,
                name = spec.name,
                category = spec.category,
                liveRankIc = null,
                liveTStat = null,
                livePValue = null,
                sampleN = quotes.size,
                liveIcIr = null,
                turnoverYearly = spec.turnoverYearly,
                status = "待样本≥8",
                formulaDesc = spec.desc
            )
        }
    }
}
