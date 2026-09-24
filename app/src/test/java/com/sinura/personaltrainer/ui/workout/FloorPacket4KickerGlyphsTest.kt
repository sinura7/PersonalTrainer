package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Packet 4 on the redesigned floor: the next-set call (HOLD / +N / BACK OFF, now the delta line
 * of [NextSetRecommendation]) sits after entry and effort with Why still opening the trace, and
 * the floor's labels are words, never a glyph standing in for one.
 *
 * The Next-set card's numbers, change words, reason, evidence, Apply and Why, its place after
 * the entry and the effort, and the coach call behind it are rendered in
 * NextSetRecommendationRenderTest and FloorRestAndCoachWiringRenderTest, and held pure in
 * WorkoutMicroRecTest. The floor's numerals are rendered in WeightRepsEditorRenderTest and
 * WorkoutFloorComponentsTest: the unit rides the weight, each well names its field aloud, and no
 * heading stands over them.
 *
 * One floor glyph is still drawn: the running rest ring on the lock screen wears the rest glyph
 * in place of the word REST, not beside it (RestRingGlyphRenderTest, audit T1c-2). The HOLD /
 * SET bar draws none, since nothing passes it one, and the weight, reps-and-time and effort
 * glyphs live only on the shared panel's compact path, which nothing composes; those are W2a's
 * to remove. The bans stay here; if W1a's numeric-entry cue needs a mark on the floor, it lifts
 * them on purpose.
 */
class FloorPacket4KickerGlyphsTest {
    @Test
    fun theNextSetCardIsNoSecondVoltAndTheWatermarkKickerStaysRetired() {
        val card = ownedSource("ui/workout/NextSetRecommendation.kt")
        assertFalse("kicker must not be a second Volt", card.contains("PrimaryGymButton"))
        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse("the dock must not host the rec card", dock.contains("NextSetRecommendation("))
        assertFalse(dock.contains("MICRO_REC"))
        val mark = ownedSource("ui/workout/SetMicroRecUi.kt")
        assertFalse(mark.contains("SetMicroRecCalculator.suggest"))
        assertFalse("the watermark kicker is retired", mark.contains("ProgressionKickerMark"))
        assertFalse(mark.contains("PROGRESSION_KICKER"))
        assertFalse(ownedSource("ui/workout/ActiveWorkoutScreen.kt").contains("PROGRESSION_KICKER"))
    }

    @Test
    fun noGlyphOrHeadingStandsInForAFloorLabel() {
        // The floor's hero numerals carry no heading at all now, so the rule they had to obey
        // — a word, never a glyph standing in for one — is kept by there being neither. The
        // field is still named where it has to be: in the well's spoken form, which is a
        // rendered fact (WeightRepsEditorRenderTest, WorkoutFloorComponentsTest).
        val editor = ownedSource("ui/workout/WeightRepsEditor.kt")
        assertFalse("no glyph stands in for a floor label", editor.contains("FloorFieldGlyph"))
        assertFalse("no heading over the hero numerals", editor.contains("Kicker(text = label"))
        // The effort track's heading and the rest card's kicker are rendered in
        // RpeSelectorRenderTest and RestTimerCardRenderTest: words, not glyphs.
        assertFalse(ownedSource("ui/workout/RpeSelector.kt").contains("TemperIcons.FloorRpe"))
        assertFalse(ownedSource("ui/workout/RestTimerCard.kt").contains("TemperIcons.FloorRest"))
        assertFalse(
            "idle rest is the dock card, not the old instrument row",
            ownedSource("ui/workout/WorkoutDock.kt").contains("RestIdleRow("),
        )
    }
}
