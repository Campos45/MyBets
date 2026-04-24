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
}