package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.AccessibilityMatrix
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image-led floor: an 88 dp identity with Details on its picture, Working / Warm-up
 * under it, stats under the identity, entry before effort before the recommendation, and one
 * companion above the commit.
 *
 * What the identity, the set-type toggle, the entry order and the dock's companion do is held
 * by rendered tests (ExerciseHeaderRenderTest, WorkoutDockRenderTest, FloorScreenWiringRenderTest,
 * DockCommitRenderTest), and since audit T1c-2 so is what used to be read here as text: no card
 * around the identity, whose one fill is the Details mark (IdentityDrawingRenderTest); the commit
 * as the dock's one filled Volt (DockVoltRenderTest); the upright plan line spoken once over a
 * bar that says nothing (WorkoutHeaderRowRenderTest); a still's badge and its cap
 * (ExerciseThumbRenderTest); Add exercise on an empty session's dock
 * (EmptySessionFloorRenderTest); a still fitted whole into a box of any shape
 * (ExerciseThumbRenderTest). What stays is the static policy around them: bans, tokens, copy and
 * catalog facts, and a ban on every scale but Fit beside that render, since a render sees only
 * the boxes it is given.
 */
class FloorImageLedHeroTest {
    @Test
    fun stillsAreNeverCroppedOrStretchedAndTheIdentityWearsNoVoltOrGlyph() {
        assertEquals(88, Metrics.exerciseHeroImage.value.toInt())
        assertEquals(64, Metrics.workoutIdentityImage.value.toInt())
        assertEquals(24, Metrics.equipmentGlyph.value.toInt())
        val hero = ownedSource("ui/workout/ExerciseHeader.kt")
        assertFalse(hero.contains("EquipmentGlyphIcon("))
        assertFalse(hero.contains("VoltDim"))
        assertFalse(hero.contains("emphasisBorder"))
        val thumb = ownedSource("ui/components/ExerciseThumb.kt")
        assertFalse(thumb.contains("ContentScale.Crop"))
        assertFalse("a still is fitted whole into its box, at no other scale", NOT_FIT.containsMatchIn(thumb))
    }

