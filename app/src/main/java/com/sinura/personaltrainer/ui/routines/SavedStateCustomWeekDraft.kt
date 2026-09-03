package com.sinura.personaltrainer.ui.routines

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.CustomWeekLift
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.Weekday

/**
 * Mirrors the custom-week draft into [SavedStateHandle] so a process death
 * while naming days does not drop the staged lifts.
 *
 * Catalog stills and muscle credits are not stored; [Exercise.id] plus the
 * fields Confirm writes are enough to rebuild the week.
 */
class SavedStateCustomWeekDraft(private val handle: SavedStateHandle) {
    fun selectedDay(): Weekday? =
        handle.get<String>(KEY_SELECTED)?.let { runCatching { Weekday.valueOf(it) }.getOrNull() }

    fun userPickedDay(): Boolean = handle.get<Boolean>(KEY_USER_PICKED) ?: false

    fun days(): Map<Weekday, List<CustomWeekLift>>? {
        val rows = handle.get<ArrayList<Bundle>>(KEY_DAYS) ?: return null
        val rebuilt = linkedMapOf<Weekday, MutableList<CustomWeekLift>>()
        for (row in rows) {
            val day = runCatching { Weekday.valueOf(row.getString(DAY).orEmpty()) }.getOrNull()
                ?: continue
            val lift = liftFrom(row) ?: continue
            rebuilt.getOrPut(day) { mutableListOf() }.add(lift)
        }
        return rebuilt.mapValues { it.value.toList() }
    }

    fun write(
        selected: Weekday,
        days: Map<Weekday, List<CustomWeekLift>>,
        userPicked: Boolean,
    ) {
        handle[KEY_SELECTED] = selected.name
        handle[KEY_USER_PICKED] = userPicked
        val rows = ArrayList<Bundle>(days.values.sumOf { it.size })
        for ((day, lifts) in days) {
            for (lift in lifts) {
                rows.add(bundleOf(day, lift))
            }
        }
        handle[KEY_DAYS] = rows
    }

    private fun bundleOf(day: Weekday, lift: CustomWeekLift): Bundle = Bundle().apply {
        putString(DAY, day.name)
        putString(ID, lift.id)
        putString(EXERCISE_ID, lift.exercise.id)
        putString(NAME, lift.exercise.name)
        putString(MUSCLE_GROUP, lift.exercise.muscleGroup)
        putString(NOTES, lift.exercise.notes)
        putBoolean(IS_CUSTOM, lift.exercise.isCustom)
        putString(EQUIPMENT, lift.exercise.equipment.name)
        putString(LOAD_TYPE, lift.exercise.loadType.name)
        putString(MOVEMENT_KEY, lift.exercise.movementKey)
        putString(IMAGE_KEY, lift.exercise.imageKey)
        putInt(TARGET_SETS, lift.targetSets)
        putInt(TARGET_REPS, lift.targetReps)
        putInt(REST_SECONDS, lift.restSeconds)
        val weight = lift.targetWeightKg
        putBoolean(HAS_WEIGHT, weight != null)
        if (weight != null) putDouble(TARGET_WEIGHT, weight)
    }

    private fun liftFrom(row: Bundle): CustomWeekLift? {
        val id = row.getString(ID) ?: return null
        val exerciseId = row.getString(EXERCISE_ID) ?: return null
        return CustomWeekLift(
            id = id,
            exercise = Exercise(
                id = exerciseId,
                name = row.getString(NAME).orEmpty(),
                muscleGroup = row.getString(MUSCLE_GROUP).orEmpty(),
                notes = row.getString(NOTES).orEmpty(),
                isCustom = row.getBoolean(IS_CUSTOM),
                equipment = runCatching {
                    EquipmentType.valueOf(row.getString(EQUIPMENT).orEmpty())
                }.getOrDefault(EquipmentType.OTHER),
                loadType = runCatching {
                    LoadType.valueOf(row.getString(LOAD_TYPE).orEmpty())
                }.getOrDefault(LoadType.EXTERNAL),
                movementKey = row.getString(MOVEMENT_KEY),
                imageKey = row.getString(IMAGE_KEY),
            ),
            targetSets = row.getInt(TARGET_SETS),
            targetReps = row.getInt(TARGET_REPS),
            restSeconds = row.getInt(REST_SECONDS),
            targetWeightKg = if (row.getBoolean(HAS_WEIGHT)) {
                row.getDouble(TARGET_WEIGHT)
            } else {
                null
            },
        )
    }

    private companion object {
        const val KEY_SELECTED = "customWeek.selectedDay"
        const val KEY_USER_PICKED = "customWeek.userPicked"
        const val KEY_DAYS = "customWeek.days"
        const val DAY = "day"
        const val ID = "id"
        const val EXERCISE_ID = "exerciseId"
        const val NAME = "name"
        const val MUSCLE_GROUP = "muscleGroup"
        const val NOTES = "notes"
        const val IS_CUSTOM = "isCustom"
        const val EQUIPMENT = "equipment"
        const val LOAD_TYPE = "loadType"
        const val MOVEMENT_KEY = "movementKey"
        const val IMAGE_KEY = "imageKey"
        const val TARGET_SETS = "targetSets"
        const val TARGET_REPS = "targetReps"
        const val REST_SECONDS = "restSeconds"
        const val HAS_WEIGHT = "hasWeight"
        const val TARGET_WEIGHT = "targetWeightKg"
    }
}
