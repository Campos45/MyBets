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
import pt.mybets.data.local.entity.NameAliasEntity
import pt.mybets.presentation.util.normalizeKey

import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Estado para copiar aposta
data class BetDraft(
    val stake: String = "",
    val isFreeBet: Boolean = false,
    val games: List<GameInputState> = emptyList()
)

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


    val nameAliases: StateFlow<List<NameAliasEntity>> = repository.getAllNameAliases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _copiedBetDraft = MutableStateFlow<BetDraft?>(null)
    val copiedBetDraft: StateFlow<BetDraft?> = _copiedBetDraft.asStateFlow()

    fun copyBet(bwg: BetWithGames, modalityMap: Map<Int, String>) {
        val draftGames = bwg.games.map { g ->
            val parts = g.teams.split(" vs ")
            val home = parts.getOrNull(0)?.trim() ?: ""
            val away = parts.getOrNull(1)?.trim() ?: ""
            GameInputState(
                id = UUID.randomUUID().toString(),
                homeTeam = home,
                awayTeam = away,
                competition = g.competition,
                modalityName = modalityMap[g.modalityId] ?: "",
                odd = if (g.odd > 0) g.odd.toString() else "",
                isLive = g.isLive
            )
        }
        _copiedBetDraft.value = BetDraft(
            stake = if (bwg.bet.stake > 0) bwg.bet.stake.toString() else "",
            isFreeBet = bwg.bet.isFreeBet,
            games = if (draftGames.isNotEmpty()) draftGames else listOf(GameInputState())
        )
    }

    fun clearCopiedBetDraft() {
        _copiedBetDraft.value = null
    }

    // REQUISITO 2: Sugestão inteligente de competições priorizando competições já associadas às equipas
    fun getCompetitionsForTeams(homeTeam: String, awayTeam: String): List<String> {
        val allComps = competitionSuggestions.value
        val homeKey = homeTeam.normalizeKey()
        val awayKey = awayTeam.normalizeKey()
        if (homeKey.isEmpty() && awayKey.isEmpty()) {
            return allComps
        }
        val currentBets = uiState.value.bets
        val associated = mutableSetOf<String>()
        currentBets.forEach { bwg ->
            bwg.games.forEach { g ->
                val teamsKey = g.teams.normalizeKey()
                val matchHome = homeKey.isNotEmpty() && teamsKey.contains(homeKey)
                val matchAway = awayKey.isNotEmpty() && teamsKey.contains(awayKey)
                if ((matchHome || matchAway) && g.competition.isNotBlank()) {
                    associated.add(g.competition.trim())
                }
            }
        }
        return (associated.toList().sorted() + allComps).distinct()
    }

    fun markAllGamesAsWon(betWithGames: BetWithGames) {
        viewModelScope.launch {
            betWithGames.games.forEach { game ->
                repository.updateGame(game.copy(result = ResultState.WON))
            }
            repository.updateBetStatus(
                betWithGames.bet.copy(globalResult = ResultState.WON, isFinished = true)
            )
        }
    }

    // REQUISITO 1: A aposta SÓ passa a concluída quando TODOS os jogos tiverem estado final (sem pendentes)
    fun updateGameResult(betWithGames: BetWithGames, game: GameEntity, clickedResult: ResultState) {
        viewModelScope.launch {
            val newResult = if (game.result == clickedResult) ResultState.PENDING else clickedResult
            val updatedGame = game.copy(result = newResult)
            repository.updateGame(updatedGame)

            val allGames = betWithGames.games.map { if (it.id == game.id) updatedGame else it }

            val hasLost = allGames.any { it.result == ResultState.LOST }
            val hasPending = allGames.any { it.result == ResultState.PENDING }

            // Só passa para 'concluído' se NENHUM jogo estiver pendente
            val isFinished = !hasPending
            val newBetResult = if (isFinished) {
                if (hasLost) ResultState.LOST else ResultState.WON
            } else {
                ResultState.PENDING
            }

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

    // REQUISITO 4: Resolução de nomes canónicos e aliases (case & acentos insensitive)
    private fun resolveCanonicalName(
        category: String,
        input: String,
        existingNames: List<String>,
        aliases: List<NameAliasEntity>
    ): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val key = trimmed.normalizeKey()

        // 1. Procurar em aliases guardados (ex: "cíty" -> alias ou combinação)
        val aliasMatch = aliases.find { it.category == category && it.alias.normalizeKey() == key }
        if (aliasMatch != null) {
            return aliasMatch.canonicalName
        }

        // 2. Procurar em nomes existentes na BD (insensível a acentos e maiúsculas/minúsculas)
        val existingMatch = existingNames.find { it.normalizeKey() == key }
        if (existingMatch != null) {
            return existingMatch
        }

        // 3. Se não existe, mantém a primeira versão guardada
        return trimmed
    }

    fun saveBet(stake: Double, isFreeBet: Boolean, gameInputs: List<GameInputState>) {
        viewModelScope.launch {
            val bet = BetEntity(stake = stake, isFreeBet = isFreeBet)

            val currentModalities = repository.getAllModalities().first().toMutableList()
            val existingCompetitions = repository.getAllUniqueCompetitions().first()
            val existingTeams = repository.getAllUniqueTeams().first().flatMap { combinedStr ->
                combinedStr.split(" vs ").mapNotNull { if (it.isNotBlank()) it.trim() else null }
            }.distinct()
            val currentAliases = repository.getAllNameAliases().first()

            val gameEntities = gameInputs.map { input ->
                // 1. Resolver equipas
                val homeResolved = resolveCanonicalName("TEAM", input.homeTeam, existingTeams, currentAliases)
                val awayResolved = resolveCanonicalName("TEAM", input.awayTeam, existingTeams, currentAliases)

                // 2. Resolver competição
                val compResolved = resolveCanonicalName("COMPETITION", input.competition, existingCompetitions, currentAliases)

                // 3. Resolver modalidade
                val rawMod = resolveCanonicalName("MODALITY", input.modalityName, currentModalities.map { it.name }, currentAliases)
                val modKey = rawMod.normalizeKey()

                var modality = currentModalities.find { it.name.normalizeKey() == modKey }
                val modalityId = if (modality != null) {
                    modality.id
                } else {
                    var newId = repository.insertModality(ModalityEntity(name = rawMod.trim())).toInt()
                    if (newId <= 0) {
                        val refreshed = repository.getAllModalities().first()
                        newId = refreshed.find { it.name.normalizeKey() == modKey }?.id ?: 1
                    }
                    currentModalities.add(ModalityEntity(id = newId, name = rawMod.trim()))
                    newId
                }

                GameEntity(
                    betId = 0,
                    teams = "${homeResolved.trim()} vs ${awayResolved.trim()}",
                    competition = compResolved.trim(),
                    modalityId = modalityId,
                    odd = input.odd.toDoubleOrNull() ?: 1.0,
                    isLive = input.isLive
                )
            }

            repository.insertBetWithGames(bet, gameEntities)
        }
    }

    // REQUISITO 5: Funções de Edição e Combinação de Dados
    fun renameTeam(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.renameTeam(oldName, newName)
        }
    }

    fun mergeTeams(sourceTeam: String, targetTeam: String) {
        viewModelScope.launch {
            repository.mergeTeams(sourceTeam, targetTeam)
        }
    }

    fun renameCompetition(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.renameCompetition(oldName, newName)
        }
    }

    fun mergeCompetitions(sourceComp: String, targetComp: String) {
        viewModelScope.launch {
            repository.mergeCompetitions(sourceComp, targetComp)
        }
    }

    fun renameModality(id: Int, newName: String) {
        viewModelScope.launch {
            repository.renameModality(id, newName)
        }
    }

    fun mergeModalities(sourceId: Int, targetId: Int, sourceName: String, targetName: String) {
        viewModelScope.launch {
            repository.mergeModalities(sourceId, targetId, sourceName, targetName)
        }
    }

    fun deleteAlias(id: Int) {
        viewModelScope.launch {
            repository.deleteNameAlias(id)
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
            } else if (b.globalResult == ResultState.VOID) {
                // Devolve a stake caso a aposta seja anulada (VOID)
                if (!b.isFreeBet) totalProfit += b.stake
            }
        }
        val rawRate = if (finishedCount > 0) (wonCount.toDouble() / finishedCount) * 100 else 0.0
        val rate = if (rawRate.isNaN() || rawRate.isInfinite()) 0.0 else rawRate
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
            val rawRate = (wins.toDouble() / games.size) * 100
            val winRate = if (rawRate.isNaN() || rawRate.isInfinite()) 0.0 else rawRate
            CompetitionStat(comp, winRate, games.size)
        }.sortedByDescending { it.total }

        val modalityMap = modalities.associate { it.id to it.name }
        val modStats = allFinishedGames.groupBy { it.modalityId }.map { (modId, games) ->
            val modName = modalityMap[modId] ?: "Desconhecida"
            val wins = games.count { it.result == ResultState.WON }
            val rawRate = (wins.toDouble() / games.size) * 100
            val winRate = if (rawRate.isNaN() || rawRate.isInfinite()) 0.0 else rawRate
            ModalityStat(modName, winRate, games.size)
        }.sortedByDescending { it.total }

        // Planificar todos os jogos finalizados com os detalhes de odd e aposta pai
        // Prevenção de duplicação: aposta múltipla reparte a stake pelos seus jogos no cálculo por faixa de odd
        val finishedGamesWithDetails = finishedBets.flatMap { bwg ->
            val gamesCount = bwg.games.size.coerceAtLeast(1)
            bwg.games.map { g ->
                val gameOdd = if (g.result == ResultState.VOID) 1.0 else g.odd
                val isWon = g.result == ResultState.WON
                GameOddDetail(
                    game = g,
                    odd = gameOdd,
                    stake = bwg.bet.stake / gamesCount,
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

            val rawRoi = if (totalStake > 0) ((totalReturn - totalStake) / totalStake) * 100 else 0.0
            val roi = if (rawRoi.isNaN() || rawRoi.isInfinite()) 0.0 else rawRoi
            OddRangeStat(range, roi, filtered.size)
        }

        // --- GERAÇÃO AUTOMÁTICA DE INSIGHTS ---
        val insights = mutableListOf<BetInsight>()

        // 1. Mercados (Global, Live e Pré-Jogo)
        finishedGamesWithDetails.groupBy { it.game.modalityId }.forEach { (modId, list) ->
            val modName = modalityMap[modId] ?: "Desconhecida"
            // Global
            if (list.size >= 3) {
                val wins = list.count { it.isWon }
                val rate = (wins.toDouble() / list.size) * 100
                if (rate >= 70.0) {
                    insights.add(BetInsight(
                        title = "🔥 Ponto Forte: Mercado $modName",
                        description = "Tens uma taxa de acerto excelente de ${String.format(Locale.getDefault(), "%.1f", rate)}% neste mercado (${list.size} apostas).",
                        type = InsightType.POSITIVE,
                        winRate = rate,
                        totalBets = list.size
                    ))
                } else if (rate <= 40.0) {
                    insights.add(BetInsight(
                        title = "⚠️ Alerta: Mercado $modName",
                        description = "Cuidado com as apostas neste mercado. A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                        type = InsightType.WARNING,
                        winRate = rate,
                        totalBets = list.size
                    ))
                }
            }
            // Por Live/Pré-Jogo
            list.groupBy { it.game.isLive }.forEach { (isLive, subList) ->
                if (subList.size >= 3) {
                    val wins = subList.count { it.isWon }
                    val rate = (wins.toDouble() / subList.size) * 100
                    val liveStr = if (isLive) "Live" else "Pré-Jogo"
                    if (rate >= 70.0) {
                        insights.add(BetInsight(
                            title = "🔥 Ponto Forte: Mercado $modName ($liveStr)",
                            description = "Tens uma taxa de acerto excelente de ${String.format(Locale.getDefault(), "%.1f", rate)}% em apostas $liveStr neste mercado (${subList.size} apostas).",
                            type = InsightType.POSITIVE,
                            winRate = rate,
                            totalBets = subList.size
                        ))
                    } else if (rate <= 40.0) {
                        insights.add(BetInsight(
                            title = "⚠️ Alerta: Mercado $modName ($liveStr)",
                            description = "Cuidado com as apostas $liveStr neste mercado. A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${subList.size} apostas).",
                            type = InsightType.WARNING,
                            winRate = rate,
                            totalBets = subList.size
                        ))
                    }
                }
            }
        }

        // 2. Competições (Global, Live e Pré-Jogo)
        finishedGamesWithDetails.groupBy { it.game.competition }.forEach { (competition, list) ->
            if (competition.isNotEmpty()) {
                // Global
                if (list.size >= 3) {
                    val wins = list.count { it.isWon }
                    val rate = (wins.toDouble() / list.size) * 100
                    if (rate >= 75.0) {
                        insights.add(BetInsight(
                            title = "⚽ Domínio: $competition",
                            description = "Estás a ter um excelente aproveitamento na competição $competition, com ${String.format(Locale.getDefault(), "%.1f", rate)}% de taxa de acerto (${list.size} apostas).",
                            type = InsightType.POSITIVE,
                            winRate = rate,
                            totalBets = list.size
                        ))
                    } else if (rate <= 35.0) {
                        insights.add(BetInsight(
                            title = "🚨 Dificuldade: $competition",
                            description = "A tua performance na competição $competition precisa de atenção. Taxa de acerto de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                            type = InsightType.WARNING,
                            winRate = rate,
                            totalBets = list.size
                        ))
                    }
                }
                // Por Live/Pré-Jogo
                list.groupBy { it.game.isLive }.forEach { (isLive, subList) ->
                    if (subList.size >= 3) {
                        val wins = subList.count { it.isWon }
                        val rate = (wins.toDouble() / subList.size) * 100
                        val liveStr = if (isLive) "Live" else "Pré-Jogo"
                        if (rate >= 75.0) {
                            insights.add(BetInsight(
                                title = "⚽ Domínio em $liveStr: $competition",
                                description = "Estás a ter um excelente aproveitamento na competição $competition em $liveStr, com ${String.format(Locale.getDefault(), "%.1f", rate)}% de taxa de acerto (${subList.size} apostas).",
                                type = InsightType.POSITIVE,
                                winRate = rate,
                                totalBets = subList.size
                            ))
                        } else if (rate <= 35.0) {
                            insights.add(BetInsight(
                                title = "🚨 Dificuldade em $liveStr: $competition",
                                description = "A tua performance em $liveStr na competição $competition precisa de atenção. Taxa de acerto de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${subList.size} apostas).",
                                type = InsightType.WARNING,
                                winRate = rate,
                                totalBets = subList.size
                            ))
                        }
                    }
                }
            }
        }

        // 3. Equipas individuais (Global, Live e Pré-Jogo)
        val teamDetailsList = finishedGamesWithDetails.flatMap { detail ->
            val teamNames = detail.game.teams.split(" vs ")
            teamNames.map { name -> Pair(name.trim(), detail) }
        }.filter { it.first.isNotEmpty() }

        teamDetailsList.groupBy { it.first }.forEach { (teamName, list) ->
            val detailList = list.map { it.second }
            // Global
            if (detailList.size >= 3) {
                val wins = detailList.count { it.isWon }
                val rate = (wins.toDouble() / detailList.size) * 100
                if (rate >= 75.0) {
                    insights.add(BetInsight(
                        title = "💪 Padrão de Lucro: $teamName",
                        description = "Tens uma fantástica taxa de acerto de ${String.format(Locale.getDefault(), "%.1f", rate)}% ao apostar com o $teamName envolvido (${detailList.size} apostas).",
                        type = InsightType.POSITIVE,
                        winRate = rate,
                        totalBets = detailList.size
                    ))
                } else if (rate <= 35.0) {
                    insights.add(BetInsight(
                        title = "📉 Risco Elevado: $teamName",
                        description = "Tens tido fraco desempenho em apostas com o $teamName. A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${detailList.size} apostas).",
                        type = InsightType.WARNING,
                        winRate = rate,
                        totalBets = detailList.size
                    ))
                }
            }
            // Por Live/Pré-Jogo
            list.groupBy { it.second.game.isLive }.forEach { (isLive, subList) ->
                val subDetailList = subList.map { it.second }
                if (subDetailList.size >= 3) {
                    val wins = subDetailList.count { it.isWon }
                    val rate = (wins.toDouble() / subDetailList.size) * 100
                    val liveStr = if (isLive) "Live" else "Pré-Jogo"
                    if (rate >= 75.0) {
                        insights.add(BetInsight(
                            title = "💪 Padrão de Lucro: $teamName ($liveStr)",
                            description = "Tens uma fantástica taxa de acerto de ${String.format(Locale.getDefault(), "%.1f", rate)}% ao apostar em $liveStr com o $teamName envolvido (${subDetailList.size} apostas).",
                            type = InsightType.POSITIVE,
                            winRate = rate,
                            totalBets = subDetailList.size
                        ))
                    } else if (rate <= 35.0) {
                        insights.add(BetInsight(
                            title = "📉 Risco Elevado: $teamName ($liveStr)",
                            description = "Tens tido fraco desempenho em apostas em $liveStr com o $teamName. A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${subDetailList.size} apostas).",
                            type = InsightType.WARNING,
                            winRate = rate,
                            totalBets = subDetailList.size
                        ))
                    }
                }
            }
        }

        // 4. Odds (Global, Live e Pré-Jogo)
        val rangesList = listOf("1.0-1.5", "1.5-2.0", "2.0-3.0", "3.0+")
        rangesList.forEach { range ->
            val list = finishedGamesWithDetails.filter { detail ->
                when (range) {
                    "1.0-1.5" -> detail.odd in 1.0..1.5
                    "1.5-2.0" -> detail.odd > 1.5 && detail.odd <= 2.0
                    "2.0-3.0" -> detail.odd > 2.0 && detail.odd <= 3.0
                    else -> detail.odd > 3.0
                }
            }
            // Global
            if (list.size >= 3) {
                val wins = list.count { it.isWon }
                val rate = (wins.toDouble() / list.size) * 100
                if (rate >= 70.0) {
                    insights.add(BetInsight(
                        title = "📈 Odds Favoráveis: Faixa $range",
                        description = "As tuas apostas com odds na faixa de $range têm trazido excelente retorno, com taxa de acerto de ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                        type = InsightType.INFO,
                        winRate = rate,
                        totalBets = list.size
                    ))
                } else if (rate <= 35.0) {
                    insights.add(BetInsight(
                        title = "⚠️ Odds Perigosas: Faixa $range",
                        description = "Aposta com cautela nesta faixa de odd ($range). A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${list.size} apostas).",
                        type = InsightType.WARNING,
                        winRate = rate,
                        totalBets = list.size
                    ))
                }
            }
            // Por Live/Pré-Jogo
            list.groupBy { it.game.isLive }.forEach { (isLive, subList) ->
                if (subList.size >= 3) {
                    val wins = subList.count { it.isWon }
                    val rate = (wins.toDouble() / subList.size) * 100
                    val liveStr = if (isLive) "Live" else "Pré-Jogo"
                    if (rate >= 70.0) {
                        insights.add(BetInsight(
                            title = "📈 Odds Favoráveis ($liveStr): Faixa $range",
                            description = "As tuas apostas em $liveStr com odds na faixa de $range têm trazido excelente retorno, com taxa de acerto de ${String.format(Locale.getDefault(), "%.1f", rate)}% (${subList.size} apostas).",
                            type = InsightType.INFO,
                            winRate = rate,
                            totalBets = subList.size
                        ))
                    } else if (rate <= 35.0) {
                        insights.add(BetInsight(
                            title = "⚠️ Odds Perigosas ($liveStr): Faixa $range",
                            description = "Aposta com cautela em $liveStr nesta faixa de odd ($range). A tua taxa de acerto é de apenas ${String.format(Locale.getDefault(), "%.1f", rate)}% (${subList.size} apostas).",
                            type = InsightType.WARNING,
                            winRate = rate,
                            totalBets = subList.size
                        ))
                    }
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
            var errorDetail: String? = null
            val success = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        InputStreamReader(inputStream).use { reader ->
                            val backup = Gson().fromJson(reader, BackupData::class.java)
                            if (backup != null) {
                                repository.restoreBackupData(backup)
                                true
                            } else {
                                errorDetail = "Ficheiro vazio ou formato inválido."
                                false
                            }
                        }
                    } ?: run {
                        errorDetail = "Não foi possível abrir o ficheiro."
                        false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorDetail = e.localizedMessage ?: e.javaClass.simpleName
                    false
                }
            }
            if (success) {
                Toast.makeText(context, "Backup importado com sucesso!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Erro ao importar: ${errorDetail ?: "Ficheiro inválido"}", Toast.LENGTH_LONG).show()
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