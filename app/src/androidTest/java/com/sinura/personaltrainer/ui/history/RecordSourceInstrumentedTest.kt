package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performScrollToNode
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.PersonalRecord
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.exercise.RecordsCard
import com.sinura.personaltrainer.ui.exercise.ExerciseTrendCard
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Native record-link targets and complete source/date copy; this class is component evidence. */
class RecordSourceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activitySourceKeepsItsCapturedDayAndOpensTheExactStoreAtLargeText() {
        val set = ExerciseSetRecord("source-set", "source-activity", 60.0, 8, 100L)
        val record = PersonalRecord(PersonalRecordKind.WEIGHT, set.setId, set.sessionId, 60.0, 8, 60.0, 100L)
        val source = ExerciseSessionSummary(sessionId = set.sessionId, sessionName = "Complete mixed activity with a deliberately long saved title", performedAtMs = 90L,
            topSet = set, workingSets = 1, volumeKg = 480.0, estimatedOneRepMaxKg = 76.0, sets = listOf(set), kind = HistoryKind.ACTIVITY,
            localEpochDay = LocalDate.of(2025, 12, 31).toEpochDay())
        var opened = ""
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PersonalTrainerTheme {
                    Column(Modifier.fillMaxSize().background(Pit).verticalScroll(rememberScrollState())) {
                        RecordsCard(records = mapOf(record.kind to record), unit = WeightUnit.KG, sessions = listOf(source),
                            onOpenSession = { opened = "wrong:$it" }, onOpenActivity = { opened = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("record-source-WEIGHT").performScrollTo().assertIsDisplayed()
        capture("record-source-captured-date")
        compose.onNodeWithTag("record-source-WEIGHT").performClick()
        assertEquals(set.sessionId, opened)
        compose.onNodeWithText("Heaviest 60 kg × 8 · 31 December 2025").assertIsDisplayed()
    }

    @Test fun bodyweightRecordDoesNotInventZeroKilogramsOrAnUnavailableLink() {
        val record = PersonalRecord(PersonalRecordKind.REPS, "gone-set", "gone-session", 0.0, 15, 15.0, 100L)
        compose.setContent {
            PersonalTrainerTheme {
                RecordsCard(records = mapOf(record.kind to record), unit = WeightUnit.LBS, sessions = emptyList(),
                    onOpenSession = { error("Missing source must not navigate") }, onOpenActivity = { error("Missing source must not navigate") })
            }
        }
        compose.onNodeWithText("15 bodyweight reps", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Supporting session unavailable").assertIsDisplayed()
        compose.onNodeWithTag("record-source-REPS").assertDoesNotExist()
        capture("record-source-bodyweight-missing")
    }

    @Test fun irregularSessionDatesDiscloseEqualSpacingAndHaveExactEquivalentValues() {
        val tag = "p07-irregular-session-trend"
        val dates = listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 4, 1))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PersonalTrainerTheme {
                    Column(Modifier.fillMaxSize().background(Pit).verticalScroll(rememberScrollState())) {
                        ExerciseTrendCard(title = "Estimated 1RM", values = listOf(60.0, 65.0, 70.0),
                            epochDays = dates.map { it.toEpochDay() }, pointNoun = "session", hint = "Recorded estimates",
                            measure = "kg", line = true, grouped = false, tag = tag)
                    }
                }
            }
        }
        compose.onNodeWithText("Recorded sessions · equal spacing · focused scale").performScrollTo().assertIsDisplayed()
        capture("irregular-session-spacing")
        compose.onNodeWithTag("$tag-values").performScrollTo().performClick()
        for ((date, value) in listOf("1 January 2026" to "60 kg", "2 January 2026" to "65 kg", "1 April 2026" to "70 kg")) {
            compose.onNodeWithTag("$tag-data-list").performScrollToNode(hasText(date))
            compose.onNode(hasText(date) and hasAnyAncestor(hasTestTag("$tag-data"))).assertIsDisplayed()
            compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("$tag-data"))).assertIsDisplayed()
        }
        NativeArtifacts.write("notes-irregular-session-exact-values", compose.onNodeWithTag("$tag-data").captureToImage().asAndroidBitmap())
        compose.onNodeWithTag("$tag-close").performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        NativeArtifacts.write("notes-$name", compose.onRoot().captureToImage().asAndroidBitmap())
    }
}
