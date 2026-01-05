package com.stockmarket.app.ui.screens.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockmarket.app.domain.models.ChartTimeframe
import com.stockmarket.app.domain.models.Stock
import com.stockmarket.app.ui.components.ErrorState
import com.stockmarket.app.ui.components.charts.AdvancedChartView
import com.stockmarket.app.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    symbol: String,
    onBackClick: () -> Unit,
    viewModel: ChartViewModel = koinViewModel { parametersOf(symbol) }
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            ChartTopBar(
                stock = uiState.stock,
                symbol = symbol,
                isInWatchlist = uiState.isInWatchlist,
                onBackClick = onBackClick,
                onWatchlistClick = { viewModel.toggleWatchlist() }
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Stock Price Header
            uiState.stock?.let { stock ->
                StockPriceHeader(stock = stock)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Timeframe Selector
            TimeframeSelectorRow(
                selectedTimeframe = uiState.selectedTimeframe,
                onTimeframeSelected = { viewModel.selectTimeframe(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Chart
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (uiState.error != null) {
                ErrorState(
                    message = uiState.error ?: "Failed to load chart",
                    onRetry = { viewModel.refresh() }
                )
            } else {
                AdvancedChartView(
                    candles = uiState.candles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .padding(horizontal = 8.dp),
                    showVolume = uiState.showVolume,
                    showMA5 = uiState.showMA5,
                    showMA10 = uiState.showMA10,
                    showMA20 = uiState.showMA20
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Indicator Toggles
            IndicatorToggles(
                showVolume = uiState.showVolume,
                showMA5 = uiState.showMA5,
                showMA10 = uiState.showMA10,
                showMA20 = uiState.showMA20,
                onToggleVolume = { viewModel.toggleVolume() },
                onToggleMA5 = { viewModel.toggleMA5() },
                onToggleMA10 = { viewModel.toggleMA10() },
                onToggleMA20 = { viewModel.toggleMA20() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stock Details
            uiState.stock?.let { stock ->
                StockDetailsCard(stock = stock)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartTopBar(
    stock: Stock?,
    symbol: String,
    isInWatchlist: Boolean,
    onBackClick: () -> Unit,
    onWatchlistClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stock?.companyName ?: symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
        },
        actions = {
            IconButton(onClick = onWatchlistClick) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = if (isInWatchlist) "Remove from watchlist" else "Add to watchlist",
                    tint = if (isInWatchlist) AccentGold else TextSecondary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Background
        )
    )
}

@Composable
private fun StockPriceHeader(stock: Stock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stock.formattedPrice,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (stock.isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = if (stock.isPositive) GainGreen else LossRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${stock.formattedChange} (${stock.formattedPercentChange})",
                style = MaterialTheme.typography.titleMedium,
                color = if (stock.isPositive) GainGreen else LossRed,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeframeSelectorRow(
    selectedTimeframe: ChartTimeframe,
    onTimeframeSelected: (ChartTimeframe) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ChartTimeframe.entries) { timeframe ->
            FilterChip(
                selected = selectedTimeframe == timeframe,
                onClick = { onTimeframeSelected(timeframe) },
                label = { 
                    Text(
                        text = timeframe.label,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorToggles(
    showVolume: Boolean,
    showMA5: Boolean,
    showMA10: Boolean,
    showMA20: Boolean,
    onToggleVolume: () -> Unit,
    onToggleMA5: () -> Unit,
    onToggleMA10: () -> Unit,
    onToggleMA20: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Indicators",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IndicatorChip(
                label = "Volume",
                selected = showVolume,
                onClick = onToggleVolume,
                color = ChartVolume
            )
            IndicatorChip(
                label = "MA5",
                selected = showMA5,
                onClick = onToggleMA5,
                color = Ma5Color
            )
            IndicatorChip(
                label = "MA10",
                selected = showMA10,
                onClick = onToggleMA10,
                color = Ma10Color
            )
            IndicatorChip(
                label = "MA20",
                selected = showMA20,
                onClick = onToggleMA20,
                color = Ma20Color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { 
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            ) 
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color,
            containerColor = SurfaceLight,
            labelColor = TextSecondary
        )
    )
}

@Composable
private fun StockDetailsCard(stock: Stock) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Stock Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(label = "Open", value = "₹%.2f".format(stock.open))
                DetailItem(label = "High", value = "₹%.2f".format(stock.dayHigh))
                DetailItem(label = "Low", value = "₹%.2f".format(stock.dayLow))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(label = "Prev Close", value = "₹%.2f".format(stock.previousClose))
                DetailItem(label = "Volume", value = formatVolume(stock.volume))
                DetailItem(label = "52W High", value = "₹%.2f".format(stock.yearHigh))
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

private fun formatVolume(volume: Long): String {
    return when {
        volume >= 10_000_000 -> "%.2f Cr".format(volume / 10_000_000.0)
        volume >= 100_000 -> "%.2f L".format(volume / 100_000.0)
        volume >= 1000 -> "%.2f K".format(volume / 1000.0)
        else -> volume.toString()
    }
}