    @Test
    fun noLatestSavedRowAndNoSecondaryButtonInTheSetLoop() {
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("LatestWorkoutSet("))
        assertFalse(screen.contains("workout-latest-saved"))
        assertFalse(sourceFrom(screen, "LazyColumn(").contains("SecondaryGymButton"))
    }

    @Test
    fun theDockKeepsItsTokensAndNoContextRailReceiptOrHiddenClock() {
        assertEquals(56, Metrics.logTimerRow.value.toInt())
        assertEquals(72, Metrics.commit.value.toInt())
        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("CONTEXT_RAIL"))
        assertFalse(dock.contains("logContextRail"))
        assertFalse(dock.contains("GymReceiptBanner("))
        assertFalse(dock.contains("LOG_RECEIPT"))
        // The clock stays reachable in every companion: beside error / undo / Cancel edit,
        // beside Add another set, and as the surface itself when nothing else needs the room.
        // WorkoutDockRenderTest composes each of those companions and finds the clock.
        assertFalse(dock.contains("showTimer && !completeDock"))
        // One companion above one commit: DockVoltRenderTest draws the commit as the dock's only
        // filled Volt in each companion state; this keeps a second filled button out of the rest.
        assertEquals("one filled Volt in the dock", 1, PRIMARY_BUTTON.findAll(dock).count())
    }

    @Test
    fun heroCopyStaysWordsAndTheIdentityCarriesNoTelemetry() {
        assertEquals("Lift 3 of 7", CurrentLiftCopy.heroOrdinal(3, 7))
        assertEquals("1 of 3 done", CurrentLiftCopy.heroProgress(1, 3))
        assertEquals("Switch exercise", CurrentLiftCopy.SWITCH)
        assertEquals("Details", CurrentLiftCopy.DETAILS)
        val spoken = CurrentLiftCopy.cardSpoken(
            name = "Walking Lunge",
            number = 3,
            total = 7,
            workingLogged = 1,
            targetSets = 3,
            equipmentLabel = "Dumbbell",
            meaning = WeightMeaning.ADDED,
        )
        assertTrue(spoken.startsWith("Current. "))
        assertTrue(spoken.contains("Walking Lunge"))
        assertTrue(spoken.contains("Lift 3 of 7"))
        assertTrue(spoken.contains("1 of 3 done"))
        assertTrue(spoken.contains("Dumbbell"))
        // What the identity, the switch and Details announce, and what a tap on each does, is
        // rendered in ExerciseHeaderRenderTest; the reading order (header, identity, entry,
        // dock) is where each sits on screen, top to bottom, in LandscapeChromeRenderTest and
        // FloorScreenWiringRenderTest.
        val hero = ownedSource("ui/workout/ExerciseHeader.kt")
        assertFalse("session telemetry belongs to Session summary", hero.contains("heroSpoken("))
        assertFalse(hero.contains("telemetry"))
        val notes = AccessibilityMatrix.page("active-strength").talkBackNotes
        assertTrue(notes.contains("header with its progress line, exercise identity"))
        assertTrue(notes.contains("decorative"))
        assertTrue(notes.contains("Switch exercise is explicit"))
    }

    @Test
    fun longLimbAndEquipmentStillsStayFitAndZeroWeightIsNoWeight() {
        val names = DefaultExercises.catalog().map { it.name }.toSet()
        assertTrue("Walking Lunge", "Walking Lunge" in names)
        assertTrue("Leg Curl", "Leg Curl" in names)
        val lunge = DefaultExercises.catalog().first { it.name == "Walking Lunge" }
        val curl = DefaultExercises.catalog().first { it.name == "Leg Curl" }
        assertTrue(lunge.imageKey.isNotBlank())
        assertTrue(curl.imageKey.isNotBlank())
        val thumb = ownedSource("ui/components/ExerciseThumb.kt")
        assertFalse(thumb.contains("ContentScale.Crop"))
        assertFalse("a long-limbed still is fitted whole, never cut, stretched or drawn at its own size", NOT_FIT.containsMatchIn(thumb))
        assertEquals("no weight", SetCopy.NO_WEIGHT)
        assertEquals(
            "Weight, no weight",
            SetCopy.weightWellSpoken(WeightMeaning.LIFTED, 0.0, WeightUnit.LBS),
        )
        assertFalse(
            SetCopy.weightWellSpoken(WeightMeaning.LIFTED, 0.0, WeightUnit.LBS).contains("0 lb"),
        )
        val bwHero = SetCopy.weightEntryHero(WeightMeaning.ADDED, 0.0, WeightUnit.LBS)
        assertEquals(SetCopy.BW_SHORT, bwHero.value)
        assertNull(bwHero.unitSuffix)
        assertFalse("${bwHero.value} ${bwHero.unitSuffix}".contains("0 lb"))
        // The editor's spoken zero, bodyweight column and warm-up ramp are rendered in
        // WeightRepsEditorRenderTest and WorkoutFloorComponentsTest. The Next-set card and the
        // effort track, one row at 360 dp and wrapping where it runs out of room, are rendered
        // in NextSetRecommendationRenderTest and RpeSelectorRenderTest.
    }

    private companion object {
        /**
         * Any scale but Fit: Crop, FillWidth and FillHeight cut whatever overflows, FillBounds
         * stretches the still out of its proportions, None draws it at its own size and cuts the
         * rest, and Inside is Fit only while the still is larger than its box.
         */
        val NOT_FIT = Regex("ContentScale\\.(?!Fit\\b)")

        /** A call of the filled Volt button. */
        val PRIMARY_BUTTON = Regex("\\bPrimaryGymButton\\(")
    }
}
