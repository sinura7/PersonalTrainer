package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramDoseTest {
    @Test
    fun aGeneralFourDayReturningSessionMatchesHandAddedDefaults() {
        val dose = SessionDose(
            age = TrainingAge.RETURNING,
            goal = TrainingGoal.GENERAL,
            daysPerWeek = 4,
        )
        LoadType.entries.forEach { loadType ->
            listOf(true, false).forEach { compound ->
                listOf(LiftRole.PRIMARY, LiftRole.ACCESSORY).forEach { role ->
                    assertEquals(
                        "$loadType/$compound/$role",
                        AddDefaults.forExercise(loadType, compound, role),
                        ProgramDose.apply(
                            AddDefaults.forExercise(loadType, compound, role),
                            compound,
                            role,
                            dose,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun novicesStayAtThreeSetsAndLearnCompoundsAtEightToTen() {
        val dose = SessionDose(TrainingAge.NEW, TrainingGoal.STRENGTH, daysPerWeek = 2)
        val squat = ProgramDose.apply(
            AddDefaults.forExercise(LoadType.EXTERNAL, true, LiftRole.PRIMARY),
            isCompound = true,
            role = LiftRole.PRIMARY,
            dose = dose,
        )
        assertEquals(3, squat.sets)
        assertTrue(squat.reps in 8..10)
        assertEquals(120, squat.restSeconds)
    }

    @Test
    fun aShortTrainedWeekAddsASetOnTheOpener() {
        val dose = SessionDose(TrainingAge.RETURNING, TrainingGoal.HYPERTROPHY, daysPerWeek = 2)
        assertEquals(4, ProgramDose.setsFor(LiftRole.PRIMARY, dose.age, dose.daysPerWeek))
        assertEquals(3, ProgramDose.setsFor(LiftRole.ACCESSORY, dose.age, dose.daysPerWeek))
    }

    @Test
    fun aLongTrainedWeekCutsAccessorySets() {
        val dose = SessionDose(TrainingAge.EXPERIENCED, TrainingGoal.HYPERTROPHY, daysPerWeek = 6)
        assertEquals(3, ProgramDose.setsFor(LiftRole.PRIMARY, dose.age, dose.daysPerWeek))
        assertEquals(2, ProgramDose.setsFor(LiftRole.ACCESSORY, dose.age, dose.daysPerWeek))
    }

    @Test
    fun strengthOpenersAreHeavyAndLongRested() {
        val dose = SessionDose(TrainingAge.EXPERIENCED, TrainingGoal.STRENGTH, daysPerWeek = 4)
        val squat = ProgramDose.apply(
            AddDefaults.forExercise(LoadType.EXTERNAL, true, LiftRole.PRIMARY),
            isCompound = true,
            role = LiftRole.PRIMARY,
            dose = dose,
        )
        assertEquals(5, squat.reps)
        assertEquals(180, squat.restSeconds)
    }

    @Test
    fun hypertrophyOpenersSitInTheEightToTwelveRange() {
        val dose = SessionDose(TrainingAge.RETURNING, TrainingGoal.HYPERTROPHY, daysPerWeek = 4)
        val squat = ProgramDose.apply(
            AddDefaults.forExercise(LoadType.EXTERNAL, true, LiftRole.PRIMARY),
            isCompound = true,
            role = LiftRole.PRIMARY,
            dose = dose,
        )
        assertEquals(8, squat.reps)
        assertEquals(90, squat.restSeconds)
    }

    @Test
    fun resilienceOpenersSitWithHypertrophyDose() {
        val dose = SessionDose(TrainingAge.RETURNING, TrainingGoal.RESILIENCE, daysPerWeek = 4)
        val squat = ProgramDose.apply(
            AddDefaults.forExercise(LoadType.EXTERNAL, true, LiftRole.PRIMARY),
            isCompound = true,
            role = LiftRole.PRIMARY,
            dose = dose,
        )
        assertEquals(8, squat.reps)
        assertEquals(90, squat.restSeconds)
    }
}
