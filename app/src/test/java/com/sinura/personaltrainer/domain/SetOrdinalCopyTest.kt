package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SetOrdinalCopyTest {
    @Test
    fun mixedWarmupWorkingAndExtraNeverSaySetSixOfFive() {
        val lines = SetOrdinalCopy.loggedLines(
            warmupFlags = listOf(true, true, false, false, false, false, false),
            targetSets = 5,
        )
        assertEquals(
            listOf("WU 1", "WU 2", "Set 1 of 5", "Set 2 of 5", "Set 3 of 5", "Set 4 of 5", "Set 5 of 5"),
            lines.take(7),
        )
        val withExtra = SetOrdinalCopy.loggedLines(
            warmupFlags = listOf(true, true, false, false, false, false, false, false),
            targetSets = 5,
        )
        assertEquals("Extra 1", withExtra.last())
        assertEquals(false, withExtra.any { it == "Set 6 of 5" })
    }

    @Test
    fun deleteRederivesLabelsWhileStorageNumbersStaySomeoneElsesJob() {
        val afterDelete = SetOrdinalCopy.loggedLines(
            warmupFlags = listOf(true, false, false),
            targetSets = 3,
        )
        assertEquals(listOf("WU 1", "Set 1 of 3", "Set 2 of 3"), afterDelete)
    }

    @Test
    fun identityLinesAreFullPhrasesWhileChipsStayCompact() {
        assertEquals("Warm-up 2", SetOrdinalCopy.identityWarmup(2))
        assertEquals("Working set 3 of 4", SetOrdinalCopy.identityWorking(3, 4))
        assertEquals("WU 2", SetOrdinalCopy.warmup(2))
        assertEquals("Set 3 of 4", SetOrdinalCopy.working(3, 4))
    }

    @Test
    fun draftLineNamesTheSetAboutToBeLogged() {
        assertEquals(
            "Warm-up 1",
            SetOrdinalCopy.draftLine(
                isWarmup = true,
                warmupLogged = 0,
                workingLogged = 0,
                targetSets = 4,
            ),
        )
        assertEquals(
            "Warm-up 2",
            SetOrdinalCopy.draftLine(
                isWarmup = true,
                warmupLogged = 1,
                workingLogged = 0,
                targetSets = 4,
            ),
        )
        assertEquals(
            "Working set 3 of 4",
            SetOrdinalCopy.draftLine(
                isWarmup = false,
                warmupLogged = 2,
                workingLogged = 2,
                targetSets = 4,
            ),
        )
        assertEquals(
            "Extra 1",
            SetOrdinalCopy.draftLine(
                isWarmup = false,
                warmupLogged = 2,
                workingLogged = 4,
                targetSets = 4,
            ),
        )
        assertEquals(
            "Working set 1",
            SetOrdinalCopy.draftLine(
                isWarmup = false,
                warmupLogged = 0,
                workingLogged = 0,
                targetSets = 0,
            ),
        )
    }
}
