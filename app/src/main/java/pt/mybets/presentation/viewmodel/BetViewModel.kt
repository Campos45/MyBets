package pt.mybets.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pt.mybets.domain.model.ResultState
import pt.mybets.domain.repository.BetRepository
import pt.mybets.data.local.relations.BetWithGames

class BetViewModel(
    private val repository: BetRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)

    // O combine junta a lista de apostas da BD com o nosso estado de loading
    val uiState: StateFlow<MainUiState> = combine(
        repository.getAllBetsWithGames(),
        _isLoading
    ) { bets, isLoading ->
        val stats = calculateStats(bets)
        MainUiState(
            bets = bets,
            stats = stats,
            isLoading = false // Assim que recebemos dados, desligamos o loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    /**
     * Lógica Financeira Central
     * Lucro = (Retorno Total das Ganhas) - (Stake Total em Dinheiro Real).
     */
    private fun calculateStats(bets: List<BetWithGames>): BetStatsState {
        var totalProfit = 0.0
        var totalInvestedRealMoney = 0.0
        var freeBetProfit = 0.0

        var totalFinishedBets = 0
        var totalWonBets = 0

        for (betWithGames in bets) {
            val bet = betWithGames.bet
            val games = betWithGames.games

            // Só contamos apostas finalizadas para a Win Rate
            if (bet.isFinished) {
                totalFinishedBets++
                if (bet.globalResult == ResultState.WON) {
                    totalWonBets++
                }
            }

            // Calculamos o gasto em dinheiro real
            if (!bet.isFreeBet) {
                totalInvestedRealMoney += bet.stake
                // Se for dinheiro real, subtraímos imediatamente ao lucro (é um investimento)
                totalProfit -= bet.stake
            }

            // Calculamos os retornos se a aposta for ganha
            if (bet.globalResult == ResultState.WON) {
                // A Odd Total é a multiplicação de todas as odds dos jogos daquela aposta
                val totalOdd = games.fold(1.0) { acc, game -> acc * game.odd }
                val returnAmount = bet.stake * totalOdd

                if (bet.isFreeBet) {
                    freeBetProfit += returnAmount
                    totalProfit += returnAmount // Free bet ganha adiciona lucro, mas não subtraiu stake antes
                } else {
                    totalProfit += returnAmount
                }
            }
        }

        val hitRate = if (totalFinishedBets > 0) {
            (totalWonBets.toDouble() / totalFinishedBets.toDouble()) * 100
        } else 0.0

        return BetStatsState(
            totalProfit = totalProfit,
            totalInvested = totalInvestedRealMoney,
            freeBetProfit = freeBetProfit,
            hitRatePercentage = hitRate
        )
    }

    // Funções para a UI chamar
    // ... Aqui vamos adicionar depois funções para inserir e remover apostas
}