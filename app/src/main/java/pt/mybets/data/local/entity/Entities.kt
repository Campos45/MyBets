package pt.mybets.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import pt.mybets.domain.model.ResultState

@Entity(tableName = "bets")
data class BetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val stake: Double,
    val isFreeBet: Boolean,
    val isFinished: Boolean = false,
    val globalResult: ResultState = ResultState.PENDING
)

@Entity(
    tableName = "games",
    foreignKeys = [
        ForeignKey(entity = BetEntity::class, parentColumns = ["id"], childColumns = ["betId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ModalityEntity::class, parentColumns = ["id"], childColumns = ["modalityId"], onDelete = ForeignKey.CASCADE)
    ],
    // ADICIONADO: Índices para evitar Full Table Scans e lentidão
    indices = [
        Index(value = ["betId"]),
        Index(value = ["modalityId"])
    ]
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val betId: Int, // Chave estrangeira para Bet
    val teams: String,
    val competition: String,
    val modalityId: Int, // Chave estrangeira para Modality
    val odd: Double,
    val result: ResultState = ResultState.PENDING,
    val isLive: Boolean = false
)

@Entity(tableName = "modalities")
data class ModalityEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "app_logs")
data class AppLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val action: String, // Ex: "INSERT_BET", "DELETE_GAME"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "name_aliases",
    indices = [
        Index(value = ["category", "alias"], unique = true)
    ]
)
data class NameAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // "TEAM", "COMPETITION", "MODALITY"
    val alias: String,
    val canonicalName: String
)