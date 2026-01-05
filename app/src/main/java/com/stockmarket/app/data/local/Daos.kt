package com.stockmarket.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Watchlist Item Entity for Room Database
 */
@Entity(tableName = "watchlist")
data class WatchlistItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "symbol")
    val symbol: String,
    
    @ColumnInfo(name = "company_name")
    val companyName: String,
    
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * Recent Search Entity
 */
@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey
    @ColumnInfo(name = "query")
    val query: String,
    
    @ColumnInfo(name = "searched_at")
    val searchedAt: Long = System.currentTimeMillis()
)

/**
 * Cached Stock Data Entity
 */
@Entity(tableName = "cached_stocks")
data class CachedStockEntity(
    @PrimaryKey
    @ColumnInfo(name = "symbol")
    val symbol: String,
    
    @ColumnInfo(name = "company_name")
    val companyName: String,
    
    @ColumnInfo(name = "last_price")
    val lastPrice: Double,
    
    @ColumnInfo(name = "change")
    val change: Double,
    
    @ColumnInfo(name = "percent_change")
    val percentChange: Double,
    
    @ColumnInfo(name = "open")
    val open: Double,
    
    @ColumnInfo(name = "high")
    val high: Double,
    
    @ColumnInfo(name = "low")
    val low: Double,
    
    @ColumnInfo(name = "previous_close")
    val previousClose: Double,
    
    @ColumnInfo(name = "volume")
    val volume: Long,
    
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Watchlist DAO
 */
@Dao
interface WatchlistDao {
    
    @Query("SELECT * FROM watchlist ORDER BY sort_order ASC, added_at DESC")
    fun getAllWatchlistItems(): Flow<List<WatchlistItemEntity>>
    
    @Query("SELECT * FROM watchlist WHERE symbol = :symbol LIMIT 1")
    suspend fun getWatchlistItem(symbol: String): WatchlistItemEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE symbol = :symbol)")
    suspend fun isInWatchlist(symbol: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE symbol = :symbol)")
    fun observeIsInWatchlist(symbol: String): Flow<Boolean>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistItemEntity)
    
    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun removeFromWatchlist(symbol: String)
    
    @Query("UPDATE watchlist SET sort_order = :sortOrder WHERE symbol = :symbol")
    suspend fun updateSortOrder(symbol: String, sortOrder: Int)
    
    @Query("DELETE FROM watchlist")
    suspend fun clearWatchlist()
}

/**
 * Recent Searches DAO
 */
@Dao
interface RecentSearchDao {
    
    @Query("SELECT * FROM recent_searches ORDER BY searched_at DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<RecentSearchEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSearch(search: RecentSearchEntity)
    
    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun removeSearch(query: String)
    
    @Query("DELETE FROM recent_searches")
    suspend fun clearSearches()
}

/**
 * Cached Stocks DAO
 */
@Dao
interface CachedStockDao {
    
    @Query("SELECT * FROM cached_stocks WHERE symbol = :symbol LIMIT 1")
    suspend fun getCachedStock(symbol: String): CachedStockEntity?
    
    @Query("SELECT * FROM cached_stocks ORDER BY cached_at DESC")
    fun getAllCachedStocks(): Flow<List<CachedStockEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheStock(stock: CachedStockEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheStocks(stocks: List<CachedStockEntity>)
    
    @Query("DELETE FROM cached_stocks WHERE cached_at < :timestamp")
    suspend fun deleteOldCache(timestamp: Long)
    
    @Query("DELETE FROM cached_stocks")
    suspend fun clearCache()
}
