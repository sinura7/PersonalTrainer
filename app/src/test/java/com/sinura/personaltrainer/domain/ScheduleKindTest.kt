package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleKindTest {
    @Test
    fun cardioTagRoundTripsKnownTypes() {
        ScheduleKind.planCardioTypes.forEach { type ->
            assertEquals(type, ScheduleKind.cardioType(ScheduleKind.cardio(type)))
        }
        assertEquals(
            listOf(
                CardioType.WALK,
                CardioType.RUN,
                CardioType.RIDE,
                CardioType.ROW,
                CardioType.SWIM,
                CardioType.HIKE,
            ),
            ScheduleKind.planCardioTypes,
        )
        assertTrue(CardioType.SKI !in ScheduleKind.planCardioTypes)
    }

    @Test
    fun unknownOrMissingCardioFallsBackToRun() {
        assertNull(ScheduleKind.cardioType(null))
        assertNull(ScheduleKind.cardioType("cardio:NOPE"))
        assertNull(ScheduleKind.cardioType("aux:stretch"))
        assertEquals(CardioType.RUN, ScheduleKind.cardioTypeOrRun(null))
        assertEquals(CardioType.RUN, ScheduleKind.cardioTypeOrRun("strength"))
    }

    @Test
    fun auxTagRoundTripsPackId() {
        assertEquals("stretch", ScheduleKind.auxPackId(ScheduleKind.aux("stretch")))
        assertTrue(ScheduleKind.isAux(ScheduleKind.aux("holds")))
        assertNull(ScheduleKind.auxPackId("cardio:RUN"))
        assertNull(ScheduleKind.auxPackId(null))
    }
}
