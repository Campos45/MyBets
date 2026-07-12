package pt.mybets.data.repository

import pt.mybets.data.local.dao.BetDatabaseDao
import pt.mybets.data.local.entity.AppLogEntity
import pt.mybets.data.local.entity.BetEntity
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.entity.ModalityEntity
import pt.mybets.data.local.relations.BetWithGames
import pt.mybets.domain.repository.BetRepository
import kotlinx.coroutines.flow.Flow

class BetRepositoryImpl(
    private val dao: BetDatabaseDao
) : BetRepository {

    override fun getAllBetsWithGames(): Flow<List<BetWithGames>> {
        return dao.getAllBetsWithGames()
    }

    override fun getAllModalities(): Flow<List<ModalityEntity>> {
        return dao.getAllModalities()
    }

    override fun getAllLogs(): Flow<List<AppLogEntity>> {
        return dao.getAllLogs()
    }

    override suspend fun insertBetWithGames(bet: BetEntity, games: List<GameEntity>) {
        dao.insertBetWithGames(bet, games)
    }

    override suspend fun updateBetStatus(bet: BetEntity) {
        dao.updateBet(bet)
        dao.insertLog(AppLogEntity(action = "UPDATE_BET_ID_${bet.id}"))
    }

    override suspend fun insertModality(modality: ModalityEntity): Long {
        val id = dao.insertModality(modality)
        dao.insertLog(AppLogEntity(action = "INSERT_MODALITY_${modality.name}"))
        return id
    }

    override fun getAllUniqueTeams(): Flow<List<String>> {
        return dao.getAllUniqueTeams()
    }

    override fun getAllUniqueCompetitions(): Flow<List<String>> = dao.getAllUniqueCompetitions()

    override suspend fun updateGame(game: GameEntity) {
        dao.updateGame(game)
    }

    override suspend fun getBackupData(): pt.mybets.domain.model.BackupData {
        return pt.mybets.domain.model.BackupData(
            bets = dao.getAllBetsRaw(),
            games = dao.getAllGamesRaw(),
            modalities = dao.getAllModalitiesRaw(),
            logs = dao.getAllLogsRaw()
        )
    }

    override suspend fun restoreBackupData(backupData: pt.mybets.domain.model.BackupData) {
        dao.restoreFullBackup(backupData) // Substitui as múltiplas chamadas por esta única segura
    }

    override suspend fun deleteBet(bet: BetEntity) {
        dao.deleteBet(bet)
        dao.insertLog(AppLogEntity(action = "DELETE_BET_ID_${bet.id}"))
    }
}