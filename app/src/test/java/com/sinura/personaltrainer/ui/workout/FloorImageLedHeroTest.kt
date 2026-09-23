package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.AccessibilityMatrix
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.GoldenPageCatalog
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Metrics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image-led floor: a 112 dp identity with Details beside it and Working /
 * Warm-up under it, stats under the identity, entry before effort before the
 * recommendation, and one companion above commit. Rendered geometry is
 * exercised by WorkoutEntryLayoutInstrumentedTest.
 *
 * What the identity, the set-type toggle, the entry order and the dock's companion do is
 * held by rendered tests now (ExerciseHeaderRenderTest, WorkoutDockRenderTest,
 * FloorScreenWiringRenderTest), because W1a rebuilds those lines on purpose. What stays
 * here is the static policy around them: bans, tokens, and surfaces W1a does not touch.
 */
class FloorImageLedHeroTest {
    @Test
    fun imageLedIdentityPreservesUncroppedExerciseArtwork() {
        assertEquals(88, Metrics.exerciseHeroImage.value.toInt())
        assertEquals(64, Metrics.workoutIdentityImage.value.toInt())
        assertEquals(24, Metrics.equipmentGlyph.value.toInt())
        assertTrue(FloorCompactChrome.imageLedHero())
        assertTrue(FloorCompactChrome.oneCurrentLiftOnFloor())
        // The 88 dp still, its 64 dp fallback at large text, and the equipment kicker are
        // rendered in ExerciseHeaderRenderTest.
        val hero = readOwned("ui/workout/ExerciseHeader.kt")
        assertTrue(hero.contains("fun ExerciseHeader("))
        assertFalse(hero.contains("EquipmentGlyphIcon("))
        assertFalse(hero.contains("VoltDim"))
        assertFalse(hero.contains("emphasisBorder"))
        // No card around the identity: the header's one fill is the small corner mark on the
        // picture that says it opens Details (W1a).
        assertEquals("no card around the identity", 1, Regex("Surface2\\)").findAll(hero).count())
        assertEquals(1, Regex("\\.background\\(").findAll(hero).count())
        assertTrue(hero.contains(".background(Surface2)"))
        val thumb = readOwned("ui/components/ExerciseThumb.kt")
        assertTrue(thumb.contains("ContentScale.Fit"))
        assertTrue(thumb.contains("showBadge: Boolean = true"))
        assertTrue(thumb.contains("Metrics.equipmentGlyph"))
        assertFalse(thumb.contains("ContentScale.Crop"))
    }

