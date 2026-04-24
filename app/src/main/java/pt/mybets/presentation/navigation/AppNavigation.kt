package pt.mybets.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pt.mybets.presentation.viewmodel.BetViewModel

// Definimos as nossas rotas como constantes simples
object Routes {
    const val HOME = "home"
    const val ADD_BET = "add_bet"
}

@Composable
fun AppNavigation(viewModel: BetViewModel) {
    // Controlador de navegação do Compose
    val navController = rememberNavController()

    // Observamos o estado global da nossa app (o StateFlow do ViewModel que criámos na Fase 2)
    val uiState by viewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        // Ecrã 1: Dashboard / Home
        composable(Routes.HOME) {
            // (Placeholder - vamos criar este ecrã a seguir)
            HomeScreen(
                uiState = uiState,
                onNavigateToAddBet = { navController.navigate(Routes.ADD_BET) }
            )
        }

        // Ecrã 2: Adicionar Aposta
        composable(Routes.ADD_BET) {
            // (Placeholder - vamos criar este ecrã depois)
            AddBetScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}