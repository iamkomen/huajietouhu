package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OrderBook
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderBookView(
    orderBook: OrderBook,
    yesterdayClose: Double,
    modifier: Modifier = Modifier,
    horizontalMargin: androidx.compose.ui.unit.Dp = LocalAdaptiveLayout.current.cardHorizontalPadding
) {
    val adaptive = LocalAdaptiveLayout.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = 6.dp)
            .testTag("order_book_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(adaptive.cardInnerPadding)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "实时五档盘口与深度撮合",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "L1 实时切片",
                    color = WallStreetGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Order Book Bid/Ask Ratio Bar
            val totalBidLots = orderBook.bids.sumOf { it.volumeLots }.coerceAtLeast(1)
            val totalAskLots = orderBook.asks.sumOf { it.volumeLots }.coerceAtLeast(1)
            val bidRatio = totalBidLots.toFloat() / (totalBidLots + totalAskLots).toFloat()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TerminalSurfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(bidRatio.coerceIn(0.05f, 0.95f))
                        .background(StockRed)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((1f - bidRatio).coerceIn(0.05f, 0.95f))
                        .background(StockGreen)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "委买: ${totalBidLots}手 (${(bidRatio * 100).toInt()}%)",
                    color = StockRed,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "委卖: ${totalAskLots}手 (${((1f - bidRatio) * 100).toInt()}%)",
                    color = StockGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sell Orders (卖五到卖一，从上到下)
            val asksReversed = orderBook.asks.take(5).reversed()
            asksReversed.forEachIndexed { index, tier ->
                val level = 5 - index
                val tierRatio = (tier.volumeLots.toFloat() / totalAskLots.toFloat()).coerceIn(0.05f, 1f)
                OrderTierRow(
                    label = "卖 $level",
                    price = tier.price,
                    volumeLots = tier.volumeLots,
                    yesterdayClose = yesterdayClose,
                    fillRatio = tierRatio,
                    isBid = false
                )
            }

            HorizontalDivider(
                color = TerminalBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Buy Orders (买一到买五)
            orderBook.bids.take(5).forEachIndexed { index, tier ->
                val level = index + 1
                val tierRatio = (tier.volumeLots.toFloat() / totalBidLots.toFloat()).coerceIn(0.05f, 1f)
                OrderTierRow(
                    label = "买 $level",
                    price = tier.price,
                    volumeLots = tier.volumeLots,
                    yesterdayClose = yesterdayClose,
                    fillRatio = tierRatio,
                    isBid = true
                )
            }
        }
    }
}

@Composable
private fun OrderTierRow(
    label: String,
    price: Double,
    volumeLots: Int,
    yesterdayClose: Double,
    fillRatio: Float,
    isBid: Boolean
) {
    val priceColor = when {
        price > yesterdayClose -> StockRed
        price < yesterdayClose -> StockGreen
        else -> TextPrimary
    }

    val depthColor = (if (isBid) StockRed else StockGreen).copy(alpha = 0.14f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .drawBehind {
                val barW = size.width * (fillRatio.coerceIn(0.05f, 1f) * 0.48f)
                val startX = if (isBid) 0f else size.width - barW
                drawRect(
                    color = depthColor,
                    topLeft = Offset(startX, 0f),
                    size = Size(barW, size.height)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(0.8f)
            )

            Text(
                text = if (price > 0) String.format(java.util.Locale.US, "%.2f", price) else "--",
                color = priceColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1.2f)
            )

            Text(
                text = "$volumeLots 手",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
