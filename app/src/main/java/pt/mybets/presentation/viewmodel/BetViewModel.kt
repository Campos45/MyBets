package pt.mybets.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pt.mybets.data.local.entity.BetEntity
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.entity.ModalityEntity
import pt.mybets.data.local.relations.BetWithGames
import pt.mybets.domain.model.ResultState
import pt.mybets.domain.repository.BetRepository
import java.util.UUID
import java.util.Locale

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import pt.mybets.domain.model.BackupData

import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



// Estado para a UI preencher
data class GameInputState(
    val id: String = UUID.randomUUID().toString(),
    val homeTeam: String = "",
    val awayTeam: String = "",
    val competition: String = "",
    val modalityName: String = "", // Agora é texto livre!
    val odd: String = "",
    val isLive: Boolean = false
)

class BetViewModel(
    private val repository: BetRepository
) : ViewModel() {

    init {
        // Seeder automático desativado para garantir base de dados limpa na instalação
        /*
        viewModelScope.launch {
            if (repository.getAllBetsWithGames().first().isEmpty()) {
                seedDatabaseWithTestData()
            }
        }
        */
    }

    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<MainUiState> = combine(
        repository.getAllBetsWithGames(),
        repository.getAllModalities(),
        _isLoading
    ) { bets, modalities, isLoading ->
        // CORREÇÃO: Passar o cálculo pesado para uma Thread de fundo (Default)
        withContext(Dispatchers.Default) {
            MainUiState(
                bets = bets,
                stats = calculateStats(bets),
                detailedStats = calculateDetailedStats(bets, modalities),
                modalities = modalities,
                isLoading = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    val modalities: StateFlow<List<ModalityEntity>> = repository.getAllModalities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val competitionSuggestions: StateFlow<List<String>> = repository.getAllUniqueCompetitions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // LÓGICA MÁGICA: Pega nos jogos (ex: "Benfica vs Porto"), corta o texto a meio e sugere as equipas individuais!
    val teamSuggestions: StateFlow<List<String>> = repository.getAllUniqueTeams()
        .map { teamStrings ->
            val uniqueTeams = mutableSetOf<String>()
            teamStrings.forEach { combinedStr ->
                val parts = combinedStr.split(" vs ")
                parts.forEach { if (it.isNotBlank()) uniqueTeams.add(it.trim()) }
            }
            uniqueTeams.toList().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    fun markAllGamesAsWon(betWithGames: BetWithGames) {
        viewModelScope.launch {
            betWithGames.games.forEach { game ->
                repository.updateGame(game.copy(result = ResultState.WON))
            }
            // Atualiza a aposta global como ganha
            repository.updateBetStatus(
                betWithGames.bet.copy(globalResult = ResultState.WON, isFinished = true)
            )
        }
    }

    // Função para atualizar o resultado de um jogo específico
    fun updateGameResult(betWithGames: BetWithGames, game: GameEntity, clickedResult: ResultState) {
        viewModelScope.launch {
            val newResult = if (game.result == clickedResult) ResultState.PENDING else clickedResult
            val updatedGame = game.copy(result = newResult)
            repository.updateGame(updatedGame)

            val allGames = betWithGames.games.map { if (it.id == game.id) updatedGame else it }

            val hasLost = allGames.any { it.result == ResultState.LOST }
            val hasPending = allGames.any { it.result == ResultState.PENDING }

            // LÓGICA CORRIGIDA PARA O JOGO ADIADO
            val newBetResult = when {
                hasLost -> ResultState.LOST
                !hasPending -> ResultState.WON // Se não há perdas nem pendentes (tudo WON ou VOID), a aposta é ganha!
                else -> ResultState.PENDING
            }

            // A aposta também passa a não estar concluída se newBetResult for PENDING
            val isFinished = newBetResult != ResultState.PENDING
            repository.updateBetStatus(
                betWithGames.bet.copy(
                    globalResult = newBetResult,
                    isFinished = isFinished
                )
            )
        }
    }

    fun deleteBet(bet: BetEntity) {
        viewModelScope.launch {
            repository.deleteBet(bet)
        }
    }

    fun saveBet(stake: Double, isFreeBet: Boolean, gameInputs: List<GameInputState>) {
        viewModelScope.launch {
            val bet = BetEntity(stake = stake, isFreeBet = isFreeBet)

            // Vai buscar as modalidades atuais (para não criar duplicados)
            val currentModalities = repository.getAllModalities().first().toMutableList()

            val gameEntities = gameInputs.map { input ->
                val modName = input.modalityName.trim()

                // 1. Procura se a modalidade já existe (ignorando maiúsculas/minúsculas)
                var modality = currentModalities.find { it.name.equals(modName, ignoreCase = true) }

                // 2. Se não existir, insere agora e guarda na lista local (caso o próximo jogo da mesma aposta use a mesma)
                val modalityId = if (modality != null) {
                    modality.id
                } else {
                    val newId = repository.insertModality(ModalityEntity(name = modName)).toInt()
                    currentModalities.add(ModalityEntity(id = newId, name = modName))
                    newId
                }

                // 3. Junta a equipa da casa e visitante num só texto para a Base de Dados
                GameEntity(
                    betId = 0,
                    teams = "${input.homeTeam.trim()} vs ${input.awayTeam.trim()}",
                    competition = input.competition.trim(),
                    modalityId = modalityId,
                    odd = input.odd.toDoubleOrNull() ?: 1.0,
                    isLive = input.isLive
                )
            }

            repository.insertBetWithGames(bet, gameEntities)
        }
    }

    private fun calculateStats(bets: List<BetWithGames>): BetStatsState {
        var totalProfit = 0.0
        var totalInvested = 0.0
        var freeBetProfit = 0.0
        var finishedCount = 0
        var wonCount = 0

        bets.forEach { bwg ->
            val b = bwg.bet

            if (b.globalResult != ResultState.PENDING) {
                finishedCount++
                if (b.globalResult == ResultState.WON) wonCount++
            }

            if (!b.isFreeBet) {
                totalInvested += b.stake
                totalProfit -= b.stake
            }

            if (b.globalResult == ResultState.WON) {
                // CORREÇÃO: Odd passa a 1.0 se o jogo for VOID
                val odd = bwg.games.fold(1.0) { acc, g -> acc * (if (g.result == ResultState.VOID) 1.0 else g.odd) }
                val ret = b.stake * odd
                if (b.isFreeBet) freeBetProfit += ret
                totalProfit += ret
            }
        }
        val rate = if (finishedCount > 0) (wonCount.toDouble() / finishedCount) * 100 else 0.0
        return BetStatsState(totalProfit, totalInvested, freeBetProfit, rate)
    }

private data class GameOddDetail(
    val game: GameEntity,
    val odd: Double,
    val stake: Double,
    val isFreeBet: Boolean,
    val isWon: Boolean
)

    private fun calculateDetailedStats(bets: List<BetWithGames>, modalities: List<ModalityEntity>): DetailedStatsState {
        val finishedBets = bets.filter { it.bet.globalResult != ResultState.PENDING }
        if (finishedBets.isEmpty()) return DetailedStatsState()

        val allFinishedGames = finishedBets.flatMap { it.games }

        val compStats = allFinishedGames.groupBy { it.competition }.map { (comp, games) ->
            val wins = games.count { it.result == ResultState.WON }
            CompetitionStat(comp, (wins.toDouble() / games.size) * 100, games.size)
        }.sortedByDescending { it.total }

        val modalityMap = modalities.associate { it.id to it.name }
        val modStats = allFinishedGames.groupBy { it.modalityId }.map { (modId, games) ->
            val modName = modalityMap[modId] ?: "Desconhecida"
            val wins = games.count { it.result == ResultState.WON }
            ModalityStat(modName, (wins.toDouble() / games.size) * 100, games.size)
        }.sortedByDescending { it.total }

        // Planificar todos os jogos finalizados com os detalhes de odd e aposta pai
        val finishedGamesWithDetails = finishedBets.flatMap { bwg ->
            bwg.games.map { g ->
                val gameOdd = if (g.result == ResultState.VOID) 1.0 else g.odd
                val isWon = g.result == ResultState.WON
                GameOddDetail(
                    game = g,
                    odd = gameOdd,
                    stake = bwg.bet.stake,
                    isFreeBet = bwg.bet.isFreeBet,
                    isWon = isWon
                )
            }
        }

        val ranges = listOf("1.0-1.5", "1.5-2.0", "2.0-3.0", "3.0+")
        val oddRangeStats = ranges.map { range ->
            val filtered = finishedGamesWithDetails.filter { detail ->
                when (range) {
                    "1.0-1.5" -> detail.odd in 1.0..1.5
                    "1.5-2.0" -> detail.odd > 1.5 && detail.odd <= 2.0
                    "2.0-3.0" -> detail.odd > 2.0 && detail.odd <= 3.0
                    else -> detail.odd > 3.0
                }
            }

            var totalStake = 0.0
            var totalReturn = 0.0
            filtered.forEach { detail ->
                if (!detail.isFreeBet) totalStake += detail.stake
                if (detail.isWon) {
                    totalReturn += detail.stake * detail.odd
                }
            }

            val roi = if (totalStake > 0) ((totalReturn - totalStake) / totalStake) * 100 else 0.0
            OddRangeStat(range, roi, filtered.size)
        }

        // --- GERAÇÃO AUTOMÁTICA DE INSIGHTS ---
        val insights = mutableListOf<BetInsight>()

        // 1. Mercados Live
        finishedGamesWithDetails.groupBy { Pair(it.game.modalityId, it.game.isLive) }.forEach { (key, list) ->
            val (modId, isLive) = key
            if (isLive && list.size >= 3) {
                val modName = modalityMap[modId] ?: "Desconhecida"
                val wins = list.count { it.isWon }
                val rate = (wins.toDouble() / list.size) * 100
                if (rate >= 70.0) {
                    insights.add(BetInsight(
                        title = "🔥 Ponto Forte: Mercado $modName (Live)",
                        description = "Tens uma taxa de acerto excelente de ${String.format(Locale.getDefault(), "%.1f", rate)}% em apostas ao vivo neste mercado (${list.size} apostas).",
                        type = InsightType.POSITIVE,
                        winRate = rate,
                        totalBets = list.size
                    ))
                } else if (rate <= 40.0) {
                    insights.add(BetInsight(
                        title = "⚠️ Alerta: Mercado $modName (Live)",
                        description = "Cuidado com as apostas ao vivo neste mercado. A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                        type = InsightType.WARNING,
                        winRate = rate,
                        totalBets = list.size
                    ))
                }
            }
        }

        // 2. Competições Live
        finishedGamesWithDetails.groupBy { Pair(it.game.competition, it.game.isLive) }.forEach { (key, list) ->
            val (competition, isLive) = key
            if (isLive && list.size >= 3 && competition.isNotEmpty()) {
                val wins = list.count { it.isWon }
                val rate = (wins.toDouble() / list.size) * 100
                if (rate >= 75.0) {
                    insights.add(BetInsight(
                        title = "⚽ Domínio em Live: $competition",
                        description = "Estás a ter um excelente aproveitamento na competição $competition em direto, com ${String.format(Locale.getDefault(), "%.1f", rate)}% de taxa de acerto (${list.size} apostas).",
                        type = InsightType.POSITIVE,
                        winRate = rate,
                        totalBets = list.size
                    ))
                } else if (rate <= 35.0) {
                    insights.add(BetInsight(
                        title = "🚨 Dificuldade em Live: $competition",
                        description = "A tua performance ao vivo na competição $competition precisa de atenção. Taxa de acerto de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                        type = InsightType.WARNING,
                        winRate = rate,
                        totalBets = list.size
                    ))
                }
            }
        }

        // 3. Equipas individuais em Live
        val teamLiveList = finishedGamesWithDetails.flatMap { detail ->
            val teamNames = detail.game.teams.split(" vs ")
            teamNames.map { name -> Pair(name.trim(), detail) }
        }.filter { it.first.isNotEmpty() }

        teamLiveList.groupBy { Pair(it.first, it.second.game.isLive) }.forEach { (key, list) ->
            val (teamName, isLive) = key
            if (isLive && list.size >= 3) {
                val wins = list.count { it.second.isWon }
                val rate = (wins.toDouble() / list.size) * 100
                if (rate >= 75.0) {
                    insights.add(BetInsight(
                        title = "💪 Padrão de Lucro: $teamName (Live)",
                        description = "Tens uma fantástica taxa de acerto de ${String.format(Locale.getDefault(), "%.1f", rate)}% ao apostar em direto com o $teamName envolvido (${list.size} apostas).",
                        type = InsightType.POSITIVE,
                        winRate = rate,
                        totalBets = list.size
                    ))
                } else if (rate <= 35.0) {
                    insights.add(BetInsight(
                        title = "📉 Risco Elevado: $teamName (Live)",
                        description = "Tens tido fraco desempenho em apostas ao vivo com o $teamName. A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                        type = InsightType.WARNING,
                        winRate = rate,
                        totalBets = list.size
                    ))
                }
            }
        }

        // 4. Odds em Live (faixas)
        val rangesList = listOf("1.0-1.5", "1.5-2.0", "2.0-3.0", "3.0+")
        rangesList.forEach { range ->
            val list = finishedGamesWithDetails.filter { detail ->
                val matchRange = when (range) {
                    "1.0-1.5" -> detail.odd in 1.0..1.5
                    "1.5-2.0" -> detail.odd > 1.5 && detail.odd <= 2.0
                    "2.0-3.0" -> detail.odd > 2.0 && detail.odd <= 3.0
                    else -> detail.odd > 3.0
                }
                matchRange && detail.game.isLive
            }
            if (list.size >= 3) {
                val wins = list.count { it.isWon }
                val rate = (wins.toDouble() / list.size) * 100
                if (rate >= 70.0) {
                    insights.add(BetInsight(
                        title = "📈 Odds Favoráveis (Live): Faixa $range",
                        description = "As tuas apostas em direto com odds na faixa de $range têm trazido excelente retorno, com taxa de acerto de ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                        type = InsightType.INFO,
                        winRate = rate,
                        totalBets = list.size
                    ))
                } else if (rate <= 35.0) {
                    insights.add(BetInsight(
                        title = "⚠️ Odds Perigosas (Live): Faixa $range",
                        description = "Aposta com cautela ao vivo nesta faixa de odd ($range). A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                        type = InsightType.WARNING,
                        winRate = rate,
                        totalBets = list.size
                    ))
                }
            }
        }

        return DetailedStatsState(compStats, modStats, oddRangeStats, insights)
    }

    // --- EXPORTAÇÃO E IMPORTAÇÃO JSON ---
    fun exportDatabase(context: Context, uri: Uri) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val backup = repository.getBackupData()
                    val jsonString = Gson().toJson(backup)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(jsonString)
                        }
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            if (success) {
                Toast.makeText(context, "Backup exportado com sucesso!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Erro ao exportar backup.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun importDatabase(context: Context, uri: Uri) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        InputStreamReader(inputStream).use { reader ->
                            val backup = Gson().fromJson(reader, BackupData::class.java)
                            if (backup != null) {
                                repository.restoreBackupData(backup)
                                true
                            } else {
                                false
                            }
                        }
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            if (success) {
                Toast.makeText(context, "Backup importado com sucesso!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Ficheiro inválido ou corrompido.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun seedDatabaseWithTestData() {
        val modality1 = repository.insertModality(ModalityEntity(name = "1X2"))
        val modality2 = repository.insertModality(ModalityEntity(name = "Over 2.5"))
        val modality3 = repository.insertModality(ModalityEntity(name = "Ambas Marcam"))
        val modality4 = repository.insertModality(ModalityEntity(name = "Vencedor"))

        repository.insertBetWithGames(
            bet = BetEntity(stake = 10.0, isFreeBet = false, isFinished = true, globalResult = ResultState.WON),
            games = listOf(
                GameEntity(betId = 0, teams = "Benfica vs Porto", competition = "Primeira Liga", modalityId = modality2.toInt(), odd = 1.85, result = ResultState.WON, isLive = true)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 5.0, isFreeBet = true, isFinished = true, globalResult = ResultState.WON),
            games = listOf(
                GameEntity(betId = 0, teams = "Sporting vs Braga", competition = "Primeira Liga", modalityId = modality3.toInt(), odd = 1.70, result = ResultState.WON, isLive = true)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 20.0, isFreeBet = false, isFinished = true, globalResult = ResultState.WON),
            games = listOf(
                GameEntity(betId = 0, teams = "Real Madrid vs Barcelona", competition = "La Liga", modalityId = modality1.toInt(), odd = 2.10, result = ResultState.WON, isLive = false)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 15.0, isFreeBet = false, isFinished = true, globalResult = ResultState.LOST),
            games = listOf(
                GameEntity(betId = 0, teams = "Lakers vs Celtics", competition = "NBA", modalityId = modality4.toInt(), odd = 1.90, result = ResultState.LOST, isLive = true)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 10.0, isFreeBet = false, isFinished = true, globalResult = ResultState.LOST),
            games = listOf(
                GameEntity(betId = 0, teams = "Man City vs Liverpool", competition = "Premier League", modalityId = modality2.toInt(), odd = 1.65, result = ResultState.LOST, isLive = true)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 10.0, isFreeBet = false, isFinished = true, globalResult = ResultState.WON),
            games = listOf(
                GameEntity(betId = 0, teams = "Benfica vs Guimaraes", competition = "Primeira Liga", modalityId = modality2.toInt(), odd = 1.55, result = ResultState.WON, isLive = true),
                GameEntity(betId = 0, teams = "Porto vs Boavista", competition = "Primeira Liga", modalityId = modality1.toInt(), odd = 1.30, result = ResultState.WON, isLive = true)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 5.0, isFreeBet = false, isFinished = true, globalResult = ResultState.WON),
            games = listOf(
                GameEntity(betId = 0, teams = "Arsenal vs Chelsea", competition = "Premier League", modalityId = modality3.toInt(), odd = 1.80, result = ResultState.WON, isLive = false),
                GameEntity(betId = 0, teams = "Bayern vs Dortmund", competition = "Bundesliga", modalityId = modality2.toInt(), odd = 1.40, result = ResultState.WON, isLive = false)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 10.0, isFreeBet = false, isFinished = false, globalResult = ResultState.PENDING),
            games = listOf(
                GameEntity(betId = 0, teams = "PSG vs Marseille", competition = "Ligue 1", modalityId = modality1.toInt(), odd = 1.50, result = ResultState.PENDING, isLive = false),
                GameEntity(betId = 0, teams = "Juventus vs Inter", competition = "Serie A", modalityId = modality3.toInt(), odd = 1.90, result = ResultState.PENDING, isLive = false)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 10.0, isFreeBet = false, isFinished = true, globalResult = ResultState.WON),
            games = listOf(
                GameEntity(betId = 0, teams = "Porto vs Gil Vicente", competition = "Primeira Liga", modalityId = modality2.toInt(), odd = 1.60, result = ResultState.WON, isLive = true)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 10.0, isFreeBet = false, isFinished = true, globalResult = ResultState.WON),
            games = listOf(
                GameEntity(betId = 0, teams = "Benfica vs Arouca", competition = "Primeira Liga", modalityId = modality2.toInt(), odd = 1.50, result = ResultState.WON, isLive = true)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 15.0, isFreeBet = false, isFinished = true, globalResult = ResultState.LOST),
            games = listOf(
                GameEntity(betId = 0, teams = "Lakers vs Warriors", competition = "NBA", modalityId = modality4.toInt(), odd = 1.85, result = ResultState.LOST, isLive = true)
            )
        )
        repository.insertBetWithGames(
            bet = BetEntity(stake = 20.0, isFreeBet = false, isFinished = true, globalResult = ResultState.LOST),
            games = listOf(
                GameEntity(betId = 0, teams = "Bulls vs Heat", competition = "NBA", modalityId = modality4.toInt(), odd = 1.75, result = ResultState.LOST, isLive = true)
            )
        )
    }
}