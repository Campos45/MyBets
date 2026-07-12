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
    val odd: String = ""
)

class BetViewModel(
    private val repository: BetRepository
) : ViewModel() {

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
                    odd = input.odd.toDoubleOrNull() ?: 1.0
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

        // Pré-calcular a odd acumulada e stake de cada aposta finalizada para evitar folds redundantes
        val finishedBetsWithOdds = finishedBets.map { bwg ->
            val totalOdd = bwg.games.fold(1.0) { acc, g ->
                acc * (if (g.result == ResultState.VOID) 1.0 else g.odd)
            }
            Triple(bwg, totalOdd, bwg.bet.stake)
        }

        val ranges = listOf("1.0-1.5", "1.5-2.0", "2.0-3.0", "3.0+")
        val oddRangeStats = ranges.map { range ->
            val filtered = finishedBetsWithOdds.filter { (_, totalOdd, _) ->
                when (range) {
                    "1.0-1.5" -> totalOdd in 1.0..1.5
                    "1.5-2.0" -> totalOdd > 1.5 && totalOdd <= 2.0
                    "2.0-3.0" -> totalOdd > 2.0 && totalOdd <= 3.0
                    else -> totalOdd > 3.0
                }
            }

            var totalStake = 0.0
            var totalReturn = 0.0
            filtered.forEach { (bwg, totalOdd, stake) ->
                if (!bwg.bet.isFreeBet) totalStake += stake
                if (bwg.bet.globalResult == ResultState.WON) {
                    totalReturn += stake * totalOdd
                }
            }

            val roi = if (totalStake > 0) ((totalReturn - totalStake) / totalStake) * 100 else 0.0
            OddRangeStat(range, roi, filtered.size)
        }

        return DetailedStatsState(compStats, modStats, oddRangeStats)
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
}