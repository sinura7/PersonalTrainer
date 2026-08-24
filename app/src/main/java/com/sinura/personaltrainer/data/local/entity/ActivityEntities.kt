package com.sinura.personaltrainer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_sessions",
    indices = [
        Index("performedStartLocalEpochDay"),
        Index(value = ["liveToken"], unique = true),
    ],
)
data class ActivitySessionEntity(
    @PrimaryKey val id: String,
    val status: String,
    val origin: String,
    val source: String,
    val title: String,
    val notes: String,
    val performedStartInstantMs: Long,
    val performedStartZoneId: String,
    val performedStartOffsetSeconds: Int,
    val performedStartLocalEpochDay: Long,
    val performedEndInstantMs: Long?,
    val performedEndZoneId: String?,
    val performedEndOffsetSeconds: Int?,
    val performedEndLocalEpochDay: Long?,
    val templateId: String?,
    val occurrenceId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val revision: Long,
    /**
     * `"LIVE"` while [status] is ACTIVE, otherwise null. SQLite unique
     * allows many nulls and at most one live row.
     */
    val liveToken: String?,
)

@Entity(tableName = "activity_templates")
data class ActivityTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "activity_blocks",
    foreignKeys = [
        ForeignKey(
            entity = ActivitySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ActivityTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("templateId"), Index("sortOrder")],
)
data class ActivityBlockEntity(
    @PrimaryKey val id: String,
    val sessionId: String?,
    val templateId: String?,
    val sortOrder: Int,
    val kind: String,
    val exerciseId: String?,
    val exerciseName: String?,
    val loadType: String?,
    val equipment: String?,
    val musclesEncoded: String?,
    val cardioType: String?,
    val indoor: Boolean?,
    val elapsedSeconds: Long?,
    val movingSeconds: Long?,
    val distanceMeters: Double?,
    val elevationMeters: Double?,
    val heartRateBpm: Int?,
    val energyKj: Double?,
    val rpe: Int?,
    val routeRef: String?,
)

@Entity(
    tableName = "activity_strength_sets",
    foreignKeys = [
        ForeignKey(
            entity = ActivityBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("blockId")],
)
data class ActivityStrengthSetEntity(
    @PrimaryKey val id: String,
    val blockId: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val completedAtMs: Long,
)

@Entity(
    tableName = "activity_cardio_intervals",
    foreignKeys = [
        ForeignKey(
            entity = ActivityBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("blockId")],
)
data class ActivityCardioIntervalEntity(
    @PrimaryKey val id: String,
    val blockId: String,
    val sortOrder: Int,
    val elapsedSeconds: Long,
    val distanceMeters: Double?,
    val rpe: Int?,
)
