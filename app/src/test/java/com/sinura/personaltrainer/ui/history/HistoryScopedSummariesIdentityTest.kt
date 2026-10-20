package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HistoryScopedSummariesIdentityTest {
    private fun summary(
        id: String = "s1",
        volumeKg: Double = 100.0,
        routineId: String? = "r1",
        routineName: String? = "Push",
        date: Long = 1L,
        finishedAt: Long? = 2L,
        durationMinutes: Int = 30,
        workingSets: Int = 3,
        localEpochDay: Long = 10L,
        cardioSeconds: Long = 0L,
        cardioDistanceMeters: Double? = null,
        kind: HistoryKind = HistoryKind.WORKOUT,
        stills: List<Exercise> = listOf(
            Exercise(id = "bench", name = "Bench", muscleGroup = "Chest", notes = "", isCustom = false),
        ),
    ) = SessionSummary(
        id = id,
        routineId = routineId,
        routineName = routineName,
        date = date,
        finishedAt = finishedAt,
        durationMinutes = durationMinutes,
        workingSets = workingSets,
        volumeKg = volumeKg,
        localEpochDay = localEpochDay,
        cardioSeconds = cardioSeconds,
        cardioDistanceMeters = cardioDistanceMeters,
        kind = kind,
        stills = stills,
    )

    @Test
    fun equalContentInDistinctListInstancesGivesTheSameIdentity() {
        val a = listOf(summary(id = "a"), summary(id = "b"))
        val b = listOf(summary(id = "a"), summary(id = "b"))
        assertEquals(scopedSummariesIdentity(a), scopedSummariesIdentity(b))
    }

    @Test
    fun reorderedScopedListGivesTheSameIdentity() {
        val forward = listOf(summary(id = "a"), summary(id = "b"))
        val reversed = listOf(summary(id = "b"), summary(id = "a"))
        assertEquals(scopedSummariesIdentity(forward), scopedSummariesIdentity(reversed))
    }

    @Test
    fun idChangeGivesADifferentIdentity() {
        val before = listOf(summary(id = "s1"))
        val after = listOf(summary(id = "s2"))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun finishedAtChangeGivesADifferentIdentity() {
        val before = listOf(summary(finishedAt = 2L))
        val after = listOf(summary(finishedAt = 3L))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun workingSetsChangeGivesADifferentIdentity() {
        val before = listOf(summary(workingSets = 3))
        val after = listOf(summary(workingSets = 4))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun localEpochDayChangeGivesADifferentIdentity() {
        val before = listOf(summary(localEpochDay = 10L))
        val after = listOf(summary(localEpochDay = 11L))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun kindChangeGivesADifferentIdentity() {
        val before = listOf(summary(kind = HistoryKind.WORKOUT))
        val after = listOf(summary(kind = HistoryKind.ACTIVITY))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun cardioSecondsChangeGivesADifferentIdentity() {
        val before = listOf(summary(cardioSeconds = 0L))
        val after = listOf(summary(cardioSeconds = 600L))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun cardioDistanceMetersChangeGivesADifferentIdentity() {
        val before = listOf(summary(cardioDistanceMeters = 1000.0))
        val after = listOf(summary(cardioDistanceMeters = 2000.0))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun volumeKgChangeGivesADifferentIdentity() {
        val before = listOf(summary(volumeKg = 100.0))
        val after = listOf(summary(volumeKg = 950.0))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun routineIdChangeGivesADifferentIdentity() {
        val before = listOf(summary(routineId = "r1"))
        val after = listOf(summary(routineId = "r2"))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun routineNameChangeGivesADifferentIdentity() {
        val before = listOf(summary(routineName = "Push"))
        val after = listOf(summary(routineName = "Pull"))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun dateChangeGivesADifferentIdentity() {
        val before = listOf(summary(date = 1L))
        val after = listOf(summary(date = 2L))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun durationMinutesChangeGivesADifferentIdentity() {
        val before = listOf(summary(durationMinutes = 30))
        val after = listOf(summary(durationMinutes = 45))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

    @Test
    fun stillExerciseSwapGivesADifferentIdentity() {
        val before = listOf(summary(stills = listOf(
            Exercise(id = "bench", name = "Bench", muscleGroup = "Chest", notes = "", isCustom = false),
        )))
        val after = listOf(summary(stills = listOf(
            Exercise(id = "row", name = "Row", muscleGroup = "Back", notes = "", isCustom = false),
        )))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }

}
