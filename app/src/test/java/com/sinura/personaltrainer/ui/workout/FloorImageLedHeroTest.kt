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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image-led floor: a 112 dp identity with Details beside it and Working /
 * Warm-up under it, stats under the identity, entry before effort before the
 * recommendation, and one companion above commit. Rendered geometry is
 * exercised by WorkoutEntryLayoutInstrumentedTest.
 */
class FloorImageLedHeroTest {
    @Test
    fun imageLedIdentityPreservesUncroppedExerciseArtwork() {
        assertEquals(112, Metrics.exerciseHeroImage.value.toInt())
        assertEquals(64, Metrics.workoutIdentityImage.value.toInt())
        assertEquals(24, Metrics.equipmentGlyph.value.toInt())
        assertTrue(FloorCompactChrome.imageLedHero())
        assertTrue(FloorCompactChrome.oneCurrentLiftOnFloor())
        val hero = readOwned("ui/workout/ExerciseHeader.kt")
        assertTrue(hero.contains("fun ExerciseHeader("))
        assertTrue(hero.contains("size = Metrics.exerciseHeroImage"))
        assertTrue(hero.contains("artPadding = Metrics.space2"))
        assertTrue(
            "long names and font 1.6 fall back to the 64 dp still",
            hero.contains("val stacked = density.fontScale >= 1.6f || titleLines > 2"),
        )
        assertTrue(hero.contains("size = Metrics.workoutIdentityImage"))
        assertTrue(hero.contains("artPadding = Metrics.space1"))
        assertTrue(hero.contains("showBadge = false"))
        assertTrue(
            "equipment is the kicker, not a badge over the still",
            hero.contains("Kicker(text = equipment, color = TextTertiary, asHeading = false)"),
        )
        assertTrue(hero.contains("CurrentLiftCopy.secondaryLine(lift.exercise.equipment.label, meaning)"))
        assertTrue(hero.contains("style = InstrumentType.heroTitle"))
        assertFalse(hero.contains("EquipmentGlyphIcon("))
        assertFalse(hero.contains("VoltDim"))
        assertFalse(hero.contains("emphasisBorder"))
        assertFalse("no card around the identity", hero.contains("Surface2"))
        assertFalse(hero.contains(".background("))
        val thumb = readOwned("ui/components/ExerciseThumb.kt")
        assertTrue(thumb.contains("ContentScale.Fit"))
        assertTrue(thumb.contains("showBadge: Boolean = true"))
        assertTrue(thumb.contains("Metrics.equipmentGlyph"))
        assertFalse(thumb.contains("ContentScale.Crop"))
    }

