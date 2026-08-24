package com.sinura.personaltrainer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bodyweight_entries")
data class BodyweightEntryEntity(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
    val recordedAtMs: Long,
)

@Entity(
    tableName = "training_blocks",
    indices = [Index("startEpochDay")],
)
data class TrainingBlockEntity(
    @PrimaryKey val id: String,
    val startEpochDay: Long,
    val weeks: Int,
    val isCurrent: Boolean,
    val archivedAtMs: Long?,
)
