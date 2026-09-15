package com.sinura.personaltrainer.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutDraftCacheTest {
    @Test
    fun storesAndClearsPerSession() {
        val cache = WorkoutDraftCache()
        val draft = WorkoutDraft(
            sessionId = "s1",
            exerciseId = "ex-1",
            weightKg = 80.0,
            reps = 5,
            rpe = 8,
            isWarmup = false,
            notes = "paused",
        )
        cache.put(draft)
        assertEquals(draft, cache.get("s1"))
        assertNull(cache.get("other"))
        cache.clear("other")
        assertEquals(draft, cache.get("s1"))
        cache.clear("s1")
        assertNull(cache.get("s1"))
    }

    @Test
    fun storesPerLiftWithoutClobberingTheOther() {
        val cache = WorkoutDraftCache()
        val squat = WorkoutDraft("s1", "squat", 155.0, 8, 8, false, "", dirty = true)
        val row = WorkoutDraft("s1", "row", 87.5, 6, null, false, "")
        cache.put(squat)
        cache.put(row)
        assertEquals(row, cache.get("s1"))
        assertEquals(squat, cache.getLift("s1", "squat"))
        assertEquals(row, cache.getLift("s1", "row"))
        cache.select("s1", "squat")
        assertEquals(squat, cache.get("s1"))
        cache.removeLift("s1", "row")
        assertNull(cache.getLift("s1", "row"))
        assertEquals(squat, cache.getLift("s1", "squat"))
    }

    @Test
    fun twoSessionsDoNotOverwriteEachOther() {
        val cache = WorkoutDraftCache()
        cache.put(WorkoutDraft("a", "ex", 100.0, 5, null, false, ""))
        cache.put(WorkoutDraft("b", "ex", 90.0, 5, null, false, ""))
        assertEquals("a", cache.get("a")?.sessionId)
        assertEquals("b", cache.get("b")?.sessionId)
        cache.clear("a")
        assertNull(cache.get("a"))
        assertEquals("b", cache.get("b")?.sessionId)
    }
}
