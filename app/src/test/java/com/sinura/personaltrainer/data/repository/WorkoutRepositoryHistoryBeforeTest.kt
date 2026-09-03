package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The summary screen used to walk each lift's lifetime sets as its own
 * query. One batched read must cover every working lift.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryHistoryBeforeTest {
    private val queryExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "room-query-history-before").apply { isDaemon = true }
    }
    private val transactionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "room-txn-history-before").apply { isDaemon = true }
    }
    private val sql = CopyOnWriteArrayList<String>()
    private lateinit var database: TemperDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .setQueryCallback({ query, _ -> sql += query }, java.util.concurrent.Executor { it.run() })
            .build()
        repository = WorkoutRepository(database, database.workoutDao())
        runBlocking {
            database.exerciseDao().insertAll(
                listOf(exerciseRow(SQUAT), exerciseRow(BENCH), exerciseRow(ROW)),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
        queryExecutor.shutdown()
        transactionExecutor.shutdown()
    }

    @Test
    fun historyBeforeIssuesOneSetLogQueryForThreeLifts() = runBlocking {
        insertFinished("prior", START + 1, listOf(SQUAT, BENCH, ROW))
        insertFinished("current", START + 2, listOf(SQUAT, BENCH, ROW))
        sql.clear()

        val prior = repository.historyBefore("current", listOf(SQUAT, BENCH, ROW))

        assertEquals(1, prior.getValue(SQUAT).size)
        assertEquals("prior", prior.getValue(SQUAT).single().sessionId)
        assertEquals(1, prior.getValue(BENCH).size)
        assertEquals(1, prior.getValue(ROW).size)
        val setLogSelects = sql.filter { isFinishedWorkingSetSelect(it) }
        assertEquals(sql.joinToString("\n"), 1, setLogSelects.size)
        assertTrue(setLogSelects.single().contains("IN", ignoreCase = true))
    }

    private fun isFinishedWorkingSetSelect(query: String): Boolean {
        val sql = query.lowercase()
        return sql.contains("from set_logs") &&
            sql.contains("iswarmup") &&
            sql.contains("select") &&
            !sql.contains("insert") &&
            !sql.contains("update") &&
            !sql.contains("delete")
    }

    private suspend fun insertFinished(id: String, finishedAt: Long, lifts: List<String>) {
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
        lifts.forEachIndexed { index, exerciseId ->
            database.workoutDao().insertSet(
                SetLogEntity(
                    id = "$id-$exerciseId-$index",
                    sessionId = id,
                    exerciseId = exerciseId,
                    setNumber = index + 1,
                    weightKg = 100.0,
                    reps = 5,
                    rpe = null,
                    isWarmup = false,
                    completedAt = finishedAt + index,
                ),
            )
        }
    }

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
    )

    private companion object {
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val ROW = "row"
        const val START = 1_700_000_000_000L
    }
}
