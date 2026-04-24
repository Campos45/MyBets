package pt.mybets.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pt.mybets.presentation.viewmodel.BetViewModel

object Routes {
    const val HOME = "home"
    const val ADD_BET = "add_bet"
    const val STATS = "stats" // A rota que o botão vai chamar
}

@Composable
fun AppNavigation(viewModel: BetViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    // O contexto local necessário para o Exportar/Importar
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                uiState = uiState,
                onNavigateToAddBet = { navController.navigate(Routes.ADD_BET) },
                onNavigateToStats = { navController.navigate(Routes.STATS) },
                onUpdateGameResult = { betWithGames, game, result ->
                    viewModel.updateGameResult(betWithGames, game, result)
                },
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

        // É ISTO QUE EVITA O CRASH DA NAVEGAÇÃO! O ecrã tem de estar aqui declarado.
        composable(Routes.STATS) {
            StatsScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}