package pt.mybets.domain.repository

import pt.mybets.data.local.entity.AppLogEntity
import pt.mybets.data.local.entity.BetEntity
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.entity.ModalityEntity
import pt.mybets.data.local.relations.BetWithGames
import kotlinx.coroutines.flow.Flow

import pt.mybets.data.local.entity.NameAliasEntity

interface BetRepository {
    fun getAllBetsWithGames(): Flow<List<BetWithGames>>
    fun getAllModalities(): Flow<List<ModalityEntity>>
    fun getAllLogs(): Flow<List<AppLogEntity>>
    fun getAllNameAliases(): Flow<List<NameAliasEntity>>

    suspend fun insertBetWithGames(bet: BetEntity, games: List<GameEntity>)
    suspend fun updateBetStatus(bet: BetEntity)
    suspend fun insertModality(modality: ModalityEntity): Long

    fun getAllUniqueTeams(): Flow<List<String>>
    fun getAllUniqueCompetitions(): Flow<List<String>>

    suspend fun updateGame(game: GameEntity)

    suspend fun insertNameAlias(alias: NameAliasEntity): Long
    suspend fun deleteNameAlias(id: Int)

    suspend fun renameModality(id: Int, newName: String)
    suspend fun mergeModalities(sourceId: Int, targetId: Int, sourceName: String, targetName: String)

    suspend fun renameCompetition(oldName: String, newName: String)
    suspend fun mergeCompetitions(sourceComp: String, targetComp: String)

    suspend fun renameTeam(oldName: String, newName: String)
    suspend fun mergeTeams(sourceTeam: String, targetTeam: String)

    suspend fun getBackupData(): pt.mybets.domain.model.BackupData
    suspend fun restoreBackupData(backupData: pt.mybets.domain.model.BackupData)
    suspend fun deleteBet(bet: BetEntity)
}