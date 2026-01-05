package com.stockmarket.app.domain.models

/**
 * Domain model for Stock
 */
data class Stock(
    val symbol: String,
    val companyName: String,
    val lastPrice: Double,
    val change: Double,
    val percentChange: Double,
    val open: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val previousClose: Double,
    val volume: Long,
    val yearHigh: Double = 0.0,
    val yearLow: Double = 0.0,
    val lastUpdateTime: String = ""
) {
    val isPositive: Boolean
        get() = change >= 0
    
    val formattedPrice: String
        get() = "₹%.2f".format(lastPrice)
    
    val formattedChange: String
        get() = "${if (change >= 0) "+" else ""}%.2f".format(change)
    
    val formattedPercentChange: String
        get() = "${if (percentChange >= 0) "+" else ""}%.2f%%".format(percentChange)
}

/**
 * Domain model for candlestick data point
 */
data class CandleData(
    val timestamp: Long,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Long
) {
    val isBullish: Boolean
        get() = close >= open
}

/**
 * Domain model for Market Index
 */
data class MarketIndex(
    val name: String,
    val displayName: String,
    val lastPrice: Double,
    val change: Double,
    val percentChange: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val previousClose: Double
) {
    val isPositive: Boolean
        get() = change >= 0
    
    val formattedPrice: String
        get() = "%.2f".format(lastPrice)
    
    val formattedChange: String
        get() = "${if (change >= 0) "+" else ""}%.2f (%.2f%%)".format(change, percentChange)
}

/**
 * Search Result Item
 */
data class SearchResult(
    val symbol: String,
    val companyName: String,
    val industry: String?
)

/**
 * Timeframe options for charts
 */
enum class ChartTimeframe(
    val label: String,
    val range: String,
    val interval: String
) {
    FIVE_MIN("5m", "1d", "1m"),
    TEN_MIN("10m", "1d", "2m"),
    THIRTY_MIN("30m", "1d", "5m"),
    ONE_HOUR("1H", "1d", "15m"),
    ONE_DAY("1D", "1d", "5m"),
    FIVE_DAYS("5D", "5d", "15m"),
    ONE_MONTH("1M", "1mo", "1d"),
    THREE_MONTHS("3M", "3mo", "1d"),
    SIX_MONTHS("6M", "6mo", "1d"),
    ONE_YEAR("1Y", "1y", "1wk"),
    FIVE_YEARS("5Y", "5y", "1mo"),
    MAX("MAX", "max", "1mo")
}

/**
 * Sector enumeration
 */
enum class Sector(val displayName: String, val apiValue: String) {
    NIFTY50("NIFTY 50", "nifty50"),
    BANK_NIFTY("Bank NIFTY", "banknifty"),
    IT("IT", "it"),
    PHARMA("Pharma", "pharma"),
    AUTO("Auto", "auto"),
    FMCG("FMCG", "fmcg"),
    METAL("Metal", "metal"),
    ENERGY("Energy", "energy")
}

/**
 * Technical Indicator Types
 */
enum class TechnicalIndicator(val displayName: String) {
    SMA_5("SMA 5"),
    SMA_10("SMA 10"),
    SMA_20("SMA 20"),
    EMA_5("EMA 5"),
    EMA_10("EMA 10"),
    EMA_20("EMA 20"),
    RSI("RSI"),
    MACD("MACD"),
    BOLLINGER("Bollinger Bands")
}

/**
 * Watchlist Item
 */
data class WatchlistItem(
    val symbol: String,
    val companyName: String,
    val addedAt: Long,
    val stock: Stock? = null // Optional live data
)

/**
 * Cryptocurrency data model
 */
data class Crypto(
    val id: String,
    val symbol: String,
    val name: String,
    val currentPrice: Double,
    val priceChange24h: Double,
    val marketCap: Double?,
    val imageUrl: String? = null
) {
    val isPositive: Boolean
        get() = priceChange24h >= 0
    
    val formattedPrice: String
        get() = "₹%.2f".format(currentPrice)
    
    val formattedChange: String
        get() = "${if (priceChange24h >= 0) "+" else ""}%.2f%%".format(priceChange24h)
}
