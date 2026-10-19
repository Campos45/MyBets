package pt.mybets.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pt.mybets.presentation.AddBetScreen
import pt.mybets.presentation.BetsScreen
import pt.mybets.presentation.HomeScreen
import pt.mybets.presentation.StatsScreen
import pt.mybets.presentation.DataExplorerScreen
import pt.mybets.presentation.ManageDataScreen
import pt.mybets.presentation.viewmodel.BetViewModel

object Routes {
    const val HOME = "home"
    const val ADD_BET = "add_bet"
    const val STATS = "stats"
    const val BETS = "bets"
    const val EXPLORER = "explorer"
    const val MANAGE_DATA = "manage_data"
}

@Composable
fun AppNavigation(viewModel: BetViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.BETS, Routes.STATS, Routes.EXPLORER)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = {
                            if (currentRoute != Routes.HOME) {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Início") },
                        label = { Text("Início") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.BETS,
                        onClick = {
                            if (currentRoute != Routes.BETS) {
                                navController.navigate(Routes.BETS) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Apostas") },
                        label = { Text("Apostas") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.STATS,
                        onClick = {
                            if (currentRoute != Routes.STATS) {
                                navController.navigate(Routes.STATS) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Análise") },
                        label = { Text("Análise") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.EXPLORER,
                        onClick = {
                            if (currentRoute != Routes.EXPLORER) {
                                navController.navigate(Routes.EXPLORER) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Explorar") },
                        label = { Text("Explorar") }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(if (showBottomBar) paddingValues else PaddingValues(0.dp))
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    uiState = uiState,
                    onNavigateToAddBet = { navController.navigate(Routes.ADD_BET) },
                    onNavigateToManageData = { navController.navigate(Routes.MANAGE_DATA) },
                    onExportBackup = { uri -> viewModel.exportDatabase(context, uri) },
                    onImportBackup = { uri -> viewModel.importDatabase(context, uri) }
                )
            }

            composable(Routes.BETS) {
                BetsScreen(
                    uiState = uiState,
                    onUpdateGameResult = viewModel::updateGameResult,
                    onMarkAllWon = viewModel::markAllGamesAsWon,
                    onDeleteBet = { viewModel.deleteBet(it.bet) },
                    onCopyBet = { bwg, modalityMap ->
                        viewModel.copyBet(bwg, modalityMap)
                        navController.navigate(Routes.ADD_BET)
                    }
                )
            }

            composable(Routes.ADD_BET) {
                AddBetScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.STATS) {
                StatsScreen(
                    uiState = uiState
                )
            }

            composable(Routes.EXPLORER) {
                DataExplorerScreen(
                    viewModel = viewModel,
                    onNavigateToManageData = { navController.navigate(Routes.MANAGE_DATA) }
                )
            }

            composable(Routes.MANAGE_DATA) {
                ManageDataScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}