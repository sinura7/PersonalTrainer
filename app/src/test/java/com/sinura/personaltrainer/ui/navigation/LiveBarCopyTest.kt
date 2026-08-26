package com.sinura.personaltrainer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBarCopyTest {
    @Test
    fun cardioNeverSpeaksWorkoutOrFakeSets() {
        assertEquals("Back to the session", LiveBarCopy.resumeLabel(LiveBarKind.ACTIVITY))
        assertEquals("Finish session", LiveBarCopy.finish(LiveBarKind.ACTIVITY))
        assertEquals("Discard session…", LiveBarCopy.discard(LiveBarKind.ACTIVITY))
        assertEquals("Discard this session?", LiveBarCopy.discardTitle(LiveBarKind.ACTIVITY))
        assertEquals(
            "This deletes the session. This cannot be undone.",
            LiveBarCopy.discardBody(LiveBarKind.ACTIVITY, totalSets = 4),
        )
        assertFalse(LiveBarCopy.showsSets(LiveBarKind.ACTIVITY))
        assertTrue(LiveBarCopy.showsSets(LiveBarKind.WORKOUT))
    }

    @Test
    fun strengthKeepsWorkoutVocabularyAndSetCount() {
        assertEquals("Finish workout", LiveBarCopy.finish(LiveBarKind.WORKOUT))
        assertEquals(
            "This deletes the session and its 3 logged sets. This cannot be undone.",
            LiveBarCopy.discardBody(LiveBarKind.WORKOUT, totalSets = 3),
        )
        assertEquals(
            "This deletes the session. This cannot be undone.",
            LiveBarCopy.discardBody(LiveBarKind.WORKOUT, totalSets = 0),
        )
        assertEquals("Back to the workout", LiveBarCopy.resumeLabel(LiveBarKind.WORKOUT))
    }
}
