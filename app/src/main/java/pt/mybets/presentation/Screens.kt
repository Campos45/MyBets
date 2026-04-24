package pt.mybets.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pt.mybets.presentation.viewmodel.BetViewModel
import pt.mybets.presentation.viewmodel.MainUiState

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onNavigateToAddBet: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onNavigateToAddBet) {
            Text(text = "Ir para Adicionar Aposta")
        }
    }
}

@Composable
fun AddBetScreen(
    viewModel: BetViewModel,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onNavigateBack) {
            Text(text = "Voltar para a Home")
        }
    }
}