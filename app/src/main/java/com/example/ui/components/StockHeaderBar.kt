package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.StockQuote
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockHeaderBar(
    quote: StockQuote?,
    isWatchlisted: Boolean,
    onToggleWatchlist: () -> Unit,
    onSearchClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onVersionClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val adaptive = LocalAdaptiveLayout.current
    val fontScale = LocalDensity.current.fontScale

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stock_header_bar"),
        color = TerminalSurface,
        tonalElevation = 2.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = adaptive.screenHorizontalPadding, vertical = 10.dp)
        ) {
            val isNarrow = isNarrowPortrait(maxWidth, fontScale)
            val isUltraNarrow = isUltraNarrowPortrait(maxWidth, fontScale)

            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: App Branding & Top Actions (accessible 44-48dp targets, weighted left branding)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(WallStreetGold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "花街投壶",
                            color = WallStreetGold,
                            fontSize = if (isUltraNarrow) 16.sp else 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onVersionClick)
                                .testTag("header_version_changelog_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TerminalSurfaceElevated)
                                    .border(1.dp, QuantCyan.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "查看版本更新日志",
                                    tint = QuantCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isUltraNarrow) {
                                        "v${BuildConfig.VERSION_NAME}"
                                    } else {
                                        "v${BuildConfig.VERSION_NAME} 日志"
                                    },
                                    color = QuantCyan,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (!isNarrow) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TerminalSurfaceElevated)
                                    .border(1.dp, TerminalBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "A股实盘直连",
                                    color = SuccessGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("header_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索标的",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onRefreshClick,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("header_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新行情",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleWatchlist,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("header_watchlist_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isWatchlisted) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isWatchlisted) "已在自选" else "加入自选",
                                tint = if (isWatchlisted) WallStreetGold else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (quote != null) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Stock Name, Code, Market Badge, Live Clock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .clickable(onClick = onSearchClick)
                                .testTag("header_stock_title_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = quote.name,
                                color = TextPrimary,
                                fontSize = if (isNarrow) 18.sp else 20.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = quote.code,
                                color = TextMuted,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(WallStreetGoldSoft)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = quote.market.uppercase(),
                                    color = WallStreetGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatMarketTime(quote.timestamp),
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 3: Responsive Price & Change + Turnover Info Pill
                    val priceColor = when {
                        quote.isUp -> StockRed
                        quote.isDown -> StockGreen
                        else -> TextPrimary
                    }
                    val turnoverText = if (quote.turnoverTenThousand >= 10000) {
                        String.format(java.util.Locale.US, "%.2f亿", quote.turnoverTenThousand / 10000.0)
                    } else {
                        String.format(java.util.Locale.US, "%.0f万", quote.turnoverTenThousand)
                    }

                    if (isNarrow) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = quote.formattedPrice,
                                    color = priceColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = quote.formattedChange,
                                        color = priceColor,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = quote.formattedChangePercent,
                                        color = priceColor,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            TurnoverPill(turnoverText = turnoverText)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quote.formattedPrice,
                                    color = priceColor,
                                    fontSize = 31.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = quote.formattedChange,
                                        color = priceColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = quote.formattedChangePercent,
                                        color = priceColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            TurnoverPill(turnoverText = turnoverText)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 4: Key Valuation & Liquidity Chips (2x2 on narrow/high fontScale, 1x4 otherwise)
                    val peValText = when {
                        quote.peTtm < 0.0 -> "亏损"
                        quote.peTtm == 0.0 -> "--"
                        else -> String.format(java.util.Locale.US, "%.1f", quote.peTtm)
                    }
                    val pbValText = if (quote.pb <= 0.0) "--" else String.format(java.util.Locale.US, "%.2f", quote.pb)
                    val turnValText = if (quote.turnoverRate <= 0.0 && quote.code.startsWith("000") && quote.market == "sh") {
                        "--"
                    } else {
                        String.format(java.util.Locale.US, "%.2f%%", quote.turnoverRate)
                    }
                    val capValText = when {
                        quote.totalMarketCapHundredMillion <= 0.0 -> "--"
                        quote.totalMarketCapHundredMillion >= 10000 ->
                            String.format(java.util.Locale.US, "%.2f万亿", quote.totalMarketCapHundredMillion / 10000.0)
                        else ->
                            String.format(java.util.Locale.US, "%.0f亿", quote.totalMarketCapHundredMillion)
                    }

                    if (isNarrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MetricChip(label = "市盈率(TTM)", value = peValText, modifier = Modifier.weight(1f))
                                MetricChip(label = "市净率(PB)", value = pbValText, modifier = Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MetricChip(label = "换手率", value = turnValText, modifier = Modifier.weight(1f))
                                MetricChip(label = "总市值", value = capValText, modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricChip(label = "市盈率(TTM)", value = peValText, modifier = Modifier.weight(1f))
                            MetricChip(label = "市净率(PB)", value = pbValText, modifier = Modifier.weight(1f))
                            MetricChip(label = "换手率", value = turnValText, modifier = Modifier.weight(1f))
                            MetricChip(label = "总市值", value = capValText, modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 5: Intraday OHLC Strip (3+2 FlowRow on ultra-narrow, single 5-column row otherwise)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TerminalSurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        if (isUltraNarrow) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                maxItemsInEachRow = 3
                            ) {
                                OhlcChip("今开", String.format(java.util.Locale.US, "%.2f", quote.open), quote.open > quote.yesterdayClose, Modifier.weight(1f))
                                OhlcChip("最高", String.format(java.util.Locale.US, "%.2f", quote.high), quote.high > quote.yesterdayClose, Modifier.weight(1f))
                                OhlcChip("最低", String.format(java.util.Locale.US, "%.2f", quote.low), quote.low > quote.yesterdayClose, Modifier.weight(1f))
                                OhlcChip("昨收", String.format(java.util.Locale.US, "%.2f", quote.yesterdayClose), null, Modifier.weight(1f))
                                OhlcChip("振幅", String.format(java.util.Locale.US, "%.2f%%", quote.amplitude), null, Modifier.weight(1f))
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OhlcChip("今开", String.format(java.util.Locale.US, "%.2f", quote.open), quote.open > quote.yesterdayClose, Modifier.weight(1f))
                                OhlcChip("最高", String.format(java.util.Locale.US, "%.2f", quote.high), quote.high > quote.yesterdayClose, Modifier.weight(1f))
                                OhlcChip("最低", String.format(java.util.Locale.US, "%.2f", quote.low), quote.low > quote.yesterdayClose, Modifier.weight(1f))
                                OhlcChip("昨收", String.format(java.util.Locale.US, "%.2f", quote.yesterdayClose), null, Modifier.weight(1f))
                                OhlcChip("振幅", String.format(java.util.Locale.US, "%.2f%%", quote.amplitude), null, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnoverPill(turnoverText: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalSurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "成交额 $turnoverText",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalSurfaceElevated)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 9.5.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OhlcChip(
    label: String,
    value: String,
    isUp: Boolean?,
    modifier: Modifier = Modifier
) {
    val textColor = when (isUp) {
        true -> StockRed
        false -> StockGreen
        null -> TextPrimary
    }
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.5.sp,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatMarketTime(raw: String): String {
    if (raw.length >= 14) {
        return "${raw.substring(8, 10)}:${raw.substring(10, 12)}:${raw.substring(12, 14)}"
    } else if (raw.length == 6 && raw.all { it.isDigit() }) {
        return "${raw.substring(0, 2)}:${raw.substring(2, 4)}:${raw.substring(4, 6)}"
    } else if (raw.length >= 8) {
        val s = raw.takeLast(6)
        if (s.all { it.isDigit() }) {
            return "${s.substring(0, 2)}:${s.substring(2, 4)}:${s.substring(4, 6)}"
        }
    }
    return if (raw.isNotEmpty()) raw.takeLast(8) else "实时"
}
