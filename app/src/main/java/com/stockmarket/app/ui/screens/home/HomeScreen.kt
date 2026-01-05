package com.stockmarket.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockmarket.app.domain.models.Stock
import com.stockmarket.app.ui.components.*
import com.stockmarket.app.ui.theme.*
import com.stockmarket.app.ui.screens.logs.LogViewerActivity
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onStockClick: (String) -> Unit,
    onViewAllClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // App Bar with Search
            item {
                HomeAppBar(
                    onSearchClick = onSearchClick,
                    onLongPressSearch = { LogViewerActivity.start(context) }
                )
            }
            
            // Market Indices Ticker
            item {
                if (uiState.indices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(title = "Market Indices")
                    Spacer(modifier = Modifier.height(8.dp))
                    IndexTickerRow(indices = uiState.indices)
                }
            }
            
            // Top Gainers
            item {
                if (uiState.topGainers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader(
                        title = "🚀 Top Gainers",
                        actionText = "View All",
                        onActionClick = { onViewAllClick("gainers") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TopMoversRow(
                        stocks = uiState.topGainers,
                        onStockClick = onStockClick
                    )
                }
            }
            
            // Top Losers
            item {
                if (uiState.topLosers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader(
                        title = "📉 Top Losers",
                        actionText = "View All",
                        onActionClick = { onViewAllClick("losers") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TopMoversRow(
                        stocks = uiState.topLosers,
                        onStockClick = onStockClick
                    )
                }
            }
            
            // NIFTY 50 Stocks
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(
                    title = "NIFTY 50",
                    actionText = "View All",
                    onActionClick = { onViewAllClick("nifty50") }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (uiState.isLoading && uiState.nifty50Stocks.isEmpty()) {
                items(5) {
                    StockCardShimmer(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            } else if (uiState.error != null && uiState.nifty50Stocks.isEmpty()) {
                item {
                    ErrorState(
                        message = uiState.error ?: "Something went wrong",
                        onRetry = { viewModel.refresh() }
                    )
                }
            } else {
                items(
                    items = uiState.nifty50Stocks.take(10),
                    key = { it.symbol }
                ) { stock ->
                    StockCard(
                        stock = stock,
                        onClick = { onStockClick(stock.symbol) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeAppBar(
    onSearchClick: () -> Unit,
    onLongPressSearch: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Primary, Background)
                )
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Stock Market",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Indian Exchange",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                // Search icon with long press support for logs
                Box(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = onSearchClick,
                            onLongClick = onLongPressSearch
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search (Long press for logs)",
                        tint = TextPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search Bar (non-functional, just clickable)
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                color = Surface,
                shape = MaterialTheme.shapes.medium,
                onClick = onSearchClick
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search stocks...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun TopMoversRow(
    stocks: List<Stock>,
    onStockClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stocks, key = { it.symbol }) { stock ->
            TopMoverCard(
                stock = stock,
                onClick = { onStockClick(stock.symbol) }
            )
        }
    }
}
