package com.stockmarket.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.data.repository.Result
import com.stockmarket.app.data.repository.StockRepository
import com.stockmarket.app.domain.models.Crypto
import com.stockmarket.app.domain.models.MarketIndex
import com.stockmarket.app.domain.models.Stock
import kotlinx.coroutines.async
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
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "🔄 loadData() called - Starting PARALLEL fetch of all home data...")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // Launch all API calls in parallel using async
            val indicesDeferred = async { 
                Log.d(TAG, "📊 [ASYNC] Fetching market indices...")
                repository.getMarketIndices() 
            }
            val moversDeferred = async { 
                Log.d(TAG, "📊 [ASYNC] Fetching top movers...")
                repository.getTopMovers() 
            }
            val nifty50Deferred = async { 
                Log.d(TAG, "📊 [ASYNC] Fetching NIFTY 50...")
                repository.getNifty50() 
            }
            val cryptoDeferred = async { 
                Log.d(TAG, "💰 [ASYNC] Fetching cryptocurrencies...")
                repository.getCryptos() 
            }
            
            // Await all results
            val indicesResult = indicesDeferred.await()
            val moversResult = moversDeferred.await()
            val nifty50Result = nifty50Deferred.await()
            val cryptoResult = cryptoDeferred.await()
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ All API calls completed in ${elapsed}ms")
            
            // Process indices
            when (indicesResult) {
                is Result.Success -> {
                    Log.d(TAG, "✅ Market indices: ${indicesResult.data.size} items")
                    _uiState.value = _uiState.value.copy(indices = indicesResult.data)
                }
                is Result.Error -> Log.e(TAG, "❌ Indices failed: ${indicesResult.message}")
                else -> {}
            }
            
            // Process top movers
            when (moversResult) {
                is Result.Success -> {
                    val (gainers, losers) = moversResult.data
                    Log.d(TAG, "✅ Top movers: ${gainers.size} gainers, ${losers.size} losers")
                    _uiState.value = _uiState.value.copy(
                        topGainers = gainers.take(5),
                        topLosers = losers.take(5)
                    )
                }
                is Result.Error -> Log.e(TAG, "❌ Movers failed: ${moversResult.message}")
                else -> {}
            }
            
            // Process NIFTY 50
            when (nifty50Result) {
                is Result.Success -> {
                    Log.d(TAG, "✅ NIFTY 50: ${nifty50Result.data.size} stocks")
                    _uiState.value = _uiState.value.copy(nifty50Stocks = nifty50Result.data)
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ NIFTY 50 failed: ${nifty50Result.message}")
                    val showError = _uiState.value.indices.isEmpty() && _uiState.value.topGainers.isEmpty()
                    if (showError) {
                        _uiState.value = _uiState.value.copy(error = nifty50Result.message)
                    }
                }
                else -> {}
            }
            
            // Process cryptos
            when (cryptoResult) {
                is Result.Success -> {
                    Log.d(TAG, "✅ Cryptos: ${cryptoResult.data.size} coins")
                    _uiState.value = _uiState.value.copy(cryptos = cryptoResult.data)
                }
                is Result.Error -> Log.e(TAG, "❌ Cryptos failed: ${cryptoResult.message}")
                else -> {}
            }
            
            // Mark loading complete
            _uiState.value = _uiState.value.copy(isLoading = false)
            
            val totalElapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "📊 FINAL STATE (${totalElapsed}ms total):")
            Log.d(TAG, "  - Indices: ${_uiState.value.indices.size}")
            Log.d(TAG, "  - Gainers/Losers: ${_uiState.value.topGainers.size}/${_uiState.value.topLosers.size}")
            Log.d(TAG, "  - NIFTY 50: ${_uiState.value.nifty50Stocks.size}")
            Log.d(TAG, "  - Cryptos: ${_uiState.value.cryptos.size}")
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
