package pt.mybets.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.relations.BetWithGames
import pt.mybets.domain.model.ResultState
import pt.mybets.presentation.viewmodel.BetStatsState
import pt.mybets.presentation.viewmodel.MainUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    onNavigateToAddBet: () -> Unit,
    onNavigateToStats: () -> Unit,
    onUpdateGameResult: (BetWithGames, GameEntity, ResultState) -> Unit,
    onMarkAllWon: (BetWithGames) -> Unit,
    onExportBackup: (android.net.Uri) -> Unit = {},
    onImportBackup: (android.net.Uri) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { onExportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onImportBackup(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("O Meu Tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToStats) { Icon(Icons.AutoMirrored.Filled.List, "Estatísticas") }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Opções") }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Exportar Backup (JSON)") },
                                onClick = {
                                    menuExpanded = false
                                    exportLauncher.launch("mybets_backup.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Importar Backup (JSON)") },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(arrayOf("application/json"))
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddBet) { Icon(Icons.Default.Add, "Nova Aposta") }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    StatsGrid(stats = uiState.stats)
                    Spacer(Modifier.height(16.dp))
                    ProfitLineChart(bets = uiState.bets)
                    Spacer(Modifier.height(16.dp))
                    Text("Histórico", style = MaterialTheme.typography.titleLarge)
                }
                items(uiState.bets, key = { it.bet.id }) { bwg ->
                    BetCard(
                        bwg = bwg,
                        onUpdateGameResult = { g, r -> onUpdateGameResult(bwg, g, r) },
                        onMarkAllWon = { onMarkAllWon(bwg) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun StatsGrid(stats: BetStatsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                title = "Lucro Total",
                value = "${String.format(Locale.getDefault(), "%.2f", stats.totalProfit)} €",
                modifier = Modifier.weight(1f),
                color = if (stats.totalProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            StatCard(
                title = "Win Rate",
                value = "${String.format(Locale.getDefault(), "%.1f", stats.hitRatePercentage)} %",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                title = "Investido Real",
                value = "${String.format(Locale.getDefault(), "%.2f", stats.totalInvested)} €",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Lucro FreeBets",
                value = "${String.format(Locale.getDefault(), "%.2f", stats.freeBetProfit)} €",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
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

@Composable
fun ProfitLineChart(bets: List<BetWithGames>) {
    val profitHistory = remember(bets) {
        var currentProfit = 0.0
        val history = mutableListOf(0.0)

        val recentBets = bets.filter { it.bet.isFinished }.take(50).reversed()

        recentBets.forEach { betWithGames ->
            val bet = betWithGames.bet
            if (!bet.isFreeBet) currentProfit -= bet.stake
            if (bet.globalResult == ResultState.WON) {
                val totalOdd = betWithGames.games.fold(1.0) { acc, g -> acc * (if (g.result == ResultState.VOID) 1.0 else g.odd) }
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
                    val previousPoint = Offset(0f, size.height - ((profitHistory[0] - minValue) * heightRatio).toFloat())
                    path.moveTo(previousPoint.x, previousPoint.y)

                    for (i in 1 until profitHistory.size) {
                        val x = i * widthPerPoint
                        val y = size.height - ((profitHistory[i] - minValue) * heightRatio).toFloat()
                        path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

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

@Composable
fun BetCard(
    bwg: BetWithGames,
    onUpdateGameResult: (GameEntity, ResultState) -> Unit,
    onMarkAllWon: () -> Unit
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

    Card(
        Modifier.fillMaxWidth(),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(color))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(if (bwg.bet.isFreeBet) "Free Bet" else "Dinheiro Real", fontWeight = FontWeight.Bold)
                    Text("Retorno: ${String.format(Locale.getDefault(), "%.2f", bwg.bet.stake * totalOdd)}€", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Odd: ${String.format(Locale.getDefault(), "%.2f", totalOdd)}", Modifier.padding(end = 8.dp))

                    // USAR O BOTÃO DE CHECK NORMAL PARA O "TUDO GANHO"
                    IconButton(onClick = onMarkAllWon) {
                        Icon(Icons.Default.CheckCircle, "Tudo Ganho", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            bwg.games.forEach { g ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.teams, style = MaterialTheme.typography.bodySmall)
                        Text("Odd original: ${g.odd}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    Row {
                        // BOTÃO GANHO (Visto)
                        IconButton(onClick = { onUpdateGameResult(g, ResultState.WON) }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Ganho",
                                tint = if (g.result == ResultState.WON) Color.Green else Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                        // BOTÃO PERDIDO (Cruz)
                        IconButton(onClick = { onUpdateGameResult(g, ResultState.LOST) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Perdido",
                                tint = if (g.result == ResultState.LOST) Color.Red else Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                        // BOTÃO ADIADO (=)
                        IconButton(onClick = { onUpdateGameResult(g, ResultState.VOID) }) {
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