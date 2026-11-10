package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.ui.workout.ActiveExerciseDraft
import com.sinura.personaltrainer.ui.workout.WorkoutPrimaryAction
import com.sinura.personaltrainer.ui.workout.WorkoutPrimaryIdentity
import com.sinura.personaltrainer.ui.workout.WorkoutPrimaryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** G2: machine new at gym — blank weight copy on floor, Up next, and Log (04-screens / 05-edge-cases). */
class NewAtGymSetCopyTest {
    @Test
    fun loadedWeightUnsetReadsAsRepsOnlyNotNoWeight() {
        assertEquals(
            "10 reps",
            SetCopy.setLine(0.0, 10, LoadClass.LOADED, WeightUnit.KG, loadedWeightUnset = true),
        )
        assertEquals(
            "no weight × 10",
            SetCopy.setLine(0.0, 10, LoadClass.LOADED, WeightUnit.KG, loadedWeightUnset = false),
        )
    }

    @Test
    fun weightHeroIsBlankDashNotZeroWhenUnset() {
        val hero = SetCopy.weightEntryHero(WeightMeaning.LIFTED, 0.0, WeightUnit.KG, loadedWeightUnset = true)
        assertEquals(SetCopy.NOTHING_YET, hero.value)
        assertEquals(null, hero.unitSuffix)
    }

    @Test
    fun microRecAtNewGymShowsRepsOnlyOnUpNext() {
        val inputs = setMicroRecInputs(
            editing = false,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
            targetSets = 1,
            targetReps = 10,
            targetWeightKg = null,
            working = emptyList(),
            lastAnySetWasWarmup = false,
            hint = null,
            lighterWeek = false,
            draftWeightKg = 0.0,
            draftReps = 10,
            draftRpe = null,
            nowMs = 0L,
            todayEpochDay = 0L,
            equipment = EquipmentType.MACHINE,
            loadedWeightUnset = true,
        )
        val rec = checkNotNull(SetMicroRecCalculator.suggest(inputs))
        assertEquals(SetMicroRecCalculator.NEW_AT_GYM, rec.reasonCode)
        assertEquals("10 reps", SetMicroRecCopy.numbers(rec, LoadClass.LOADED, WeightUnit.KG))
        assertEquals("Next: 10 reps", SetMicroRecCopy.line(rec, LoadClass.LOADED, WeightUnit.KG))
    }

    @Test
    fun logCommitOmitsBlankLoadPayload() {
        val draft = ActiveExerciseDraft(weightKg = 0.0, reps = 10, rpe = 7, isWarmup = false)
        val action = WorkoutPrimaryAction(
            identity = WorkoutPrimaryIdentity(
                kind = WorkoutPrimaryKind.LOG_SET,
                sessionId = "s1",
                exerciseId = "ex",
                editingSetId = null,
                draft = draft,
                sets = emptyList(),
                nextExerciseId = null,
                extraSet = false,
                timedGeneration = 0,
                activation = 0L,
                pendingSave = null,
            ),
            enabled = true,
        )
        assertNull(action.payload(WeightUnit.KG, LoadClass.LOADED, loadedWeightUnset = true))
        assertEquals(
            "no weight × 10 · RPE 7",
            action.payload(WeightUnit.KG, LoadClass.LOADED, loadedWeightUnset = false),
        )
    }
}
