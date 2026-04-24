package pt.mybets.domain.model

import pt.mybets.data.local.entity.AppLogEntity
import pt.mybets.data.local.entity.BetEntity
import pt.mybets.data.local.entity.GameEntity
import pt.mybets.data.local.entity.ModalityEntity

data class BackupData(
    val bets: List<BetEntity>,
    val games: List<GameEntity>,
    val modalities: List<ModalityEntity>,
    val logs: List<AppLogEntity>
)