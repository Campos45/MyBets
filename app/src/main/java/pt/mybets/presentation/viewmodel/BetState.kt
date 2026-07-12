package pt.mybets.presentation.viewmodel

import androidx.compose.runtime.Immutable
import pt.mybets.data.local.relations.BetWithGames

import pt.mybets.data.local.entity.ModalityEntity

@Immutable
data class BetStatsState(
    val totalProfit: Double = 0.0,
    val totalInvested: Double = 0.0,
    val freeBetProfit: Double = 0.0,
    val hitRatePercentage: Double = 0.0
)

@Immutable
data class MainUiState(
    val bets: List<BetWithGames> = emptyList(),
    val stats: BetStatsState = BetStatsState(),
    val detailedStats: DetailedStatsState = DetailedStatsState(), // Adiciona isto
    val modalities: List<ModalityEntity> = emptyList(),
    val isLoading: Boolean = true
)

@Immutable
data class CompetitionStat(val name: String, val winRate: Double, val total: Int)

@Immutable
data class ModalityStat(val name: String, val winRate: Double, val total: Int)

@Immutable
data class OddRangeStat(val range: String, val roi: Double, val totalBets: Int)

@Immutable
data class BetInsight(
    val title: String,
    val description: String,
    val type: InsightType,
    val winRate: Double,
    val totalBets: Int
)

enum class InsightType { POSITIVE, WARNING, INFO }

@Immutable
data class DetailedStatsState(
    val competitionStats: List<CompetitionStat> = emptyList(),
    val modalityStats: List<ModalityStat> = emptyList(),
    val oddRangeStats: List<OddRangeStat> = emptyList(),
    val insights: List<BetInsight> = emptyList()
)

