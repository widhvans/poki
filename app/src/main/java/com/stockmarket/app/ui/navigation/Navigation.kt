package com.stockmarket.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.stockmarket.app.ui.screens.chart.ChartScreen
import com.stockmarket.app.ui.screens.home.HomeScreen
import com.stockmarket.app.ui.screens.search.SearchScreen
import com.stockmarket.app.ui.screens.stocklist.StockListScreen
import com.stockmarket.app.ui.screens.watchlist.WatchlistScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Stocks : Screen("stocks")
    object Search : Screen("search")
    object Watchlist : Screen("watchlist")
    object Chart : Screen("chart/{symbol}") {
        fun createRoute(symbol: String) = "chart/$symbol"
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStockClick = { symbol ->
                    navController.navigate(Screen.Chart.createRoute(symbol))
                },
                onViewAllClick = { category ->
                    navController.navigate(Screen.Stocks.route)
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }
        
        composable(Screen.Stocks.route) {
            StockListScreen(
                onStockClick = { symbol ->
                    navController.navigate(Screen.Chart.createRoute(symbol))
                }
            )
        }
        
        composable(Screen.Search.route) {
            SearchScreen(
                onStockClick = { symbol ->
                    navController.navigate(Screen.Chart.createRoute(symbol))
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Watchlist.route) {
            WatchlistScreen(
                onStockClick = { symbol ->
                    navController.navigate(Screen.Chart.createRoute(symbol))
                }
            )
        }
        
        composable(
            route = Screen.Chart.route,
            arguments = listOf(
                navArgument("symbol") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: return@composable
            ChartScreen(
                symbol = symbol,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
