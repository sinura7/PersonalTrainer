package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reconstructing the questionnaire from what is already on the phone.
 *
 * Replay of an empty week is only safe if this mapping is honest: an empty equipment filter
 * is a full gym (no filtering), not "owns nothing", and a home-dumbbell kit must not come
 * back as a barbell program.
 */
class OnboardingAnswersRestoreTest {

    @Test
    fun emptyEquipmentIsAFullGym() {
        assertEquals(TrainingPlace.FULL_GYM, OnboardingAnswers.inferPlace(emptySet()))
    }

    @Test
    fun bodyweightKitInfersBodyweightOnly() {
        assertEquals(
            TrainingPlace.BODYWEIGHT_ONLY,
            OnboardingAnswers.inferPlace(setOf("BODYWEIGHT", "OTHER")),
        )
    }

    @Test
    fun homeDumbbellKitInfersHome() {
        assertEquals(
            TrainingPlace.HOME_DUMBBELLS,
            OnboardingAnswers.inferPlace(setOf("DUMBBELL", "BAND")),
        )
    }

    @Test
    fun aBarbellInTheFilterIsAFullGym() {
        assertEquals(
            TrainingPlace.FULL_GYM,
            OnboardingAnswers.inferPlace(setOf("BARBELL", "DUMBBELL")),
        )
    }

    @Test
    fun unknownTokensDoNotInventAPlace() {
        assertEquals(TrainingPlace.FULL_GYM, OnboardingAnswers.inferPlace(setOf("laser-cannon")))
    }

    @Test
    fun fromStoredSanitizesDaysAndKeepsThePickedWeekdays() {
        val answers = OnboardingAnswers.fromStored(
            trainingAge = TrainingAge.EXPERIENCED,
            daysPerWeek = 9,
            preferredDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            place = TrainingPlace.HOME_DUMBBELLS,
            goal = TrainingGoal.ATHLETIC,
            emphasis = TrainingEmphasis.UPPER,
            bodyweightKg = 82.0,
        )
        assertEquals(SchedulePreferences.MAX_DAYS, answers.daysPerWeek)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), answers.preferredDays)
        assertEquals(TrainingAge.EXPERIENCED, answers.trainingAge)
        assertEquals(TrainingGoal.ATHLETIC, answers.goal)
        assertEquals(TrainingEmphasis.UPPER, answers.emphasis)
        assertEquals(82.0, answers.bodyweightKg!!, 0.001)
    }
}
