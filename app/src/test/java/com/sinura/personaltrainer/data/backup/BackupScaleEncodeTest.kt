package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T10 encode half: a 500 / 15,000 document must encode and decode under the
 * signed budget. No Room. The snapshot half is [BackupScaleSnapshotTest].
 */
class BackupScaleEncodeTest {
    @Test
    fun wholeDocumentEncodeStaysInsideSignedBudget() {
        val document = scaleDocument()
        assertEquals(BackupScaleBudget.SESSIONS, document.sessions.size)
        assertEquals(BackupScaleBudget.SETS, document.setLogs.size)

        val encodeStarted = System.nanoTime()
        val json = BackupJson.encode(document)
        val encodeMs = (System.nanoTime() - encodeStarted) / 1_000_000
        assertTrue(
            "encode ${encodeMs}ms exceeded ${BackupScaleBudget.ENCODE_MS}ms",
            encodeMs <= BackupScaleBudget.ENCODE_MS,
        )
        assertTrue(
            "encoded ${json.length} bytes exceeded ${BackupScaleBudget.ENCODED_BYTES_MAX}",
            json.length.toLong() <= BackupScaleBudget.ENCODED_BYTES_MAX,
        )

        val decodeStarted = System.nanoTime()
        val parsed = BackupJson.decode(json)
        val decodeMs = (System.nanoTime() - decodeStarted) / 1_000_000
        assertTrue(
            "decode ${decodeMs}ms exceeded ${BackupScaleBudget.DECODE_MS}ms",
            decodeMs <= BackupScaleBudget.DECODE_MS,
        )
        assertEquals(BackupScaleBudget.SESSIONS, parsed.sessions.size)
        assertEquals(BackupScaleBudget.SETS, parsed.setLogs.size)
        println(
            "P3.7 encode: ${encodeMs}ms decode: ${decodeMs}ms bytes: ${json.length}",
        )
    }

    private fun scaleDocument(): BackupDocument {
        val sessions = ArrayList<BackupSession>(BackupScaleBudget.SESSIONS)
        val items = ArrayList<BackupSessionExercise>(BackupScaleBudget.SESSIONS)
        val sets = ArrayList<BackupSetLog>(BackupScaleBudget.SETS)
        val stamp = 1_700_000_000_000L
        repeat(BackupScaleBudget.SESSIONS) { sessionIndex ->
            val sessionId = "scale-session-%04d".format(sessionIndex)
            val started = stamp + sessionIndex * 86_400_000L
            sessions.add(
                BackupSession(
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
                BackupSessionExercise(
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
                    BackupSetLog(
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
        return BackupDocument(
            version = BackupJson.CURRENT_VERSION,
            app = BackupJson.APP_ID,
            exportedAt = "2026-08-24T12:00:00Z",
            preferences = BackupPreferences(weightUnit = "kg"),
            routines = emptyList(),
            routineExercises = emptyList(),
            exercises = listOf(
                BackupExercise(
                    id = EXERCISE_ID,
                    name = "Scale squat",
                    muscleGroup = "Quads",
                    notes = "",
                    isCustom = true,
                    equipment = "BARBELL",
                    loadType = "EXTERNAL",
                ),
            ),
            sessions = sessions,
            sessionExercises = items,
            setLogs = sets,
        )
    }

    private companion object {
        const val EXERCISE_ID = "scale-squat"
    }
}
