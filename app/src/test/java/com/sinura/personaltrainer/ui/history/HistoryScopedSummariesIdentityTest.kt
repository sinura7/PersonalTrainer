package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HistoryScopedSummariesIdentityTest {
    private fun summary(id: String, volumeKg: Double = 100.0) = SessionSummary(
        id = id,
        routineId = null,
        routineName = "Push",
        date = 1L,
        finishedAt = 2L,
        durationMinutes = 30,
        workingSets = 3,
        volumeKg = volumeKg,
        localEpochDay = 10L,
        cardioSeconds = 0L,
        cardioDistanceMeters = null,
        kind = HistoryKind.WORKOUT,
    )

    @Test
    fun equalContentInDistinctListInstancesGivesTheSameIdentity() {
        val a = listOf(summary("a"), summary("b"))
        val b = listOf(summary("a"), summary("b"))
        assertEquals(scopedSummariesIdentity(a), scopedSummariesIdentity(b))
    }

    @Test
    fun reorderedScopedListGivesTheSameIdentity() {
        val forward = listOf(summary("a"), summary("b"))
        val reversed = listOf(summary("b"), summary("a"))
        assertEquals(scopedSummariesIdentity(forward), scopedSummariesIdentity(reversed))
    }

    @Test
    fun volumeKgChangeGivesADifferentIdentity() {
        val before = listOf(summary("a", volumeKg = 100.0))
        val after = listOf(summary("a", volumeKg = 950.0))
        assertNotEquals(scopedSummariesIdentity(before), scopedSummariesIdentity(after))
    }
}
