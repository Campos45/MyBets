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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBetsRaw(bets: List<BetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModalitiesRaw(modalities: List<ModalityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogsRaw(logs: List<AppLogEntity>)

    // ADICIONADO: Transação segura para o Restore
    @Transaction
    suspend fun restoreFullBackup(backup: pt.mybets.domain.model.BackupData) {
        deleteAllGames()
        deleteAllBets()
        deleteAllModalities()
        deleteAllLogs()

        insertModalitiesRaw(backup.modalities)
        insertBetsRaw(backup.bets)
        insertGames(backup.games) // Usa a função de inserir jogos que já tinhas
        insertLogsRaw(backup.logs)
    }

    @Transaction
    suspend fun insertBetWithGames(bet: BetEntity, games: List<GameEntity>) {
        val betId = insertBet(bet).toInt()
        val gamesWithBetId = games.map { it.copy(betId = betId) }
        insertGames(gamesWithBetId)
        insertLog(AppLogEntity(action = "INSERT_BET_WITH_${games.size}_GAMES"))
    }
}