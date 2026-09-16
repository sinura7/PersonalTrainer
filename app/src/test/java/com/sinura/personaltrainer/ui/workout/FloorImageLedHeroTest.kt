package com.sinura.personaltrainer.ui.workout

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
 * Image-led floor: 112 dp hero, compact Warm-up row, stable Log dock,
 * 360×800 goldens, TalkBack order, uncropped stills. Zero-weight copy
 * stays “no weight”.
 */
class FloorImageLedHeroTest {
    @Test
    fun heroIsFourTimesTheOldStillNotSixteen() {
        assertEquals(112, Metrics.exerciseHeroImage.value.toInt())
        assertEquals(88, Metrics.exerciseHeroImageLandscape.value.toInt())
        assertEquals(128, Metrics.exerciseHeroMin.value.toInt())
        assertEquals(24, Metrics.equipmentGlyph.value.toInt())
        assertTrue(FloorCompactChrome.imageLedHero())
        val hero = readOwned("ui/workout/CurrentLiftCard.kt")
        assertTrue(hero.contains("fun ExerciseHero("))
        assertTrue(hero.contains("ThumbSize.hero"))
        assertTrue(hero.contains("showBadge = false"))
        assertTrue(hero.contains("artPadding = Metrics.space2"))
        assertFalse(hero.contains("VoltDim"))
        assertFalse(hero.contains("emphasisBorder"))
        assertTrue(hero.contains("Surface2"))
        assertTrue(hero.contains("EquipmentGlyphIcon("))
        val thumb = readOwned("ui/components/ExerciseThumb.kt")
        assertTrue(thumb.contains("ContentScale.Fit"))
        assertTrue(thumb.contains("showBadge: Boolean = true"))
        assertTrue(thumb.contains("Metrics.equipmentGlyph"))
        assertFalse(thumb.contains("ContentScale.Crop"))
    }

    @Test
    fun setContextAndWarmupShareOneRowAndAddLiftLivesInTheSwitcher() {
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        val context = card.indexOf("SET_CONTEXT")
        val warmup = card.indexOf("WARMUP_CHIP")
        val coach = card.indexOf("MicroRecLine(")
        val fields = card.indexOf("SetEntryPanel(")
        assertTrue(context in 0 until warmup)
        assertTrue(warmup in 0 until coach)
        assertTrue(coach in 0 until fields)
        assertTrue(card.contains("heightIn(min = Metrics.touchMin)"))
        assertTrue(card.contains("Kicker(\"Latest sets\")"))
        assertTrue(FloorCompactChrome.addLiftLivesInSwitcher())
        val switcher = readOwned("ui/workout/LiftSwitcherSheet.kt")
        assertTrue(switcher.contains("onAddLift"))
        assertTrue(switcher.contains("SWITCHER_ADD_LIFT"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val lazy = screen.indexOf("LazyColumn(")
        val afterLazy = screen.substring(lazy)
        assertFalse(afterLazy.contains("SecondaryGymButton"))
        assertTrue(screen.contains("LogBarCopy.ADD_LIFT"))
    }

    @Test
    fun dockReservesTimerRailAndKeepsLogFilledVolt() {
        assertTrue(FloorCompactChrome.logButtonStaysAnchored())
        assertFalse(FloorCompactChrome.liftCompleteReplacesClock())
        assertEquals(56, Metrics.logTimerRow.value.toInt())
        assertEquals(56, Metrics.logContextRail.value.toInt())
        assertEquals(72, Metrics.commit.value.toInt())
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("TIMER_ROW"))
        assertTrue(bar.contains("CONTEXT_RAIL"))
        assertTrue(bar.contains("GymUndoHost("))
        assertTrue(bar.contains("GymErrorBanner("))
        assertTrue(bar.contains("GymReceiptBanner("))
        assertTrue(bar.contains("CompletionRail("))
        assertTrue(bar.contains("next = false"))
        assertTrue(bar.contains("finish = false"))
        assertTrue(bar.contains("testTag(WorkoutTestTags.LOG_SET)"))
        assertTrue(bar.contains("height = Metrics.commit"))
        val volt = bar.substring(bar.indexOf("volt = {"), bar.indexOf("fun CompletionRail"))
        assertFalse(volt.contains("WorkoutTestTags.NEXT"))
        assertFalse(volt.contains("WorkoutTestTags.DOCK_FINISH"))
        assertTrue(bar.contains("if (showTimer)"))
        assertFalse(bar.contains("showTimer && !completeDock"))
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
        val current = screen.indexOf("item(key = \"current-lift\")")
        val entry = screen.indexOf("item(key = \"current-entry\")")
        val logBar = screen.indexOf("LogBar(")
        assertTrue(current in 0 until entry)
        assertTrue(logBar in 0 until current || logBar > 0 && current > 0)
        assertEquals("Lift 3 of 7", CurrentLiftCopy.heroOrdinal(3, 7))
        assertEquals("1 of 3 done", CurrentLiftCopy.heroProgress(1, 3))
        val spoken = CurrentLiftCopy.heroSpoken(
            name = "Walking Lunge",
            number = 3,
            total = 7,
            workingLogged = 1,
            targetSets = 3,
            equipmentLabel = "Dumbbell",
            meaning = WeightMeaning.ADDED,
            telemetry = "19 min · 5 sets",
        )
        assertTrue(spoken.contains("Walking Lunge"))
        assertTrue(spoken.contains("Lift 3 of 7"))
        assertTrue(spoken.contains("1 of 3 done"))
        assertTrue(spoken.contains("19 min · 5 sets"))
        val notes = com.sinura.personaltrainer.domain.AccessibilityMatrix.page("active-strength").talkBackNotes
        assertTrue(notes.contains("header, hero identity, set context"))
        assertTrue(notes.contains("decorative"))
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
            "Weight, no weight, bodyweight",
            SetCopy.weightWellSpoken(WeightMeaning.LIFTED, 0.0, WeightUnit.LBS),
        )
        assertFalse(
            SetCopy.weightWellSpoken(WeightMeaning.LIFTED, 0.0, WeightUnit.LBS).contains("0 lb"),
        )
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("maxLines = 2"))
        assertTrue(bar.contains("fontScale >= 2f") || bar.contains("largeType"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
