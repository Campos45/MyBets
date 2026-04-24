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
    val isLoading: Boolean = true
)