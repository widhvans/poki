package com.stockmarket.app.di

import com.stockmarket.app.data.api.CoinGeckoService
import com.stockmarket.app.data.api.StockApiService
import com.stockmarket.app.data.api.YahooFinanceService
import com.stockmarket.app.data.local.StockDatabase
import com.stockmarket.app.data.repository.StockRepository
import com.stockmarket.app.ui.screens.chart.ChartViewModel
import com.stockmarket.app.ui.screens.home.HomeViewModel
import com.stockmarket.app.ui.screens.search.SearchViewModel
import com.stockmarket.app.ui.screens.stocklist.StockListViewModel
import com.stockmarket.app.ui.screens.watchlist.WatchlistViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val appModule = module {
    
    // OkHttpClient
    single {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Retrofit for primary API
    single<StockApiService> {
        Retrofit.Builder()
            .baseUrl(StockApiService.BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StockApiService::class.java)
    }
    
    // Retrofit for Yahoo Finance
    single<YahooFinanceService> {
        Retrofit.Builder()
            .baseUrl(StockApiService.YAHOO_BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YahooFinanceService::class.java)
    }
    
    // Retrofit for CoinGecko (Crypto)
    single<CoinGeckoService> {
        Retrofit.Builder()
            .baseUrl(CoinGeckoService.BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoService::class.java)
    }
    
    // Database
    single { StockDatabase.getInstance(androidContext()) }
    
    // Repository (includes crypto service now)
    single { StockRepository(get(), get(), get(), get()) }
    
    // ViewModels
    viewModel { HomeViewModel(get()) }
    viewModel { StockListViewModel(get()) }
    viewModel { (symbol: String) -> ChartViewModel(symbol, get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { WatchlistViewModel(get()) }
}
