package com.stockmarket.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.data.repository.Result
import com.stockmarket.app.data.repository.StockRepository
import com.stockmarket.app.domain.models.MarketIndex
import com.stockmarket.app.domain.models.Stock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val indices: List<MarketIndex> = emptyList(),
    val topGainers: List<Stock> = emptyList(),
    val topLosers: List<Stock> = emptyList(),
    val nifty50Stocks: List<Stock> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    private val repository: StockRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var autoRefreshJob: kotlinx.coroutines.Job? = null
    
    init {
        loadData()
        startAutoRefresh()
    }
    
    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // Load market indices
            when (val result = repository.getMarketIndices()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(indices = result.data)
                }
                is Result.Error -> {
                    // Continue with other data even if indices fail
                }
                else -> {}
            }
            
            // Load top movers
            when (val result = repository.getTopMovers()) {
                is Result.Success -> {
                    val (gainers, losers) = result.data
                    _uiState.value = _uiState.value.copy(
                        topGainers = gainers.take(5),
                        topLosers = losers.take(5)
                    )
                }
                is Result.Error -> {
                    // Continue
                }
                else -> {}
            }
            
            // Load NIFTY 50 stocks
            when (val result = repository.getNifty50()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        nifty50Stocks = result.data,
                        isLoading = false
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (_uiState.value.indices.isEmpty() && 
                                   _uiState.value.topGainers.isEmpty()) 
                                   result.message else null
                    )
                }
                else -> {}
            }
        }
    }
    
    fun refresh() {
        loadData()
    }
    
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(30_000) // Refresh every 30 seconds
                // Only refresh indices for real-time feel
                when (val result = repository.getMarketIndices()) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(indices = result.data)
                    }
                    else -> {}
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
