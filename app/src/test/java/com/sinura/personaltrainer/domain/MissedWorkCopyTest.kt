package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedWorkCopyTest {
    @Test
    fun bodyNeverSaysRecurrence() {
        assertEquals(
            "One planned session was not done. Next week still starts the same way.",
            MissedWorkCopy.body(1),
        )
        assertEquals(
            "3 planned sessions were not done. Next week still starts the same way.",
            MissedWorkCopy.body(3),
        )
        assertEquals("Keep the dates", MissedWorkCopy.KEEP)
        assertEquals("Other choices", MissedWorkCopy.OTHER)
        assertEquals("Choose once. This week will not ask again.", MissedWorkCopy.CAPTION)
    }

    @Test
    fun keepIsTheVoltAndOtherChoicesStartCollapsed() {
        assertEquals(
            listOf(
                MissedWorkCopy.KEEP,
                MissedWorkCopy.OTHER,
            ),
            MissedWorkCopy.actions(otherChoicesOpen = false),
        )
        assertEquals(
            listOf(
                MissedWorkCopy.KEEP,
                MissedWorkCopy.OTHER,
                MissedWorkCopy.MOVE,
                MissedWorkCopy.ADAPT,
                MissedWorkCopy.SKIP,
            ),
            MissedWorkCopy.actions(otherChoicesOpen = true),
        )
        assertEquals(184, MissedWorkCopy.collapsedSavingsDp())
        assertTrue(MissedWorkCopy.firstStartFitsShortPhone(otherChoicesOpen = false))
        assertFalse(MissedWorkCopy.firstStartFitsShortPhone(otherChoicesOpen = true))
    }

    @Test
    fun recoveryVoltStaysQuietWhileTheMissedWorkPromptIsUp() {
        assertEquals(true, MissedWorkCopy.suppressRecoveryVolt(true))
        assertEquals(false, MissedWorkCopy.suppressRecoveryVolt(false))
    }
}
