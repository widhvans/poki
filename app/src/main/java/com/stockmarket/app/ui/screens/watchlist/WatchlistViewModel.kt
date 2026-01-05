package com.stockmarket.app.ui.screens.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.data.repository.Result
import com.stockmarket.app.data.repository.StockRepository
import com.stockmarket.app.domain.models.Stock
import com.stockmarket.app.domain.models.WatchlistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val isLoading: Boolean = true,
    val items: List<WatchlistItem> = emptyList(),
    val stocksData: Map<String, Stock> = emptyMap()
)

class WatchlistViewModel(
    private val repository: StockRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()
    
    init {
        observeWatchlist()
    }
    
    private fun observeWatchlist() {
        viewModelScope.launch {
            repository.getWatchlist().collect { items ->
                _uiState.value = _uiState.value.copy(
                    items = items,
                    isLoading = false
                )
                // Load stock data for each item
                loadStocksData(items)
            }
        }
    }
    
    private fun loadStocksData(items: List<WatchlistItem>) {
        viewModelScope.launch {
            val stocksMap = mutableMapOf<String, Stock>()
            
            for (item in items) {
                when (val result = repository.getStockQuote(item.symbol)) {
                    is Result.Success -> {
                        stocksMap[item.symbol] = result.data
                    }
                    else -> {}
                }
            }
            
            _uiState.value = _uiState.value.copy(stocksData = stocksMap)
        }
    }
    
    fun removeFromWatchlist(symbol: String) {
        viewModelScope.launch {
            repository.removeFromWatchlist(symbol)
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            loadStocksData(_uiState.value.items)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
