package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun pickingGymClearsHomeAndBodyweight() {
        val mixed = OnboardingAnswers(
            places = setOf(TrainingPlace.HOME_DUMBBELLS, TrainingPlace.BODYWEIGHT_ONLY),
        ).sanitized()
        val gym = mixed.withToggledPlace(TrainingPlace.FULL_GYM)
        assertEquals(setOf(TrainingPlace.FULL_GYM), gym.resolvedPlaces())
        val home = gym.withToggledPlace(TrainingPlace.HOME_DUMBBELLS)
        assertEquals(setOf(TrainingPlace.HOME_DUMBBELLS), home.resolvedPlaces())
    }

    @Test
    fun placeCopyDoesNotPromiseTwoSchedules() {
        assertTrue(TrainingPlace.STEP_BLURB.contains("Hyper Pro is its own kit"))
        assertFalse(TrainingPlace.STEP_BLURB.contains("gym days and home days"))
        assertEquals(
            "Gym plus Hyper Pro.",
            TrainingPlace.mixCaption(setOf(TrainingPlace.FULL_GYM, TrainingPlace.HYPER_PRO)),
        )
        assertTrue(
            TrainingPlace.mixCaption(
                setOf(TrainingPlace.HOME_DUMBBELLS, TrainingPlace.BODYWEIGHT_ONLY),
            ).contains("No barbell"),
        )
    }

    @Test
    fun gymDoesNotIncludeHyperProUntilAsked() {
        assertFalse(EquipmentType.HYPER_PRO in TrainingPlace.FULL_GYM.equipment)
        assertTrue(EquipmentType.HYPER_PRO in TrainingPlace.HYPER_PRO.equipment)
        val gym = OnboardingAnswers(places = setOf(TrainingPlace.FULL_GYM)).sanitized()
        assertFalse(EquipmentType.HYPER_PRO in gym.equipment())
        val mixed = gym.withToggledPlace(TrainingPlace.HYPER_PRO)
        assertEquals(
            setOf(TrainingPlace.FULL_GYM, TrainingPlace.HYPER_PRO),
            mixed.resolvedPlaces(),
        )
        assertTrue(EquipmentType.HYPER_PRO in mixed.equipment())
        assertTrue(EquipmentType.BARBELL in mixed.equipment())
    }

    @Test
    fun hyperProAloneDoesNotOfferABarbell() {
        val only = OnboardingAnswers(places = setOf(TrainingPlace.HYPER_PRO)).sanitized()
        assertEquals(setOf(EquipmentType.HYPER_PRO), only.equipment())
        assertEquals(TrainingPlace.HYPER_PRO, OnboardingAnswers.inferPlace(setOf("HYPER_PRO")))
    }

    @Test
    fun emptyCoachKitStillHidesHyperPro() {
        assertFalse(CoachPreferences().allows(EquipmentType.HYPER_PRO))
        assertTrue(CoachPreferences().allows(EquipmentType.BARBELL))
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
            preferredDays = setOf(Weekday.TUESDAY, Weekday.THURSDAY),
            place = TrainingPlace.HOME_DUMBBELLS,
            goal = TrainingGoal.ATHLETIC,
            emphasis = TrainingEmphasis.UPPER,
            bodyweightKg = 82.0,
        )
        assertEquals(SchedulePreferences.MAX_DAYS, answers.daysPerWeek)
        assertEquals(setOf(Weekday.TUESDAY, Weekday.THURSDAY), answers.preferredDays)
        assertEquals(TrainingAge.EXPERIENCED, answers.trainingAge)
        assertEquals(TrainingGoal.ATHLETIC, answers.goal)
        assertEquals(TrainingEmphasis.UPPER, answers.emphasis)
        assertEquals(82.0, answers.bodyweightKg!!, 0.001)
    }

    @Test
    fun draftEncodingRoundTripsGuidedAnswers() {
        val original = OnboardingAnswers(
            trainingAge = TrainingAge.EXPERIENCED,
            daysPerWeek = 4,
            preferredDays = setOf(Weekday.TUESDAY, Weekday.THURSDAY),
            place = TrainingPlace.HOME_DUMBBELLS,
            places = setOf(TrainingPlace.HOME_DUMBBELLS, TrainingPlace.BODYWEIGHT_ONLY),
            goal = TrainingGoal.ATHLETIC,
            emphasis = TrainingEmphasis.UPPER,
            bodyweightKg = 80.0,
        )
        val restored = OnboardingAnswers.decodeDraft(OnboardingAnswers.encodeDraft(original))!!
        assertEquals(TrainingAge.EXPERIENCED, restored.trainingAge)
        assertEquals(4, restored.daysPerWeek)
        assertEquals(setOf(Weekday.TUESDAY, Weekday.THURSDAY), restored.preferredDays)
        assertEquals(
            setOf(TrainingPlace.HOME_DUMBBELLS, TrainingPlace.BODYWEIGHT_ONLY),
            restored.resolvedPlaces(),
        )
        assertEquals(TrainingGoal.ATHLETIC, restored.goal)
        assertEquals(TrainingEmphasis.UPPER, restored.emphasis)
        assertEquals(80.0, restored.bodyweightKg!!, 0.001)
        assertEquals(TrainingFocus.STRENGTH, restored.focus)
        assertNull(OnboardingAnswers.decodeDraft(""))

        // Explicit kit survives the draft and an eight-field draft from before it still decodes.
        val kitted = OnboardingAnswers(availableEquipment = setOf("DUMBBELL", "BARBELL"))
        assertEquals(setOf("BARBELL", "DUMBBELL"), OnboardingAnswers.decodeDraft(OnboardingAnswers.encodeDraft(kitted))!!.availableEquipment)
        val eightFields = OnboardingAnswers.encodeDraft(kitted).substringBeforeLast("|")
        assertEquals(emptySet<String>(), OnboardingAnswers.decodeDraft(eightFields)!!.availableEquipment)
    }

    @Test
    fun draftCarriesFocusAndOldSevenPartDraftsDefaultToStrength() {
        val both = OnboardingAnswers(focus = TrainingFocus.BOTH)
        assertEquals(TrainingFocus.BOTH, OnboardingAnswers.decodeDraft(OnboardingAnswers.encodeDraft(both))!!.focus)
        // A seven-part draft from before focus (and before kit) existed: drop the last two fields.
        val old = OnboardingAnswers.encodeDraft(OnboardingAnswers()).split("|").take(7).joinToString("|")
        assertEquals(6, old.count { it == '|' })
        assertEquals(TrainingFocus.STRENGTH, OnboardingAnswers.decodeDraft(old)!!.focus)
        assertEquals(TrainingFocus.CARDIO, TrainingFocus.fromStorage("cardio"))
        assertEquals(TrainingFocus.STRENGTH, TrainingFocus.fromStorage(null))
        assertEquals(TrainingFocus.STRENGTH, TrainingFocus.fromStorage("NOPE"))
    }

    @Test
    fun wheelDoesNotCommitTheParkedPageUntilTheLifterFlicks() {
        assertFalse(BodyweightSteps.shouldCommitSettledPage(settledPage = 40, initialPage = 40))
        assertTrue(BodyweightSteps.shouldCommitSettledPage(settledPage = 41, initialPage = 40))
        assertFalse(BodyweightSteps.shouldCommitSettledPage(settledPage = 40, initialPage = 40))
    }

    @Test
    fun wheelUnitToggleDoesNotWalkTheStoredKilograms() {
        val start = 80.0
        assertEquals(176, BodyweightSteps.displayOf(start, WeightUnit.LBS))
        assertEquals(80, BodyweightSteps.displayOf(start, WeightUnit.KG))
        var kg = start
        repeat(5) {
            val lbs = BodyweightSteps.displayOf(kg, WeightUnit.LBS)
            kg = BodyweightSteps.toKg(lbs, WeightUnit.LBS)
            val kilos = BodyweightSteps.displayOf(kg, WeightUnit.KG)
            kg = BodyweightSteps.toKg(kilos, WeightUnit.KG)
        }
        assertEquals(80, BodyweightSteps.displayOf(kg, WeightUnit.KG))
        assertEquals(176, BodyweightSteps.displayOf(kg, WeightUnit.LBS))
    }

    @Test
    fun shrinkingDaysKeepsTheFirstPreferredInWeekOrder() {
        val four = OnboardingAnswers(
            daysPerWeek = 4,
            preferredDays = setOf(
                Weekday.SUNDAY,
                Weekday.MONDAY,
                Weekday.WEDNESDAY,
                Weekday.FRIDAY,
            ),
        )
        val three = four.withDaysPerWeek(3)
        assertEquals(3, three.daysPerWeek)
        assertEquals(
            setOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY),
            three.preferredDays,
        )
        assertEquals(four.preferredDays, four.withDaysPerWeek(5).preferredDays)
    }

    @Test
    fun shrinkingDaysHonoursTheLiftersWeekStart() {
        // A Sunday-week lifter's "first N days in week order" starts on Sunday.
        // Trimming from the Monday default cost them the first day of their week.
        val four = OnboardingAnswers(
            daysPerWeek = 4,
            preferredDays = setOf(
                Weekday.SUNDAY,
                Weekday.MONDAY,
                Weekday.WEDNESDAY,
                Weekday.FRIDAY,
            ),
        )
        val three = four.withDaysPerWeek(3, weekStart = Weekday.SUNDAY)
        assertEquals(
            setOf(Weekday.SUNDAY, Weekday.MONDAY, Weekday.WEDNESDAY),
            three.preferredDays,
        )
    }

    @Test
    fun storedEquipmentWinsUntilPlaceChanges() {
        val gym = OnboardingAnswers(
            places = setOf(TrainingPlace.FULL_GYM),
            availableEquipment = setOf(EquipmentType.DUMBBELL.name, EquipmentType.BODYWEIGHT.name),
        ).sanitized()
        assertFalse(gym.coachPreferences().allows(EquipmentType.BARBELL))
        assertTrue(gym.coachPreferences().allows(EquipmentType.DUMBBELL))
        val afterPlace = gym.withToggledPlace(TrainingPlace.HOME_DUMBBELLS)
        assertTrue(afterPlace.availableEquipment.isEmpty())
        assertFalse(afterPlace.coachPreferences().allows(EquipmentType.BARBELL))
        assertTrue(afterPlace.coachPreferences().allows(EquipmentType.DUMBBELL))
    }

    @Test
    fun fromStoredKeepsAnExplicitKit() {
        val answers = OnboardingAnswers.fromStored(
            trainingAge = TrainingAge.NEW,
            daysPerWeek = 4,
            preferredDays = emptySet(),
            place = TrainingPlace.FULL_GYM,
            goal = TrainingGoal.STRENGTH,
            emphasis = TrainingEmphasis.BALANCED,
            bodyweightKg = null,
            availableEquipment = setOf(EquipmentType.CABLE.name),
        )
        assertEquals(setOf(EquipmentType.CABLE.name), answers.availableEquipment)
        assertTrue(answers.coachPreferences().allows(EquipmentType.CABLE))
        assertFalse(answers.coachPreferences().allows(EquipmentType.BARBELL))
    }
}
