package com.teunome.bettracker.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.teunome.bettracker.data.local.entity.BetEntity
import com.teunome.bettracker.data.local.entity.GameEntity

data class BetWithGames(
    @Embedded val bet: BetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "betId"
    )
    val games: List<GameEntity>
)