package com.stockmarket.app.ui.screens.stocklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockmarket.app.domain.models.Sector
import com.stockmarket.app.ui.components.CompactStockItem
import com.stockmarket.app.ui.components.ErrorState
import com.stockmarket.app.ui.components.StockCardShimmer
import com.stockmarket.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StockListScreen(
    onStockClick: (String) -> Unit,
    viewModel: StockListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Stocks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Sector Filter Chips
                SectorFilterRow(
                    selectedSector = uiState.selectedSector,
                    onSectorSelected = { viewModel.selectSector(it) }
                )
                
                // Stock List
                if (uiState.isLoading && uiState.stocks.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(10) {
                            StockCardShimmer()
                        }
                    }
                } else if (uiState.error != null && uiState.stocks.isEmpty()) {
                    ErrorState(
                        message = uiState.error ?: "Something went wrong",
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = uiState.stocks,
                            key = { it.symbol }
                        ) { stock ->
                            CompactStockItem(
                                stock = stock,
                                onClick = { onStockClick(stock.symbol) }
                            )
                            HorizontalDivider(
                                color = SurfaceLight,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
            
            PullRefreshIndicator(
                refreshing = uiState.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = Surface,
                contentColor = AccentBlue
            )
        }
    }
}

@Composable
private fun SectorFilterRow(
    selectedSector: Sector,
    onSectorSelected: (Sector) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(Sector.entries) { sector ->
            FilterChip(
                selected = selectedSector == sector,
                onClick = { onSectorSelected(sector) },
                label = { 
                    Text(
                        text = sector.displayName,
                        style = MaterialTheme.typography.labelMedium
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentBlue,
                    selectedLabelColor = Color.White,
                    containerColor = SurfaceLight,
                    labelColor = TextSecondary
                )
            )
        }
    }
}
