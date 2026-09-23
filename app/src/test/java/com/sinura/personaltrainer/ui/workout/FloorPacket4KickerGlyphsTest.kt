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
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val fieldsAt = screen.indexOf("item(key = \"entry\")")
        val rpeAt = screen.indexOf("item(key = \"rpe\")")
        val recAt = screen.indexOf("item(key = \"next-set\")")
        assertTrue("entry must precede recommendations", fieldsAt in 0 until recAt)
        assertTrue("effort must precede recommendations", rpeAt in fieldsAt until recAt)
        assertTrue(screen.contains("val rec = microRec?.takeIf { entryEnabled && !state.draft.isWarmup && SetMicroRecCopy.visibleOnEntry(it) }"))
        assertTrue(screen.contains("onApply = viewModel::applyMicroRec"))
        val card = readOwned("ui/workout/NextSetRecommendation.kt")
        assertTrue(card.contains("if (!SetMicroRecCopy.visibleOnEntry(rec)) return"))
        assertTrue(card.contains("WorkoutTestTags.NEXT_SET"))
        assertTrue(card.contains("private const val NEXT_SET_KICKER = \"Next set\""))
        assertTrue(card.contains("SetMicroRecCopy.numbers(rec, loadClass, unit)"))
        assertTrue(card.contains("SetMicroRecCopy.deltaLine(rec, loadClass, unit)"))
        assertTrue(card.contains("suggestion.explanationShort"))
        assertTrue(card.contains("WorkoutTestTags.MICRO_REC"))
        assertTrue(card.contains("contentDescription = \"Next set, \$numbers\""))
        assertTrue(card.contains("WorkoutTestTags.MICRO_REC_WHY"))
        assertTrue(card.contains("\"Why this set\""))
        assertTrue(card.contains("SetMicroRecCopy.whyLines"))
        assertTrue(card.contains("EvidenceCitationChip"))
        assertTrue(card.contains("SetMicroRecCopy.USE_SUGGESTION"))
        assertTrue(card.contains("SetMicroRecCopy.KEEP_MY_NUMBERS"))
        assertTrue(card.contains("WorkoutTestTags.MICRO_REC_APPLY"))
        assertTrue(card.contains("text = if (applied) \"Applied\" else \"Apply\""))
        assertTrue(card.contains("enabled = enabled && !applied"))
        assertTrue(card.contains("QuietButton("))
        assertFalse("kicker must not be a second Volt", card.contains("PrimaryGymButton"))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse("the dock must not host the rec card", dock.contains("NextSetRecommendation("))
        assertFalse(dock.contains("MICRO_REC"))
        val mark = readOwned("ui/workout/SetMicroRecUi.kt")
        assertTrue(mark.contains("fun workoutMicroRec("))
        assertTrue(mark.contains("CoachEngine.suggest("))
        assertTrue(mark.contains("historyWorking = historySets.map"))
        assertFalse(mark.contains("SetMicroRecCalculator.suggest"))
        assertFalse("the watermark kicker is retired", mark.contains("ProgressionKickerMark"))
        assertFalse(mark.contains("PROGRESSION_KICKER"))
        assertFalse(screen.contains("PROGRESSION_KICKER"))
        val copy = readOwned("domain/SetMicroRec.kt")
        assertTrue(copy.contains("object ProgressionKickerCopy"))
        assertTrue(copy.contains("const val HOLD"))
        assertTrue(copy.contains("const val BACK_OFF"))
        assertTrue(copy.contains("fun fromHint"))
        assertTrue(copy.contains("fun fromMicroRec"))
        assertTrue(copy.contains("fun collapsed"))
        assertTrue(copy.contains("fun deltaLine"))
        assertTrue(copy.contains("ProgressionKickerCopy.PLUS_REP to \"+1 rep\""))
        assertTrue(copy.contains("ProgressionKickerCopy.HOLD to \"Hold the load\""))
        assertTrue(copy.contains("ProgressionKickerCopy.BACK_OFF to \"Back off\""))
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
        val rpe = readOwned("ui/workout/RpeSelector.kt")
        assertTrue(rpe.contains("Kicker(\"RPE\")"))
        assertFalse(rpe.contains("TemperIcons.FloorRpe"))
        val rest = readOwned("ui/workout/RestTimerCard.kt")
        assertTrue(rest.contains("TalkBackPolicy.REST_RUNNING_KICKER"))
        assertTrue(rest.contains("Kicker(text = kicker, color = accent, asHeading = false)"))
        assertTrue(rest.contains("WorkoutTestTags.REST_IDLE"))
        assertFalse(rest.contains("TemperIcons.FloorRest"))
        // Where a glyph still sits beside a kicker — the HOLD / SET bar and the rest page's
        // ring — it stays a supporting mark next to the word.
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("TemperIcons.FloorRest"))
        val instrument = dock.substring(dock.indexOf("fun FloorInstrumentBar"), dock.indexOf("fun SetWorkDock"))
        assertTrue(instrument.contains("leadingGlyph"))
        assertTrue(instrument.contains("FloorFieldGlyph("))
        assertTrue(instrument.contains("Kicker(kicker, color = accent, asHeading = false)"))
        val workoutDock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(workoutDock.contains("SetWorkDock("))
        assertTrue(workoutDock.contains("RestTimerCard("))
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
