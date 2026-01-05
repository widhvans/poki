package com.stockmarket.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.data.repository.Result
import com.stockmarket.app.data.repository.StockRepository
import com.stockmarket.app.domain.models.Crypto
import com.stockmarket.app.domain.models.MarketIndex
import com.stockmarket.app.domain.models.Stock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "HomeViewModel"

data class HomeUiState(
    val isLoading: Boolean = true,
    val indices: List<MarketIndex> = emptyList(),
    val topGainers: List<Stock> = emptyList(),
    val topLosers: List<Stock> = emptyList(),
    val nifty50Stocks: List<Stock> = emptyList(),
    val cryptos: List<Crypto> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    private val repository: StockRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var autoRefreshJob: kotlinx.coroutines.Job? = null
    
    init {
        Log.d(TAG, "🏠 HomeViewModel initialized")
        loadData()
        startAutoRefresh()
    }
    
    fun loadData() {
        Log.d(TAG, "🔄 loadData() called - Starting to fetch all home data...")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // Load market indices
            Log.d(TAG, "📊 Fetching market indices...")
            when (val result = repository.getMarketIndices()) {
                is Result.Success -> {
                    Log.d(TAG, "✅ Market indices loaded: ${result.data.size} items")
                    result.data.forEach { index ->
                        Log.d(TAG, "  📈 ${index.displayName}: ${index.lastPrice} (${index.formattedChange})")
                    }
                    _uiState.value = _uiState.value.copy(indices = result.data)
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Failed to load market indices: ${result.message}")
                    Log.e(TAG, "❌ Exception: ${result.exception?.javaClass?.simpleName}")
                }
                else -> {}
            }
            
            // Load top movers
            Log.d(TAG, "📊 Fetching top movers...")
            when (val result = repository.getTopMovers()) {
                is Result.Success -> {
                    val (gainers, losers) = result.data
                    Log.d(TAG, "✅ Top movers loaded - Gainers: ${gainers.size}, Losers: ${losers.size}")
                    gainers.take(3).forEach { stock ->
                        Log.d(TAG, "  🟢 Gainer: ${stock.symbol} +${stock.formattedPercentChange}")
                    }
                    losers.take(3).forEach { stock ->
                        Log.d(TAG, "  🔴 Loser: ${stock.symbol} ${stock.formattedPercentChange}")
                    }
                    _uiState.value = _uiState.value.copy(
                        topGainers = gainers.take(5),
                        topLosers = losers.take(5)
                    )
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Failed to load top movers: ${result.message}")
                }
                else -> {}
            }
            
            // Load NIFTY 50 stocks
            Log.d(TAG, "📊 Fetching NIFTY 50 stocks...")
            when (val result = repository.getNifty50()) {
                is Result.Success -> {
                    Log.d(TAG, "✅ NIFTY 50 loaded: ${result.data.size} stocks")
                    result.data.take(5).forEach { stock ->
                        Log.d(TAG, "  📊 ${stock.symbol}: ₹${stock.formattedPrice} (${stock.formattedPercentChange})")
                    }
                    _uiState.value = _uiState.value.copy(
                        nifty50Stocks = result.data,
                        isLoading = false
                    )
                    Log.d(TAG, "✅ All data loaded successfully!")
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Failed to load NIFTY 50: ${result.message}")
                    Log.e(TAG, "❌ Exception: ${result.exception?.javaClass?.simpleName}")
                    Log.e(TAG, "❌ Stack trace: ${result.exception?.stackTraceToString()?.take(500)}")
                    
                    val showError = _uiState.value.indices.isEmpty() && _uiState.value.topGainers.isEmpty()
                    Log.d(TAG, "⚠️ Show error to user: $showError")
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (showError) result.message else null
                    )
                }
                else -> {}
            }
            
            // Load Cryptocurrencies
            Log.d(TAG, "💰 Fetching cryptocurrencies...")
            when (val result = repository.getCryptos()) {
                is Result.Success -> {
                    Log.d(TAG, "✅ Cryptos loaded: ${result.data.size} coins")
                    result.data.take(3).forEach { crypto ->
                        Log.d(TAG, "  🪙 ${crypto.symbol}: ${crypto.formattedPrice} (${crypto.formattedChange})")
                    }
                    _uiState.value = _uiState.value.copy(cryptos = result.data)
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Failed to load cryptos: ${result.message}")
                }
                else -> {}
            }
            
            // Final state
            Log.d(TAG, "📊 FINAL STATE:")
            Log.d(TAG, "  - Indices: ${_uiState.value.indices.size}")
            Log.d(TAG, "  - Top Gainers: ${_uiState.value.topGainers.size}")
            Log.d(TAG, "  - Top Losers: ${_uiState.value.topLosers.size}")
            Log.d(TAG, "  - NIFTY 50 Stocks: ${_uiState.value.nifty50Stocks.size}")
            Log.d(TAG, "  - Cryptos: ${_uiState.value.cryptos.size}")
            Log.d(TAG, "  - Error: ${_uiState.value.error ?: "none"}")
            Log.d(TAG, "  - Is Loading: ${_uiState.value.isLoading}")
        }
    }
    
    fun refresh() {
        Log.d(TAG, "🔄 Manual refresh triggered")
        loadData()
    }
    
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            Log.d(TAG, "⏱️ Auto-refresh started (every 30 seconds)")
            while (true) {
                delay(30_000) // Refresh every 30 seconds
                Log.d(TAG, "⏱️ Auto-refresh: updating indices...")
                when (val result = repository.getMarketIndices()) {
                    is Result.Success -> {
                        Log.d(TAG, "✅ Auto-refresh: indices updated")
                        _uiState.value = _uiState.value.copy(indices = result.data)
                    }
                    is Result.Error -> {
                        Log.e(TAG, "❌ Auto-refresh failed: ${result.message}")
                    }
                    else -> {}
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🗑️ HomeViewModel cleared, stopping auto-refresh")
        autoRefreshJob?.cancel()
    }
}
