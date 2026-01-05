package com.stockmarket.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockmarket.app.data.repository.Result
import com.stockmarket.app.data.repository.StockRepository
import com.stockmarket.app.domain.models.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val error: String? = null
)

class SearchViewModel(
    private val repository: StockRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null
    
    init {
        loadRecentSearches()
    }
    
    private fun loadRecentSearches() {
        viewModelScope.launch {
            repository.getRecentSearches().collect { searches ->
                _uiState.value = _uiState.value.copy(recentSearches = searches)
            }
        }
    }
    
    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        
        // Debounce search
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // Wait 300ms after last keystroke
            if (query.length >= 2) {
                performSearch(query)
            } else {
                _uiState.value = _uiState.value.copy(results = emptyList())
            }
        }
    }
    
    fun searchFromRecent(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(query)
        }
    }
    
    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        
        when (val result = repository.searchStocks(query)) {
            is Result.Success -> {
                _uiState.value = _uiState.value.copy(
                    results = result.data,
                    isSearching = false
                )
            }
            is Result.Error -> {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = result.message
                )
            }
            else -> {}
        }
    }
    
    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "",
            results = emptyList(),
            error = null
        )
    }
    
    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }
}
