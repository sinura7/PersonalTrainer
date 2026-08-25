package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionOrderCopyTest {
    @Test
    fun numberedPreviewKeepsTapOrderAndEmptyCopy() {
        assertEquals(SessionOrderCopy.EMPTY_PREVIEW, SessionOrderCopy.numberedPreview(emptyList()))
        assertEquals(
            "1 Squat · 2 Row · 3 Bench",
            SessionOrderCopy.numberedPreview(listOf("Squat", "Row", "Bench", "Curl")),
        )
        assertEquals("1 Squat", SessionOrderCopy.numberedPreview(listOf("Squat")))
    }

    @Test
    fun sectionAndLiftIndexNameTheJob() {
        assertEquals("Session · 1 lift", SessionOrderCopy.sectionLabel(1))
        assertEquals("Session · 4 lifts", SessionOrderCopy.sectionLabel(4))
        assertEquals("Mon · 3", SessionOrderCopy.daySection("Mon", 3))
        assertEquals("Lift 2 of 4", SessionOrderCopy.liftIndex(2, 4))
    }

    @Test
    fun cardSpokenNamesEveryField() {
        assertEquals(
            "1. Squat. Quads. 4 by 6. Rest 2:00. 80 kg",
            SessionOrderCopy.cardSpoken(
                number = 1,
                name = "Squat",
                muscleGroup = "Quads",
                sets = 4,
                reps = 6,
                restClock = "2:00",
                load = "80 kg",
            ),
        )
        assertEquals(
            "2. Hang. 3 by 8. Rest 1:30",
            SessionOrderCopy.cardSpoken(
                number = 2,
                name = "Hang",
                muscleGroup = "",
                sets = 3,
                reps = 8,
                restClock = "1:30",
                load = null,
            ),
        )
    }
}
