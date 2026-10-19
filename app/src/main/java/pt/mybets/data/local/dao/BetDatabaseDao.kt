package pt.mybets.data.local.dao
import androidx.room.*
import pt.mybets.data.local.entity.*
import pt.mybets.data.local.relations.BetWithGames
import kotlinx.coroutines.flow.Flow

@Dao
interface BetDatabaseDao {

    // --- B ETS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBet(bet: BetEntity): Long

    @Update
    suspend fun updateBet(bet: BetEntity)

    @Delete
    suspend fun deleteBet(bet: BetEntity)

    @Transaction
    @Query("SELECT * FROM bets ORDER BY dateTimestamp DESC")
    fun getAllBetsWithGames(): Flow<List<BetWithGames>>

    // --- GAMES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<GameEntity>)

    @Update
    suspend fun updateGame(game: GameEntity)

    // --- MODALITIES ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertModality(modality: ModalityEntity): Long

    @Query("SELECT * FROM modalities ORDER BY name ASC")
    fun getAllModalities(): Flow<List<ModalityEntity>>

    // --- LOGS ---
    @Insert
    suspend fun insertLog(log: AppLogEntity)

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AppLogEntity>>

    @Query("SELECT DISTINCT teams FROM games")
    fun getAllUniqueTeams(): Flow<List<String>>

    @Query("SELECT DISTINCT competition FROM games WHERE competition != ''")
    fun getAllUniqueCompetitions(): Flow<List<String>>

    // --- MODALITIES MANAGEMENT ---
    @Query("UPDATE modalities SET name = :newName WHERE id = :id")
    suspend fun updateModalityName(id: Int, newName: String)

    @Query("UPDATE games SET modalityId = :targetModalityId WHERE modalityId = :sourceModalityId")
    suspend fun reassignGamesModality(sourceModalityId: Int, targetModalityId: Int)

    @Query("DELETE FROM modalities WHERE id = :id")
    suspend fun deleteModality(id: Int)

    // --- COMPETITIONS MANAGEMENT ---
    @Query("UPDATE games SET competition = :newName WHERE competition = :oldName")
    suspend fun updateCompetitionName(oldName: String, newName: String)

    // --- NAME ALIASES ---
    @Query("SELECT * FROM name_aliases ORDER BY canonicalName ASC")
    fun getAllNameAliases(): Flow<List<NameAliasEntity>>

    @Query("SELECT * FROM name_aliases")
    suspend fun getAllNameAliasesRaw(): List<NameAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNameAlias(alias: NameAliasEntity): Long

    @Query("DELETE FROM name_aliases WHERE id = :id")
    suspend fun deleteNameAlias(id: Int)

    @Query("DELETE FROM name_aliases WHERE category = :category AND alias = :alias")
    suspend fun deleteNameAliasByCategoryAndAlias(category: String, alias: String)

    // --- FUNÇÕES DE BACKUP (JSON) ---
    @Query("SELECT * FROM bets")
    suspend fun getAllBetsRaw(): List<BetEntity>

    @Query("SELECT * FROM games")
    suspend fun getAllGamesRaw(): List<GameEntity>

    @Query("SELECT * FROM modalities")
    suspend fun getAllModalitiesRaw(): List<ModalityEntity>

    @Query("SELECT * FROM app_logs")
    suspend fun getAllLogsRaw(): List<AppLogEntity>

    @Query("DELETE FROM bets")
    suspend fun deleteAllBets()

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()

    @Query("DELETE FROM modalities")
    suspend fun deleteAllModalities()

    @Query("DELETE FROM app_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM name_aliases")
    suspend fun deleteAllNameAliases()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBetsRaw(bets: List<BetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModalitiesRaw(modalities: List<ModalityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogsRaw(logs: List<AppLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNameAliasesRaw(aliases: List<NameAliasEntity>)

    // ADICIONADO: Transação segura para o Restore
    @Transaction
    suspend fun restoreFullBackup(backup: pt.mybets.domain.model.BackupData) {
        deleteAllGames()
        deleteAllBets()
        deleteAllModalities()
        deleteAllLogs()
        deleteAllNameAliases()

        val modalities = (backup.modalities ?: emptyList()).toMutableList()
        val bets = backup.bets ?: emptyList()
        val rawGames = backup.games ?: emptyList()
        val logs = backup.logs ?: emptyList()
        val aliases = backup.aliases ?: emptyList()

        // Garantir que todos os modalityIds usados nos jogos existem para evitar Foreign Key Constraint Failed
        val existingModalityIds = modalities.map { it.id }.toMutableSet()
        for (g in rawGames) {
            if (g.modalityId !in existingModalityIds) {
                modalities.add(ModalityEntity(id = g.modalityId, name = "Geral"))
                existingModalityIds.add(g.modalityId)
            }
        }

        if (modalities.isNotEmpty()) {
            insertModalitiesRaw(modalities)
        }

        if (bets.isNotEmpty()) {
            insertBetsRaw(bets)
        }

        val existingBetIds = bets.map { it.id }.toSet()
        val validGames = if (existingBetIds.isNotEmpty()) {
            rawGames.filter { it.betId in existingBetIds }
        } else {
            rawGames
        }

        if (validGames.isNotEmpty()) {
            insertGames(validGames)
        }

        if (logs.isNotEmpty()) {
            insertLogsRaw(logs)
        }
        if (aliases.isNotEmpty()) {
            insertNameAliasesRaw(aliases)
        }
    }

    @Transaction
    suspend fun insertBetWithGames(bet: BetEntity, games: List<GameEntity>) {
        val betId = insertBet(bet).toInt()
        val gamesWithBetId = games.map { it.copy(betId = betId) }
        insertGames(gamesWithBetId)
        insertLog(AppLogEntity(action = "INSERT_BET_WITH_${games.size}_GAMES"))
    }
}