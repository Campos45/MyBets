package pt.mybets.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import pt.mybets.data.local.entity.BetEntity
import pt.mybets.data.local.entity.GameEntity

data class BetWithGames(
    @Embedded val bet: BetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "betId"
    )
    val games: List<GameEntity>
)