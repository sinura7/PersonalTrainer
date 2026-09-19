package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartCopyTest {
    @Test
    fun sheetOffersFreeRoutinesCardioAndExtra() {
        assertEquals("Start a free workout", HomeStartCopy.FREE)
        assertEquals("Select a routine", HomeStartCopy.ROUTINE)
        assertEquals("Cardio", HomeStartCopy.CARDIO)
        assertEquals("Extra", HomeStartCopy.EXTRA)
        assertTrue(HomeStartCopy.EXTRA_SUBTITLE.contains("equipment", ignoreCase = true))
        assertTrue(HomeStartCopy.EMPTY_BODY.startsWith("Start a workout"))
        assertFalse(HomeStartCopy.EMPTY_BODY.contains("Add a workout"))
        assertFalse(HomeStartCopy.ROUTINE_SUBTITLE.contains("generator", ignoreCase = true))
    }

    @Test
    fun namedRoutinesSkipExtraPacksAndKeepPlanRoutines() {
        val push = routine("r-push", "Push")
        val golf = routine("r-golf", "Golf warm-up")
        val stretch = routine("r-stretch", "Stretch")
        val named = HomeStart.namedRoutines(listOf(golf, push, stretch))
        assertEquals(listOf("Push"), named.map { it.name })
    }

    private fun routine(id: String, name: String) = Routine(
        id = id,
        name = name,
        notes = "",
        createdAt = 1L,
        updatedAt = 1L,
        exercises = emptyList(),
    )
}
