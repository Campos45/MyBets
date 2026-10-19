package pt.mybets.domain.model

import pt.mybets.data.local.entity.AppLogEntity
import pt.mybets.data.local.entity.BetEntity
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.entity.ModalityEntity

import pt.mybets.data.local.entity.NameAliasEntity

data class BackupData(
    val bets: List<BetEntity>? = emptyList(),
    val games: List<GameEntity>? = emptyList(),
    val modalities: List<ModalityEntity>? = emptyList(),
    val logs: List<AppLogEntity>? = emptyList(),
    val aliases: List<NameAliasEntity>? = emptyList()
)