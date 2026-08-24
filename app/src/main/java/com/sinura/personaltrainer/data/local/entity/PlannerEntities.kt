package com.sinura.personaltrainer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_rules",
    indices = [Index("weekday"), Index("enabled")],
)
data class ScheduleRuleEntity(
    @PrimaryKey val id: String,
    val weekday: Int,
    val hour: Int,
    val minute: Int,
    val modality: String,
    val zonePolicy: String,
    val fixedZoneId: String?,
    val routineId: String?,
    val templateId: String?,
    val focusKind: String?,
    val reminderOffsetMinutes: Int,
    val enabled: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "schedule_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("ruleId"),
        Index("localEpochDay"),
        Index(value = ["ruleId", "localEpochDay"]),
    ],
)
data class ScheduleOccurrenceEntity(
    @PrimaryKey val id: String,
    val ruleId: String,
    val status: String,
    val instantMs: Long,
    val zoneId: String,
    val offsetSeconds: Int,
    val localEpochDay: Long,
    val hour: Int,
    val minute: Int,
    val completedActivityId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "missed_work_decisions")
data class MissedWorkDecisionEntity(
    @PrimaryKey val weekStartEpochDay: Long,
    val choice: String,
    val decidedAtMs: Long,
)

@Entity(
    tableName = "reminder_deliveries",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("occurrenceId"), Index("status")],
)
data class ReminderDeliveryEntity(
    @PrimaryKey val id: String,
    val occurrenceId: String,
    val scheduledAtMs: Long,
    val status: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
