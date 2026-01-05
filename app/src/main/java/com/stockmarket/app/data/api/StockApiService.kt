package com.stockmarket.app.data.api

import com.stockmarket.app.data.api.models.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API Service for Indian Stock Market Data
 * Using free open-source API + Yahoo Finance fallback
 */
interface StockApiService {
    
    companion object {
        const val BASE_URL = "https://indian-stock-market-api.vercel.app/"
        const val YAHOO_BASE_URL = "https://query1.finance.yahoo.com/"
    }
    
    /**
     * Get stock quote by symbol
     */
    @GET("api/stocks/{symbol}")
    suspend fun getStockQuote(
        @Path("symbol") symbol: String
    ): Response<StockQuoteResponse>
    
    /**
     * Search stocks by query
     */
    @GET("api/search")
    suspend fun searchStocks(
        @Query("q") query: String
    ): Response<SearchResponse>
    
    /**
     * Get historical data for candlestick charts
     */
    @GET("api/historical/{symbol}")
    suspend fun getHistoricalData(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "1mo", // 1d, 5d, 1mo, 3mo, 6mo, 1y, 5y
        @Query("interval") interval: String = "1d" // 1m, 5m, 15m, 1h, 1d
    ): Response<HistoricalDataResponse>
    
    /**
     * Get all market indices (NIFTY, SENSEX, etc.)
     */
    @GET("api/indices")
    suspend fun getIndices(): Response<IndicesResponse>
    
    /**
     * Get top gainers and losers
     */
    @GET("api/top-movers")
    suspend fun getTopMovers(): Response<TopMoversResponse>
    
    /**
     * Get NIFTY 50 stocks
     */
    @GET("api/nifty50")
    suspend fun getNifty50(): Response<StockListResponse>
    
    /**
     * Get Bank NIFTY stocks
     */
    @GET("api/banknifty")
    suspend fun getBankNifty(): Response<StockListResponse>
    
    /**
     * Get stocks by sector
     */
    @GET("api/sector/{sector}")
    suspend fun getSectorStocks(
        @Path("sector") sector: String // it, pharma, auto, fmcg, metal, energy
    ): Response<StockListResponse>
}

/**
 * Yahoo Finance API for fallback/additional data
 */
interface YahooFinanceService {
    
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChartData(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "1mo",
        @Query("interval") interval: String = "1d",
        @Query("includePrePost") includePrePost: Boolean = false
    ): Response<YahooChartResponse>
}

/**
 * Yahoo Finance Chart Response Models
 */
data class YahooChartResponse(
    val chart: YahooChartResult
)

data class YahooChartResult(
    val result: List<YahooChartData>?,
    val error: YahooError?
)

data class YahooChartData(
    val meta: YahooMeta,
    val timestamp: List<Long>?,
    val indicators: YahooIndicators
)

data class YahooMeta(
    val symbol: String,
    val regularMarketPrice: Double,
    val previousClose: Double,
    val currency: String
)

data class YahooIndicators(
    val quote: List<YahooQuote>,
    val adjclose: List<YahooAdjClose>?
)

data class YahooQuote(
    val open: List<Double?>?,
    val high: List<Double?>?,
    val low: List<Double?>?,
    val close: List<Double?>?,
    val volume: List<Long?>?
)

data class YahooAdjClose(
    val adjclose: List<Double?>?
)

data class YahooError(
    val code: String,
    val description: String
)

/**
 * CoinGecko API for cryptocurrency data (Free tier - no API key needed)
 */
interface CoinGeckoService {
    
    companion object {
        const val BASE_URL = "https://api.coingecko.com/api/v3/"
    }
    
    /**
     * Get simple price for multiple coins
     */
    @GET("simple/price")
    suspend fun getSimplePrice(
        @Query("ids") ids: String, // comma separated: bitcoin,ethereum,solana
        @Query("vs_currencies") currencies: String = "inr",
        @Query("include_24hr_change") include24hChange: Boolean = true,
        @Query("include_market_cap") includeMarketCap: Boolean = true
    ): Response<Map<String, CoinGeckoPrice>>
    
    /**
     * Get list of coins with market data
     */
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") currency: String = "inr",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = false
    ): Response<List<CoinGeckoMarket>>
}

/**
 * CoinGecko price response
 */
data class CoinGeckoPrice(
    val inr: Double?,
    val inr_24h_change: Double?,
    val inr_market_cap: Double?
)

/**
 * CoinGecko market data
 */
data class CoinGeckoMarket(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String?,
    val current_price: Double?,
    val market_cap: Double?,
    val market_cap_rank: Int?,
    val price_change_percentage_24h: Double?,
    val total_volume: Double?,
    val high_24h: Double?,
    val low_24h: Double?
)
