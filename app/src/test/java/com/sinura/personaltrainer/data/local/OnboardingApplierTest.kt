package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.repository.AcceptCrashPoint
import com.sinura.personaltrainer.data.repository.ApplyPlanResult
import com.sinura.personaltrainer.data.repository.OnboardingApplier
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.domain.CustomWeekLift
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.RoutineGenerator
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * The one moment in the app that writes across preferences, routines, their exercises and the
 * schedule at once.
 *
 * What matters is not that it wrote something — it is that the thing it wrote is trainable.
 * A pinned Tuesday pointing at a routine that was never created is exactly the empty-session
 * defect this whole phase exists to end, and it would look like success from the outside.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingApplierTest {
    private lateinit var database: TrainerDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var schedule: ScheduleRepository
    private lateinit var preferences: PreferencesRepository
    private lateinit var applier: OnboardingApplier
    private lateinit var preferencesScope: CoroutineScope

    private val catalog: List<Exercise> = DefaultExercises.catalog().map { seed ->
        Exercise(
            id = seed.id, name = seed.name, muscleGroup = seed.muscleGroup, notes = "",
            isCustom = false, equipment = seed.equipment, loadType = seed.loadType,
            movementKey = seed.movementKey, muscles = seed.credits,
        )
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // The catalog has to exist as rows: addExercise writes a foreign key to it.
        runBlocking {
            database.exerciseDao().insertAll(
                DefaultExercises.catalog().map { seed ->
                    com.sinura.personaltrainer.data.local.entity.ExerciseEntity(
                        id = seed.id, name = seed.name, muscleGroup = seed.muscleGroup,
                        notes = "", isCustom = false, equipment = seed.equipment.name,
                        loadType = seed.loadType.name, movementKey = seed.movementKey,
                        imageKey = null,
                        nameKey = com.sinura.personaltrainer.domain.MuscleNormalizer.nameKeyOf(seed.name),
                    )
                },
            )
        }
        routines = RoutineRepository(database.routineDao())
        schedule = ScheduleRepository(database.scheduleDao())
        // A process-global DataStore survives individual Robolectric contexts.
        // Own and close this fixture's store, just as the other database fixtures do.
        preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = PreferenceDataStoreFactory.create(
            scope = preferencesScope,
            produceFile = { File(context.cacheDir, "onboarding-${System.nanoTime()}.preferences_pb") },
        )
        preferences = PreferencesRepository(context = context, dataStore = store)
        applier = OnboardingApplier(database, routines, schedule, preferences)
    }

    @After
    fun tearDown() {
        preferencesScope.cancel()
        database.close()
    }

    private fun answers(
        days: Int = 4,
        age: TrainingAge = TrainingAge.RETURNING,
        place: TrainingPlace = TrainingPlace.FULL_GYM,
        preferred: Set<Weekday> = emptySet(),
        bodyweight: Double? = null,
    ) = OnboardingAnswers(
        trainingAge = age,
        daysPerWeek = days,
        preferredDays = preferred,
        place = place,
        bodyweightKg = bodyweight,
    )

    @Test
    fun setupStartsANewBlockAndRecordsItsOpeningWeighIn() = runBlocking {
        val input = answers(bodyweight = 82.0)
        val running = TrainingBlock(startEpochDay = TODAY.toEpochDay() - 21, weeks = 12)
        preferences.beginBlock(running, TODAY.toEpochDay())

        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY)

        assertNotEquals(running, preferences.trainingBlock.first())
        assertEquals(listOf(TODAY.toEpochDay()), preferences.bodyweightLog.first().map { it.epochDay })
    }

    @Test
    fun generateAWeekLeavesEvenAFinishedBlockAloneAndRecordsNoWeighIn() = runBlocking {
        // A finished block stays current until Your plan starts the next twelve.
        val input = answers(bodyweight = 82.0)
        val finished = TrainingBlock(startEpochDay = TODAY.toEpochDay() - 84, weeks = 12)
        preferences.beginBlock(finished, TODAY.toEpochDay())

        applier.apply(
            input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY,
            keepCurrentBlock = true,
        )

        assertEquals(finished, preferences.trainingBlock.first())
        assertTrue(preferences.pastBlocks.first().isEmpty())
        assertTrue(preferences.bodyweightLog.first().isEmpty())
    }

    @Test
    fun everyPinnedDayPointsAtARoutineThatHasLifts() = runBlocking {
        val input = answers(days = 4)
        val blueprint = RoutineGenerator.generate(input, catalog)
        val result = applier.apply(input, blueprint, catalog, WEEK_START, TODAY)
        assertTrue(result is ApplyPlanResult.Applied)

        val slots = schedule.slots()
        assertEquals(4, slots.size)
        slots.forEach { slot ->
            val routineId = slot.routineId
            assertTrue("a pinned slot must name a routine, not a bare focus", routineId != null)
            val routine = routines.getById(routineId!!)
            assertTrue("slot points at a routine that does not exist", routine != null)
            assertTrue("${routine!!.name} has no lifts", routine.exercises.isNotEmpty())
        }
    }

    @Test
    fun theRoutinesCarryTheTargetsTheBlueprintPromised() = runBlocking {
        val input = answers(days = 4)
        val blueprint = RoutineGenerator.generate(input, catalog)
        applier.apply(input, blueprint, catalog, WEEK_START, TODAY)

        blueprint.routines.forEach { planned ->
            val built = routines.observeAll().first().first { it.name == planned.name }
            assertEquals(planned.lifts.size, built.exercises.size)
            planned.lifts.forEachIndexed { index, lift ->
                val actual = built.exercises[index]
                assertEquals(lift.exerciseId, actual.exercise.id)
                assertEquals(lift.targets.sets, actual.targetSets)
                assertEquals(lift.targets.reps, actual.targetReps)
                assertEquals(lift.targets.restSeconds, actual.restSeconds)
            }
        }
    }

    @Test
    fun theAnswersLandInPreferences() = runBlocking {
        val input = answers(days = 5, place = TrainingPlace.HOME_DUMBBELLS, bodyweight = 82.0)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY)

        assertEquals(5, preferences.schedulePreferences.first().trainingDaysPerWeek)
        assertEquals(82.0, preferences.bodyweightKg.first()!!, 0.001)
        assertTrue(preferences.onboardingComplete.first())
        // A limited kit becomes a real filter; a full gym would leave it empty, meaning
        // gym-floor (everything except Hyper Pro), which is not the same as "owns nothing".
        val equipment = preferences.coachPreferences.first().availableEquipment
        assertTrue(equipment.isNotEmpty())
        assertTrue("BARBELL" !in equipment)
        assertEquals(TrainingAge.RETURNING, preferences.trainingAge.first())
        assertEquals(TrainingPlace.HOME_DUMBBELLS, preferences.trainingPlace.first())
    }

    @Test
    fun aFullGymFiltersNothing() = runBlocking {
        val input = answers(place = TrainingPlace.FULL_GYM)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY)
        assertEquals(emptySet<String>(), preferences.coachPreferences.first().availableEquipment)
        assertEquals(TrainingEmphasis.BALANCED, preferences.coachPreferences.first().emphasis)
    }

    @Test
    fun emphasisLandsInPreferencesWithoutRewritingTheWeekShapeHere() = runBlocking {
        val input = answers(days = 4).copy(emphasis = TrainingEmphasis.UPPER)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY)
        assertEquals(TrainingEmphasis.UPPER, preferences.coachPreferences.first().emphasis)
    }

    @Test
    fun theDaysTheLifterPickedAreTheDaysThatGetPinned() = runBlocking {
        val picked = setOf(Weekday.TUESDAY, Weekday.THURSDAY, Weekday.SUNDAY)
        val input = answers(days = 3, preferred = picked)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY)
        assertEquals(picked, schedule.slots().mapNotNull { it.anchorDay }.toSet())
        assertEquals(picked, preferences.preferredDays.first())
    }

    @Test
    fun aFailedApplyLeavesNoRoutinesForTheRetryToDuplicate() = runBlocking {
        val mine = routines.create(name = "My Own Thing")
        preferences.setOnboardingComplete(false)
        val input = answers(days = 3)
        val blueprint = RoutineGenerator.generate(input, catalog)
        val failing = OnboardingApplier(
            database = database,
            routineRepository = routines,
            scheduleRepository = schedule,
            preferencesRepository = preferences,
            failAfterRoutines = true,
        )
        val failed = failing.apply(input, blueprint, catalog, WEEK_START, TODAY)
        assertTrue(failed is ApplyPlanResult.Failed)
        assertEquals(1, routines.count())
        assertEquals(mine.id, routines.observeAll().first().single().id)
        assertEquals(emptyList<com.sinura.personaltrainer.domain.ScheduleSlot>(), schedule.slots())
        assertEquals(false, preferences.onboardingComplete.first())

        val retry = applier.apply(input, blueprint, catalog, WEEK_START, TODAY)
        val applied = retry as ApplyPlanResult.Applied
        assertEquals(1 + applied.routineCount, routines.count())
        assertTrue(routines.getById(mine.id) != null)
        assertEquals(true, preferences.onboardingComplete.first())
    }

    @Test
    fun rerunningSetupAddsAlongsideAndNeverDeletes() = runBlocking {
        // Someone with months of history pointing at their routines must not lose them because
        // they answered the questions again.
        val mine = routines.create(name = "My Own Thing")
        val input = answers(days = 3)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY)
        val firstCount = routines.count()

        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY)
        assertTrue("the second run must add, not replace", routines.count() > firstCount)
        assertTrue("the lifter's own routine survived", routines.getById(mine.id) != null)
    }

    @Test
    fun anExistingProgramIsDetectedSoTheScreenCanWarn() = runBlocking {
        assertEquals(false, applier.hasExistingProgram())
        val input = answers(days = 2)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog, WEEK_START, TODAY)
        assertEquals(true, applier.hasExistingProgram())
    }

    @Test
    fun aBodyweightOnlySetupStillProducesATrainableWeek() = runBlocking {
        val input = answers(days = 4, age = TrainingAge.NEW, place = TrainingPlace.BODYWEIGHT_ONLY)
        val blueprint = RoutineGenerator.generate(input, catalog)
        applier.apply(input, blueprint, catalog, WEEK_START, TODAY)

        schedule.slots().forEach { slot ->
            val routine = routines.getById(slot.routineId!!)!!
            assertEquals(TrainingAge.NEW.liftsPerSession, routine.exercises.size)
            routine.exercises.forEach { item ->
                assertTrue(
                    "${item.exercise.name} needs kit a bodyweight lifter has not got",
                    item.exercise.equipment in TrainingPlace.BODYWEIGHT_ONLY.equipment,
                )
            }
        }
    }

    @Test
    fun aCustomWeekPinsEachFilledDay() = runBlocking {
        val squat = catalog.first { it.movementKey == "squat" }
        preferences.setTrainingGoal(TrainingGoal.STRENGTH)
        val result = applier.applyCustom(
            days = mapOf(
                Weekday.WEDNESDAY to listOf(
                    CustomWeekLift(id = "lift-1", exercise = squat, targetSets = 4, targetReps = 6, restSeconds = 120),
                ),
            ),
            weekStart = WEEK_START,
            today = TODAY,
        )
        assertTrue(result is ApplyPlanResult.Applied)
        val slots = schedule.slots()
        assertEquals(1, slots.size)
        assertEquals(Weekday.WEDNESDAY, slots.first().anchorDay)
        val routine = routines.getById(slots.first().routineId!!)!!
        assertEquals("Wednesday", routine.name)
        assertEquals(4, routine.exercises.first().targetSets)
        assertEquals(true, preferences.onboardingComplete.first())
        // Fork-only must not invent questionnaire fields.
        assertEquals(TrainingGoal.STRENGTH, preferences.coachPreferences.first().goal)
    }

    @Test
    fun aCustomWeekStoresTheTargetWeight() = runBlocking {
        val squat = catalog.first { it.movementKey == "squat" }
        val result = applier.applyCustom(
            days = mapOf(
                Weekday.MONDAY to listOf(
                    CustomWeekLift(
                        id = "lift-1",
                        exercise = squat,
                        targetSets = 3,
                        targetReps = 5,
                        restSeconds = 150,
                        targetWeightKg = 80.0,
                    ),
                ),
            ),
            weekStart = WEEK_START,
            today = TODAY,
        )
        assertTrue(
            "Outcome: $result; logs: ${ShadowLog.getLogsForTag("PT/Onboarding").joinToString { it.msg }}",
            result is ApplyPlanResult.Applied,
        )
        val routine = routines.getById(schedule.slots().first().routineId!!)!!
        assertEquals(80.0, routine.exercises.first().targetWeightKg!!, 0.001)
    }

    @Test
    fun aCustomWeekFromGuidedKeepsTheQuestionnaire() = runBlocking {
        val squat = catalog.first { it.movementKey == "squat" }
        val guided = answers(
            days = 4,
            age = TrainingAge.EXPERIENCED,
            place = TrainingPlace.HOME_DUMBBELLS,
            preferred = setOf(Weekday.TUESDAY, Weekday.THURSDAY),
            bodyweight = 80.0,
        ).copy(goal = TrainingGoal.ATHLETIC, emphasis = TrainingEmphasis.UPPER)
        val result = applier.applyCustom(
            days = mapOf(
                Weekday.WEDNESDAY to listOf(
                    CustomWeekLift(id = "lift-1", exercise = squat, targetSets = 4, targetReps = 6, restSeconds = 120),
                ),
            ),
            weekStart = WEEK_START,
            today = TODAY,
            answers = guided,
        )
        assertTrue(result is ApplyPlanResult.Applied)
        assertEquals(TrainingAge.EXPERIENCED, preferences.trainingAge.first())
        assertEquals(TrainingPlace.HOME_DUMBBELLS, preferences.trainingPlace.first())
        assertEquals(TrainingGoal.ATHLETIC, preferences.coachPreferences.first().goal)
        assertEquals(TrainingEmphasis.UPPER, preferences.coachPreferences.first().emphasis)
        assertEquals(80.0, preferences.bodyweightKg.first()!!, 0.001)
        assertEquals(setOf(Weekday.WEDNESDAY), preferences.preferredDays.first())
    }

    @Test
    fun theSameAcceptanceDoesNotAddASecondProgram() = runBlocking {
        val input = answers(days = 3, bodyweight = 80.0)
        val blueprint = RoutineGenerator.generate(input, catalog)
        val first = applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-1",
        ) as ApplyPlanResult.Applied
        val routineCount = routines.count()
        val slotCount = schedule.slots().size
        val block = preferences.trainingBlock.first()
        val weighIns = preferences.bodyweightLog.first().size
        assertTrue(routineCount > 0)
        assertEquals(first.pinnedDays, slotCount)

        val second = applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-1",
        ) as ApplyPlanResult.Applied
        assertEquals(routineCount, routines.count())
        assertEquals(slotCount, schedule.slots().size)
        assertEquals(block, preferences.trainingBlock.first())
        assertEquals(weighIns, preferences.bodyweightLog.first().size)
        assertEquals(first.routineCount, second.routineCount)
        assertEquals("visit-1", preferences.acceptedPlanId())
    }

    @Test
    fun aLaterVisitCanAddAnotherProgram() = runBlocking {
        val input = answers(days = 3)
        val blueprint = RoutineGenerator.generate(input, catalog)
        applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-1",
        )
        val first = routines.count()
        applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-2",
        )
        assertTrue(routines.count() > first)
        assertEquals("visit-2", preferences.acceptedPlanId())
    }

    @Test
    fun generateAWeekStillAddsRoutinesAfterAPlanWasAccepted() = runBlocking {
        val input = answers(days = 3)
        val blueprint = RoutineGenerator.generate(input, catalog)
        applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-1",
        )
        val first = routines.count()
        val block = preferences.trainingBlock.first()
        val added = applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            keepCurrentBlock = true,
        ) as ApplyPlanResult.Applied
        assertTrue(added.routineCount > 0)
        assertTrue(routines.count() > first)
        assertEquals(block, preferences.trainingBlock.first())
        assertEquals("visit-1", preferences.acceptedPlanId())
    }

    @Test
    fun aFailedAcceptanceCanBeRetriedOnce() = runBlocking {
        val input = answers(days = 3)
        val blueprint = RoutineGenerator.generate(input, catalog)
        val failing = OnboardingApplier(
            database = database,
            routineRepository = routines,
            scheduleRepository = schedule,
            preferencesRepository = preferences,
            failAfterRoutines = true,
        )
        val failed = failing.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-1",
        )
        assertTrue(failed is ApplyPlanResult.Failed)
        assertEquals(0, routines.count())
        assertEquals(emptyList<com.sinura.personaltrainer.domain.ScheduleSlot>(), schedule.slots())
        assertNull(preferences.acceptedPlanId())

        val retry = applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-1",
        ) as ApplyPlanResult.Applied
        val routineCount = routines.count()
        val slotCount = schedule.slots().size
        assertEquals(retry.routineCount, routineCount)
        assertTrue(slotCount > 0)
        applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-1",
        )
        assertEquals(routineCount, routines.count())
        assertEquals(slotCount, schedule.slots().size)
    }

    @Test
    fun acceptingCardioTwiceDoesNotStartASecondBlock() = runBlocking {
        val input = answers(days = 4, bodyweight = 70.0).copy(focus = TrainingFocus.CARDIO)
        val blueprint = RoutineGenerator.generate(input, catalog)
        assertTrue(blueprint.routines.isEmpty())
        val first = applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "cardio-1",
        )
        assertTrue(first is ApplyPlanResult.Applied)
        val block = preferences.trainingBlock.first()
        assertEquals(1, preferences.bodyweightLog.first().size)
        assertEquals(0, routines.count())
        assertEquals(0, schedule.slots().size)

        applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "cardio-1",
        )
        assertEquals(block, preferences.trainingBlock.first())
        assertEquals(1, preferences.bodyweightLog.first().size)
        assertEquals(0, routines.count())
        assertEquals(0, schedule.slots().size)
    }

    @Test
    fun aCrashAfterTheBlockDoesNotLogASecondWeighInOnALaterDay() = runBlocking {
        val input = answers(days = 3, bodyweight = 80.0)
        val blueprint = RoutineGenerator.generate(input, catalog)
        applier.crashPoint = AcceptCrashPoint.AFTER_BLOCK
        val failed = applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-crash",
        )
        assertTrue(failed is ApplyPlanResult.Failed)
        assertEquals(listOf(TODAY.toEpochDay()), preferences.bodyweightLog.first().map { it.epochDay })
        val block = preferences.trainingBlock.first()
        assertEquals(0, schedule.slots().size)
        val daysPerWeek = preferences.schedulePreferences.first().trainingDaysPerWeek

        applier.crashPoint = AcceptCrashPoint.NONE
        val later = answers(days = 6, bodyweight = 81.0)
        val laterPlan = RoutineGenerator.generate(later, catalog)
        val retry = applier.apply(
            answers = later,
            blueprint = laterPlan,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY.plusDays(4),
            acceptanceId = "visit-crash",
        )
        assertTrue(retry is ApplyPlanResult.Applied)
        val expectedSlots = preferences.planAcceptReceipt()?.pins?.size ?: 0
        val slots = schedule.slots().size
        assertEquals(expectedSlots, slots)
        assertTrue(slots > 0)
        assertEquals(daysPerWeek, preferences.schedulePreferences.first().trainingDaysPerWeek)
        assertEquals(listOf(TODAY.toEpochDay()), preferences.bodyweightLog.first().map { it.epochDay })
        assertEquals(80.0, preferences.bodyweightLog.first().single().kg, 0.001)
        assertEquals(block, preferences.trainingBlock.first())

        applier.apply(
            answers = later,
            blueprint = laterPlan,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY.plusDays(9),
            acceptanceId = "visit-crash",
        )
        assertEquals(slots, schedule.slots().size)
        assertEquals(listOf(TODAY.toEpochDay()), preferences.bodyweightLog.first().map { it.epochDay })
    }

    @Test
    fun aCrashAfterTheDaysDoesNotAppendTheChangedPlan() = runBlocking {
        val input = answers(days = 3, bodyweight = 70.0)
        val blueprint = RoutineGenerator.generate(input, catalog)
        applier.crashPoint = AcceptCrashPoint.AFTER_PINS
        val failed = applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-pins",
        )
        assertTrue(failed is ApplyPlanResult.Failed)
        val slots = schedule.slots().size
        val routineCount = routines.count()
        assertTrue(slots > 0)
        assertTrue(routineCount > 0)

        applier.crashPoint = AcceptCrashPoint.NONE
        val later = answers(days = 6, bodyweight = 72.0)
        val laterPlan = RoutineGenerator.generate(later, catalog)
        applier.apply(
            answers = later,
            blueprint = laterPlan,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY.plusDays(2),
            acceptanceId = "visit-pins",
        )
        assertEquals(slots, schedule.slots().size)
        assertEquals(routineCount, routines.count())
        assertEquals(listOf(TODAY.toEpochDay()), preferences.bodyweightLog.first().map { it.epochDay })
        assertEquals(70.0, preferences.bodyweightLog.first().single().kg, 0.001)
    }

    @Test
    fun aThrowAfterTheWeekIsStoredKeepsTheDaysAndTheRetryDoesNotAddMore() = runBlocking {
        val input = answers(days = 3, bodyweight = 75.0)
        val blueprint = RoutineGenerator.generate(input, catalog)
        applier.throwAfterCommit = true
        val failed = applier.apply(
            answers = input,
            blueprint = blueprint,
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY,
            acceptanceId = "visit-after-commit",
        )
        assertTrue(failed is ApplyPlanResult.Failed)
        val slots = schedule.slots().size
        val routineCount = routines.count()
        assertTrue(slots > 0)
        assertTrue(routineCount > 0)
        assertEquals(listOf(TODAY.toEpochDay()), preferences.bodyweightLog.first().map { it.epochDay })

        applier.throwAfterCommit = false
        val later = answers(days = 6, bodyweight = 76.0)
        val retry = applier.apply(
            answers = later,
            blueprint = RoutineGenerator.generate(later, catalog),
            catalog = catalog,
            weekStart = WEEK_START,
            today = TODAY.plusDays(3),
            acceptanceId = "visit-after-commit",
        )
        assertTrue(retry is ApplyPlanResult.Applied)
        assertEquals(slots, schedule.slots().size)
        assertEquals(routineCount, routines.count())
        assertEquals(listOf(TODAY.toEpochDay()), preferences.bodyweightLog.first().map { it.epochDay })
        assertEquals(75.0, preferences.bodyweightLog.first().single().kg, 0.001)
    }

    private companion object {
        // Fixed rather than read from the clock: the block's start date is derived from this,
        // and a test whose expectations move at midnight is a test that fails in CI at 00:00.
        val WEEK_START: Weekday = Weekday.MONDAY
        val TODAY: LocalDate = LocalDate.of(2026, 8, 19)
    }
}
