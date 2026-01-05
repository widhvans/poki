package com.stockmarket.app.ui.screens.chart

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.data.repository.Result
import com.stockmarket.app.data.repository.StockRepository
import com.stockmarket.app.domain.models.CandleData
import com.stockmarket.app.domain.models.ChartTimeframe
import com.stockmarket.app.domain.models.Stock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ChartViewModel"

data class ChartUiState(
    val isLoading: Boolean = true,
    val stock: Stock? = null,
    val candles: List<CandleData> = emptyList(),
    val selectedTimeframe: ChartTimeframe = ChartTimeframe.ONE_MONTH,
    val isInWatchlist: Boolean = false,
    val error: String? = null,
    // Technical indicators toggles
    val showMA5: Boolean = false,
    val showMA10: Boolean = false,
    val showMA20: Boolean = false,
    val showVolume: Boolean = true
)

class ChartViewModel(
    private val symbol: String,
    private val repository: StockRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChartUiState())
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()
    
    // Auto-refresh job for live candle updates
    private var autoRefreshJob: Job? = null
    
    init {
        loadStock()
        loadChartData()
        observeWatchlistStatus()
    }
    
    private fun loadStock() {
        viewModelScope.launch {
            when (val result = repository.getStockQuote(symbol)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(stock = result.data)
                }
                is Result.Error -> {
                    // Stock data is nice to have, chart is priority
                }
                else -> {}
            }
        }
    }
    
    private fun loadChartData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = repository.getHistoricalData(
                symbol = symbol,
                timeframe = _uiState.value.selectedTimeframe
            )) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        candles = result.data,
                        isLoading = false
                    )
                    // Start auto-refresh for intraday timeframes
                    startAutoRefreshIfNeeded()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }
    
    /**
     * Start auto-refresh for intraday timeframes to show live candle movement
     */
    private fun startAutoRefreshIfNeeded() {
        autoRefreshJob?.cancel()
        
        val timeframe = _uiState.value.selectedTimeframe
        val isIntraday = timeframe in listOf(
            ChartTimeframe.FIVE_MIN,
            ChartTimeframe.TEN_MIN,
            ChartTimeframe.THIRTY_MIN,
            ChartTimeframe.ONE_HOUR,
            ChartTimeframe.ONE_DAY
        )
        
        if (isIntraday) {
            Log.d(TAG, "⏱️ Starting live candle refresh for ${timeframe.label}")
            autoRefreshJob = viewModelScope.launch {
                while (true) {
                    delay(1_000) // Refresh every 1 second for live candle movement
                    Log.d(TAG, "⏱️ Auto-refreshing chart data...")
                    refreshChartDataSilently()
                }
            }
        } else {
            Log.d(TAG, "📊 Non-intraday timeframe, no auto-refresh needed")
        }
    }
    
    /**
     * Refresh chart data without showing loading indicator
     */
    private suspend fun refreshChartDataSilently() {
        when (val result = repository.getHistoricalData(
            symbol = symbol,
            timeframe = _uiState.value.selectedTimeframe
        )) {
            is Result.Success -> {
                _uiState.value = _uiState.value.copy(candles = result.data)
                Log.d(TAG, "✅ Chart data refreshed with ${result.data.size} candles")
            }
            is Result.Error -> {
                Log.e(TAG, "❌ Silent refresh failed: ${result.message}")
            }
            else -> {}
        }
    }
    
    private fun observeWatchlistStatus() {
        viewModelScope.launch {
            repository.observeWatchlistStatus(symbol).collect { inWatchlist ->
                _uiState.value = _uiState.value.copy(isInWatchlist = inWatchlist)
            }
        }
    }
    
    fun selectTimeframe(timeframe: ChartTimeframe) {
        if (timeframe != _uiState.value.selectedTimeframe) {
            _uiState.value = _uiState.value.copy(selectedTimeframe = timeframe)
            loadChartData()
        }
    }
    
    fun toggleWatchlist() {
        viewModelScope.launch {
            val stock = _uiState.value.stock
            val name = stock?.companyName ?: symbol
            repository.toggleWatchlist(symbol, name)
        }
    }
    
    fun toggleMA5() {
        _uiState.value = _uiState.value.copy(showMA5 = !_uiState.value.showMA5)
    }
    
    fun toggleMA10() {
        _uiState.value = _uiState.value.copy(showMA10 = !_uiState.value.showMA10)
    }
    
    fun toggleMA20() {
        _uiState.value = _uiState.value.copy(showMA20 = !_uiState.value.showMA20)
    }
    
    fun toggleVolume() {
        _uiState.value = _uiState.value.copy(showVolume = !_uiState.value.showVolume)
    }
    
    fun refresh() {
        loadStock()
        loadChartData()
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🗑️ ChartViewModel cleared, stopping auto-refresh")
        autoRefreshJob?.cancel()
    }
}
