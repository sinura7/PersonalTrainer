package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sinura.personaltrainer.util.toCivilYearMonth
import java.time.YearMonth

class ActivityHistoryProjectionTest {
    @Test
    fun cardioOnlyDoesNotProjectKilograms() {
        val session = activity(
            blocks = listOf(cardio(elapsedSeconds = 1_800, distanceMeters = 5_000.0)),
        )
        assertNull(session.toInsightSession())
        assertTrue(session.strengthWork().isEmpty)
        assertEquals(30, session.cardioMinutes())
    }

    @Test
    fun mixedCalendarMarksTheDayWithoutCombiningScores() {
        val session = activity(
            blocks = listOf(
                strength(),
                cardio(elapsedSeconds = 600),
            ),
        )
        val month = TrainingCalendarBuilder.build(
            month = YearMonth.of(2024, 10).toCivilYearMonth(),
            sessions = emptyList(),
            activities = listOf(session),
        )
        val day = month.weeks.flatten().first { it.date.epochDay == 20_000L }
        assertTrue(day.trained)
        assertEquals(listOf("act-1"), day.activityIds)
        assertTrue(day.sessionIds.isEmpty())
        assertEquals(1, month.workingSets)
        assertFalse(day.work.isEmpty)
    }

    @Test
    fun historyGroupIncludesActivities() {
        val session = activity(blocks = listOf(cardio(elapsedSeconds = 120)))
        val groups = groupHistoryByMonth(listOf(session.toHistoryEntry()))
        assertEquals(1, groups.size)
        assertEquals(HistoryKind.ACTIVITY, groups.first().entries.first().kind)
    }

    @Test
    fun strengthActivityProjectsSetsWithoutInventingCardioVolume() {
        val session = activity(
            title = "",
            blocks = listOf(
                strength(warmup = true),
                strength(id = "str-2", setId = "set-2", weightKg = 120.0, isWarmup = false),
                cardio(elapsedSeconds = 600),
            ),
        )
        val projected = session.toInsightSession()!!
        assertEquals(session.id, projected.id)
        assertEquals("", projected.routineName)
        assertEquals(2, projected.exercises.size)
        assertEquals(2, projected.sets.size)
        assertEquals(120.0 * 5, projected.work().volumeKg, 0.001)
        val untitled = session.toHistoryEntry()
        assertEquals("Mixed session", untitled.title)
        val cardioOnly = activity(title = "", blocks = listOf(cardio(elapsedSeconds = 120))).toHistoryEntry()
        assertEquals("Cardio", cardioOnly.title)
        val strengthOnly = activity(
            title = "",
            blocks = listOf(strength()),
        ).toHistoryEntry()
        assertEquals("Workout", strengthOnly.title)
        val workout = WorkoutSession(
            id = "w1",
            routineId = null,
            routineName = null,
            date = 1_700_000_000_000L,
            notes = "",
            durationMinutes = 40,
            startedAt = 1_700_000_000_000L,
            finishedAt = 1_700_000_100_000L,
            exercises = emptyList(),
            sets = emptyList(),
        ).toHistoryEntry()
        assertEquals(HistoryKind.WORKOUT, workout.kind)
        assertEquals("Workout", workout.title)
    }

    private fun activity(
        blocks: List<ActivityBlock>,
        title: String = "Mixed",
    ) = ActivitySession(
        id = "act-1",
        status = ActivityStatus.COMPLETED,
        origin = ActivityOrigin.BACKDATED,
        source = ActivitySource.TEMPER,
        title = title,
        notes = "",
        performedStart = CapturedCivilTime(1_000L, "UTC", 0, 20_000L),
        performedEnd = CapturedCivilTime(2_000L, "UTC", 0, 20_000L),
        templateId = null,
        occurrenceId = null,
        blocks = blocks,
        createdAtMs = 1L,
        updatedAtMs = 1L,
        revision = 1L,
    )

    private fun strength(
        id: String = "str-1",
        setId: String = "set-1",
        weightKg: Double = 100.0,
        warmup: Boolean = false,
        isWarmup: Boolean = warmup,
    ) = StrengthBlock(
        id = id,
        sortOrder = 0,
        exerciseId = "ex-1",
        exerciseName = "Squat",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.BARBELL,
        muscles = listOf(MuscleCredit("quads", 1.0)),
        sets = listOf(
            StrengthSet(setId, 1, weightKg, 5, null, isWarmup, 1L),
        ),
    )

    private fun cardio(elapsedSeconds: Long, distanceMeters: Double? = null) = CardioBlock(
        id = "car-1",
        sortOrder = 1,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = elapsedSeconds,
        movingSeconds = elapsedSeconds,
        distanceMeters = distanceMeters,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )
}
