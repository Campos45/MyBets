package pt.mybets.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.relations.BetWithGames
import pt.mybets.domain.model.ResultState
import pt.mybets.presentation.viewmodel.BetStatsState
import pt.mybets.presentation.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    onNavigateToAddBet: () -> Unit,
    onUpdateGameResult: (BetWithGames, GameEntity, ResultState) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("O Meu Tracker", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddBet) {
                Icon(Icons.Default.Add, contentDescription = "Nova Aposta")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Secção de Estatísticas e Gráfico
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    StatsGrid(stats = uiState.stats)
                    Spacer(modifier = Modifier.height(16.dp))
                    ProfitLineChart(bets = uiState.bets)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Histórico de Apostas", style = MaterialTheme.typography.titleLarge)
                }

                // Lista de Apostas
                if (uiState.bets.isEmpty()) {
                    item {
                        Text(
                            text = "Ainda não registaste nenhuma aposta.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                } else {
                    items(uiState.bets, key = { it.bet.id }) { betWithGames ->
                        BetCard(
                            betWithGames = betWithGames,
                            onUpdateGameResult = { game, result ->
                                onUpdateGameResult(betWithGames, game, result)
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun StatsGrid(stats: BetStatsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Lucro Total", "${String.format("%.2f", stats.totalProfit)} €",
                color = if (stats.totalProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.weight(1f))
            StatCard("Win Rate", "${String.format("%.1f", stats.hitRatePercentage)} %",
                modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Investido Real", "${String.format("%.2f", stats.totalInvested)} €",
                modifier = Modifier.weight(1f))
            StatCard("Lucro FreeBets", "${String.format("%.2f", stats.freeBetProfit)} €",
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// GRÁFICO DE LINHA DESENHADO NATIVAMENTE
@Composable
fun ProfitLineChart(bets: List<BetWithGames>) {
    // Calculamos o histórico de lucro cronologicamente (lista revertida pois vem ordenada por DESC)
    val profitHistory = remember(bets) {
        var currentProfit = 0.0
        val history = mutableListOf(0.0) // Ponto inicial no 0

        bets.reversed().filter { it.bet.isFinished }.forEach { betWithGames ->
            val bet = betWithGames.bet
            if (!bet.isFreeBet) currentProfit -= bet.stake
            if (bet.globalResult == ResultState.WON) {
                val totalOdd = betWithGames.games.fold(1.0) { acc, g -> acc * g.odd }
                currentProfit += bet.stake * totalOdd
            }
            history.add(currentProfit)
        }
        history
    }

    if (profitHistory.size > 1) {
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Evolução da Banca", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                val lineColor = MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp)) {
                    val maxValue = profitHistory.maxOrNull() ?: 1.0
                    val minValue = profitHistory.minOrNull() ?: -1.0
                    val range = (maxValue - minValue).takeIf { it > 0 } ?: 1.0

                    val widthPerPoint = size.width / (profitHistory.size - 1)
                    val heightRatio = size.height / range

                    val path = Path()
                    var previousPoint = Offset(0f, size.height - ((profitHistory[0] - minValue) * heightRatio).toFloat())
                    path.moveTo(previousPoint.x, previousPoint.y)

                    for (i in 1 until profitHistory.size) {
                        val x = i * widthPerPoint
                        val y = size.height - ((profitHistory[i] - minValue) * heightRatio).toFloat()
                        path.lineTo(x, y)
                    }

                    // Linha do Gráfico
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Linha do ZERO (Breakeven) se estiver visível no gráfico
                    if (maxValue > 0 && minValue < 0) {
                        val zeroY = size.height - ((0.0 - minValue) * heightRatio).toFloat()
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.5f),
                            start = Offset(0f, zeroY),
                            end = Offset(size.width, zeroY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

// CARTÃO PARA CADA APOSTA
@Composable
fun BetCard(
    betWithGames: BetWithGames,
    onUpdateGameResult: (GameEntity, ResultState) -> Unit
) {
    val bet = betWithGames.bet
    val games = betWithGames.games
    val totalOdd = games.fold(1.0) { acc, game -> acc * game.odd }

    val borderColor = when (bet.globalResult) {
        ResultState.WON -> Color(0xFF4CAF50)
        ResultState.LOST -> Color(0xFFF44336)
        ResultState.PENDING -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabeçalho da Aposta
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (bet.isFreeBet) "Free Bet" else "Aposta Múltipla",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "Odd: ${String.format("%.2f", totalOdd)}")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Stake: ${bet.stake}€")
                Text(text = "Retorno: ${String.format("%.2f", bet.stake * totalOdd)}€", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Lista de Jogos dentro desta aposta
            games.forEach { game ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = game.teams, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(text = "Odd: ${game.odd}", style = MaterialTheme.typography.bodySmall)
                    }

                    // Botões para marcar Ganho ou Perdido (apenas se estiver pendente)
                    if (game.result == ResultState.PENDING) {
                        Row {
                            IconButton(onClick = { onUpdateGameResult(game, ResultState.WON) }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Ganhou", tint = Color(0xFF4CAF50))
                            }
                            IconButton(onClick = { onUpdateGameResult(game, ResultState.LOST) }) {
                                Icon(Icons.Default.Close, contentDescription = "Perdeu", tint = Color(0xFFF44336))
                            }
                        }
                    } else {
                        // Mostra ícone estático do resultado
                        Icon(
                            imageVector = if (game.result == ResultState.WON) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (game.result == ResultState.WON) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }
}