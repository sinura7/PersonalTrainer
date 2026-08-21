package com.sinura.personaltrainer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One position in the training cycle.
 *
 * Deliberately NOT a `routineId × weekday` row. A weekday table can only express "Push on
 * Mondays", and cannot express a three-on-one-off rotation at all — the cycle would have to be
 * re-derived into weekdays every time it drifted, which is how a plan starts lying about
 * itself. An ordered list of slots expresses both: [anchorDay] is a *preference* for where the
 * cycle prefers to sit in a week, and the effective week is derived from it, never written back.
 *
 * A slot is either a routine slot ([routineId] set) or a focus-only slot ([focusKind] set),
 * never neither. A position with no slot is rest.
 *
 * Phase 3 ships the table, its DAO and its backup coverage. Nothing writes a slot until the
 * Plan tab does.
 */
@Entity(
    tableName = "schedule_slots",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class ScheduleSlotEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val routineId: String?,
    val focusKind: String?,
    val anchorDay: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)
