package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 4 on the redesigned floor: the next-set call (HOLD / +N / BACK OFF,
 * now the delta line of [NextSetRecommendation]) sits after entry and effort
 * with Why still opening the trace; the floor's hero numerals carry word
 * labels, and the four glyphs stay supporting marks wherever one still sits
 * beside a label.
 *
 * The floor's numerals are rendered in WeightRepsEditorRenderTest and
 * WorkoutFloorComponentsTest: the unit rides the weight, each well names its field aloud,
 * and no heading stands over them. The bans on a glyph or a heading in their place stay
 * here; if W1a's numeric-entry cue needs a mark there, it lifts them on purpose.
 */
class FloorPacket4KickerGlyphsTest {
    @Test
    fun kickerSitsOnTheEntryStripAndWhyStillOpensTheTrace() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.progressionKickerInline())
        // The Next-set card's numbers, change words, reason, evidence, Apply and Why, its place
        // after the entry and the effort, and the coach call behind it are rendered in
        // NextSetRecommendationRenderTest and FloorRestAndCoachWiringRenderTest, and held pure
        // in WorkoutMicroRecTest. The bans stay here.
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val card = readOwned("ui/workout/NextSetRecommendation.kt")
        assertFalse("kicker must not be a second Volt", card.contains("PrimaryGymButton"))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse("the dock must not host the rec card", dock.contains("NextSetRecommendation("))
        assertFalse(dock.contains("MICRO_REC"))
        val mark = readOwned("ui/workout/SetMicroRecUi.kt")
        assertFalse(mark.contains("SetMicroRecCalculator.suggest"))
        assertFalse("the watermark kicker is retired", mark.contains("ProgressionKickerMark"))
        assertFalse(mark.contains("PROGRESSION_KICKER"))
        assertFalse(screen.contains("PROGRESSION_KICKER"))
        val copy = readOwned("domain/SetMicroRec.kt")
        assertTrue(copy.contains("fun fromHint"))
        assertTrue(copy.contains("fun collapsed"))
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
        // The shared entry panel (history's edit sheet) keeps each glyph beside its word label.
        val entry = readOwned("ui/components/SetEntryPanel.kt")
        val compactFn = entry.indexOf("private fun CompactFloorEntry")
        val weightStepper = entry.indexOf("fun WeightStepper")
        val floor = entry.substring(compactFn, weightStepper)
        assertTrue(floor.contains("TemperIcons.FloorWeight"))
        assertTrue(floor.contains("TemperIcons.FloorRepsTime"))
        assertTrue(floor.contains("Text(label, style = InstrumentType.caption"))
        assertTrue(floor.contains("meaning.fieldLabel"))
        assertTrue(floor.contains("label = \"Reps\""))
        assertTrue(floor.contains("label = \"Time\""))
        assertTrue(floor.contains("FloorFieldGlyph("))
        // The floor's hero numerals carry no heading at all now, so the rule they had to obey
        // — a word, never a glyph standing in for one — is kept by there being neither. The
        // field is still named where it has to be: in the well's spoken form, which is a
        // rendered fact (WeightRepsEditorRenderTest, WorkoutFloorComponentsTest).
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertFalse("no glyph stands in for a floor label", editor.contains("FloorFieldGlyph"))
        assertFalse("no heading over the hero numerals", editor.contains("Kicker(text = label"))
        // The effort track's heading and the rest card's kicker are rendered in
        // RpeSelectorRenderTest and RestTimerCardRenderTest: words, not glyphs, and no heading.
        val rpe = readOwned("ui/workout/RpeSelector.kt")
        assertFalse(rpe.contains("TemperIcons.FloorRpe"))
        val rest = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse(rest.contains("TemperIcons.FloorRest"))
        // Where a glyph still sits beside a kicker — the HOLD / SET bar and the rest page's
        // ring — it stays a supporting mark next to the word.
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("TemperIcons.FloorRest"))
        val instrument = dock.substring(dock.indexOf("fun FloorInstrumentBar"), dock.indexOf("fun SetWorkDock"))
        assertTrue(instrument.contains("leadingGlyph"))
        assertTrue(instrument.contains("FloorFieldGlyph("))
        val workoutDock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse("idle rest is the dock card, not the old instrument row", workoutDock.contains("RestIdleRow("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
