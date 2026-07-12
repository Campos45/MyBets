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
                    Spacer(Modifier.height(80.dp))
                }
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

private data class ChartData(
    val history: List<Double>,
    val maxValue: Double,
    val minValue: Double,
    val range: Double
)

@Composable
fun ProfitLineChart(bets: List<BetWithGames>) {
    val chartData = remember(bets) {
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
        val maxValue = history.maxOrNull() ?: 1.0
        val minValue = history.minOrNull() ?: -1.0
        val range = (maxValue - minValue).takeIf { it > 0 } ?: 1.0
        ChartData(history, maxValue, minValue, range)
    }

    if (chartData.history.size > 1) {
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Evolução da Banca", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                val lineColor = MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp)) {
                    val widthPerPoint = size.width / (chartData.history.size - 1)
                    val heightRatio = size.height / chartData.range

                    val path = Path()
                    val previousPoint = Offset(0f, size.height - ((chartData.history[0] - chartData.minValue) * heightRatio).toFloat())
                    path.moveTo(previousPoint.x, previousPoint.y)

                    for (i in 1 until chartData.history.size) {
                        val x = i * widthPerPoint
                        val y = size.height - ((chartData.history[i] - chartData.minValue) * heightRatio).toFloat()
                        path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    if (chartData.maxValue > 0 && chartData.minValue < 0) {
                        val zeroY = size.height - ((0.0 - chartData.minValue) * heightRatio).toFloat()
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