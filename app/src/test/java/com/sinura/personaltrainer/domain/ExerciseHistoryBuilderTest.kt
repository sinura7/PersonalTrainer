package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sinura.personaltrainer.domain.Weekday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ExerciseHistoryBuilderTest {
    private val zone = ZoneOffset.UTC
    private val squat = "ex-squat"
    private var nextId = 0

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun workout(
        id: String,
        atMs: Long,
        sets: List<SetLog>,
        finished: Boolean = true,
    ) = session(
        id = id,
        finishedAt = if (finished) atMs else null,
        sets = sets,
        exercises = listOf(sessionExercise(squat, "Squat", "Quads")),
        date = atMs,
    )

    private fun squatSet(
        sessionId: String,
        weightKg: Double,
        reps: Int,
        atMs: Long,
        warmup: Boolean = false,
        exerciseId: String = squat,
    ) = set(
        id = "set-${nextId++}",
        sessionId = sessionId,
        exerciseId = exerciseId,
        name = "Squat",
        weightKg = weightKg,
        reps = reps,
        warmup = warmup,
        at = atMs,
    )

    @Test
    fun sessionsComeBackNewestFirst() {
        val early = at("2026-08-03T10:00:00Z")
        val late = at("2026-08-10T10:00:00Z")
        val history = ExerciseHistoryBuilder.build(
            exerciseId = squat,
            sessions = listOf(
                workout("a", early, listOf(squatSet("a", 100.0, 5, early))),
                workout("b", late, listOf(squatSet("b", 105.0, 5, late))),
            ),
            zone = zone,
        )
        assertEquals(listOf("b", "a"), history.sessions.map { it.sessionId })
    }

    @Test
    fun theTopSetIsTheOneProgressionWouldJudge() {
        // A top set followed by a back-off: the summary must agree with ProgressionBasis, or
        // the number the lifter reads and the number the app suggests from come apart.
        val day = at("2026-08-10T10:00:00Z")
        val history = ExerciseHistoryBuilder.build(
            exerciseId = squat,
            sessions = listOf(
                workout(
                    "a",
                    day,
                    listOf(
                        squatSet("a", 100.0, 5, day),
                        squatSet("a", 80.0, 8, day + 1000),
                    ),
                ),
            ),
            zone = zone,
        )
        val top = history.sessions.single().topSet!!
        assertEquals(100.0, top.weightKg, 0.0001)
        assertEquals(5, top.reps)
    }

    @Test
    fun warmupsCountTowardNothing() {
        val day = at("2026-08-10T10:00:00Z")
        val history = ExerciseHistoryBuilder.build(
            exerciseId = squat,
            sessions = listOf(
                workout(
                    "a",
                    day,
                    listOf(
                        squatSet("a", 200.0, 1, day, warmup = true),
                        squatSet("a", 100.0, 5, day + 1000),
                    ),
                ),
            ),
            zone = zone,
        )
        val summary = history.sessions.single()
        assertEquals(1, summary.workingSets)
        assertEquals(100.0, summary.topSet!!.weightKg, 0.0001)
        assertEquals(100.0, history.records.getValue(PersonalRecordKind.WEIGHT).weightKg, 0.0001)
    }

    @Test
    fun otherExercisesInTheSameSessionAreIgnored() {
        val day = at("2026-08-10T10:00:00Z")
        val history = ExerciseHistoryBuilder.build(
            exerciseId = squat,
            sessions = listOf(
                workout(
                    "a",
                    day,
                    listOf(
                        squatSet("a", 60.0, 10, day, exerciseId = "ex-curl"),
                        squatSet("a", 100.0, 5, day + 1000),
                    ),
                ),
            ),
            zone = zone,
        )
        assertEquals(1, history.lifetimeWorkingSets)
        assertEquals(500.0, history.lifetimeVolumeKg, 0.0001)
    }

    @Test
    fun unfinishedSessionsAreNotHistory() {
        val day = at("2026-08-10T10:00:00Z")
        val history = ExerciseHistoryBuilder.build(
            exerciseId = squat,
            sessions = listOf(
                workout("live", day, listOf(squatSet("live", 100.0, 5, day)), finished = false),
            ),
            zone = zone,
        )
        assertFalse(history.hasHistory)
        assertTrue(history.records.isEmpty())
    }

    @Test
    fun sessionsWithoutThisExerciseDoNotAppear() {
        val day = at("2026-08-10T10:00:00Z")
        val history = ExerciseHistoryBuilder.build(
            exerciseId = squat,
            sessions = listOf(workout("a", day, listOf(squatSet("a", 60.0, 10, day, exerciseId = "ex-curl")))),
            zone = zone,
        )
        assertTrue(history.sessions.isEmpty())
    }

    @Test
    fun tonnageBucketsByTheConfiguredWeekStart() {
        // 2026-08-16 is a Sunday, 2026-08-17 the Monday after it.
        val sunday = at("2026-08-16T10:00:00Z")
        val monday = at("2026-08-17T10:00:00Z")
        val sessions = listOf(
            workout("a", sunday, listOf(squatSet("a", 100.0, 5, sunday))),
            workout("b", monday, listOf(squatSet("b", 100.0, 5, monday))),
        )

        val mondayWeeks = ExerciseHistoryBuilder
            .build(squat, sessions, zone = zone, weekStart = Weekday.MONDAY)
            .weeklyTonnage
        assertEquals(2, mondayWeeks.size)
        assertEquals(LocalDate.of(2026, 8, 10).toEpochDay(), mondayWeeks.first().weekStart.epochDay)

        val sundayWeeks = ExerciseHistoryBuilder
            .build(squat, sessions, zone = zone, weekStart = Weekday.SUNDAY)
            .weeklyTonnage
        assertEquals(1, sundayWeeks.size)
        assertEquals(LocalDate.of(2026, 8, 16).toEpochDay(), sundayWeeks.single().weekStart.epochDay)
        assertEquals(1000.0, sundayWeeks.single().volumeKg, 0.0001)
    }

    @Test
    fun aBodyweightLiftIsCountedInRepsAndCarriesNoKilograms() {
        // This used to assert the opposite: that eight bodyweight reps were worth 320 kg,
        // being eight times a flat 40 kg stand-in. The number was consistent across every
        // surface and true on none of them.
        val day = at("2026-08-10T10:00:00Z")
        val history = ExerciseHistoryBuilder.build(
            exerciseId = squat,
            sessions = listOf(workout("a", day, listOf(squatSet("a", 0.0, 8, day)))),
            loadClass = LoadClass.BODYWEIGHT,
            zone = zone,
        )
        val summary = history.sessions.single()
        assertEquals(0.0, summary.volumeKg, 0.0001)
        assertEquals(8, summary.bodyweightReps)
        assertNull(summary.estimatedOneRepMaxKg)
        assertEquals(8, history.weeklyTonnage.single().bodyweightReps)
        assertEquals(8, history.lifetimeBodyweightReps)
        assertEquals(0.0, history.lifetimeVolumeKg, 0.0001)
    }

    @Test
    fun aLoadedLiftStillEstimatesAOneRepMax() {
        val day = at("2026-08-10T10:00:00Z")
        val history = ExerciseHistoryBuilder.build(
            exerciseId = squat,
            sessions = listOf(workout("a", day, listOf(squatSet("a", 100.0, 5, day)))),
            loadClass = LoadClass.LOADED,
            zone = zone,
        )
        val summary = history.sessions.single()
        assertEquals(500.0, summary.volumeKg, 0.0001)
        assertEquals(0, summary.bodyweightReps)
        assertNotNull(summary.estimatedOneRepMaxKg)
    }

    @Test
    fun priorSetsAreCutByTheMomentTheSetWasCompleted() {
        // Not by session: reopening an older session must not let a set compare against itself.
        val first = at("2026-08-03T10:00:00Z")
        val second = at("2026-08-10T10:00:00Z")
        val sessions = listOf(
            workout("a", first, listOf(squatSet("a", 100.0, 5, first))),
            workout("b", second, listOf(squatSet("b", 105.0, 5, second))),
        )
        val prior = ExerciseHistoryBuilder.priorSets(squat, sessions, beforeMs = second)
        assertEquals(1, prior.size)
        assertEquals(100.0, prior.single().weightKg, 0.0001)
    }
}
