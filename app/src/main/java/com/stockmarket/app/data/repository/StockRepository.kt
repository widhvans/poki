package com.stockmarket.app.data.repository

import com.stockmarket.app.data.api.StockApiService
import com.stockmarket.app.data.api.YahooFinanceService
import com.stockmarket.app.data.api.models.*
import com.stockmarket.app.data.local.*
import com.stockmarket.app.domain.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Result wrapper for API calls
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

/**
 * Stock Repository - Single source of truth for stock data
 */
class StockRepository(
    private val apiService: StockApiService,
    private val yahooService: YahooFinanceService,
    private val database: StockDatabase
) {
    private val watchlistDao = database.watchlistDao()
    private val recentSearchDao = database.recentSearchDao()
    private val cachedStockDao = database.cachedStockDao()
    
    // ============= Stock Quotes =============
    
    /**
     * Get stock quote by symbol
     */
    suspend fun getStockQuote(symbol: String): Result<Stock> {
        return try {
            val response = apiService.getStockQuote(symbol)
            if (response.isSuccessful && response.body() != null) {
                val quote = response.body()!!
                val stock = quote.toDomainModel()
                
                // Cache the stock data
                cachedStockDao.cacheStock(stock.toCacheEntity())
                
                Result.Success(stock)
            } else {
                // Try Yahoo Finance as fallback
                getStockFromYahoo(symbol)
            }
        } catch (e: Exception) {
            // Try cached data
            val cached = cachedStockDao.getCachedStock(symbol)
            if (cached != null) {
                Result.Success(cached.toDomainModel())
            } else {
                Result.Error("Failed to fetch stock data: ${e.message}", e)
            }
        }
    }
    
    /**
     * Fallback to Yahoo Finance
     */
    private suspend fun getStockFromYahoo(symbol: String): Result<Stock> {
        return try {
            // Add .NS for NSE stocks
            val yahooSymbol = if (!symbol.contains(".")) "$symbol.NS" else symbol
            val response = yahooService.getChartData(yahooSymbol, "1d", "1m")
            
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                val result = data.chart.result?.firstOrNull()
                
                if (result != null) {
                    val quote = result.indicators.quote.firstOrNull()
                    val closes = quote?.close?.filterNotNull() ?: emptyList()
                    val opens = quote?.open?.filterNotNull() ?: emptyList()
                    val highs = quote?.high?.filterNotNull() ?: emptyList()
                    val lows = quote?.low?.filterNotNull() ?: emptyList()
                    val volumes = quote?.volume?.filterNotNull() ?: emptyList()
                    
                    val lastPrice = closes.lastOrNull() ?: result.meta.regularMarketPrice
                    val change = lastPrice - result.meta.previousClose
                    val percentChange = (change / result.meta.previousClose) * 100
                    
                    val stock = Stock(
                        symbol = symbol,
                        companyName = symbol,
                        lastPrice = lastPrice,
                        change = change,
                        percentChange = percentChange,
                        open = opens.firstOrNull() ?: 0.0,
                        dayHigh = highs.maxOrNull() ?: 0.0,
                        dayLow = lows.minOrNull() ?: 0.0,
                        previousClose = result.meta.previousClose,
                        volume = volumes.sum()
                    )
                    Result.Success(stock)
                } else {
                    Result.Error("No data from Yahoo Finance")
                }
            } else {
                Result.Error("Yahoo Finance API failed")
            }
        } catch (e: Exception) {
            Result.Error("Yahoo fallback failed: ${e.message}", e)
        }
    }
    
    // ============= Historical Data =============
    
    /**
     * Get historical candlestick data
     */
    suspend fun getHistoricalData(
        symbol: String,
        timeframe: ChartTimeframe
    ): Result<List<CandleData>> {
        return try {
            // Try primary API first
            val response = apiService.getHistoricalData(
                symbol = symbol,
                range = timeframe.range,
                interval = timeframe.interval
            )
            
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                Result.Success(data.map { it.toDomainModel() })
            } else {
                // Fallback to Yahoo
                getHistoricalFromYahoo(symbol, timeframe)
            }
        } catch (e: Exception) {
            // Try Yahoo as fallback
            getHistoricalFromYahoo(symbol, timeframe)
        }
    }
    
    private suspend fun getHistoricalFromYahoo(
        symbol: String,
        timeframe: ChartTimeframe
    ): Result<List<CandleData>> {
        return try {
            val yahooSymbol = if (!symbol.contains(".")) "$symbol.NS" else symbol
            val response = yahooService.getChartData(
                symbol = yahooSymbol,
                range = timeframe.range,
                interval = timeframe.interval
            )
            
            if (response.isSuccessful && response.body() != null) {
                val chartData = response.body()!!.chart.result?.firstOrNull()
                
                if (chartData != null) {
                    val timestamps = chartData.timestamp ?: emptyList()
                    val quote = chartData.indicators.quote.firstOrNull()
                    
                    val candles = timestamps.mapIndexedNotNull { index, timestamp ->
                        val open = quote?.open?.getOrNull(index) ?: return@mapIndexedNotNull null
                        val high = quote.high?.getOrNull(index) ?: return@mapIndexedNotNull null
                        val low = quote.low?.getOrNull(index) ?: return@mapIndexedNotNull null
                        val close = quote.close?.getOrNull(index) ?: return@mapIndexedNotNull null
                        val volume = quote.volume?.getOrNull(index) ?: 0L
                        
                        CandleData(
                            timestamp = timestamp * 1000, // Convert to milliseconds
                            open = open.toFloat(),
                            high = high.toFloat(),
                            low = low.toFloat(),
                            close = close.toFloat(),
                            volume = volume
                        )
                    }
                    Result.Success(candles)
                } else {
                    Result.Error("No historical data available")
                }
            } else {
                Result.Error("Failed to fetch historical data")
            }
        } catch (e: Exception) {
            Result.Error("Failed to fetch historical data: ${e.message}", e)
        }
    }
    
    // ============= Market Indices =============
    
    /**
     * Get market indices (NIFTY 50, SENSEX, etc.)
     */
    suspend fun getMarketIndices(): Result<List<MarketIndex>> {
        return try {
            val response = apiService.getIndices()
            if (response.isSuccessful && response.body() != null) {
                val indices = response.body()!!.data.map { it.toDomainModel() }
                Result.Success(indices)
            } else {
                // Return default indices from Yahoo
                getIndicesFromYahoo()
            }
        } catch (e: Exception) {
            getIndicesFromYahoo()
        }
    }
    
    private suspend fun getIndicesFromYahoo(): Result<List<MarketIndex>> {
        val indices = mutableListOf<MarketIndex>()
        
        val symbolMap = mapOf(
            "^NSEI" to "NIFTY 50",
            "^BSESN" to "SENSEX",
            "^NSEBANK" to "BANK NIFTY"
        )
        
        for ((symbol, name) in symbolMap) {
            try {
                val response = yahooService.getChartData(symbol, "1d", "1m")
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.chart.result?.firstOrNull()
                    if (data != null) {
                        val lastPrice = data.meta.regularMarketPrice
                        val prevClose = data.meta.previousClose
                        val change = lastPrice - prevClose
                        val percentChange = (change / prevClose) * 100
                        
                        indices.add(
                            MarketIndex(
                                name = symbol,
                                displayName = name,
                                lastPrice = lastPrice,
                                change = change,
                                percentChange = percentChange,
                                open = lastPrice,
                                high = lastPrice,
                                low = lastPrice,
                                previousClose = prevClose
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Skip this index
            }
        }
        
        return if (indices.isNotEmpty()) {
            Result.Success(indices)
        } else {
            Result.Error("Failed to fetch market indices")
        }
    }
    
    // ============= Top Movers =============
    
    /**
     * Get top gainers and losers
     */
    suspend fun getTopMovers(): Result<Pair<List<Stock>, List<Stock>>> {
        return try {
            val response = apiService.getTopMovers()
            if (response.isSuccessful && response.body() != null) {
                val movers = response.body()!!
                Result.Success(
                    Pair(
                        movers.gainers.map { it.toDomainModel() },
                        movers.losers.map { it.toDomainModel() }
                    )
                )
            } else {
                Result.Error("Failed to fetch top movers")
            }
        } catch (e: Exception) {
            Result.Error("Failed to fetch top movers: ${e.message}", e)
        }
    }
    
    // ============= Stock Lists =============
    
    /**
     * Get NIFTY 50 stocks
     */
    suspend fun getNifty50(): Result<List<Stock>> {
        return try {
            val response = apiService.getNifty50()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!.data.map { it.toDomainModel() })
            } else {
                Result.Error("Failed to fetch NIFTY 50")
            }
        } catch (e: Exception) {
            Result.Error("Failed to fetch NIFTY 50: ${e.message}", e)
        }
    }
    
    /**
     * Get Bank NIFTY stocks
     */
    suspend fun getBankNifty(): Result<List<Stock>> {
        return try {
            val response = apiService.getBankNifty()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!.data.map { it.toDomainModel() })
            } else {
                Result.Error("Failed to fetch Bank NIFTY")
            }
        } catch (e: Exception) {
            Result.Error("Failed to fetch Bank NIFTY: ${e.message}", e)
        }
    }
    
    /**
     * Get stocks by sector
     */
    suspend fun getSectorStocks(sector: Sector): Result<List<Stock>> {
        return try {
            val response = apiService.getSectorStocks(sector.apiValue)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!.data.map { it.toDomainModel() })
            } else {
                Result.Error("Failed to fetch ${sector.displayName} stocks")
            }
        } catch (e: Exception) {
            Result.Error("Failed to fetch sector stocks: ${e.message}", e)
        }
    }
    
    // ============= Search =============
    
    /**
     * Search stocks
     */
    suspend fun searchStocks(query: String): Result<List<SearchResult>> {
        return try {
            // Save to recent searches
            recentSearchDao.addSearch(RecentSearchEntity(query = query))
            
            val response = apiService.searchStocks(query)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!.data.map { it.toDomainModel() })
            } else {
                Result.Error("No results found")
            }
        } catch (e: Exception) {
            Result.Error("Search failed: ${e.message}", e)
        }
    }
    
    /**
     * Get recent searches
     */
    fun getRecentSearches(): Flow<List<String>> {
        return recentSearchDao.getRecentSearches().map { list ->
            list.map { it.query }
        }
    }
    
    /**
     * Clear recent searches
     */
    suspend fun clearRecentSearches() {
        recentSearchDao.clearSearches()
    }
    
    // ============= Watchlist =============
    
    /**
     * Get watchlist items
     */
    fun getWatchlist(): Flow<List<WatchlistItem>> {
        return watchlistDao.getAllWatchlistItems().map { list ->
            list.map { entity ->
                WatchlistItem(
                    symbol = entity.symbol,
                    companyName = entity.companyName,
                    addedAt = entity.addedAt
                )
            }
        }
    }
    
    /**
     * Check if stock is in watchlist
     */
    suspend fun isInWatchlist(symbol: String): Boolean {
        return watchlistDao.isInWatchlist(symbol)
    }
    
    /**
     * Observe watchlist status
     */
    fun observeWatchlistStatus(symbol: String): Flow<Boolean> {
        return watchlistDao.observeIsInWatchlist(symbol)
    }
    
    /**
     * Add stock to watchlist
     */
    suspend fun addToWatchlist(symbol: String, companyName: String) {
        watchlistDao.addToWatchlist(
            WatchlistItemEntity(
                symbol = symbol,
                companyName = companyName
            )
        )
    }
    
    /**
     * Remove stock from watchlist
     */
    suspend fun removeFromWatchlist(symbol: String) {
        watchlistDao.removeFromWatchlist(symbol)
    }
    
    /**
     * Toggle watchlist status
     */
    suspend fun toggleWatchlist(symbol: String, companyName: String): Boolean {
        return if (isInWatchlist(symbol)) {
            removeFromWatchlist(symbol)
            false
        } else {
            addToWatchlist(symbol, companyName)
            true
        }
    }
}

