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
        assertFalse("idle rest label must not use numeralMd", idle.contains("numeralMd"))
        assertTrue(idle.contains("RestIdleCopy"))
        assertTrue(idle.contains("bodyStrong"))
        assertFalse(
            "idle Start next must not be composed",
            idle.contains("RestIdleCopy.START_NEXT"),
        )
        assertTrue(idle.contains("RestIdleCopy.START"))
        assertTrue(idle.contains("TextButton("))
        assertFalse(idle.contains("SnapValueWheel("))
        assertTrue(idle.contains("RestPresetChips("))
        assertFalse("idle rest must not use a filled Volt", idle.contains("PrimaryGymButton"))
    }

    @Test
    fun firstRestMentionsUnrestrictedBattery() {
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("RestBatteryCopy.SENTENCE"))
        assertTrue(dock.contains("RestBatteryHintRow"))
        val floor = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(floor.contains("RestBatteryHintRow"))
        assertTrue(floor.contains("RestFloorTags.BATTERY"))
        assertTrue(floor.contains("RestHonestyCopy.EXACT_DENIED"))
        assertTrue(floor.contains("RestFloorTags.EXACT"))
        assertFalse(floor.contains("precise", ignoreCase = true))
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
