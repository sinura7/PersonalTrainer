package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Direct saved-row correction controls over isolated Room; supported restore has separate UI evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkoutSavedHoldCorrectionTest {
    private lateinit var deps: FakeAppDependencies
    private val repository get() = deps.workoutRepository
    private val dao get() = deps.database.workoutDao()

    @Before
    fun setUp() {
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() { deps.close() }

    @Test
    fun aSavedOnlyTimedOriginalKeepsItsTypeAcrossUntouchedAndRepDraftCorrections() = runBlocking {
        val original = savedOnlyRow(reps = 0)
        val before = inventory()
        repository.updateSet(original.id, original.weightKg, 0, null, false)
        assertEquals(original, dao.getSet(original.id))
        repository.updateSet(original.id, 15.0, 3, 9, false)
        assertEquals(original.copy(weightKg = 15.0, rpe = 9), dao.getSet(original.id))
        assertOnlyOriginalChanged(before, original.copy(weightKg = 15.0, rpe = 9))
        assertEquals(0.0, checkNotNull(repository.getSession(original.sessionId)).work().volumeKg, 0.0)
    }

    @Test
    fun stopwatchStrengthKeepsEditableRepsAndDurationWithoutAPlannedRow() = runBlocking {
        val original = savedOnlyRow(reps = 6)
        val before = inventory()
        repository.updateSet(original.id, 30.0, 8, 9, false)
        assertEquals(original.copy(weightKg = 30.0, reps = 8, rpe = 9), dao.getSet(original.id))
        assertOnlyOriginalChanged(before, original.copy(weightKg = 30.0, reps = 8, rpe = 9))
        assertEquals(240.0, checkNotNull(repository.getSession(original.sessionId)).work().volumeKg, 0.0)
    }

    @Test
    fun plannedHoldMetadataCannotReclassifyASavedStopwatchStrengthCorrection() = runBlocking {
        val fixture = seedTestWorkout(
            deps = deps, exerciseId = "hold-original-lift", exerciseName = "Original weighted static hold",
            routineId = "hold-original-routine", finish = true,
            notes = "Preserve this captured stopwatch strength result",
        )
        assertTrue(HoldWork.isHold(fixture.exercise))
        val original = SetLogEntity(
            id = "planned-hold-stopwatch-original", sessionId = fixture.session.id,
            exerciseId = fixture.exercise.id, setNumber = 4, weightKg = 12.5,
            reps = 8, rpe = 8, isWarmup = false, completedAt = fixture.session.startedAt,
            durationSeconds = 45,
        )
        dao.insertSet(original)
        seedTestWorkout(
            deps = deps, exerciseId = "sentinel-exercise", routineId = "sentinel-routine",
            loggedSets = listOf(TestSetInput(35.0, 5, 8)), finish = true,
            notes = "Preserve this unrelated saved session and set",
        )
        assertEquals(2, dao.getAllSets().size)
        val before = inventory()

        // This is a real accepted correction, not a refusal masking a destructive update.
        repository.updateSet(original.id, original.weightKg, original.reps, 9, original.isWarmup)

        val expected = original.copy(rpe = 9)
        assertOnlyOriginalChanged(before, expected)
        assertEquals(expected, dao.getSet(original.id))
        assertEquals(100.0, checkNotNull(repository.getSession(original.sessionId)).work().volumeKg, 0.0)
    }

    @Test
    fun submittedZeroRepsCannotReclassifyStopwatchStrengthAsAHold() = runBlocking {
        val original = savedOnlyRow(reps = 6)
        val before = inventory()
        val failure = runCatching { repository.updateSet(original.id, 12.5, 0, 8, false) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(SetLogRules.INVALID_REPS, failure?.message)
        assertEquals(before, inventory())
    }

    @Test
    fun unknownLoadZeroWeightWorkingHoldStillRefusesWithoutMutation() = runBlocking {
        val original = savedOnlyRow(reps = 0, weightKg = 0.0)
        val before = inventory()
        val failure = runCatching { repository.updateSet(original.id, 0.0, 1, 8, false) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(SetLogRules.ZERO_WORKING_WEIGHT, failure?.message)
        assertEquals(before, inventory())
    }

    @Test
    fun holdTypeDoesNotBypassInvalidWeightValidation() = runBlocking {
        val original = savedOnlyRow(reps = 0)
        val before = inventory()
        for (weight in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val failure = runCatching { repository.updateSet(original.id, weight, 1, 8, false) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals(SetLogRules.INVALID_WEIGHT, failure?.message)
            assertEquals(before, inventory())
        }
    }

    @Test
    fun explicitDurationCorrectionKeepsTheOriginalTimedTypeAndCapturedIdentity() = runBlocking {
        val original = savedOnlyRow(reps = 0)
        val before = inventory()
        repository.updateSet(original.id, 12.5, 1, 8, false, 90)
        assertEquals(original.copy(rpe = 8, durationSeconds = 90), dao.getSet(original.id))
        assertOnlyOriginalChanged(before, original.copy(rpe = 8, durationSeconds = 90))
        assertEquals(0.0, checkNotNull(repository.getSession(original.sessionId)).work().volumeKg, 0.0)
    }

    @Test
    fun olderNonpositiveTimedRowsKeepTheirExactRepresentationThroughCorrection() = runBlocking {
        val original = savedOnlyRow(reps = -2)
        val before = inventory()
        repository.updateSet(original.id, 12.5, 1, 8, false)
        assertEquals(original.copy(rpe = 8), dao.getSet(original.id))
        assertOnlyOriginalChanged(before, original.copy(rpe = 8))
        assertEquals(0.0, checkNotNull(repository.getSession(original.sessionId)).work().volumeKg, 0.0)
    }

    @Test
    fun untouchedCapturedDurationBoundariesKeepExactNonpositiveRepresentations() = runBlocking {
        val fixture = savedOnlyRow(reps = 0)
        for (reps in listOf(0, -3)) {
            for (seconds in listOf(1, 2, 3, 4, 1801, Int.MAX_VALUE)) {
                // Author the isolated captured fixture before the correction baseline.
                val original = fixture.copy(reps = reps, durationSeconds = seconds)
                dao.updateSet(original)
                val before = inventory()
                repository.updateSet(original.id, original.weightKg, original.reps, null, false, seconds)
                assertEquals("untouched $reps reps / $seconds seconds", before, inventory())
                repository.updateSet(original.id, original.weightKg, original.reps, null, false)
                assertEquals("omitted duration retains $seconds seconds", before, inventory())
                assertEquals(original, dao.getSet(original.id))
            }
        }
    }

    @Test
    fun explicitFiveSecondCorrectionKeepsLiteralZeroAndNegativeReps() = runBlocking {
        val fixture = savedOnlyRow(reps = 0)
        for (reps in listOf(0, -3)) {
            val original = fixture.copy(reps = reps)
            dao.updateSet(original)
            val before = inventory()
            repository.updateSet(original.id, original.weightKg, original.reps, null, false, 50)
            assertOnlyOriginalChanged(before, original.copy(durationSeconds = 50))
            assertEquals(original.copy(durationSeconds = 50), dao.getSet(original.id))
        }
    }

    @Test
    fun zeroRepSubmissionCannotReclassifyStopwatchStrengthUnderPlannedHoldMetadata() = runBlocking {
        val fixture = seedTestWorkout(
            deps = deps, exerciseId = "hold-original-lift", exerciseName = "Original weighted static hold",
            routineId = "hold-original-routine", finish = true,
        )
        assertTrue(HoldWork.isHold(fixture.exercise))
        val original = SetLogEntity(
            id = "planned-hold-stopwatch-original", sessionId = fixture.session.id,
            exerciseId = fixture.exercise.id, setNumber = 4, weightKg = 12.5,
            reps = 8, rpe = 8, isWarmup = false, completedAt = fixture.session.startedAt,
            durationSeconds = 45,
        )
        dao.insertSet(original)
        seedTestWorkout(
            deps = deps, exerciseId = "sentinel-exercise", routineId = "sentinel-routine",
            loggedSets = listOf(TestSetInput(35.0, 5, 8)), finish = true,
        )
        val before = inventory()
        val failure = runCatching {
            repository.updateSet(original.id, original.weightKg, 0, 8, false, 50)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(SetLogRules.INVALID_REPS, failure?.message)
        assertEquals(before, inventory())
    }

    private suspend fun savedOnlyRow(reps: Int, weightKg: Double = 12.5): SetLogEntity {
        val fixture = seedTestWorkout(deps = deps, finish = true, notes = "Preserve this finished session")
        insertTestExercise(deps = deps, id = "saved-only", name = "Original saved exercise", isCustom = true)
        val original = SetLogEntity(
            id = "saved-original", sessionId = fixture.session.id, exerciseId = "saved-only",
            setNumber = 4, weightKg = weightKg, reps = reps, rpe = if (reps > 0) 8 else null,
            isWarmup = false, completedAt = fixture.session.startedAt, durationSeconds = 45,
        )
        dao.insertSet(original)
        seedTestWorkout(
            deps = deps, exerciseId = "sentinel-exercise", routineId = "sentinel-routine",
            loggedSets = listOf(TestSetInput(35.0, 5, 8)), finish = true,
            notes = "Preserve this unrelated saved session and set",
        )
        assertEquals(2, dao.getAllSets().size)
        assertTrue(checkNotNull(repository.getSession(original.sessionId)).exercises.none { it.exercise.id == original.exerciseId })
        return original
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun assertOnlyOriginalChanged(before: List<Any?>, expected: SetLogEntity) {
        val wanted = before.toMutableList()
        wanted[0] = (before[0] as List<SetLogEntity>).map { if (it.id == expected.id) expected else it }
        assertEquals("only the requested original fields change, including every unrelated saved row", wanted, inventory())
    }

    private suspend fun inventory(): List<Any?> = listOf(
        dao.getAllSets().sortedBy { it.id },
        dao.getAllSessions().sortedBy { it.id },
        dao.getAllSessionExercises().sortedBy { it.id },
        deps.database.exerciseDao().getAll().sortedBy { it.id },
        deps.database.routineDao().getAllRoutines().sortedBy { it.id },
        deps.database.routineDao().getAllRoutineExercises().sortedBy { it.id },
    )
}