    @Test
    fun setTypePrecedesEntryAndRecommendationsFollowEffort() {
        val hero = readOwned("ui/workout/ExerciseHeader.kt")
        val context = hero.indexOf("WorkoutTestTags.SET_CONTEXT")
        val toggleCall = hero.indexOf("SetTypeToggle(")
        val toggleFn = hero.indexOf("fun SetTypeToggle(")
        assertTrue(context in 0 until toggleCall)
        assertTrue(toggleCall in 0 until toggleFn)
        val toggle = hero.substring(toggleFn)
        val working = toggle.indexOf("WorkoutTestTags.WORKING_CHIP")
        val warmup = toggle.indexOf("WorkoutTestTags.WARMUP_CHIP")
        assertTrue(working in 0 until warmup)
        assertTrue(toggle.contains(".selectableGroup()"))
        assertTrue(toggle.contains(".testTag(WorkoutTestTags.SET_TYPE)"))
        assertTrue(toggle.contains("role = Role.RadioButton"))
        assertTrue(toggle.contains("\"Working set, selected\""))
        assertTrue(toggle.contains("\"Warm-up set, not selected\""))
        val chip = readOwned("ui/components/InstrumentChip.kt")
        assertTrue(chip.contains(".heightIn(min = Metrics.touchMin)"))
        assertTrue(chip.contains("Modifier.selectable("))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val header = screen.indexOf("item(key = \"exercise-header\")")
        val stats = screen.indexOf("item(key = \"stats\")")
        val entry = screen.indexOf("item(key = \"entry\")")
        val rpe = screen.indexOf("item(key = \"rpe\")")
        val nextSet = screen.indexOf("item(key = \"next-set\")")
        val history = screen.indexOf("item(key = \"set-history\")")
        assertTrue(header in 0 until stats)
        assertTrue(stats in 0 until entry)
        assertTrue(entry in 0 until rpe)
        assertTrue(rpe in 0 until nextSet)
        assertTrue(nextSet in 0 until history)
        assertTrue(screen.indexOf("WeightRepsEditor(") in entry until rpe)
        assertTrue(screen.indexOf("WarmupRampRow(") in entry until rpe)
        assertTrue(screen.indexOf("RpeSelector(") in rpe until nextSet)
        assertTrue(screen.indexOf("NextSetRecommendation(") in nextSet until history)
        assertTrue(screen.indexOf("SetHistoryStrip(") > history)
        assertTrue(screen.contains("val rec = microRec?.takeIf { entryEnabled && !state.draft.isWarmup }"))
        assertTrue(screen.contains("receiptSetId = logReceipt?.setId"))
        assertTrue(screen.contains("WorkoutSetsSheet("))
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
        assertTrue(dock.contains(".heightIn(min = Metrics.logTimerRow)"))
        assertTrue(dock.contains(".testTag(WorkoutTestTags.TIMER_ROW)"))
        assertFalse(dock.contains("CONTEXT_RAIL"))
        assertFalse(dock.contains("logContextRail"))
        assertTrue(dock.contains("GymUndoHost("))
        assertTrue(dock.contains("WorkoutTestTags.ERROR_DETAILS"))
        assertTrue(dock.contains("WorkoutTestTags.CANCEL_EDIT"))
        assertTrue(dock.contains("WorkoutTestTags.COMPANION_CLOCK"))
        assertFalse(dock.contains("GymReceiptBanner("))
        assertFalse(dock.contains("LOG_RECEIPT"))
        assertTrue(dock.contains("Add another set"))
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
        val prelude = dock.substring(dock.indexOf("prelude = {"), dock.indexOf("volt = {"))
        val context = prelude.substring(
            prelude.indexOf("contextVisible -> FlowRow("),
            prelude.indexOf("completeDock -> Row("),
        )
        assertTrue(context.contains("if (timer.show) clockButton()"))
        val complete = prelude.substring(
            prelude.indexOf("completeDock -> Row("),
            prelude.indexOf("timer.show && timer.hideIdleRest && !timedActive ->"),
        )
        assertTrue(complete.contains("WorkoutTestTags.ANOTHER_SET"))
        assertTrue(complete.contains("if (timer.show) clockButton()"))
        assertTrue(prelude.contains("else -> timerSurface()"))
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
        val hero = readOwned("ui/workout/ExerciseHeader.kt")
        assertTrue(hero.contains("val spoken = CurrentLiftCopy.cardSpoken("))
        assertFalse("session telemetry belongs to Session summary", hero.contains("heroSpoken("))
        assertFalse(hero.contains("telemetry"))
        assertTrue(hero.contains("contentDescription = \"\$spoken. \$setContext. \${CurrentLiftCopy.SWITCH}\""))
        assertTrue(hero.contains("selected = true"))
        assertTrue(hero.contains("onClickLabel = CurrentLiftCopy.SWITCH, onClick = onOpenSwitcher"))
        assertTrue(hero.contains(".testTag(WorkoutTestTags.liftCard(lift.exercise.id))"))
        assertTrue(hero.contains("spoken = \"Exercise details\""))
        assertTrue(hero.contains("modifier = Modifier.testTag(WorkoutTestTags.DETAILS)"))
        assertTrue(FloorCompactChrome.headerShowsSessionProgress())
        assertFalse(FloorCompactChrome.headerShowsMinuteTelemetryOnly())
        val chrome = readOwned("ui/workout/WorkoutHeader.kt")
        val line = chrome.indexOf(".testTag(WorkoutTestTags.PROGRESS_LINE)")
        val bar = chrome.indexOf(".testTag(WorkoutTestTags.PROGRESS_BAR)")
        assertTrue(line in 0 until bar)
        assertTrue(
            "the progress line is spoken once",
            chrome.substring(line, bar).contains(".semantics { contentDescription = spoken }"),
        )
        assertTrue("the segmented bar is decorative", chrome.substring(bar).contains(".clearAndSetSemantics { }"))
        val notes = AccessibilityMatrix.page("active-strength").talkBackNotes
        assertTrue(notes.contains("header, compact exercise identity, set context"))
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
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(
            editor.contains(
                "spoken = SetCopy.weightWellSpoken(meaning = meaning, weightKg = weightKg, unit = unit, entryPrecision = true)",
            ),
        )
        assertTrue("a bodyweight lift has no weight column", editor.contains("val showWeight = meaning != WeightMeaning.NONE"))
        assertTrue(editor.contains("\" · Suggested\""))
        assertTrue(editor.contains("rememberTextMeasurer"))
        assertTrue(editor.contains("FlowRow("))
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
