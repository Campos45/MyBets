package pt.mybets.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.relations.BetWithGames
import pt.mybets.domain.model.ResultState
import pt.mybets.presentation.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetsScreen(
    uiState: MainUiState,
    onUpdateGameResult: (BetWithGames, GameEntity, ResultState) -> Unit,
    onMarkAllWon: (BetWithGames) -> Unit,
    onDeleteBet: (BetWithGames) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Em Aberto", "Concluídas")
    var betToDelete by remember { mutableStateOf<BetWithGames?>(null) }

    // Filtrar apostas
    val filteredBets = remember(uiState.bets, selectedTabIndex) {
        uiState.bets.filter {
            if (selectedTabIndex == 0) {
                !it.bet.isFinished
            } else {
                it.bet.isFinished
            }
        }
    }

    // Mapa de id da modalidade para o nome
    val modalityMap = remember(uiState.modalities) {
        uiState.modalities.associate { it.id to it.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("As Minhas Apostas") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (filteredBets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTabIndex == 0) "Não tens apostas em aberto." else "Ainda não concluíste nenhuma aposta.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredBets, key = { it.bet.id }) { bwg ->
                        BetCard(
                            bwg = bwg,
                            modalityMap = modalityMap,
                            onUpdateGameResult = onUpdateGameResult,
                            onMarkAllWon = onMarkAllWon,
                            onDeleteBet = { betToDelete = bwg }
                        )
                    }
                }
            }
        }
    }

    if (betToDelete != null) {
        AlertDialog(
            onDismissRequest = { betToDelete = null },
            title = { Text("Eliminar Aposta") },
            text = { Text("Tens a certeza que pretendes eliminar definitivamente esta aposta? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        betToDelete?.let { onDeleteBet(it) }
                        betToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { betToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun BetCard(
    bwg: BetWithGames,
    modalityMap: Map<Int, String>,
    onUpdateGameResult: (BetWithGames, GameEntity, ResultState) -> Unit,
    onMarkAllWon: (BetWithGames) -> Unit,
    onDeleteBet: (BetWithGames) -> Unit
) {
    val totalOdd = bwg.games.fold(1.0) { acc, g ->
        acc * (if (g.result == ResultState.VOID) 1.0 else g.odd)
    }

    val color = when (bwg.bet.globalResult) {
        ResultState.WON -> Color(0xFF4CAF50)
        ResultState.LOST -> Color(0xFFF44336)
        ResultState.VOID -> Color(0xFF2196F3)
        else -> Color.Gray
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = dateFormatter.format(Date(bwg.bet.dateTimestamp))

    Card(
        Modifier.fillMaxWidth(),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(color))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(if (bwg.bet.isFreeBet) "Free Bet" else "Dinheiro Real", fontWeight = FontWeight.Bold)
                    Text(
                        "Retorno: ${String.format(Locale.getDefault(), "%.2f", bwg.bet.stake * totalOdd)}€",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Odd total: ${String.format(Locale.getDefault(), "%.2f", totalOdd)}", Modifier.padding(end = 8.dp))

                    if (!bwg.bet.isFinished) {
                        IconButton(onClick = { onMarkAllWon(bwg) }) {
                            Icon(Icons.Default.CheckCircle, "Tudo Ganho", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { onDeleteBet(bwg) }) {
                        Icon(Icons.Default.Delete, "Eliminar Aposta", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            bwg.games.forEach { g ->
                val modalityName = modalityMap[g.modalityId] ?: "Desconhecida"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.teams, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            if (g.isLive) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFD32F2F),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Comp: ${g.competition}  |  Mod: $modalityName",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "Odd: ${g.odd}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onUpdateGameResult(bwg, g, ResultState.WON) }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Ganho",
                                tint = if (g.result == ResultState.WON) Color.Green else Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(onClick = { onUpdateGameResult(bwg, g, ResultState.LOST) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Perdido",
                                tint = if (g.result == ResultState.LOST) Color.Red else Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(onClick = { onUpdateGameResult(bwg, g, ResultState.VOID) }) {
                            Text(
                                text = "=",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (g.result == ResultState.VOID) Color(0xFF2196F3) else Color.Gray.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
