package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.dao.WORKING_SET_COUNTS_SINCE_SQL
import com.sinura.personaltrainer.data.local.dao.WorkingSetCountRow
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** v4 V7: Body's weekly sets per muscle reads working sets of finished sessions only. */
@RunWith(RobolectricTestRunner::class)
class WeeklyVolumeQueryTest {

    private val queryExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "room-query-volume-test").apply { isDaemon = true }
    }
    private val transactionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "room-txn-volume-test").apply { isDaemon = true }
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
            database.exerciseDao().insertAll(listOf(exercise(SQUAT), exercise(BENCH)))
        }
    }

    @After
    fun tearDown() {
        database.close()
        queryExecutor.shutdown()
        transactionExecutor.shutdown()
    }

    @Test
    fun countsWorkingSetsPerLiftPerFinishedSessionSinceTheCutoff() = runBlocking {
        session("old", finishedAt = START - 1, sets = listOf(SQUAT to false))
        session("a", finishedAt = START + 10, sets = listOf(SQUAT to true, SQUAT to false, SQUAT to false, BENCH to false))
        session("live", finishedAt = null, sets = listOf(BENCH to false))
        session("b", finishedAt = START + 20, sets = listOf(BENCH to false, BENCH to false))

        assertEquals(
            listOf(
                WorkingSetCountRow(BENCH, START + 10, 1),
                WorkingSetCountRow(SQUAT, START + 10, 2),
                WorkingSetCountRow(BENCH, START + 20, 2),
            ),
            repository.workingSetCountsSince(START),
        )
    }

    @Test
    fun theQueryUsesExistingIndexesAndScansNoTable() {
        val plan = mutableListOf<String>()
        database.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN " + WORKING_SET_COUNTS_SINCE_SQL.replace(":sinceMs", "0"),
        ).use { cursor -> while (cursor.moveToNext()) plan += cursor.getString(cursor.columnCount - 1) }
        assertFalse(plan.joinToString(), plan.any { it.startsWith("SCAN ws") || it.startsWith("SCAN sl") || it.contains("SCAN TABLE") })
    }

    private suspend fun session(id: String, finishedAt: Long?, sets: List<Pair<String, Boolean>>) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id, routineId = null, routineName = "Lower", date = START, notes = "",
                durationMinutes = 45, startedAt = START, finishedAt = finishedAt,
            ),
        )
        sets.forEachIndexed { index, (exerciseId, warmup) ->
            database.workoutDao().insertSet(
                SetLogEntity(
                    id = "$id-$index", sessionId = id, exerciseId = exerciseId, setNumber = index + 1,
                    weightKg = 60.0, reps = 5, rpe = null, isWarmup = warmup, completedAt = START + index,
                ),
            )
        }
    }

    private fun exercise(id: String) = ExerciseEntity(id = id, name = id, muscleGroup = "Legs", notes = "", isCustom = false)

    private companion object {
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val START = 1_700_000_000_000L
    }
}
