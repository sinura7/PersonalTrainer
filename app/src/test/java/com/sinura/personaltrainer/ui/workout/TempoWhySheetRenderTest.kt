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
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.coach.CoachEngine
import com.sinura.personaltrainer.domain.coach.TempoWhySheetCopy
import com.sinura.personaltrainer.domain.setMicroRecInputs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class TempoWhySheetRenderTest {
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
    private val model = TempoWhySheetCopy.forNextSet(rec, suggestion, LoadClass.LOADED, WeightUnit.LBS)

    @Test
    fun theSheetShowsTitleSummaryEvidenceAndActions() {
        var applied = 0
        compose.showFloor {
            TempoWhySheet(
                model = model,
                canApply = true,
                applied = false,
                onApply = { applied += 1 },
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertIsDisplayed()
        compose.onNodeWithText(TempoWhySheetCopy.TITLE_NEXT).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SUMMARY).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_CALLOUT).assertIsDisplayed()
        compose.onNodeWithText(TempoWhySheetCopy.SECTION_EVIDENCE).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_USE).performClick()
        assertEquals(1, applied)
    }
}
