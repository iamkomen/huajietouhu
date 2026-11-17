package com.example.data.model

data class OrderBookTier(
    val price: Double,
    val volumeLots: Int
)

data class OrderBook(
    val bids: List<OrderBookTier> = emptyList(), // 买一到买五
    val asks: List<OrderBookTier> = emptyList()  // 卖一到卖五
)

data class StockQuote(
    val code: String,
    val market: String, // "sh" or "sz"
    val symbol: String, // "sh600519"
    val name: String,
    val price: Double,
    val yesterdayClose: Double,
    val open: Double,
    val change: Double,
    val changePercent: Double,
    val high: Double,
    val low: Double,
    val volumeLots: Long,
    val turnoverTenThousand: Double,
    val turnoverRate: Double = 0.0,
    val peTtm: Double = 0.0,
    val pb: Double = 0.0,
    val totalMarketCapHundredMillion: Double = 0.0,
    val circMarketCapHundredMillion: Double = 0.0,
    val amplitude: Double = 0.0,
    val orderBook: OrderBook = OrderBook(),
    val timestamp: String = ""
) {
    val formattedPrice: String get() = String.format(java.util.Locale.US, "%.2f", price)
    val formattedChange: String get() = (if (change > 0) "+" else "") + String.format(java.util.Locale.US, "%.2f", change)
    val formattedChangePercent: String get() = (if (changePercent > 0) "+" else "") + String.format(java.util.Locale.US, "%.2f%%", changePercent)
    val isUp: Boolean get() = changePercent > 0.0001
    val isDown: Boolean get() = changePercent < -0.0001
}

data class KLinePoint(
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val ma5: Double = 0.0,
    val ma10: Double = 0.0,
    val ma20: Double = 0.0,
    val ma60: Double = 0.0,
    val bollUpper: Double = 0.0,
    val bollMid: Double = 0.0,
    val bollLower: Double = 0.0,
    val macdDif: Double = 0.0,
    val macdDea: Double = 0.0,
    val macdBar: Double = 0.0,
    val rsi6: Double = 50.0
)

data class MinutePoint(
    val time: String,
    val price: Double,
    val averagePrice: Double,
    val volumeLots: Long,
    val turnover: Double
)

enum class ChartPeriod(val key: String, val label: String) {
    INTRADAY("minute", "分时"),
    DAY("day", "日K"),
    WEEK("week", "周K"),
    MONTH("month", "月K")
}

data class StockSearchItem(
    val code: String,
    val market: String,
    val symbol: String,
    val name: String,
    val pinyin: String = ""
)
