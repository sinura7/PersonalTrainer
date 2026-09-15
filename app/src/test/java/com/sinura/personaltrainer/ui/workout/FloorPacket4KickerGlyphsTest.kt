package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 4: HOLD / +N / BACK OFF on the Next row; four glyphs replace
 * weight, reps/time, RPE, and rest labels on the floor.
 */
class FloorPacket4KickerGlyphsTest {
    @Test
    fun kickerSitsOnTheNextRowAndWhyStillOpensTheTrace() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.progressionKickerInline())
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("ProgressionKickerMark("))
        assertTrue(bar.contains("SetMicroRecCopy.kicker"))
        assertTrue(bar.contains("WorkoutTestTags.MICRO_REC_WHY"))
        assertTrue(bar.contains("SetMicroRecCopy.whyLines"))
        assertFalse(
            "kicker must not be a second Volt",
            bar.contains("PrimaryGymButton") &&
                bar.substring(
                    bar.indexOf("fun MicroRecLine"),
                    bar.indexOf("fun SecondaryLogOptions"),
                ).contains("PrimaryGymButton"),
        )
        val mark = readOwned("ui/workout/SetMicroRecUi.kt")
        assertTrue(mark.contains("fun ProgressionKickerMark"))
        assertTrue(mark.contains("WorkoutTestTags.PROGRESSION_KICKER"))
        val copy = readOwned("domain/SetMicroRec.kt")
        assertTrue(copy.contains("object ProgressionKickerCopy"))
        assertTrue(copy.contains("const val HOLD"))
        assertTrue(copy.contains("const val BACK_OFF"))
        assertTrue(copy.contains("fun fromHint"))
        assertTrue(copy.contains("fun fromMicroRec"))
        assertTrue(copy.contains("RpeModifier") || copy.contains("RPE_HOLD"))
    }

    @Test
    fun fourFloorGlyphsRemainAsSupportingMarksBesideWordLabels() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.floorFieldGlyphsReplaceLabels())
        val icons = readOwned("ui/components/TemperIcons.kt")
        assertTrue(icons.contains("val FloorWeight"))
        assertTrue(icons.contains("val FloorRepsTime"))
        assertTrue(icons.contains("val FloorRpe"))
        assertTrue(icons.contains("val FloorRest"))
        val paths = readOwned("ui/components/TemperGlyphPaths.kt")
        assertTrue(paths.contains("const val FLOOR_WEIGHT"))
        assertTrue(paths.contains("const val FLOOR_REPS_TIME"))
        assertTrue(paths.contains("const val FLOOR_RPE"))
        assertTrue(paths.contains("const val FLOOR_REST"))
        val entry = readOwned("ui/components/SetEntryPanel.kt")
        val compactFn = entry.indexOf("private fun CompactFloorEntry")
        val weightStepper = entry.indexOf("fun WeightStepper")
        val floor = entry.substring(compactFn, weightStepper)
        assertTrue(floor.contains("TemperIcons.FloorWeight"))
        assertTrue(floor.contains("TemperIcons.FloorRepsTime"))
        assertTrue(floor.contains("Kicker("))
        assertTrue(floor.contains("meaning.fieldLabel"))
        assertTrue(floor.contains("label = \"Reps\""))
        assertTrue(floor.contains("label = \"Time\""))
        assertTrue(floor.contains("FloorFieldGlyph("))
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        val rpe = bar.substring(bar.indexOf("fun SecondaryLogOptions"))
        assertTrue(rpe.contains("TemperIcons.FloorRpe"))
        assertFalse(rpe.contains("Kicker(\"RPE\")"))
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("TemperIcons.FloorRest"))
        val idleStart = dock.indexOf("fun RestIdleRow")
        val idleEnd = dock.indexOf("fun RestLinearTrack")
        val idle = dock.substring(idleStart, idleEnd)
        assertTrue(idle.contains("FloorFieldGlyph("))
        assertFalse(idle.contains("Kicker(RestIdleCopy.KICKER)"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
