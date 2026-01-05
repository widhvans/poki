package com.stockmarket.app.data.api.models

import com.google.gson.annotations.SerializedName

/**
 * Stock Quote Response from API
 */
data class StockQuoteResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("companyName") val companyName: String,
    @SerializedName("lastPrice") val lastPrice: Double,
    @SerializedName("change") val change: Double,
    @SerializedName("pChange") val percentChange: Double,
    @SerializedName("open") val open: Double,
    @SerializedName("high") val dayHigh: Double,
    @SerializedName("low") val dayLow: Double,
    @SerializedName("previousClose") val previousClose: Double,
    @SerializedName("totalTradedVolume") val volume: Long,
    @SerializedName("totalTradedValue") val tradedValue: Double,
    @SerializedName("yearHigh") val yearHigh: Double,
    @SerializedName("yearLow") val yearLow: Double,
    @SerializedName("lastUpdateTime") val lastUpdateTime: String
)

/**
 * Search Result Item
 */
data class SearchResultItem(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("companyName") val companyName: String,
    @SerializedName("industry") val industry: String? = null,
    @SerializedName("series") val series: String? = null
)

/**
 * Search Response
 */
data class SearchResponse(
    @SerializedName("data") val data: List<SearchResultItem>
)

/**
 * Historical Data Point (Candlestick)
 */
data class CandleDataPoint(
    @SerializedName("date") val date: String,
    @SerializedName("open") val open: Double,
    @SerializedName("high") val high: Double,
    @SerializedName("low") val low: Double,
    @SerializedName("close") val close: Double,
    @SerializedName("volume") val volume: Long
)

/**
 * Historical Data Response
 */
data class HistoricalDataResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("data") val data: List<CandleDataPoint>
)

/**
 * Market Index Data
 */
data class MarketIndexData(
    @SerializedName("indexName") val indexName: String,
    @SerializedName("lastPrice") val lastPrice: Double,
    @SerializedName("change") val change: Double,
    @SerializedName("pChange") val percentChange: Double,
    @SerializedName("open") val open: Double,
    @SerializedName("high") val high: Double,
    @SerializedName("low") val low: Double,
    @SerializedName("previousClose") val previousClose: Double
)

/**
 * Indices Response
 */
data class IndicesResponse(
    @SerializedName("data") val data: List<MarketIndexData>
)

/**
 * Top Gainers/Losers Response
 */
data class TopMoversResponse(
    @SerializedName("gainers") val gainers: List<StockQuoteResponse>,
    @SerializedName("losers") val losers: List<StockQuoteResponse>
)

/**
 * Stock List Response (NIFTY 50, etc.)
 */
data class StockListResponse(
    @SerializedName("data") val data: List<StockQuoteResponse>
)

/**
 * API Error Response
 */
data class ApiError(
    @SerializedName("error") val error: String,
    @SerializedName("message") val message: String
)
