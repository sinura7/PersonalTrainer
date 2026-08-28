package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
        preferences = PreferencesRepository(context)
        applier = OnboardingApplier(routines, schedule, preferences)
    }

    @After
    fun tearDown() {
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
        assertTrue(result is ApplyPlanResult.Applied)
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

    private companion object {
        // Fixed rather than read from the clock: the block's start date is derived from this,
        // and a test whose expectations move at midnight is a test that fails in CI at 00:00.
        val WEEK_START: Weekday = Weekday.MONDAY
        val TODAY: LocalDate = LocalDate.of(2026, 8, 19)
    }
}
