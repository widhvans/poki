package com.stockmarket.app.data.repository

import android.util.Log
import com.stockmarket.app.data.api.CoinGeckoService
import com.stockmarket.app.data.api.StockApiService
import com.stockmarket.app.data.api.YahooFinanceService
import com.stockmarket.app.data.api.models.*
import com.stockmarket.app.data.local.*
import com.stockmarket.app.domain.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "StockRepository"

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
    private val cryptoService: CoinGeckoService,
    private val database: StockDatabase
) {
    private val watchlistDao = database.watchlistDao()
    private val recentSearchDao = database.recentSearchDao()
    private val cachedStockDao = database.cachedStockDao()
    
    init {
        Log.d(TAG, "📦 StockRepository initialized")
    }
    
    // Crypto ID to Yahoo Finance symbol mapping
    private val cryptoSymbolMap = mapOf(
        "bitcoin" to "BTC-USD",
        "ethereum" to "ETH-USD",
        "tether" to "USDT-USD",
        "binancecoin" to "BNB-USD",
        "ripple" to "XRP-USD",
        "solana" to "SOL-USD",
        "dogecoin" to "DOGE-USD",
        "polkadot" to "DOT-USD",
        "cardano" to "ADA-USD",
        "avalanche-2" to "AVAX-USD",
        "chainlink" to "LINK-USD",
        "uniswap" to "UNI-USD",
        "litecoin" to "LTC-USD",
        "wrapped-bitcoin" to "WBTC-USD",
        "shiba-inu" to "SHIB-USD",
        "matic-network" to "MATIC-USD",
        "stellar" to "XLM-USD",
        "tron" to "TRX-USD",
        "cosmos" to "ATOM-USD"
    )
    
    /**
     * Convert symbol to Yahoo Finance compatible format
     * - Crypto IDs (bitcoin, ethereum) → Yahoo crypto symbols (BTC-USD)
     * - Indian stocks → Add .NS suffix
     */
    private fun toYahooSymbol(symbol: String): String {
        // Check if it's a known crypto
        val cryptoSymbol = cryptoSymbolMap[symbol.lowercase()]
        if (cryptoSymbol != null) {
            Log.d(TAG, "🪙 Crypto symbol mapped: $symbol → $cryptoSymbol")
            return cryptoSymbol
        }
        // If already has suffix or is a crypto pair format, return as-is
        if (symbol.contains(".") || symbol.contains("-")) {
            return symbol
        }
        // Indian stock - add .NS
        return "$symbol.NS"
    }
    
    // ============= Stock Quotes =============
    
    /**
     * Get stock quote by symbol
     */
    suspend fun getStockQuote(symbol: String): Result<Stock> {
        Log.d(TAG, "📊 getStockQuote() called for symbol: $symbol")
        return try {
            Log.d(TAG, "🌐 Making API call to getStockQuote...")
            val response = apiService.getStockQuote(symbol)
            Log.d(TAG, "📡 API Response - isSuccessful: ${response.isSuccessful}, code: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val quote = response.body()!!
                Log.d(TAG, "✅ Got stock quote: ${quote.symbol} @ ${quote.lastPrice}")
                val stock = quote.toDomainModel()
                
                // Cache the stock data
                cachedStockDao.cacheStock(stock.toCacheEntity())
                Log.d(TAG, "💾 Cached stock data for: $symbol")
                
                Result.Success(stock)
            } else {
                Log.w(TAG, "⚠️ Primary API failed, trying Yahoo Finance fallback...")
                Log.w(TAG, "⚠️ Response error: ${response.errorBody()?.string()}")
                // Try Yahoo Finance as fallback
                getStockFromYahoo(symbol)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in getStockQuote: ${e.javaClass.simpleName}: ${e.message}", e)
            // Try cached data
            val cached = cachedStockDao.getCachedStock(symbol)
            if (cached != null) {
                Log.d(TAG, "📦 Using cached data for: $symbol")
                Result.Success(cached.toDomainModel())
            } else {
                Log.e(TAG, "❌ No cached data available for: $symbol")
                Result.Error("Failed to fetch stock data: ${e.message}", e)
            }
        }
    }
    
    /**
     * Fallback to Yahoo Finance
     */
    private suspend fun getStockFromYahoo(symbol: String): Result<Stock> {
        Log.d(TAG, "🔄 Trying Yahoo Finance for: $symbol")
        return try {
            // Use toYahooSymbol for proper crypto/stock symbol mapping
            val yahooSymbol = toYahooSymbol(symbol)
            Log.d(TAG, "🌐 Yahoo API call with symbol: $yahooSymbol")
            
            val response = yahooService.getChartData(yahooSymbol, "1d", "1m")
            Log.d(TAG, "📡 Yahoo Response - isSuccessful: ${response.isSuccessful}, code: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                val result = data.chart.result?.firstOrNull()
                Log.d(TAG, "📊 Yahoo chart result: ${result != null}")
                
                if (result != null) {
                    val quote = result.indicators.quote.firstOrNull()
                    val closes = quote?.close?.filterNotNull() ?: emptyList()
                    val opens = quote?.open?.filterNotNull() ?: emptyList()
                    val highs = quote?.high?.filterNotNull() ?: emptyList()
                    val lows = quote?.low?.filterNotNull() ?: emptyList()
                    val volumes = quote?.volume?.filterNotNull() ?: emptyList()
                    
                    Log.d(TAG, "📈 Yahoo data - closes: ${closes.size}, opens: ${opens.size}")
                    
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
                    Log.d(TAG, "✅ Yahoo success: ${stock.symbol} @ ${stock.lastPrice}")
                    Result.Success(stock)
                } else {
                    Log.e(TAG, "❌ No data from Yahoo Finance - result is null")
                    Result.Error("No data from Yahoo Finance")
                }
            } else {
                Log.e(TAG, "❌ Yahoo Finance API failed - code: ${response.code()}")
                Log.e(TAG, "❌ Yahoo error body: ${response.errorBody()?.string()}")
                Result.Error("Yahoo Finance API failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Yahoo fallback exception: ${e.javaClass.simpleName}: ${e.message}", e)
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
        Log.d(TAG, "📊 getHistoricalData() for $symbol, timeframe: ${timeframe.label}")
        return try {
            Log.d(TAG, "🌐 Primary API - range: ${timeframe.range}, interval: ${timeframe.interval}")
            val response = apiService.getHistoricalData(
                symbol = symbol,
                range = timeframe.range,
                interval = timeframe.interval
            )
            Log.d(TAG, "📡 Historical API Response - isSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                Log.d(TAG, "✅ Got ${data.size} candles from primary API")
                Result.Success(data.map { it.toDomainModel() })
            } else {
                Log.w(TAG, "⚠️ Primary API failed, trying Yahoo...")
                getHistoricalFromYahoo(symbol, timeframe)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Historical data exception: ${e.message}", e)
            getHistoricalFromYahoo(symbol, timeframe)
        }
    }
    
    private suspend fun getHistoricalFromYahoo(
        symbol: String,
        timeframe: ChartTimeframe
    ): Result<List<CandleData>> {
        Log.d(TAG, "🔄 Yahoo historical for $symbol")
        return try {
            val yahooSymbol = toYahooSymbol(symbol)
            Log.d(TAG, "🌐 Yahoo chart API: $yahooSymbol, range: ${timeframe.range}")
            
            val response = yahooService.getChartData(
                symbol = yahooSymbol,
                range = timeframe.range,
                interval = timeframe.interval
            )
            Log.d(TAG, "📡 Yahoo historical - isSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val chartData = response.body()!!.chart.result?.firstOrNull()
                
                if (chartData != null) {
                    val timestamps = chartData.timestamp ?: emptyList()
                    val quote = chartData.indicators.quote.firstOrNull()
                    Log.d(TAG, "📊 Yahoo timestamps: ${timestamps.size}")
                    
                    val candles = timestamps.mapIndexedNotNull { index, timestamp ->
                        val open = quote?.open?.getOrNull(index) ?: return@mapIndexedNotNull null
                        val high = quote.high?.getOrNull(index) ?: return@mapIndexedNotNull null
                        val low = quote.low?.getOrNull(index) ?: return@mapIndexedNotNull null
                        val close = quote.close?.getOrNull(index) ?: return@mapIndexedNotNull null
                        val volume = quote.volume?.getOrNull(index) ?: 0L
                        
                        CandleData(
                            timestamp = timestamp * 1000,
                            open = open.toFloat(),
                            high = high.toFloat(),
                            low = low.toFloat(),
                            close = close.toFloat(),
                            volume = volume
                        )
                    }
                    Log.d(TAG, "✅ Yahoo historical success: ${candles.size} candles")
                    Result.Success(candles)
                } else {
                    Log.e(TAG, "❌ No Yahoo historical data available")
                    Result.Error("No historical data available")
                }
            } else {
                Log.e(TAG, "❌ Yahoo historical API failed: ${response.code()}")
                Result.Error("Failed to fetch historical data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Yahoo historical exception: ${e.message}", e)
            Result.Error("Failed to fetch historical data: ${e.message}", e)
        }
    }
    
    // ============= Market Indices =============
    
    /**
     * Get market indices (NIFTY 50, SENSEX, etc.)
     */
    suspend fun getMarketIndices(): Result<List<MarketIndex>> {
        Log.d(TAG, "📊 getMarketIndices() called")
        return try {
            Log.d(TAG, "🌐 Fetching indices from primary API...")
            val response = apiService.getIndices()
            Log.d(TAG, "📡 Indices API - isSuccessful: ${response.isSuccessful}, code: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val indices = response.body()!!.data.map { it.toDomainModel() }
                Log.d(TAG, "✅ Got ${indices.size} indices")
                Result.Success(indices)
            } else {
                Log.w(TAG, "⚠️ Primary indices API failed, trying Yahoo...")
                getIndicesFromYahoo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Indices exception: ${e.message}", e)
            getIndicesFromYahoo()
        }
    }
    
    private suspend fun getIndicesFromYahoo(): Result<List<MarketIndex>> {
        Log.d(TAG, "🔄 Getting indices from Yahoo...")
        val indices = mutableListOf<MarketIndex>()
        
        val symbolMap = mapOf(
            "^NSEI" to "NIFTY 50",
            "^BSESN" to "SENSEX",
            "^NSEBANK" to "BANK NIFTY"
        )
        
        for ((symbol, name) in symbolMap) {
            try {
                Log.d(TAG, "🌐 Yahoo index: $symbol")
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
                        Log.d(TAG, "✅ Got $name @ $lastPrice")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get index $symbol: ${e.message}")
            }
        }
        
        Log.d(TAG, "📊 Total indices fetched: ${indices.size}")
        return if (indices.isNotEmpty()) {
            Result.Success(indices)
        } else {
            Log.e(TAG, "❌ No indices fetched from Yahoo")
            Result.Error("Failed to fetch market indices")
        }
    }
    
    // ============= Top Movers =============
    
    /**
     * Get top gainers and losers
     */
    suspend fun getTopMovers(): Result<Pair<List<Stock>, List<Stock>>> {
        Log.d(TAG, "📊 getTopMovers() called")
        return try {
            Log.d(TAG, "🌐 Fetching top movers...")
            val response = apiService.getTopMovers()
            Log.d(TAG, "📡 Top movers API - isSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val movers = response.body()!!
                Log.d(TAG, "✅ Got ${movers.gainers.size} gainers, ${movers.losers.size} losers")
                Result.Success(
                    Pair(
                        movers.gainers.map { it.toDomainModel() },
                        movers.losers.map { it.toDomainModel() }
                    )
                )
            } else {
                Log.e(TAG, "❌ Top movers API failed: ${response.code()}")
                Result.Error("Failed to fetch top movers")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Top movers exception: ${e.message}", e)
            Result.Error("Failed to fetch top movers: ${e.message}", e)
        }
    }
    
    /**
     * Get NIFTY 50 stocks
     */
    suspend fun getNifty50(): Result<List<Stock>> {
        Log.d(TAG, "📊 getNifty50() called")
        return try {
            Log.d(TAG, "🌐 Fetching NIFTY 50 stocks...")
            val response = apiService.getNifty50()
            Log.d(TAG, "📡 NIFTY 50 API - isSuccessful: ${response.isSuccessful}, code: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val stocks = response.body()!!.data.map { it.toDomainModel() }
                Log.d(TAG, "✅ Got ${stocks.size} NIFTY 50 stocks")
                Result.Success(stocks)
            } else {
                Log.w(TAG, "⚠️ Primary API failed, using Yahoo Finance fallback...")
                getNifty50FromYahoo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ NIFTY 50 exception: ${e.javaClass.simpleName}: ${e.message}", e)
            Log.w(TAG, "⚠️ Trying Yahoo Finance fallback...")
            getNifty50FromYahoo()
        }
    }
    
    /**
     * NIFTY 50 + Popular NSE stock symbols with company names (80+ stocks)
     */
    private val nifty50Symbols = mapOf(
        // NIFTY 50 - Complete list
        "RELIANCE" to "Reliance Industries",
        "TCS" to "Tata Consultancy Services",
        "HDFCBANK" to "HDFC Bank",
        "INFY" to "Infosys",
        "ICICIBANK" to "ICICI Bank",
        "HINDUNILVR" to "Hindustan Unilever",
        "ITC" to "ITC Limited",
        "SBIN" to "State Bank of India",
        "BHARTIARTL" to "Bharti Airtel",
        "KOTAKBANK" to "Kotak Mahindra Bank",
        "LT" to "Larsen & Toubro",
        "AXISBANK" to "Axis Bank",
        "ASIANPAINT" to "Asian Paints",
        "MARUTI" to "Maruti Suzuki",
        "HCLTECH" to "HCL Technologies",
        "SUNPHARMA" to "Sun Pharma",
        "TITAN" to "Titan Company",
        "BAJFINANCE" to "Bajaj Finance",
        "WIPRO" to "Wipro",
        "ULTRACEMCO" to "UltraTech Cement",
        "ONGC" to "ONGC",
        "NTPC" to "NTPC",
        "POWERGRID" to "Power Grid Corp",
        "M&M" to "Mahindra & Mahindra",
        "TATAMOTORS" to "Tata Motors",
        "JSWSTEEL" to "JSW Steel",
        "TATASTEEL" to "Tata Steel",
        "ADANIENT" to "Adani Enterprises",
        "ADANIPORTS" to "Adani Ports",
        "COALINDIA" to "Coal India",
        "BAJAJFINSV" to "Bajaj Finserv",
        "NESTLEIND" to "Nestle India",
        "GRASIM" to "Grasim Industries",
        "TECHM" to "Tech Mahindra",
        "INDUSINDBK" to "IndusInd Bank",
        "DRREDDY" to "Dr Reddy's Labs",
        "DIVISLAB" to "Divi's Laboratories",
        "CIPLA" to "Cipla",
        "EICHERMOT" to "Eicher Motors",
        "APOLLOHOSP" to "Apollo Hospitals",
        "HEROMOTOCO" to "Hero MotoCorp",
        "BRITANNIA" to "Britannia Industries",
        "BPCL" to "BPCL",
        "TATACONSUM" to "Tata Consumer",
        "HINDALCO" to "Hindalco Industries",
        "SBILIFE" to "SBI Life Insurance",
        "HDFCLIFE" to "HDFC Life",
        "LTIM" to "LTIMindtree",
        "SHRIRAMFIN" to "Shriram Finance",
        // NIFTY NEXT 50 - Popular stocks
        "ADANIGREEN" to "Adani Green Energy",
        "VEDL" to "Vedanta",
        "JINDALSTEL" to "Jindal Steel & Power",
        "IOC" to "Indian Oil Corp",
        "GAIL" to "GAIL India",
        "PIDILITIND" to "Pidilite Industries",
        "HAVELLS" to "Havells India",
        "BERGEPAINT" to "Berger Paints",
        "SIEMENS" to "Siemens India",
        "DABUR" to "Dabur India",
        "GODREJCP" to "Godrej Consumer",
        "MOTHERSON" to "Motherson Sumi",
        "ZOMATO" to "Zomato",
        "POLYCAB" to "Polycab India",
        "ABB" to "ABB India",
        "TATAPOWER" to "Tata Power",
        "IRCTC" to "IRCTC",
        "HAL" to "Hindustan Aeronautics",
        "BANKBARODA" to "Bank of Baroda",
        "PNB" to "Punjab National Bank",
        "CANBK" to "Canara Bank",
        "RECLTD" to "REC Limited",
        "PFC" to "Power Finance Corp",
        "NHPC" to "NHPC",
        "IRFC" to "Indian Railway Finance",
        "PAYTM" to "One97 Communications",
        "NAUKRI" to "Info Edge (Naukri)",
        "DMART" to "Avenue Supermarts",
        "MCDOWELL-N" to "United Spirits",
        "TRENT" to "Trent Limited"
    )
    
    /**
     * Fallback to Yahoo Finance for NIFTY 50 stocks
     */
    private suspend fun getNifty50FromYahoo(): Result<List<Stock>> {
        Log.d(TAG, "🔄 Getting NIFTY 50 from Yahoo Finance...")
        val stocks = mutableListOf<Stock>()
        
        // Fetch top 40 stocks for better coverage
        val topStocks = nifty50Symbols.entries.take(40)
        
        for ((symbol, companyName) in topStocks) {
            try {
                val yahooSymbol = "$symbol.NS"
                val response = yahooService.getChartData(yahooSymbol, "1d", "1m")
                
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.chart.result?.firstOrNull()
                    if (data != null) {
                        val quote = data.indicators.quote.firstOrNull()
                        val closes = quote?.close?.filterNotNull() ?: emptyList()
                        
                        val lastPrice = closes.lastOrNull() ?: data.meta.regularMarketPrice
                        val prevClose = data.meta.previousClose
                        val change = lastPrice - prevClose
                        val percentChange = if (prevClose != 0.0) (change / prevClose) * 100 else 0.0
                        
                        stocks.add(
                            Stock(
                                symbol = symbol,
                                companyName = companyName,
                                lastPrice = lastPrice,
                                change = change,
                                percentChange = percentChange,
                                open = lastPrice,
                                dayHigh = lastPrice,
                                dayLow = lastPrice,
                                previousClose = prevClose,
                                volume = 0L
                            )
                        )
                        Log.d(TAG, "✅ Yahoo: $symbol @ $lastPrice")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get $symbol: ${e.message}")
            }
        }
        
        Log.d(TAG, "📊 Total NIFTY 50 stocks from Yahoo: ${stocks.size}")
        return if (stocks.isNotEmpty()) {
            Result.Success(stocks)
        } else {
            Log.e(TAG, "❌ Failed to fetch any stocks from Yahoo")
            Result.Error("Failed to fetch NIFTY 50 stocks")
        }
    }
    
    /**
     * Bank NIFTY stock symbols
     */
    private val bankNiftySymbols = mapOf(
        "HDFCBANK" to "HDFC Bank",
        "ICICIBANK" to "ICICI Bank",
        "SBIN" to "State Bank of India",
        "KOTAKBANK" to "Kotak Mahindra Bank",
        "AXISBANK" to "Axis Bank",
        "INDUSINDBK" to "IndusInd Bank",
        "BANDHANBNK" to "Bandhan Bank",
        "FEDERALBNK" to "Federal Bank",
        "IDFCFIRSTB" to "IDFC First Bank",
        "PNB" to "Punjab National Bank",
        "BANKBARODA" to "Bank of Baroda",
        "AUBANK" to "AU Small Finance Bank"
    )
    
    /**
     * Get Bank NIFTY stocks
     */
    suspend fun getBankNifty(): Result<List<Stock>> {
        Log.d(TAG, "📊 getBankNifty() called")
        return try {
            val response = apiService.getBankNifty()
            Log.d(TAG, "📡 Bank NIFTY API - isSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "✅ Got Bank NIFTY stocks")
                Result.Success(response.body()!!.data.map { it.toDomainModel() })
            } else {
                Log.w(TAG, "⚠️ Using Yahoo fallback for Bank NIFTY...")
                getStocksFromYahoo(bankNiftySymbols, "Bank NIFTY")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Bank NIFTY exception: ${e.message}", e)
            getStocksFromYahoo(bankNiftySymbols, "Bank NIFTY")
        }
    }
    
    /**
     * Sector stock symbols
     */
    private val sectorStocks = mapOf(
        "IT" to mapOf(
            "TCS" to "Tata Consultancy Services",
            "INFY" to "Infosys",
            "WIPRO" to "Wipro",
            "HCLTECH" to "HCL Technologies",
            "TECHM" to "Tech Mahindra",
            "LTIM" to "LTIMindtree"
        ),
        "PHARMA" to mapOf(
            "SUNPHARMA" to "Sun Pharma",
            "DRREDDY" to "Dr. Reddy's",
            "DIVISLAB" to "Divi's Labs",
            "CIPLA" to "Cipla",
            "APOLLOHOSP" to "Apollo Hospitals"
        ),
        "AUTO" to mapOf(
            "MARUTI" to "Maruti Suzuki",
            "TATAMOTORS" to "Tata Motors",
            "M&M" to "Mahindra & Mahindra",
            "BAJAJ-AUTO" to "Bajaj Auto",
            "HEROMOTOCO" to "Hero MotoCorp"
        ),
        "FMCG" to mapOf(
            "HINDUNILVR" to "Hindustan Unilever",
            "ITC" to "ITC Limited",
            "NESTLEIND" to "Nestle India",
            "BRITANNIA" to "Britannia",
            "DABUR" to "Dabur India"
        ),
        "METAL" to mapOf(
            "TATASTEEL" to "Tata Steel",
            "JSWSTEEL" to "JSW Steel",
            "HINDALCO" to "Hindalco",
            "COALINDIA" to "Coal India",
            "VEDL" to "Vedanta"
        )
    )
    
    /**
     * Get stocks by sector
     */
    suspend fun getSectorStocks(sector: Sector): Result<List<Stock>> {
        Log.d(TAG, "📊 getSectorStocks() for: ${sector.displayName}")
        return try {
            val response = apiService.getSectorStocks(sector.apiValue)
            Log.d(TAG, "📡 Sector API - isSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "✅ Got sector stocks")
                Result.Success(response.body()!!.data.map { it.toDomainModel() })
            } else {
                Log.w(TAG, "⚠️ Using Yahoo fallback for ${sector.displayName}...")
                val symbols = sectorStocks[sector.apiValue.uppercase()] ?: emptyMap()
                getStocksFromYahoo(symbols, sector.displayName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sector exception: ${e.message}", e)
            val symbols = sectorStocks[sector.apiValue.uppercase()] ?: emptyMap()
            getStocksFromYahoo(symbols, sector.displayName)
        }
    }
    
    /**
     * Generic helper to fetch stocks from Yahoo Finance
     */
    private suspend fun getStocksFromYahoo(
        symbolMap: Map<String, String>,
        category: String
    ): Result<List<Stock>> {
        Log.d(TAG, "🔄 Getting $category from Yahoo Finance...")
        val stocks = mutableListOf<Stock>()
        
        for ((symbol, companyName) in symbolMap) {
            try {
                val yahooSymbol = "$symbol.NS"
                val response = yahooService.getChartData(yahooSymbol, "1d", "1m")
                
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.chart.result?.firstOrNull()
                    if (data != null) {
                        val lastPrice = data.meta.regularMarketPrice
                        val prevClose = data.meta.previousClose
                        val change = lastPrice - prevClose
                        val percentChange = if (prevClose != 0.0) (change / prevClose) * 100 else 0.0
                        
                        stocks.add(
                            Stock(
                                symbol = symbol,
                                companyName = companyName,
                                lastPrice = lastPrice,
                                change = change,
                                percentChange = percentChange,
                                open = lastPrice,
                                dayHigh = lastPrice,
                                dayLow = lastPrice,
                                previousClose = prevClose,
                                volume = 0L
                            )
                        )
                        Log.d(TAG, "✅ Yahoo: $symbol @ $lastPrice")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get $symbol: ${e.message}")
            }
        }
        
        Log.d(TAG, "📊 Total $category stocks from Yahoo: ${stocks.size}")
        return if (stocks.isNotEmpty()) {
            Result.Success(stocks)
        } else {
            Result.Error("Failed to fetch $category stocks")
        }
    }
    
    // ============= Search =============
    
    /**
     * Search stocks
     */
    suspend fun searchStocks(query: String): Result<List<SearchResult>> {
        Log.d(TAG, "🔍 searchStocks() for: $query")
        return try {
            // Save to recent searches
            recentSearchDao.addSearch(RecentSearchEntity(query = query))
            
            val response = apiService.searchStocks(query)
            Log.d(TAG, "📡 Search API - isSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val results = response.body()!!.data.map { it.toDomainModel() }
                Log.d(TAG, "✅ Got ${results.size} search results")
                Result.Success(results)
            } else {
                Log.e(TAG, "❌ Search API failed")
                Result.Error("No results found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Search exception: ${e.message}", e)
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
        Log.d(TAG, "➕ Adding to watchlist: $symbol")
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
        Log.d(TAG, "➖ Removing from watchlist: $symbol")
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
    
    // ============= Cryptocurrency Data =============
    
    /**
     * Get top cryptocurrencies with INR pricing
     */
    suspend fun getCryptos(): Result<List<Crypto>> {
        Log.d(TAG, "💰 getCryptos() called")
        return try {
            Log.d(TAG, "🌐 Making CoinGecko API call for crypto markets...")
            val response = cryptoService.getMarkets(
                currency = "inr",
                perPage = 50,
                page = 1
            )
            
            Log.d(TAG, "📡 CoinGecko Response - isSuccessful: ${response.isSuccessful}, code: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val markets = response.body()!!
                Log.d(TAG, "✅ Got ${markets.size} crypto coins")
                
                val cryptos = markets.mapNotNull { market ->
                    val price = market.current_price ?: return@mapNotNull null
                    val change24h = market.price_change_percentage_24h ?: 0.0
                    
                    Crypto(
                        id = market.id,
                        symbol = market.symbol.uppercase(),
                        name = market.name,
                        currentPrice = price,
                        priceChange24h = change24h,
                        marketCap = market.market_cap,
                        imageUrl = market.image
                    )
                }
                
                Log.d(TAG, "📊 Converted to ${cryptos.size} Crypto objects")
                Result.Success(cryptos)
            } else {
                Log.e(TAG, "❌ CoinGecko API failed - code: ${response.code()}")
                Result.Error("Failed to fetch crypto data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in getCryptos: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.Error("Failed to fetch crypto data: ${e.message}", e)
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
