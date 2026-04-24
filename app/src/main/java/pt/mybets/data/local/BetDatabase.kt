package com.teunome.bettracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.teunome.bettracker.data.local.dao.BetDatabaseDao
import com.teunome.bettracker.data.local.entity.*

@Database(
    entities = [BetEntity::class, GameEntity::class, ModalityEntity::class, AppLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BetDatabase : RoomDatabase() {
    abstract val dao: BetDatabaseDao
}