package com.stockmarket.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchlistItemEntity::class,
        RecentSearchEntity::class,
        CachedStockEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StockDatabase : RoomDatabase() {
    
    abstract fun watchlistDao(): WatchlistDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun cachedStockDao(): CachedStockDao
    
    companion object {
        private const val DATABASE_NAME = "stock_market_db"
        
        @Volatile
        private var INSTANCE: StockDatabase? = null
        
        fun getInstance(context: Context): StockDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): StockDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                StockDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