    @Test
    fun setTypePrecedesEntryAndRecommendationsFollowEffort() {
        // The toggle's radio semantics sit under the identity (ExerciseHeaderRenderTest),
        // and the loop's order, receipt chip and saved-sets sheet are tapped through the
        // screen in FloorScreenWiringRenderTest.
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("val rec = microRec?.takeIf { entryEnabled && !state.draft.isWarmup && SetMicroRecCopy.visibleOnEntry(it) }"))
        assertFalse(screen.contains("LatestWorkoutSet("))
        assertFalse(screen.contains("workout-latest-saved"))
        assertTrue(FloorCompactChrome.progressionKickerInline())
        assertTrue(FloorCompactChrome.setHistoryOnFloor())
        assertFalse(FloorCompactChrome.addSetHiddenOnFloor())
        assertTrue(FloorCompactChrome.addLiftLivesInSwitcher())
        val switcher = readOwned("ui/workout/LiftSwitcherSheet.kt")
        assertTrue(switcher.contains("onAddLift"))
        assertTrue(switcher.contains("SWITCHER_ADD_LIFT"))
        val lazy = screen.indexOf("LazyColumn(")
        val afterLazy = screen.substring(lazy)
        assertFalse(afterLazy.contains("SecondaryGymButton"))
        assertTrue(screen.contains("WorkoutTestTags.DOCK_ADD_LIFT"))
    }

    @Test
    fun dockHasOneCompanionWithoutAnEmptyContextReservation() {
        assertTrue(FloorCompactChrome.logButtonStaysAnchored())
        assertFalse(FloorCompactChrome.liftCompleteReplacesClock())
        assertTrue(FloorCompactChrome.oneClockTwoModes())
        assertEquals(56, Metrics.logTimerRow.value.toInt())
        assertEquals(72, Metrics.commit.value.toInt())
        assertTrue(FloorCompactChrome.timerIsCompactInstrumentBar())
        assertFalse(FloorCompactChrome.idleRestIsInstrumentBar())
        assertTrue(FloorCompactChrome.restIsDockCard())
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("PinnedDock("))
        assertFalse(dock.contains("CONTEXT_RAIL"))
        assertFalse(dock.contains("logContextRail"))
        assertFalse(dock.contains("GymReceiptBanner("))
        assertFalse(dock.contains("LOG_RECEIPT"))
        assertTrue(dock.contains("nextAct -> WorkoutTestTags.NEXT"))
        assertTrue(dock.contains("finishAct -> WorkoutTestTags.DOCK_FINISH"))
        assertTrue(dock.contains("else -> WorkoutTestTags.LOG_SET"))
        assertTrue(dock.contains("height = Metrics.commit"))
        val volt = dock.substring(dock.indexOf("volt = {"), dock.indexOf("if (saveDetails"))
        assertTrue(volt.contains("key(action.identity)"))
        assertTrue(volt.contains("hapticFeedback = false"))
        assertTrue(volt.contains("text = state.verb"))
        assertTrue(volt.contains("supporting = state.payload"))
        assertTrue(volt.contains("textStyle = InstrumentType.commit"))
        assertTrue(volt.contains("contentDescription = spokenAction"))
        assertEquals(1, Regex("PrimaryGymButton\\(").findAll(dock).count())
        // The clock stays reachable in every companion: beside error / undo / Cancel edit,
        // beside Add another set, and as the surface itself when nothing else needs the room.
        // WorkoutDockRenderTest composes each of those companions and finds the clock.
        assertFalse(dock.contains("showTimer && !completeDock"))
        val rest = readOwned("ui/workout/RestTimerCard.kt")
        assertTrue(rest.contains(".heightIn(min = Metrics.commit)"))
        assertTrue(rest.contains(".testTag(if (idle) WorkoutTestTags.REST_IDLE else WorkoutTestTags.REST_BAR)"))
    }

    @Test
    fun goldensNameWorkingWarmupRestHoldSuccessErrorCompletionFontAndMotion() {
        assertEquals(360, GoldenPageCatalog.FLOOR_WIDTH_DP)
        assertEquals(800, GoldenPageCatalog.FLOOR_HEIGHT_DP)
        assertEquals(9, GoldenPageCatalog.floorStateIds.size)
        listOf(
            "working",
            "warmup",
            "rest",
            "hold",
            "success",
            "error",
            "completion",
            "font20",
            "reduced-motion",
        ).forEach { id ->
            assertTrue(id, id in GoldenPageCatalog.floorStateIds)
            assertTrue(GoldenPageCatalog.floorAssetName(id), GoldenPageCatalog.isCommitted(GoldenPageCatalog.floorAssetName(id)))
        }
    }

    @Test
    fun talkBackOrderAndHeroCopyStayWords() {
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val topBar = screen.indexOf("topBar = {")
        val bottomBar = screen.indexOf("bottomBar = {")
        val dock = screen.indexOf("WorkoutDock(")
        val header = screen.indexOf("item(key = \"exercise-header\")")
        val entry = screen.indexOf("item(key = \"entry\")")
        assertTrue(topBar in 0 until bottomBar)
        assertTrue(dock in bottomBar until header)
        assertTrue(header in 0 until entry)
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
        // rendered in ExerciseHeaderRenderTest.
        val hero = readOwned("ui/workout/ExerciseHeader.kt")
        assertFalse("session telemetry belongs to Session summary", hero.contains("heroSpoken("))
        assertFalse(hero.contains("telemetry"))
        assertTrue(FloorCompactChrome.headerShowsSessionProgress())
        assertFalse(FloorCompactChrome.headerShowsMinuteTelemetryOnly())
        val chrome = readOwned("ui/workout/WorkoutHeader.kt")
        val line = chrome.indexOf(".testTag(WorkoutTestTags.PROGRESS_LINE)")
        val bar = chrome.indexOf(".testTag(WorkoutTestTags.PROGRESS_BAR)")
        assertTrue(line in 0 until bar)
        assertTrue(
            "the progress line is spoken once",
            chrome.substring(line, bar).contains(".semantics { contentDescription = spokenForm }"),
        )
        // The same tag and spoken form wherever the plan's words sit: the compact title in
        // landscape (with the routine name), the caption line in portrait.
        assertTrue(chrome.contains("titleModifier = if (planAsTitle) progressLine(\"\$routineName. \$spoken\") else Modifier,"))
        assertTrue(chrome.contains("modifier = progressLine(spoken),"))
        assertTrue("the segmented bar is decorative", chrome.substring(bar).contains(".clearAndSetSemantics { }"))
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
        val thumb = readOwned("ui/components/ExerciseThumb.kt")
        assertTrue(thumb.contains("contentScale = ContentScale.Fit"))
        assertFalse(thumb.contains("ContentScale.Crop"))
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
        // WeightRepsEditorRenderTest and WorkoutFloorComponentsTest.
        val next = readOwned("ui/workout/NextSetRecommendation.kt")
        assertTrue(next.contains("private const val NEXT_SET_KICKER = \"Next set\""))
        assertTrue(next.contains("if (!SetMicroRecCopy.visibleOnEntry(rec)) return"))
        assertTrue(next.contains("WorkoutTestTags.MICRO_REC_APPLY"))
        assertTrue(next.contains("WorkoutTestTags.MICRO_REC_WHY"))
        val rpe = readOwned("ui/workout/RpeSelector.kt")
        assertTrue(rpe.contains("rememberTextMeasurer"))
        assertTrue(rpe.contains("FlowRow("))
        assertTrue(FloorCompactChrome.rpeTrackFitsWithoutScroll())
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
