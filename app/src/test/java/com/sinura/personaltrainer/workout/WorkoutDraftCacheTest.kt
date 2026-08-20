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
}