// ============= Extension Functions for Mapping =============

private fun StockQuoteResponse.toDomainModel() = Stock(
    symbol = symbol,
    companyName = companyName,
    lastPrice = lastPrice,
    change = change,
    percentChange = percentChange,
    open = open,
    dayHigh = dayHigh,
    dayLow = dayLow,
    previousClose = previousClose,
    volume = volume,
    yearHigh = yearHigh,
    yearLow = yearLow,
    lastUpdateTime = lastUpdateTime
)

private fun CandleDataPoint.toDomainModel() = CandleData(
    timestamp = try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date)?.time ?: 0L
    } catch (e: Exception) { 0L },
    open = open.toFloat(),
    high = high.toFloat(),
    low = low.toFloat(),
    close = close.toFloat(),
    volume = volume
)

private fun MarketIndexData.toDomainModel() = MarketIndex(
    name = indexName,
    displayName = when {
        indexName.contains("NIFTY 50", ignoreCase = true) -> "NIFTY 50"
        indexName.contains("SENSEX", ignoreCase = true) -> "SENSEX"
        indexName.contains("BANK", ignoreCase = true) -> "BANK NIFTY"
        else -> indexName
    },
    lastPrice = lastPrice,
    change = change,
    percentChange = percentChange,
    open = open,
    high = high,
    low = low,
    previousClose = previousClose
)

private fun SearchResultItem.toDomainModel() = SearchResult(
    symbol = symbol,
    companyName = companyName,
    industry = industry
)

private fun Stock.toCacheEntity() = CachedStockEntity(
    symbol = symbol,
    companyName = companyName,
    lastPrice = lastPrice,
    change = change,
    percentChange = percentChange,
    open = open,
    high = dayHigh,
    low = dayLow,
    previousClose = previousClose,
    volume = volume
)

private fun CachedStockEntity.toDomainModel() = Stock(
    symbol = symbol,
    companyName = companyName,
    lastPrice = lastPrice,
    change = change,
    percentChange = percentChange,
    open = open,
    dayHigh = high,
    dayLow = low,
    previousClose = previousClose,
    volume = volume
)
