package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestIdlePresentationTest {
    @Test
    fun idleDockDoesNotWearTheLiveClockNumeral() {
        val src = readOwned("ui/components/RestTimerUi.kt")
        val idleStart = src.indexOf("fun RestIdleRow")
        val idleEnd = src.indexOf("fun RestLinearTrack")
        assertTrue("RestIdleRow missing", idleStart >= 0)
        assertTrue("RestLinearTrack missing", idleEnd > idleStart)
        val idle = src.substring(idleStart, idleEnd)
        assertFalse("idle rest must not use numeralMd", idle.contains("numeralMd"))
        assertTrue(idle.contains("RestIdleCopy"))
        assertTrue(idle.contains("bodyStrong"))
    }

    @Test
    fun firstRestMentionsUnrestrictedBattery() {
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("RestBatteryCopy.SENTENCE"))
        assertTrue(dock.contains("RestBatteryHintRow"))
        val floor = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(floor.contains("RestBatteryHintRow"))
        assertTrue(floor.contains("RestFloorTags.BATTERY"))
    }

    @Test
    fun idleFloorUsesEmptyRingAndNotRunningKicker() {
        val floor = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(floor.contains("RestIdleCopy.KICKER"))
        assertTrue(floor.contains("idleRingSeconds"))
        assertTrue(floor.contains("afterWarmupHint()"))
        assertFalse(floor.contains("\"Next rest\""))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
