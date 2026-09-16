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
    fun kickerSitsOnTheEntryStripAndWhyStillOpensTheTrace() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.progressionKickerInline())
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        val recAt = card.indexOf("MicroRecLine(")
        val fieldsAt = card.indexOf("SetEntryPanel(")
        assertTrue("rec strip must sit above the fields", recAt in 0 until fieldsAt)
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        val micro = bar.substring(
            bar.indexOf("fun MicroRecLine"),
            bar.indexOf("fun SecondaryLogOptions"),
        )
        assertTrue(micro.contains("SetMicroRecCopy.collapsed"))
        assertTrue(micro.contains("WorkoutTestTags.MICRO_REC_WHY"))
        assertTrue(micro.contains("SetMicroRecCopy.whyLines"))
        assertTrue(micro.contains("SetMicroRecCopy.USE_SUGGESTION"))
        assertTrue(micro.contains("SetMicroRecCopy.KEEP_MY_NUMBERS"))
        assertFalse("kicker must not be a second Volt", micro.contains("PrimaryGymButton"))
        assertFalse(
            "the dock must not host the rec strip",
            bar.substring(0, bar.indexOf("fun MicroRecLine")).contains("MicroRecLine("),
        )
        val mark = readOwned("ui/workout/SetMicroRecUi.kt")
        assertTrue(mark.contains("fun ProgressionKickerMark"))
        assertTrue(mark.contains("WorkoutTestTags.PROGRESSION_KICKER"))
        assertTrue(mark.contains("Coach.decide("))
        assertFalse(mark.contains("SetMicroRecCalculator.suggest"))
        val copy = readOwned("domain/SetMicroRec.kt")
        assertTrue(copy.contains("object ProgressionKickerCopy"))
        assertTrue(copy.contains("const val HOLD"))
        assertTrue(copy.contains("const val BACK_OFF"))
        assertTrue(copy.contains("fun fromHint"))
        assertTrue(copy.contains("fun fromMicroRec"))
        assertTrue(copy.contains("fun collapsed"))
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
        val idleEnd = dock.indexOf("fun RestDurationSheet")
        val idle = dock.substring(idleStart, idleEnd)
        assertTrue(idle.contains("TemperIcons.FloorRest"))
        assertTrue(idle.contains("leadingGlyph"))
        assertFalse(idle.contains("Kicker(RestIdleCopy.KICKER)"))
        val instrument = dock.substring(dock.indexOf("fun FloorInstrumentBar"), dock.indexOf("fun SetWorkDock"))
        assertTrue(instrument.contains("FloorFieldGlyph("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
