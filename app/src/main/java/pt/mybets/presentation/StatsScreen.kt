package pt.mybets.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("ROI por Odds", "Competições", "Modalidades")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análise de Performance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(text = title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> {
                        // CORREÇÃO: Só mostra VAZIO se a soma das apostas em TODAS as faixas for zero
                        if (uiState.detailedStats.oddRangeStats.all { it.totalBets == 0 }) {
                            item { Text("Ainda não tens apostas concluídas para calcular o ROI.") }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        uiState.detailedStats.oddRangeStats.forEach { stat ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Odd ${stat.range}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                                Text(
                                                    "${String.format("%.1f", stat.roi)}%",
                                                    color = if (stat.roi >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(" (${stat.totalBets})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (uiState.detailedStats.competitionStats.isEmpty()) {
                            item { Text("Ainda não há dados de competições concluídas.") }
                        } else {
                            items(uiState.detailedStats.competitionStats) { stat ->
                                StatRow(name = stat.name, rate = stat.winRate, total = stat.total)
                            }
                        }
                    }
                    2 -> {
                        if (uiState.detailedStats.modalityStats.isEmpty()) {
                            item { Text("Ainda não há dados de modalidades concluídas.") }
                        } else {
                            items(uiState.detailedStats.modalityStats) { stat ->
                                StatRow(name = stat.name, rate = stat.winRate, total = stat.total)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(name: String, rate: Double, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(name, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (rate / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (rate >= 50) Color(0xFF4CAF50) else Color(0xFFFFA000),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${String.format("%.1f", rate)}%", fontWeight = FontWeight.Bold)
            Text("$total apostas", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}