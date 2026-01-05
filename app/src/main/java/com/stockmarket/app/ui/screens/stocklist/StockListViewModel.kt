package com.stockmarket.app.ui.screens.stocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.data.repository.Result
import com.stockmarket.app.data.repository.StockRepository
import com.stockmarket.app.domain.models.Sector
import com.stockmarket.app.domain.models.Stock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StockListUiState(
    val isLoading: Boolean = true,
    val stocks: List<Stock> = emptyList(),
    val selectedSector: Sector = Sector.NIFTY50,
    val error: String? = null
)

class StockListViewModel(
    private val repository: StockRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(StockListUiState())
    val uiState: StateFlow<StockListUiState> = _uiState.asStateFlow()
    
    init {
        loadStocks()
    }
    
    fun selectSector(sector: Sector) {
        if (sector != _uiState.value.selectedSector) {
            _uiState.value = _uiState.value.copy(selectedSector = sector)
            loadStocks()
        }
    }
    
    fun loadStocks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = when (_uiState.value.selectedSector) {
                Sector.NIFTY50 -> repository.getNifty50()
                Sector.BANK_NIFTY -> repository.getBankNifty()
                else -> repository.getSectorStocks(_uiState.value.selectedSector)
            }
            
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        stocks = result.data,
                        isLoading = false
                    )
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
    
    fun refresh() {
        loadStocks()
    }
}
