package pt.mybets.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pt.mybets.presentation.AddBetScreen
import pt.mybets.presentation.HomeScreen
import pt.mybets.presentation.StatsScreen
import pt.mybets.presentation.viewmodel.BetViewModel

object Routes {
    const val HOME = "home"
    const val ADD_BET = "add_bet"
    const val STATS = "stats"
}

@Composable
fun AppNavigation(viewModel: BetViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                uiState = uiState,
                onNavigateToAddBet = { navController.navigate(Routes.ADD_BET) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onUpdateGameResult = viewModel::updateGameResult,
                onMarkAllWon = viewModel::markAllGamesAsWon,
                onExportBackup = { uri -> viewModel.exportDatabase(context, uri) },
                onImportBackup = { uri -> viewModel.importDatabase(context, uri) }
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
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}