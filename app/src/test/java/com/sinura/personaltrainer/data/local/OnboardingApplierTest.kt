package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.repository.ApplyPlanResult
import com.sinura.personaltrainer.data.repository.OnboardingApplier
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.RoutineGenerator
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingPlace
import java.time.DayOfWeek
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
        preferred: Set<DayOfWeek> = emptySet(),
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
        val result = applier.apply(input, blueprint, catalog)
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
        applier.apply(input, blueprint, catalog)

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
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog)

        assertEquals(5, preferences.schedulePreferences.first().trainingDaysPerWeek)
        assertEquals(82.0, preferences.bodyweightKg.first()!!, 0.001)
        assertTrue(preferences.onboardingComplete.first())
        // A limited kit becomes a real filter; a full gym would leave it empty, meaning
        // "no filtering", which is not the same as "owns nothing".
        val equipment = preferences.coachPreferences.first().availableEquipment
        assertTrue(equipment.isNotEmpty())
        assertTrue("BARBELL" !in equipment)
    }

    @Test
    fun aFullGymFiltersNothing() = runBlocking {
        val input = answers(place = TrainingPlace.FULL_GYM)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog)
        assertEquals(emptySet<String>(), preferences.coachPreferences.first().availableEquipment)
    }

    @Test
    fun theDaysTheLifterPickedAreTheDaysThatGetPinned() = runBlocking {
        val picked = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
        val input = answers(days = 3, preferred = picked)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog)
        assertEquals(picked, schedule.slots().mapNotNull { it.anchorDay }.toSet())
    }

    @Test
    fun rerunningSetupAddsAlongsideAndNeverDeletes() = runBlocking {
        // Someone with months of history pointing at their routines must not lose them because
        // they answered the questions again.
        val mine = routines.create(name = "My Own Thing")
        val input = answers(days = 3)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog)
        val firstCount = routines.count()

        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog)
        assertTrue("the second run must add, not replace", routines.count() > firstCount)
        assertTrue("the lifter's own routine survived", routines.getById(mine.id) != null)
    }

    @Test
    fun anExistingProgramIsDetectedSoTheScreenCanWarn() = runBlocking {
        assertEquals(false, applier.hasExistingProgram())
        val input = answers(days = 2)
        applier.apply(input, RoutineGenerator.generate(input, catalog), catalog)
        assertEquals(true, applier.hasExistingProgram())
    }

    @Test
    fun aBodyweightOnlySetupStillProducesATrainableWeek() = runBlocking {
        val input = answers(days = 4, age = TrainingAge.NEW, place = TrainingPlace.BODYWEIGHT_ONLY)
        val blueprint = RoutineGenerator.generate(input, catalog)
        applier.apply(input, blueprint, catalog)

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
}
