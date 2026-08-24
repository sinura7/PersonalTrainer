package com.sinura.personaltrainer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurable_goals",
    indices = [Index("kind"), Index("period")],
)
data class MeasurableGoalEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val targetValue: Double,
    val exerciseId: String?,
    val exerciseName: String?,
    val period: String,
    val instantMs: Long,
    val zoneId: String,
    val offsetSeconds: Int,
    val localEpochDay: Long,
    val paused: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
