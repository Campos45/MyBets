package pt.mybets.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.domain.model.ResultState
import pt.mybets.presentation.viewmodel.BetViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExplorerScreen(
    viewModel: BetViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val teamSuggestions by viewModel.teamSuggestions.collectAsState()
    val competitionSuggestions by viewModel.competitionSuggestions.collectAsState()

    // Estados dos Filtros
    var selectedTeam by remember { mutableStateOf("Todas") }
    var selectedCompetition by remember { mutableStateOf("Todas") }
    var selectedModality by remember { mutableStateOf("Todas") }
    var selectedLiveMode by remember { mutableStateOf("Todas") } // "Todas", "Live", "Pré-Jogo"

    // Estados de expansão dos Dropdowns
    var teamExpanded by remember { mutableStateOf(false) }
    var compExpanded by remember { mutableStateOf(false) }
    var modExpanded by remember { mutableStateOf(false) }
    var liveExpanded by remember { mutableStateOf(false) }

    // Dicionário de modalidades para traduzir IDs
    val modalityMap = remember(uiState.modalities) {
        uiState.modalities.associate { it.id to it.name }
    }

    val finishedBets = remember(uiState.bets) {
        uiState.bets.filter { it.bet.globalResult != ResultState.PENDING }
    }
    val allFinishedGamesWithBet = remember(finishedBets) {
        finishedBets.flatMap { bwg -> bwg.games.map { it to bwg.bet } }
    }

    // Opções de equipas dinâmicas (filtradas por competição, modalidade, live)
    val availableTeams = remember(
        allFinishedGamesWithBet, selectedCompetition, selectedModality, selectedLiveMode, modalityMap
    ) {
        val filtered = allFinishedGamesWithBet.filter { (game, bet) ->
            val compMatch = if (selectedCompetition == "Todas") true else game.competition.equals(selectedCompetition, ignoreCase = true)
            val modName = modalityMap[game.modalityId] ?: "Desconhecida"
            val modMatch = if (selectedModality == "Todas") true else modName.equals(selectedModality, ignoreCase = true)
            val liveMatch = when (selectedLiveMode) {
                "Live" -> game.isLive
                "Pré-Jogo" -> !game.isLive
                else -> true
            }
            compMatch && modMatch && liveMatch
        }
        filtered.flatMap { (game, _) -> game.teams.split(" vs ").map { it.trim() } }
            .distinct()
            .sorted()
    }

    // Opções de competições dinâmicas (filtradas por equipa, modalidade, live)
    val availableCompetitions = remember(
        allFinishedGamesWithBet, selectedTeam, selectedModality, selectedLiveMode, modalityMap
    ) {
        val filtered = allFinishedGamesWithBet.filter { (game, bet) ->
            val teamMatch = if (selectedTeam == "Todas") true else game.teams.split(" vs ").any { it.trim().equals(selectedTeam, ignoreCase = true) }
            val modName = modalityMap[game.modalityId] ?: "Desconhecida"
            val modMatch = if (selectedModality == "Todas") true else modName.equals(selectedModality, ignoreCase = true)
            val liveMatch = when (selectedLiveMode) {
                "Live" -> game.isLive
                "Pré-Jogo" -> !game.isLive
                else -> true
            }
            teamMatch && modMatch && liveMatch
        }
        filtered.map { (game, _) -> game.competition.trim() }
            .distinct()
            .sorted()
    }

    // Opções de mercados dinâmicas (filtradas por equipa, competição, live)
    val availableModalities = remember(
        allFinishedGamesWithBet, selectedTeam, selectedCompetition, selectedLiveMode, modalityMap
    ) {
        val filtered = allFinishedGamesWithBet.filter { (game, bet) ->
            val teamMatch = if (selectedTeam == "Todas") true else game.teams.split(" vs ").any { it.trim().equals(selectedTeam, ignoreCase = true) }
            val compMatch = if (selectedCompetition == "Todas") true else game.competition.equals(selectedCompetition, ignoreCase = true)
            val liveMatch = when (selectedLiveMode) {
                "Live" -> game.isLive
                "Pré-Jogo" -> !game.isLive
                else -> true
            }
            teamMatch && compMatch && liveMatch
        }
        filtered.map { (game, _) -> modalityMap[game.modalityId] ?: "Desconhecida" }
            .distinct()
            .sorted()
    }

    // Opções de live/pre-match dinâmicas (filtradas por equipa, competição, mercado)
    val availableLiveModes = remember(
        allFinishedGamesWithBet, selectedTeam, selectedCompetition, selectedModality, modalityMap
    ) {
        val filtered = allFinishedGamesWithBet.filter { (game, bet) ->
            val teamMatch = if (selectedTeam == "Todas") true else game.teams.split(" vs ").any { it.trim().equals(selectedTeam, ignoreCase = true) }
            val compMatch = if (selectedCompetition == "Todas") true else game.competition.equals(selectedCompetition, ignoreCase = true)
            val modName = modalityMap[game.modalityId] ?: "Desconhecida"
            val modMatch = if (selectedModality == "Todas") true else modName.equals(selectedModality, ignoreCase = true)
            teamMatch && compMatch && modMatch
        }
        val hasLive = filtered.any { it.first.isLive }
        val hasPre = filtered.any { !it.first.isLive }
        buildList {
            add("Todas")
            if (hasLive) add("Live")
            if (hasPre) add("Pré-Jogo")
        }
    }

    // Filtragem local dos jogos resolvidos correspondentes com base em todos os filtros ativos
    val matchedGames = remember(
        allFinishedGamesWithBet, selectedTeam, selectedCompetition, selectedModality, selectedLiveMode, modalityMap
    ) {
        allFinishedGamesWithBet.filter { (game, bet) ->
            // Filtro Equipa
            val teamMatch = if (selectedTeam == "Todas") {
                true
            } else {
                game.teams.split(" vs ").any { it.trim().equals(selectedTeam, ignoreCase = true) }
            }

            // Filtro Competição
            val compMatch = if (selectedCompetition == "Todas") {
                true
            } else {
                game.competition.equals(selectedCompetition, ignoreCase = true)
            }

            // Filtro Mercado
            val modName = modalityMap[game.modalityId] ?: "Desconhecida"
            val modMatch = if (selectedModality == "Todas") {
                true
            } else {
                modName.equals(selectedModality, ignoreCase = true)
            }

            // Filtro Live/Pre-match
            val liveMatch = when (selectedLiveMode) {
                "Live" -> game.isLive
                "Pré-Jogo" -> !game.isLive
                else -> true
            }

            teamMatch && compMatch && modMatch && liveMatch
        }
    }

    // Métricas calculadas para os jogos filtrados
    val totalInvested = matchedGames.sumOf { if (!it.second.isFreeBet) it.second.stake else 0.0 }
    val totalReturn = matchedGames.sumOf { (game, bet) ->
        if (game.result == ResultState.WON) {
            bet.stake * (if (game.result == ResultState.VOID) 1.0 else game.odd)
        } else if (game.result == ResultState.VOID) {
            bet.stake
        } else {
            0.0
        }
    }
    val profit = totalReturn - totalInvested
    val roi = if (totalInvested > 0) (profit / totalInvested) * 100 else 0.0

    val wonCount = matchedGames.count { it.first.result == ResultState.WON }
    val lostCount = matchedGames.count { it.first.result == ResultState.LOST }
    val voidCount = matchedGames.count { it.first.result == ResultState.VOID }
    val totalCount = matchedGames.size

    val winRate = if (totalCount > 0) (wonCount.toDouble() / totalCount) * 100 else 0.0

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Explorador de Dados") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Ecrã de Seletores em LazyColumn para rolagem confortável
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // SELETORES
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Cruzamento de Variáveis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = {
                                        selectedTeam = "Todas"
                                        selectedCompetition = "Todas"
                                        selectedModality = "Todas"
                                        selectedLiveMode = "Todas"
                                    }
                                ) {
                                    Text("Limpar Filtros")
                                }
                            }

                            // 1. Dropdown EQUIPA
                            FilterSelector(
                                label = "Equipa",
                                selected = selectedTeam,
                                expanded = teamExpanded,
                                onExpandedChange = { teamExpanded = it },
                                options = listOf("Todas") + availableTeams,
                                onSelect = { selectedTeam = it }
                            )

                            // 2. Dropdown COMPETIÇÃO
                            FilterSelector(
                                label = "Competição",
                                selected = selectedCompetition,
                                expanded = compExpanded,
                                onExpandedChange = { compExpanded = it },
                                options = listOf("Todas") + availableCompetitions,
                                onSelect = { selectedCompetition = it }
                            )

                            // 3. Dropdown MERCADO
                            FilterSelector(
                                label = "Mercado",
                                selected = selectedModality,
                                expanded = modExpanded,
                                onExpandedChange = { modExpanded = it },
                                options = listOf("Todas") + availableModalities,
                                onSelect = { selectedModality = it }
                            )

                            // 4. Dropdown LIVE
                            FilterSelector(
                                label = "Tipo de Entrada",
                                selected = selectedLiveMode,
                                expanded = liveExpanded,
                                onExpandedChange = { liveExpanded = it },
                                options = availableLiveModes,
                                onSelect = { selectedLiveMode = it }
                            )
                        }
                    }
                }

                // PAINEL DE RESULTADOS
                if (totalCount == 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum registo encontrado com estes critérios.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    // MÉTRICAS
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Estatísticas da Combinação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(
                                    title = "Lucro",
                                    value = "${String.format(Locale.getDefault(), "%.2f", profit)}€",
                                    color = if (profit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "ROI",
                                    value = "${String.format(Locale.getDefault(), "%.1f", roi)}%",
                                    color = if (roi >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(
                                    title = "Win Rate",
                                    value = "${String.format(Locale.getDefault(), "%.1f", winRate)}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Total Jogos",
                                    value = "$totalCount",
                                    color = Color.DarkGray,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // GRÁFICO DONUT CHART
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                DonutChart(
                                    wonCount = wonCount,
                                    lostCount = lostCount,
                                    voidCount = voidCount,
                                    modifier = Modifier.size(120.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LegendItem(label = "Ganhas: $wonCount (${String.format(Locale.getDefault(), "%.0f", (wonCount.toFloat()/totalCount)*100)}%)", color = Color(0xFF4CAF50))
                                    LegendItem(label = "Perdidas: $lostCount (${String.format(Locale.getDefault(), "%.0f", (lostCount.toFloat()/totalCount)*100)}%)", color = Color(0xFFF44336))
                                    LegendItem(label = "Anuladas: $voidCount (${String.format(Locale.getDefault(), "%.0f", (voidCount.toFloat()/totalCount)*100)}%)", color = Color(0xFF2196F3))
                                }
                            }
                        }
                    }

                    // JOGOS ENCONTRADOS
                    item {
                        Text("Jogos Correspondentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    items(matchedGames) { (game, bet) ->
                        val modName = modalityMap[game.modalityId] ?: "Desconhecida"
                        val resultColor = when (game.result) {
                            ResultState.WON -> Color(0xFF4CAF50)
                            ResultState.LOST -> Color(0xFFF44336)
                            ResultState.VOID -> Color(0xFF2196F3)
                            else -> Color.Gray
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(resultColor))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(game.teams, fontWeight = FontWeight.SemiBold)
                                        if (game.isLive) {
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
                                    Text(
                                        text = when (game.result) {
                                            ResultState.WON -> "Ganhou"
                                            ResultState.LOST -> "Perdeu"
                                            ResultState.VOID -> "Anulado"
                                            else -> "Pendente"
                                        },
                                        color = resultColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Comp: ${game.competition}  |  Mercado: $modName  |  Odd: ${game.odd}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text("Investimento na Aposta: ${String.format(Locale.getDefault(), "%.2f", bet.stake)}€", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterSelector(
    label: String,
    selected: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(true) }
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Abrir")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.distinct().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun DonutChart(
    wonCount: Int,
    lostCount: Int,
    voidCount: Int,
    modifier: Modifier = Modifier
) {
    val total = (wonCount + lostCount + voidCount).toFloat()
    if (total == 0f) return

    val sweepWon = (wonCount / total) * 360f
    val sweepLost = (lostCount / total) * 360f
    val sweepVoid = (voidCount / total) * 360f

    Canvas(modifier = modifier) {
        val strokeWidth = 14.dp.toPx()
        val size = size.minDimension - strokeWidth
        val topLeft = androidx.compose.ui.geometry.Offset(
            (this.size.width - size) / 2,
            (this.size.height - size) / 2
        )
        val arcSize = androidx.compose.ui.geometry.Size(size, size)

        var startAngle = -90f

        if (sweepWon > 0f) {
            drawArc(
                color = Color(0xFF4CAF50),
                startAngle = startAngle,
                sweepAngle = sweepWon,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweepWon
        }

        if (sweepLost > 0f) {
            drawArc(
                color = Color(0xFFF44336),
                startAngle = startAngle,
                sweepAngle = sweepLost,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweepLost
        }

        if (sweepVoid > 0f) {
            drawArc(
                color = Color(0xFF2196F3),
                startAngle = startAngle,
                sweepAngle = sweepVoid,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clickable(enabled = false) {}
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = color)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
