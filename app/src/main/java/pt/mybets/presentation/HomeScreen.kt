package pt.mybets.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    onNavigateToManageData: () -> Unit = {},
    onExportBackup: (android.net.Uri) -> Unit = {},
    onImportBackup: (android.net.Uri) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { onExportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
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
                                text = { Text("Gerir Equipas e Mercados") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToManageData()
                                }
                            )
                            HorizontalDivider()
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
                                    importLauncher.launch("*/*")
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Lucro Total",
                value = "${String.format(Locale.getDefault(), "%.2f", stats.totalProfit)} €",
                modifier = Modifier.weight(1f),
                indicatorColor = if (stats.totalProfit >= 0) Color(0xFF00E676) else Color(0xFFD32F2F),
                textColor = if (stats.totalProfit >= 0) Color(0xFF00E676) else Color(0xFFD32F2F)
            )
            StatCard(
                title = "Win Rate",
                value = "${String.format(Locale.getDefault(), "%.1f", stats.hitRatePercentage)} %",
                modifier = Modifier.weight(1f),
                indicatorColor = Color(0xFF00E676)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Investido Real",
                value = "${String.format(Locale.getDefault(), "%.2f", stats.totalInvested)} €",
                modifier = Modifier.weight(1f),
                indicatorColor = Color.Gray
            )
            StatCard(
                title = "Lucro FreeBets",
                value = "${String.format(Locale.getDefault(), "%.2f", stats.freeBetProfit)} €",
                modifier = Modifier.weight(1f),
                indicatorColor = Color(0xFF2196F3)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = Color.White
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF37474F))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(
                        color = indicatorColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )
            }
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
            } else if (bet.globalResult == ResultState.VOID) {
                if (!bet.isFreeBet) currentProfit += bet.stake
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
            modifier = Modifier.fillMaxWidth().height(220.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF37474F))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Evolução da Banca", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))

                val lineColor = MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp)) {
                    val widthPerPoint = size.width / (chartData.history.size - 1)
                    val heightRatio = size.height / chartData.range

                    val path = Path()
                    val startY = size.height - ((chartData.history[0] - chartData.minValue) * heightRatio).toFloat()
                    path.moveTo(0f, startY)

                    for (i in 1 until chartData.history.size) {
                        val x = i * widthPerPoint
                        val y = size.height - ((chartData.history[i] - chartData.minValue) * heightRatio).toFloat()
                        path.lineTo(x, y)
                    }

                    // 1. Desenhar o preenchimento translúcido com gradiente vertical
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )

                    // 2. Desenhar a linha de contorno principal
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 3. Linha de referência no zero
                    if (chartData.maxValue > 0 && chartData.minValue < 0) {
                        val zeroY = size.height - ((0.0 - chartData.minValue) * heightRatio).toFloat()
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
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