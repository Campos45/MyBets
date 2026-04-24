package pt.mybets.presentation.viewmodel

import pt.mybets.data.local.relations.BetWithGames

data class BetStatsState(
    val totalProfit: Double = 0.0,
    val totalInvested: Double = 0.0,
    val freeBetProfit: Double = 0.0,
    val hitRatePercentage: Double = 0.0
)

data class MainUiState(
    val bets: List<BetWithGames> = emptyList(),
    val stats: BetStatsState = BetStatsState(),
    val detailedStats: DetailedStatsState = DetailedStatsState(), // Adiciona isto
    val isLoading: Boolean = true
)

data class CompetitionStat(val name: String, val winRate: Double, val total: Int)
data class ModalityStat(val name: String, val winRate: Double, val total: Int)
data class OddRangeStat(val range: String, val roi: Double, val totalBets: Int)

data class DetailedStatsState(
    val competitionStats: List<CompetitionStat> = emptyList(),
    val modalityStats: List<ModalityStat> = emptyList(),
    val oddRangeStats: List<OddRangeStat> = emptyList()
)

