package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import java.util.concurrent.Executors
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Batched coach reads and aggregate PR detection must match the old
 * per-lift round-trips: top set, not last set; in-session work-up counts.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryInsightsQueriesTest {

    private val queryExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "room-query-insights-test").apply { isDaemon = true }
    }
    private val transactionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "room-txn-insights-test").apply { isDaemon = true }
    }
    private lateinit var database: TrainerDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .build()
        repository = WorkoutRepository(database, database.workoutDao())
        runBlocking {
            database.exerciseDao().insertAll(listOf(exerciseRow(SQUAT), exerciseRow(BENCH)))
        }
    }

    @After
    fun tearDown() {
        database.close()
        queryExecutor.shutdown()
        transactionExecutor.shutdown()
    }

    @Test
    fun finishedLastLoggedIgnoresTheLiveSessionThatThePickerSees() = runBlocking {
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        insertLiveSession("live")
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "live-$SQUAT-0",
                sessionId = "live",
                exerciseId = SQUAT,
                setNumber = 1,
                weightKg = 110.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = START + 10_000,
            ),
        )
        val picker = repository.observeLastLogged().first()
        val insights = repository.observeFinishedLastLogged().first()
        assertEquals(START + 10_000, picker.getValue(SQUAT))
        assertEquals(START + 1, insights.getValue(SQUAT))
    }

    @Test
    fun recordSetsCarryTheLiftAndSkipLiveAndWarmUpWork() = runBlocking {
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "done-warm",
                sessionId = "done",
                exerciseId = BENCH,
                setNumber = 9,
                weightKg = 40.0,
                reps = 10,
                rpe = null,
                isWarmup = true,
                completedAt = START + 5,
            ),
        )
        insertLiveSession("live")
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "live-$SQUAT-0",
                sessionId = "live",
                exerciseId = SQUAT,
                setNumber = 1,
                weightKg = 300.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = START + 10_000,
            ),
        )

        val health = repository.observeRecordSetsHealth().first()
        val sets = (health as DataHealth.Available).value
        val only = sets.single()
        assertEquals("done-$SQUAT-0", only.set.setId)
        assertEquals("Squat", only.exerciseName)
        assertEquals(LoadClass.LOADED, only.loadClass)
        assertEquals(100.0, only.set.weightKg, 0.0)
    }

    @Test
    fun revisionMovesOnAFinishedEditNotOnALiveSet() = runBlocking {
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        val before = repository.observeFinishedWorkRevisionHealth().presentValues().first()

        insertLiveSession("live")
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "live-$SQUAT-0",
                sessionId = "live",
                exerciseId = SQUAT,
                setNumber = 1,
                weightKg = 110.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = START + 10_000,
            ),
        )
        assertEquals(before, repository.observeFinishedWorkRevisionHealth().presentValues().first())

        // Neither a count nor a timestamp moves for a corrected weight; the sums do.
        repository.updateSet(
            setId = "done-$SQUAT-0",
            weightKg = 105.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )
        assertNotEquals(before, repository.observeFinishedWorkRevisionHealth().presentValues().first())
    }

    @Test
    fun finishedContentRowsKeepAbsentSetAndSlotGroupsNull() = runBlocking {
        insertFinishedSession(id = "empty", finishedAt = START + 1, sets = emptyList())
        insertFinishedSession(
            id = "set-only",
            finishedAt = START + 2,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        insertFinishedSession(id = "slot-only", finishedAt = START + 3, sets = emptyList())
        val slot = sessionExerciseRow(sessionId = "slot-only", exerciseId = BENCH)
        database.workoutDao().insertSessionExercises(listOf(slot))

        val rows = database.workoutDao().observeFinishedWorkContents().first()
        assertEquals(4, rows.size)
        val empty = rows.single { it.session.id == "empty" }
        assertNull(empty.set)
        assertNull(empty.slot)
        assertNull(empty.exercise)

        val setOnly = rows.single { it.session.id == "set-only" }
        assertEquals("set-only-$SQUAT-0", checkNotNull(setOnly.set).id)
        assertEquals(SQUAT, checkNotNull(setOnly.exercise).id)
        assertNull(setOnly.slot)

        val slotOnlyRows = rows.filter { it.session.id == "slot-only" }
        assertEquals(2, slotOnlyRows.size)
        assertTrue(slotOnlyRows.all { it.set == null })
        val placeholder = slotOnlyRows.single { it.slot == null }
        assertNull(placeholder.exercise)
        val planned = slotOnlyRows.single { it.slot != null }
        assertEquals(slot, planned.slot)
        assertEquals(exerciseRow(BENCH), planned.exercise)
    }

    @Test
    fun finishedContentRowsAreLinearAndExcludeLiveSetsAndSlots() = runBlocking {
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5), Triple(BENCH, 60.0, 8)),
        )
        val doneSlots = listOf(
            sessionExerciseRow(sessionId = "done", exerciseId = SQUAT),
            sessionExerciseRow(sessionId = "done", exerciseId = BENCH).copy(sortOrder = 1),
        )
        database.workoutDao().insertSessionExercises(doneSlots)
        insertLiveSession("live")
        database.workoutDao().insertSessionExercises(
            listOf(
                sessionExerciseRow(sessionId = "live", exerciseId = SQUAT),
                sessionExerciseRow(sessionId = "live", exerciseId = BENCH).copy(sortOrder = 1),
            ),
        )
        repository.logSet(
            sessionId = "live", exerciseId = SQUAT, weightKg = 120.0, reps = 5,
            rpe = null, isWarmup = false,
        )
        repository.logSet(
            sessionId = "live", exerciseId = BENCH, weightKg = 80.0, reps = 8,
            rpe = null, isWarmup = false,
        )

        val rows = database.workoutDao().observeFinishedWorkContents().first()
        assertEquals("two sets plus two slots, without a Cartesian product", 4, rows.size)
        assertTrue(rows.all { it.session.id == "done" })
        assertTrue(rows.all { (it.set != null) != (it.slot != null) })
        assertEquals(
            listOf("done-$SQUAT-0", "done-$BENCH-1").sorted(),
            rows.mapNotNull { it.set?.id }.sorted(),
        )
        assertEquals(doneSlots.map { it.id }.sorted(), rows.mapNotNull { it.slot?.id }.sorted())
        rows.forEach { row ->
            assertEquals(row.set?.exerciseId ?: row.slot?.exerciseId, checkNotNull(row.exercise).id)
        }
    }

    @Test
    fun aOneUlpFinishedWeightCorrectionChangesTheRetainedRevision() = runBlocking {
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        val original = checkNotNull(database.workoutDao().getSet("done-$SQUAT-0"))
        val corrected = original.copy(weightKg = Math.nextUp(100.0))
        assertNotEquals(original.weightKg.toRawBits(), corrected.weightKg.toRawBits())

        withRevisionCollector { emissions ->
            val before = emissions.value.last()
            database.workoutDao().updateSet(corrected)
            assertEquals(corrected, database.workoutDao().getSet(original.id))
            val rows = database.workoutDao().observeFinishedWorkContents().first()
            assertEquals(corrected.weightKg.toRawBits(), checkNotNull(rows.single().set).weightKg.toRawBits())
            val updated = withTimeout(TestWaits.FLOW_MS) {
                emissions.first { it.last() != before }.last()
            }
            assertNotEquals(before, updated)
        }
    }

    @Test
    fun referencedCatalogAndPlannedSlotChangesInvalidateTheRetainedRevision() = runBlocking {
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        val originalSlot = sessionExerciseRow(sessionId = "done", exerciseId = BENCH)
        database.workoutDao().insertSessionExercises(listOf(originalSlot))

        withRevisionCollector { emissions ->
            val beforeCatalog = emissions.value.last()
            // This lift is referenced only by a planned slot, not by a recorded set.
            val renamed = exerciseRow(BENCH).copy(name = "Renamed press", notes = "Updated instructions")
            database.exerciseDao().update(renamed)
            val afterCatalog = withTimeout(TestWaits.FLOW_MS) {
                emissions.first { it.last() != beforeCatalog }.last()
            }
            assertNotEquals(beforeCatalog, afterCatalog)
            val catalogRows = database.workoutDao().observeFinishedWorkContents().first()
            assertEquals(renamed, catalogRows.single { it.slot != null }.exercise)

            val changedSlot = originalSlot.copy(targetSeconds = 30, targetSecondsMax = 45)
            database.workoutDao().upsertSessionExercise(changedSlot)
            val afterSlot = withTimeout(TestWaits.FLOW_MS) {
                emissions.first { it.last() != afterCatalog }.last()
            }
            assertNotEquals(afterCatalog, afterSlot)
            val slotRows = database.workoutDao().observeFinishedWorkContents().first()
            assertEquals(changedSlot, slotRows.single { it.slot != null }.slot)
            assertEquals(
                listOf(checkNotNull(catalogRows.single { it.set != null }.set)),
                slotRows.mapNotNull { it.set },
            )
        }
    }

    /**
     * Audit DM-1: a hold is logged in seconds and this counts reps. A plank with a rep on
     * record (older holds carried one) read as "ready to progress: add a rep".
     */
    @Test
    fun readyForProgressionLeavesHoldsOut() = runBlocking {
        database.exerciseDao().insertAll(listOf(exerciseRow(PLANK)))
        insertFinishedSession(
            id = "s1",
            finishedAt = START + 1,
            sets = listOf(Triple(PLANK, 0.0, 1), Triple(SQUAT, 100.0, 5)),
        )
        val withPlank = routine().copy(
            exercises = routine().exercises + item(PLANK, "Plank").copy(
                exercise = item(PLANK, "Plank").exercise.copy(loadType = LoadType.BODYWEIGHT),
                targetReps = 1,
                targetWeightKg = null,
            ),
        )

        val hints = repository.readyForProgression(listOf(withPlank), WeightUnit.KG)

        assertEquals(listOf(SQUAT), hints.map { it.exerciseId })
    }

    @Test
    fun readyForProgressionUsesTheTopSetNotTheBackoff() = runBlocking {
        insertFinishedSession(
            id = "s1",
            finishedAt = START + 1,
            sets = listOf(
                Triple(SQUAT, 100.0, 5),
                Triple(SQUAT, 80.0, 8),
            ),
        )

        val hints = repository.readyForProgression(listOf(routine()), WeightUnit.KG)
        assertEquals(1, hints.size)
        assertEquals(SQUAT, hints.single().exerciseId)
        assertEquals(100.0, hints.single().lastWeightKg, 0.001)
        assertEquals(102.5, hints.single().suggestedWeightKg, 0.001)
        assertEquals(ProgressionAction.INCREASE, hints.single().action)
    }

    @Test
    fun readyForProgressionHoldsWhenTheLastTwoTopSetsAreGrinders() = runBlocking {
        insertFinishedSession(
            id = "s1",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
            rpe = 9,
        )
        insertFinishedSession(
            id = "s2",
            finishedAt = START + 2,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
            rpe = 9,
        )

        val hints = repository.readyForProgression(listOf(routine()), WeightUnit.KG)
        assertTrue(hints.none { it.exerciseId == SQUAT })
    }

    @Test
    fun aHeavierSetInTheLiveSessionBeatsTheWorkUp() = runBlocking {
        insertLiveSession("live")
        repository.logSet("live", SQUAT, 100.0, 5, rpe = null, isWarmup = false)
        val second = repository.logSet("live", SQUAT, 105.0, 5, rpe = null, isWarmup = false)
        assertTrue(PersonalRecordKind.WEIGHT in second.records)
    }

    @Test
    fun theFirstSetOfALiftBreaksNothing() = runBlocking {
        insertLiveSession("live")
        val first = repository.logSet("live", SQUAT, 100.0, 5, rpe = null, isWarmup = false)
        assertTrue(first.records.isEmpty())
    }

    @Test
    fun loggingAnInProgressSetDoesNotRescanFinishedSummaries() = runBlocking {
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        val emissions = MutableStateFlow<List<Int>>(emptyList())
        val job = launch {
            repository.observeSessionSummariesHealth().presentValues().collect { summaries ->
                emissions.update { it + summaries.size }
            }
        }
        // Wait for *this* collector's first value. The baseline used to be taken after a
        // second, independent collector had seen the row, which says nothing about this
        // one: on an unlucky interleaving `emissions` was still empty at that point, and
        // its initial emission then landed inside the sampling window below and read as a
        // re-scan that never happened.
        withTimeout(TestWaits.FLOW_MS) { emissions.first { it.isNotEmpty() } }
        val before = emissions.value.size

        insertLiveSession("live")
        repository.logSet("live", SQUAT, 102.5, 5, rpe = null, isWarmup = false)
        // Left as a real wait on purpose. This asserts an absence — that no further emission
        // arrives — and an absence cannot be waited for, only sampled. Unlike the other fixed
        // sleeps in this packet it is not a flake source: a slow machine makes a stray
        // emission arrive *after* the window, so the assertion passes rather than fails. The
        // cost is strength, not stability. Proving it properly means forcing an emission that
        // must arrive and asserting exactly one new one landed, which rewrites what the test
        // asserts rather than how it waits, so it is left for the rest of J4.
        delay(50)
        assertEquals(
            "in-progress logs must not re-emit finished summaries",
            before,
            emissions.value.size,
        )
        assertEquals(1, emissions.value.last())
        job.cancel()
    }

    @Test
    fun editingAFinishedSetsWeightRefreshesTheSummaries() = runBlocking {
        // The write-amplification gate keys every finished-work read off one fingerprint, and
        // `updateSet` deliberately touches neither `completedAt` nor `setNumber` — so a
        // corrected weight moved nothing in it. The screen you edited on updated; Home's last
        // session, History's totals and the body map kept the old number until an unrelated
        // workout happened to finish. Deleting or adding a set did refresh, which is what made
        // it look random.
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        val initial = repository.observeSessionSummariesHealth().presentValues()
            .first { it.singleOrNull()?.volumeKg == 500.0 }
        assertEquals(500.0, initial.single().volumeKg, 0.001)

        repository.updateSet("done-$SQUAT-0", weightKg = 110.0, reps = 5, rpe = null, isWarmup = false)

        val updated = repository.observeSessionSummariesHealth().presentValues()
            .first { it.singleOrNull()?.volumeKg == 550.0 }
        assertEquals(550.0, updated.single().volumeKg, 0.001)
    }

    @Test
    fun anEditThatHoldsVolumeConstantStillRefreshesTheHistory() = runBlocking {
        // Why the fingerprint carries two sums rather than one. Six reps at 100 kg and five at
        // 120 are both 600 kg, so the volume sum cannot see this correction; the rep sum can.
        // The everyday version is a bodyweight lift, where every set is 0 kg and the volume sum
        // is blind to every rep the owner ever fixes.
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 6)),
        )
        val initial = repository.observeFinishedSince(0L).first {
            it.singleOrNull()?.sets?.singleOrNull()?.reps == 6
        }
        assertEquals(6, initial.single().sets.single().reps)

        repository.updateSet("done-$SQUAT-0", weightKg = 120.0, reps = 5, rpe = null, isWarmup = false)

        val updated = repository.observeFinishedSince(0L).first {
            it.singleOrNull()?.sets?.singleOrNull()?.reps == 5
        }
        assertEquals(5, updated.single().sets.single().reps)
    }

    private fun routine() = Routine(
        id = "r",
        name = "Lower",
        notes = "",
        createdAt = START,
        updatedAt = START,
        exercises = listOf(
            item(SQUAT, "Squat"),
            item(BENCH, "Bench"),
        ),
    )

    private fun item(exerciseId: String, name: String) = RoutineExercise(
        id = "item-$exerciseId",
        routineId = "r",
        exercise = Exercise(
            id = exerciseId,
            name = name,
            muscleGroup = "Legs",
            notes = "",
            isCustom = false,
            loadType = LoadType.EXTERNAL,
        ),
        sortOrder = 0,
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
    )

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
    )

    private fun sessionExerciseRow(sessionId: String, exerciseId: String) = SessionExerciseEntity(
        id = "$sessionId-slot-$exerciseId",
        sessionId = sessionId,
        exerciseId = exerciseId,
        sortOrder = 0,
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
    )

    private suspend fun withRevisionCollector(
        block: suspend (MutableStateFlow<List<String>>) -> Unit,
    ) = coroutineScope {
        val emissions = MutableStateFlow<List<String>>(emptyList())
        val job = launch {
            repository.observeFinishedWorkRevisionHealth().presentValues().collect { revision ->
                emissions.update { it + revision }
            }
        }
        try {
            withTimeout(TestWaits.FLOW_MS) { emissions.first { it.isNotEmpty() } }
            block(emissions)
        } finally {
            job.cancelAndJoin()
        }
    }

    private suspend fun insertLiveSession(id: String) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                routineName = "Live",
                date = START,
                notes = "",
                durationMinutes = 0,
                startedAt = START,
                finishedAt = null,
            ),
        )
    }

    private suspend fun insertFinishedSession(
        id: String,
        finishedAt: Long,
        sets: List<Triple<String, Double, Int>>,
        rpe: Int? = null,
    ) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                routineName = "Lower",
                date = START,
                notes = "",
                durationMinutes = 45,
                startedAt = START,
                finishedAt = finishedAt,
            ),
        )
        sets.forEachIndexed { index, (exerciseId, weightKg, reps) ->
            database.workoutDao().insertSet(
                SetLogEntity(
                    id = "$id-$exerciseId-$index",
                    sessionId = id,
                    exerciseId = exerciseId,
                    setNumber = index + 1,
                    weightKg = weightKg,
                    reps = reps,
                    rpe = rpe,
                    isWarmup = false,
                    completedAt = finishedAt + index,
                ),
            )
        }
    }

    private companion object {
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val PLANK = "plank"
        const val START = 1_700_000_000_000L
    }
}
