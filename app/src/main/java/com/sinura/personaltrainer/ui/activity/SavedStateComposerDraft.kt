package com.sinura.personaltrainer.ui.activity

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleCredit

/**
 * Mirrors the activity composer's draft into [SavedStateHandle] so process death while
 * typing up yesterday's session does not hand back an empty form dated today.
 *
 * Rotation kept the ViewModel; ordinary background reclaim did not, and only the mode and
 * the held plan link were in saved state. Same shape as the custom-week draft: one Bundle
 * per line, the lift's fields rather than its catalog still. Muscle credits are kept because
 * the strength block written at save time carries them to the body map; a lift deleted from
 * the library between death and restore still saves as the block it was going to be, since
 * every field the block needs is here and none is looked up again.
 *
 * Bounded by construction — a composer holds a handful of lines — so it stays well inside
 * the transaction limit saved instance state is subject to.
 */
class SavedStateComposerDraft(private val handle: SavedStateHandle) {
    /** True once anything was written. Distinguishes "empty draft" from "no draft yet". */
    fun exists(): Boolean = handle.contains(KEY_TITLE)

    fun title(): String = handle.get<String>(KEY_TITLE).orEmpty()

    fun epochDay(): Long? = handle.get<Long>(KEY_EPOCH_DAY)

    fun strength(): List<ComposerStrengthLine> =
        handle.get<ArrayList<Bundle>>(KEY_STRENGTH)?.mapNotNull { strengthFrom(it) }.orEmpty()

    fun cardio(): List<ComposerCardioLine> =
        handle.get<ArrayList<Bundle>>(KEY_CARDIO)?.mapNotNull { cardioFrom(it) }.orEmpty()

    fun write(
        title: String,
        epochDay: Long,
        strength: List<ComposerStrengthLine>,
        cardio: List<ComposerCardioLine>,
    ) {
        handle[KEY_TITLE] = title
        handle[KEY_EPOCH_DAY] = epochDay
        handle[KEY_STRENGTH] = ArrayList(strength.map { bundleOf(it) })
        handle[KEY_CARDIO] = ArrayList(cardio.map { bundleOf(it) })
    }

    /** After an accepted save or a deliberate leave: the next composer must open clean. */
    fun clear() {
        handle.remove<String>(KEY_TITLE)
        handle.remove<Long>(KEY_EPOCH_DAY)
        handle.remove<ArrayList<Bundle>>(KEY_STRENGTH)
        handle.remove<ArrayList<Bundle>>(KEY_CARDIO)
    }

    private fun bundleOf(line: ComposerStrengthLine): Bundle = Bundle().apply {
        val exercise = line.exercise
        putString(EXERCISE_ID, exercise.id)
        putString(NAME, exercise.name)
        putString(MUSCLE_GROUP, exercise.muscleGroup)
        putString(NOTES, exercise.notes)
        putBoolean(IS_CUSTOM, exercise.isCustom)
        putString(EQUIPMENT, exercise.equipment.name)
        putString(LOAD_TYPE, exercise.loadType.name)
        putString(MOVEMENT_KEY, exercise.movementKey)
        putString(IMAGE_KEY, exercise.imageKey)
        putStringArrayList(
            MUSCLES,
            ArrayList(exercise.muscles.map { "${it.muscleKey}$MUSCLE_SEP${it.weight}" }),
        )
        putDouble(WEIGHT_KG, line.weightKg)
        putInt(REPS, line.reps)
    }

    private fun strengthFrom(row: Bundle): ComposerStrengthLine? {
        val exerciseId = row.getString(EXERCISE_ID) ?: return null
        val reps = row.getInt(REPS)
        if (reps <= 0) return null
        return ComposerStrengthLine(
            exercise = Exercise(
                id = exerciseId,
                name = row.getString(NAME).orEmpty(),
                muscleGroup = row.getString(MUSCLE_GROUP).orEmpty(),
                notes = row.getString(NOTES).orEmpty(),
                isCustom = row.getBoolean(IS_CUSTOM),
                equipment = EquipmentType.fromStorage(row.getString(EQUIPMENT)),
                loadType = LoadType.fromStorage(row.getString(LOAD_TYPE)),
                movementKey = row.getString(MOVEMENT_KEY),
                imageKey = row.getString(IMAGE_KEY),
                muscles = row.getStringArrayList(MUSCLES).orEmpty().mapNotNull { encoded ->
                    val cut = encoded.lastIndexOf(MUSCLE_SEP)
                    if (cut <= 0) return@mapNotNull null
                    MuscleCredit(
                        muscleKey = encoded.substring(0, cut),
                        weight = encoded.substring(cut + 1).toDoubleOrNull() ?: 1.0,
                    )
                },
            ),
            weightKg = row.getDouble(WEIGHT_KG),
            reps = reps,
        )
    }

    private fun bundleOf(line: ComposerCardioLine): Bundle = Bundle().apply {
        putString(CARDIO_TYPE, line.type.name)
        putInt(MINUTES, line.minutes)
        putBoolean(INDOOR, line.indoor)
        val distance = line.distanceKm
        putBoolean(HAS_DISTANCE, distance != null)
        if (distance != null) putDouble(DISTANCE_KM, distance)
    }

    private fun cardioFrom(row: Bundle): ComposerCardioLine? {
        val raw = row.getString(CARDIO_TYPE) ?: return null
        val type = CardioType.entries.firstOrNull { it.name == raw } ?: return null
        val minutes = row.getInt(MINUTES)
        if (minutes <= 0) return null
        return ComposerCardioLine(
            type = type,
            minutes = minutes,
            distanceKm = if (row.getBoolean(HAS_DISTANCE)) row.getDouble(DISTANCE_KM) else null,
            indoor = row.getBoolean(INDOOR),
        )
    }

    private companion object {
        const val KEY_TITLE = "composer.title"
        const val KEY_EPOCH_DAY = "composer.epochDay"
        const val KEY_STRENGTH = "composer.strength"
        const val KEY_CARDIO = "composer.cardio"
        const val EXERCISE_ID = "exerciseId"
        const val NAME = "name"
        const val MUSCLE_GROUP = "muscleGroup"
        const val NOTES = "notes"
        const val IS_CUSTOM = "isCustom"
        const val EQUIPMENT = "equipment"
        const val LOAD_TYPE = "loadType"
        const val MOVEMENT_KEY = "movementKey"
        const val IMAGE_KEY = "imageKey"
        const val MUSCLES = "muscles"
        const val MUSCLE_SEP = "\t"
        const val WEIGHT_KG = "weightKg"
        const val REPS = "reps"
        const val CARDIO_TYPE = "cardioType"
        const val MINUTES = "minutes"
        const val INDOOR = "indoor"
        const val HAS_DISTANCE = "hasDistance"
        const val DISTANCE_KM = "distanceKm"
    }
}
