package com.sinura.personaltrainer.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {
    @Test
    fun reducedMotionCollapsesDurationsToZero() {
        assertEquals(0, Motion.durationMs(reduced = true, fullMs = Motion.BASE))
        assertEquals(0, Motion.durationMs(reduced = true, fullMs = Motion.DRAW))
        assertEquals(Motion.FAST, Motion.durationMs(reduced = false, fullMs = Motion.FAST))
        assertEquals(Motion.TAP, Motion.durationMs(reduced = false, fullMs = Motion.TAP))
        assertEquals(0, Motion.durationMs(reduced = true, fullMs = Motion.TICK_MS))
        assertEquals(0, Motion.durationMs(reduced = true, fullMs = Motion.FLASH_MS))
    }

    @Test
    fun remainingSitesHonourReducedMotion() {
        val summary = readOwned("ui/summary/WorkoutSummaryScreen.kt")
        assertTrue(summary.contains("instrumentTween(Motion.DRAW)"))
        assertTrue(summary.contains("LocalReducedMotion.current"))
        assertTrue(summary.contains("recordEnter()"))
        assertFalse(summary.contains("RECORD_STAGGER_MS ="))
        val status = readOwned("ui/components/GymStatus.kt")
        assertTrue(status.contains("instrumentTween(Motion.FAST)"))
        assertTrue(status.contains("recordEnter()"))
        assertTrue(status.contains("Motion.STATUS_DWELL_MS"))
        val rest = readOwned("ui/components/Common.kt")
        assertTrue(rest.contains("instrumentLinear(Motion.TICK_MS)"))
        assertTrue(rest.contains("Motion.PULSE_MS"))
        val lists = listOf(
            "ui/progress/ProgressScreen.kt",
            "ui/library/ExerciseLibraryScreen.kt",
            "ui/history/HistoryScreen.kt",
            "ui/exercise/ExerciseDetailScreen.kt",
            "ui/components/ExercisePickerSheet.kt",
        )
        lists.forEach { path ->
            val src = readOwned(path)
            assertFalse(path, src.contains("Modifier.animateItem("))
            assertTrue(path, src.contains("instrumentAnimateItem()"))
        }
        assertEquals(5, lists.size)
    }

    @Test
    fun unusedMotionMembersAreGoneAndDwellsLiveOnMotion() {
        val motion = readOwned("ui/theme/Motion.kt")
        assertFalse(motion.contains("const val SLOW"))
        assertFalse(motion.contains("Emphasized"))
        assertFalse(motion.contains("fun <T> press("))
        assertTrue(motion.contains("fun <T> settle("))
        assertTrue(motion.contains("fun <T> celebrate("))
        assertTrue(motion.contains("STATUS_DWELL_MS"))
        assertTrue(motion.contains("FINISHED_DWELL_MS"))
        assertTrue(motion.contains("RECORD_STAGGER_MS"))
        assertTrue(motion.contains("PULSE_MS"))
        assertTrue(motion.contains("TICK_MS"))
        assertTrue(readOwned("ui/theme/Motion.kt").contains("instrumentAnimateItem"))
    }

    @Test
    fun paletteKeepsTokensAndRequiresANonColourChannel() {
        val colors = readOwned("ui/theme/Color.kt")
        assertTrue(colors.contains("val PrGold = Color(0xFFFFC53D)"))
        assertTrue(colors.contains("val Warn = Color(0xFFFFB020)"))
        assertTrue(colors.contains("val Heat3 = Color(0xFFE25A50)"))
        assertTrue(colors.contains("val Danger = Color(0xFFFF6B6B)"))
        assertTrue(colors.contains("ADR-023"))
        assertTrue(colors.contains("non-colour"))
        val adr = readDocs("architecture/ADR-023-palette-and-reduced-motion.md")
        assertTrue(adr.contains("non-colour"))
        assertTrue(adr.contains("Do not shift Warn toward orange"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }

    private fun readDocs(relative: String): String {
        val roots = listOf(File("docs"), File("../docs"))
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
