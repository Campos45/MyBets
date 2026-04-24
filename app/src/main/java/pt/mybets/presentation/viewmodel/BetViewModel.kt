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




// Estado para a UI preencher
data class GameInputState(
    val id: String = UUID.randomUUID().toString(),
    val homeTeam: String = "",
    val awayTeam: String = "",
    val competition: String = "",
    val modalityName: String = "", // Agora é texto livre!
    val odd: String = ""
)

class BetViewModel(
    private val repository: BetRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<MainUiState> = combine(
        repository.getAllBetsWithGames(),
        _isLoading
    ) { bets, isLoading ->
        MainUiState(bets = bets, stats = calculateStats(bets), isLoading = false)
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


    // Função para atualizar o resultado de um jogo específico
    fun updateGameResult(betWithGames: BetWithGames, game: GameEntity, newResult: ResultState) {
        viewModelScope.launch {
            // 1. Atualiza o jogo específico na Base de Dados
            val updatedGame = game.copy(result = newResult)
            repository.updateGame(updatedGame)

            // 2. Analisa o impacto na Aposta Global
            // Substituímos o jogo antigo pelo atualizado na nossa lista em memória para validar
            val allGames = betWithGames.games.map { if (it.id == game.id) updatedGame else it }

            val hasLost = allGames.any { it.result == ResultState.LOST }
            val allWon = allGames.all { it.result == ResultState.WON }

            val newBetResult = when {
                hasLost -> ResultState.LOST
                allWon -> ResultState.WON
                else -> ResultState.PENDING
            }

            // Só atualiza a aposta global se o resultado for diferente do atual
            if (betWithGames.bet.globalResult != newBetResult) {
                val isFinished = newBetResult != ResultState.PENDING
                repository.updateBetStatus(
                    betWithGames.bet.copy(
                        globalResult = newBetResult,
                        isFinished = isFinished
                    )
                )
            }
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
                    odd = input.odd.toDoubleOrNull() ?: 1.0
                )
            }

            repository.insertBetWithGames(bet, gameEntities)
        }
    }

    private fun calculateStats(bets: List<BetWithGames>): BetStatsState {
        var totalProfit = 0.0
        var totalInvestedRealMoney = 0.0
        var freeBetProfit = 0.0
        var totalFinishedBets = 0
        var totalWonBets = 0

        for (betWithGames in bets) {
            val bet = betWithGames.bet
            val games = betWithGames.games

            if (bet.isFinished) {
                totalFinishedBets++
                if (bet.globalResult == ResultState.WON) totalWonBets++
            }

            if (!bet.isFreeBet) {
                totalInvestedRealMoney += bet.stake
                totalProfit -= bet.stake
            }

            if (bet.globalResult == ResultState.WON) {
                val totalOdd = games.fold(1.0) { acc, game -> acc * game.odd }
                val returnAmount = bet.stake * totalOdd

                if (bet.isFreeBet) freeBetProfit += returnAmount
                totalProfit += returnAmount
            }
        }

        val hitRate = if (totalFinishedBets > 0) (totalWonBets.toDouble() / totalFinishedBets.toDouble()) * 100 else 0.0
        return BetStatsState(totalProfit, totalInvestedRealMoney, freeBetProfit, hitRate)
    }

    private fun calculateDetailedStats(bets: List<BetWithGames>, modalities: List<ModalityEntity>): DetailedStatsState {
        val finishedBets = bets.filter { it.bet.isFinished }

        // 1. Stats por Competição
        val compStats = finishedBets
            .flatMap { it.games }
            .groupBy { it.competition }
            .map { (comp, games) ->
                val wins = games.count { it.result == ResultState.WON }
                CompetitionStat(comp, (wins.toDouble() / games.size) * 100, games.size)
            }.sortedByDescending { it.total }

        // 2. Stats por Modalidade
        val modStats = finishedBets
            .flatMap { it.games }
            .groupBy { it.modalityId }
            .map { (modId, games) ->
                val modName = modalities.find { it.id == modId }?.name ?: "Unknown"
                val wins = games.count { it.result == ResultState.WON }
                ModalityStat(modName, (wins.toDouble() / games.size) * 100, games.size)
            }.sortedByDescending { it.total }

        // 3. ROI por Faixas de Odds
        val ranges = listOf("1.0-1.5", "1.5-2.0", "2.0-3.0", "3.0+")
        val oddRangeStats = ranges.map { range ->
            val filtered = finishedBets.filter { betWithGames ->
                val totalOdd = betWithGames.games.fold(1.0) { acc, g -> acc * g.odd }
                when (range) {
                    "1.0-1.5" -> totalOdd in 1.0..1.5
                    "1.5-2.0" -> totalOdd > 1.5 && totalOdd <= 2.0
                    "2.0-3.0" -> totalOdd > 2.0 && totalOdd <= 3.0
                    else -> totalOdd > 3.0
                }
            }

            var totalStake = 0.0
            var totalReturn = 0.0
            filtered.forEach {
                if (!it.bet.isFreeBet) totalStake += it.bet.stake
                if (it.bet.globalResult == ResultState.WON) {
                    val odd = it.games.fold(1.0) { acc, g -> acc * g.odd }
                    totalReturn += it.bet.stake * odd
                }
            }

            val roi = if (totalStake > 0) ((totalReturn - totalStake) / totalStake) * 100 else 0.0
            OddRangeStat(range, roi, filtered.size)
        }

        return DetailedStatsState(compStats, modStats, oddRangeStats)
    }
}