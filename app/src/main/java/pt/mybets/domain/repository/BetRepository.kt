package pt.mybets.domain.repository

import pt.mybets.data.local.entity.AppLogEntity
import pt.mybets.data.local.entity.BetEntity
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.entity.ModalityEntity
import pt.mybets.data.local.relations.BetWithGames
import kotlinx.coroutines.flow.Flow

interface BetRepository {
    fun getAllBetsWithGames(): Flow<List<BetWithGames>>
    fun getAllModalities(): Flow<List<ModalityEntity>>
    fun getAllLogs(): Flow<List<AppLogEntity>>

    suspend fun insertBetWithGames(bet: BetEntity, games: List<GameEntity>)
    suspend fun updateBetStatus(bet: BetEntity)
    suspend fun insertModality(modality: ModalityEntity)
}