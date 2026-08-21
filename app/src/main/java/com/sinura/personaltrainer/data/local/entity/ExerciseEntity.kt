package com.sinura.personaltrainer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A lift in the library.
 *
 * [muscleGroup] survives as free-text display copy — the planner, the library filter and every
 * v1 backup read it — while what a lift actually trains now lives in `exercise_muscles`.
 *
 * [nameKey] is the normalized name, written by exactly one function
 * ([com.sinura.personaltrainer.domain.MuscleNormalizer.nameKeyOf]) at every site that writes it.
 * Its index is plain, not unique: SQLite can express a partial unique index but Room cannot
 * declare one, and Room validates the live schema against its own expectation at open — an
 * index it does not know about is a permanent crash loop on a phone with no destructive
 * fallback. Uniqueness among built-ins is therefore a seed-data invariant test, and uniqueness
 * against customs is an app-layer check on create and rename.
 */
@Entity(tableName = "exercises", indices = [Index("nameKey")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String,
    val notes: String,
    val isCustom: Boolean,
    @ColumnInfo(defaultValue = "OTHER") val equipment: String = "OTHER",
    @ColumnInfo(defaultValue = "EXTERNAL") val loadType: String = "EXTERNAL",
    val movementKey: String? = null,
    val imageKey: String? = null,
    @ColumnInfo(defaultValue = "''") val nameKey: String = "",
)
