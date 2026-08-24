package com.sinura.personaltrainer.data.local

import android.app.Application
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupScaleBudget
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T10 snapshot half: createSnapshot + encode of a Room fixture matching
 * [BackupScaleBudget]. Streaming is not justified while this stays inside
 * the signed ceiling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupScaleSnapshotTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun snapshotAndEncodeStayInsideSignedBudget() = runBlocking {
        seedScaleFixture()
        val started = System.nanoTime()
        val document = deps.localBackupRepository.createSnapshot()
        val json = BackupJson.encode(document)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(BackupScaleBudget.SESSIONS, document.sessions.size)
        assertEquals(BackupScaleBudget.SETS, document.setLogs.size)
        assertTrue(
            "snapshot+encode ${elapsedMs}ms exceeded ${BackupScaleBudget.SNAPSHOT_AND_ENCODE_MS}ms",
            elapsedMs <= BackupScaleBudget.SNAPSHOT_AND_ENCODE_MS,
        )
        assertTrue(
            "encoded ${json.length} bytes exceeded ${BackupScaleBudget.ENCODED_BYTES_MAX}",
            json.length.toLong() <= BackupScaleBudget.ENCODED_BYTES_MAX,
        )
        println("P3.7 snapshot+encode: ${elapsedMs}ms bytes: ${json.length}")
    }

    private suspend fun seedScaleFixture() {
        val stamp = 1_700_000_000_000L
        val sessions = ArrayList<WorkoutSessionEntity>(BackupScaleBudget.SESSIONS)
        val items = ArrayList<SessionExerciseEntity>(BackupScaleBudget.SESSIONS)
        val sets = ArrayList<SetLogEntity>(BackupScaleBudget.SETS)
        repeat(BackupScaleBudget.SESSIONS) { sessionIndex ->
            val sessionId = "scale-session-%04d".format(sessionIndex)
            val started = stamp + sessionIndex * 86_400_000L
            sessions.add(
                WorkoutSessionEntity(
                    id = sessionId,
                    routineId = null,
                    routineName = "Scale $sessionIndex",
                    date = started,
                    notes = "",
                    durationMinutes = 45,
                    startedAt = started,
                    finishedAt = started + 2_700_000L,
                ),
            )
            items.add(
                SessionExerciseEntity(
                    id = "scale-item-%04d".format(sessionIndex),
                    sessionId = sessionId,
                    exerciseId = EXERCISE_ID,
                    sortOrder = 0,
                    targetSets = BackupScaleBudget.SETS_PER_SESSION,
                    targetReps = 5,
                    targetWeightKg = 100.0,
                    restSeconds = 90,
                ),
            )
            repeat(BackupScaleBudget.SETS_PER_SESSION) { setIndex ->
                sets.add(
                    SetLogEntity(
                        id = "scale-set-%04d-%02d".format(sessionIndex, setIndex),
                        sessionId = sessionId,
                        exerciseId = EXERCISE_ID,
                        setNumber = setIndex + 1,
                        weightKg = 100.0,
                        reps = 5,
                        rpe = 8,
                        isWarmup = false,
                        completedAt = started + setIndex * 90_000L,
                    ),
                )
            }
        }
        deps.database.withTransaction {
            deps.database.exerciseDao().insert(
                ExerciseEntity(
                    id = EXERCISE_ID,
                    name = "Scale squat",
                    muscleGroup = "Quads",
                    notes = "",
                    isCustom = true,
                    nameKey = "scale squat",
                ),
            )
            deps.database.workoutDao().replaceSessions(sessions)
            deps.database.workoutDao().insertSessionExercises(items)
            deps.database.workoutDao().replaceSets(sets)
        }
    }

    private companion object {
        const val EXERCISE_ID = "scale-squat"
    }
}
