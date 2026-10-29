package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.coach.CoachEngine
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.domain.setMicroRecInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class TempoSheetRenderTest {
    @get:Rule val compose = createComposeRule()

    private val rec = checkNotNull(
        SetMicroRecCalculator.suggest(
            setMicroRecInputs(
                editing = false,
                loadType = LoadType.EXTERNAL,
                unit = WeightUnit.LBS,
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = 31.75,
                working = listOf(LoggedSetView(31.75, 10, 8, false)),
                lastAnySetWasWarmup = false,
                hint = null,
                lighterWeek = false,
                draftWeightKg = 31.75,
                draftReps = 10,
                draftRpe = null,
                nowMs = 0L,
                todayEpochDay = 0L,
                rpeIntent = false,
            ),
        ),
    )
    private val suggestion = CoachEngine.fromMicroRec(rec)
    private val tip = TempoCoachTip.NextSet(rec = rec, suggestion = suggestion)

    @Test
    fun nextSetSheetShowsTargetsAndUse() {
        var used = 0
        compose.setContent {
            TempoSheet(
                tip = tip,
                currentTarget = SetMicroRecCopy.numbers(rec, LoadClass.LOADED, WeightUnit.LBS),
                suggestedTarget = SetMicroRecCopy.numbers(rec, LoadClass.LOADED, WeightUnit.LBS),
                applying = false,
                showIntro = true,
                onUse = { used += 1 },
                onKeep = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_SHEET).assertIsDisplayed()
        compose.onNodeWithText("Tempo suggests changes between sets.").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_SHEET_USE).performClick()
        assertEquals(1, used)
    }

    @Test
    fun previewOnlyRecDoesNotQualifyForAutoSheet() {
        assertFalse(rec.previewOnly)
        assertTrue(rec.showApply)
    }

    @Test
    fun alreadyAppliedNextSetDoesNotOpenSheet() {
        assertTrue(
            rec.isApplied(
                weightKg = 31.75,
                reps = 10,
                rpe = null,
                unit = WeightUnit.LBS,
            ),
        )
        assertFalse(
            rec.isApplied(
                weightKg = 31.75,
                reps = 11,
                rpe = null,
                unit = WeightUnit.LBS,
            ),
        )
    }
}
