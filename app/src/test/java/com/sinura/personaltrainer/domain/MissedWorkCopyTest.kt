package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
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
        assertEquals("Choose once. This week will not ask again.", MissedWorkCopy.CAPTION)
    }
}
