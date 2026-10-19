package pt.mybets.data.repository

import pt.mybets.data.local.dao.BetDatabaseDao
import pt.mybets.data.local.entity.AppLogEntity
import pt.mybets.data.local.entity.BetEntity
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.entity.ModalityEntity
import pt.mybets.data.local.relations.BetWithGames
import pt.mybets.domain.repository.BetRepository
import kotlinx.coroutines.flow.Flow

import pt.mybets.data.local.entity.NameAliasEntity
import pt.mybets.presentation.util.normalizeKey

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

    override fun getAllNameAliases(): Flow<List<NameAliasEntity>> {
        return dao.getAllNameAliases()
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

    override suspend fun insertNameAlias(alias: NameAliasEntity): Long {
        val id = dao.insertNameAlias(alias)
        dao.insertLog(AppLogEntity(action = "INSERT_ALIAS_${alias.category}_${alias.alias}->${alias.canonicalName}"))
        return id
    }

    override suspend fun deleteNameAlias(id: Int) {
        dao.deleteNameAlias(id)
        dao.insertLog(AppLogEntity(action = "DELETE_ALIAS_ID_$id"))
    }

    override suspend fun renameModality(id: Int, newName: String) {
        dao.updateModalityName(id, newName.trim())
        dao.insertLog(AppLogEntity(action = "RENAME_MODALITY_${id}_TO_${newName}"))
    }

    override suspend fun mergeModalities(sourceId: Int, targetId: Int, sourceName: String, targetName: String) {
        dao.reassignGamesModality(sourceId, targetId)
        dao.deleteModality(sourceId)
        dao.insertNameAlias(NameAliasEntity(category = "MODALITY", alias = sourceName.trim(), canonicalName = targetName.trim()))
        dao.insertLog(AppLogEntity(action = "MERGE_MODALITY_${sourceName}_INTO_${targetName}"))
    }

    override suspend fun renameCompetition(oldName: String, newName: String) {
        val allGames = dao.getAllGamesRaw()
        val oldKey = oldName.trim().normalizeKey()
        allGames.forEach { game ->
            if (game.competition.trim().normalizeKey() == oldKey) {
                dao.updateGame(game.copy(competition = newName.trim()))
            }
        }
        dao.insertLog(AppLogEntity(action = "RENAME_COMPETITION_${oldName}_TO_${newName}"))
    }

    override suspend fun mergeCompetitions(sourceComp: String, targetComp: String) {
        val allGames = dao.getAllGamesRaw()
        val sourceKey = sourceComp.trim().normalizeKey()
        allGames.forEach { game ->
            if (game.competition.trim().normalizeKey() == sourceKey) {
                dao.updateGame(game.copy(competition = targetComp.trim()))
            }
        }
        dao.insertNameAlias(NameAliasEntity(category = "COMPETITION", alias = sourceComp.trim(), canonicalName = targetComp.trim()))
        dao.insertLog(AppLogEntity(action = "MERGE_COMPETITION_${sourceComp}_INTO_${targetComp}"))
    }

    override suspend fun renameTeam(oldName: String, newName: String) {
        val allGames = dao.getAllGamesRaw()
        val oldKey = oldName.trim().normalizeKey()
        allGames.forEach { game ->
            val parts = game.teams.split(" vs ")
            var changed = false
            val newParts = parts.map { part ->
                if (part.trim().normalizeKey() == oldKey) {
                    changed = true
                    newName.trim()
                } else {
                    part.trim()
                }
            }
            if (changed) {
                dao.updateGame(game.copy(teams = newParts.joinToString(" vs ")))
            }
        }
        dao.insertLog(AppLogEntity(action = "RENAME_TEAM_${oldName}_TO_${newName}"))
    }

    override suspend fun mergeTeams(sourceTeam: String, targetTeam: String) {
        val allGames = dao.getAllGamesRaw()
        val sourceKey = sourceTeam.trim().normalizeKey()
        allGames.forEach { game ->
            val parts = game.teams.split(" vs ")
            var changed = false
            val newParts = parts.map { part ->
                if (part.trim().normalizeKey() == sourceKey) {
                    changed = true
                    targetTeam.trim()
                } else {
                    part.trim()
                }
            }
            if (changed) {
                dao.updateGame(game.copy(teams = newParts.joinToString(" vs ")))
            }
        }
        dao.insertNameAlias(NameAliasEntity(category = "TEAM", alias = sourceTeam.trim(), canonicalName = targetTeam.trim()))
        dao.insertLog(AppLogEntity(action = "MERGE_TEAM_${sourceTeam}_INTO_${targetTeam}"))
    }

    override suspend fun getBackupData(): pt.mybets.domain.model.BackupData {
        return pt.mybets.domain.model.BackupData(
            bets = dao.getAllBetsRaw(),
            games = dao.getAllGamesRaw(),
            modalities = dao.getAllModalitiesRaw(),
            logs = dao.getAllLogsRaw(),
            aliases = dao.getAllNameAliasesRaw()
        )
    }

    override suspend fun restoreBackupData(backupData: pt.mybets.domain.model.BackupData) {
        dao.restoreFullBackup(backupData)
    }

    override suspend fun deleteBet(bet: BetEntity) {
        dao.deleteBet(bet)
        dao.insertLog(AppLogEntity(action = "DELETE_BET_ID_${bet.id}"))
    }
}