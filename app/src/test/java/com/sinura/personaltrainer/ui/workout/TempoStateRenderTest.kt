package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.coach.CoachEngine
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.domain.setMicroRecInputs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 10_tempo: one render per non-empty state. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class TempoStateRenderTest {
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
    private val tip = TempoCoachTip.NextSet(rec = rec, suggestion = CoachEngine.fromMicroRec(rec))
    private val numbers = SetMicroRecCopy.numbers(rec, LoadClass.LOADED, WeightUnit.LBS)

    @Test
    fun loadingShowsUseButton() {
        compose.setContent {
            TempoSheet(
                tip = tip,
                currentTarget = numbers,
                suggestedTarget = numbers,
                applying = true,
                showIntro = false,
                onUse = {},
                onKeep = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_SHEET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_SHEET_USE).assertIsDisplayed()
    }

    @Test
    fun firstTimeShowsIntroLine() {
        compose.setContent {
            TempoSheet(
                tip = tip,
                currentTarget = numbers,
                suggestedTarget = numbers,
                applying = false,
                showIntro = true,
                onUse = {},
                onKeep = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_SHEET).assertIsDisplayed()
    }

    @Test
    fun resumeShowsSheetWithoutIntro() {
        compose.setContent {
            TempoSheet(
                tip = tip,
                currentTarget = numbers,
                suggestedTarget = numbers,
                applying = false,
                showIntro = false,
                onUse = {},
                onKeep = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_SHEET).assertIsDisplayed()
    }
}
