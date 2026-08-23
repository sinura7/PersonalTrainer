package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun mixedPlacesUnionTheKitAndNeverEmpty() {
        val mixed = OnboardingAnswers(places = setOf(TrainingPlace.HOME_DUMBBELLS, TrainingPlace.BODYWEIGHT_ONLY))
            .sanitized()
        assertEquals(
            setOf(TrainingPlace.HOME_DUMBBELLS, TrainingPlace.BODYWEIGHT_ONLY),
            mixed.resolvedPlaces(),
        )
        assertEquals(TrainingPlace.HOME_DUMBBELLS, mixed.place)
        assertTrue(EquipmentType.DUMBBELL in mixed.equipment())
        assertTrue(EquipmentType.BODYWEIGHT in mixed.equipment())
        assertFalse(EquipmentType.BARBELL in mixed.equipment())
    }

    @Test
    fun togglingTheLastPlaceIsRefused() {
        val start = OnboardingAnswers(places = setOf(TrainingPlace.FULL_GYM)).sanitized()
        val same = start.withToggledPlace(TrainingPlace.FULL_GYM)
        assertEquals(setOf(TrainingPlace.FULL_GYM), same.resolvedPlaces())
    }

    @Test
    fun commaSeparatedStorageRoundTripsAMix() {
        val raw = TrainingPlace.formatPlaces(
            setOf(TrainingPlace.FULL_GYM, TrainingPlace.HOME_DUMBBELLS),
        )
        assertEquals(setOf(TrainingPlace.FULL_GYM, TrainingPlace.HOME_DUMBBELLS), TrainingPlace.parsePlaces(raw))
        assertEquals(TrainingPlace.FULL_GYM, TrainingPlace.fromStorage(raw))
    }

    @Test
    fun bodyweightStepsStayInsideTheStoredRange() {
        BodyweightSteps.displayValues(WeightUnit.KG).forEach { display ->
            val kg = BodyweightSteps.toKg(display, WeightUnit.KG)
            assertTrue(kg in OnboardingAnswers.MIN_BODYWEIGHT_KG..OnboardingAnswers.MAX_BODYWEIGHT_KG)
        }
        BodyweightSteps.displayValues(WeightUnit.LBS).forEach { display ->
            val kg = BodyweightSteps.toKg(display, WeightUnit.LBS)
            assertTrue(
                kg >= OnboardingAnswers.MIN_BODYWEIGHT_KG - 0.6 &&
                    kg <= OnboardingAnswers.MAX_BODYWEIGHT_KG + 0.6,
            )
        }
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

    @Test
    fun wheelDoesNotCommitTheParkedPageUntilTheLifterFlicks() {
        assertFalse(BodyweightSteps.shouldCommitSettledPage(settledPage = 40, initialPage = 40, alreadyChosen = false))
        assertTrue(BodyweightSteps.shouldCommitSettledPage(settledPage = 41, initialPage = 40, alreadyChosen = false))
        assertTrue(BodyweightSteps.shouldCommitSettledPage(settledPage = 40, initialPage = 40, alreadyChosen = true))
    }
}
