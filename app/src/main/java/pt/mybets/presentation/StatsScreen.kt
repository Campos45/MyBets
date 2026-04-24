package pt.mybets.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.mybets.presentation.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    uiState: MainUiState,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análise de Performance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // SECCÃO 1: ROI POR ODDS
            item {
                Text("ROI por Faixa de Odds", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card {
                    Column(Modifier.padding(16.dp)) {
                        uiState.detailedStats.oddRangeStats.forEach { stat ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Odd ${stat.range}", Modifier.weight(1f))
                                Text(
                                    "${String.format("%.1f", stat.roi)}%",
                                    color = if (stat.roi >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(" (${stat.totalBets} apostas)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // SECÇÃO 2: WIN RATE POR COMPETIÇÃO
            item {
                Text("Acerto por Competição", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(uiState.detailedStats.competitionStats) { stat ->
                StatRow(name = stat.name, rate = stat.winRate, total = stat.total)
            }

            // SECÇÃO 3: WIN RATE POR MODALIDADE
            item {
                Text("Acerto por Modalidade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(uiState.detailedStats.modalityStats) { stat ->
                StatRow(name = stat.name, rate = stat.winRate, total = stat.total)
            }
        }
    }
}

@Composable
fun StatRow(name: String, rate: Double, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(
                progress = { (rate / 100).toFloat() },
                modifier = Modifier.fillMaxWidth(0.8f).height(8.dp),
                color = if (rate >= 50) Color(0xFF4CAF50) else Color(0xFFFFA000),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        Text("${String.format("%.1f", rate)}%", fontWeight = FontWeight.Bold)
    }
}