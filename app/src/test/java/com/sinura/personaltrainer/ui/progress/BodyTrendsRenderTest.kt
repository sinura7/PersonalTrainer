package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.data.local.dao.WorkingSetCountRow
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v4 V7 (D10 to D12): Body's bodyweight, records and weekly sets cards. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class BodyTrendsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 4).toEpochDay() // a Wednesday
    private val opened = mutableListOf<String>()

    @Test
    fun bodyweightTrendShowsLatestAndChange() {
        mount(bodyweight = listOf(BodyweightEntry(today - 20, 82.0), BodyweightEntry(today, 80.5)))
        compose.onNodeWithTag(BodyTrendTags.BODYWEIGHT).assertIsDisplayed()
        compose.onNodeWithText("80.5 kg", substring = true).assertExists()
        compose.onNodeWithText("−1.5 kg since", substring = true).assertExists()
    }

    @Test
    fun noBodyweightSaysWhereToAddOne() {
        mount()
        compose.onNodeWithText("No bodyweight logged yet. Add one in Settings.").assertExists()
    }

    @Test
    fun recordsCardListsTopFiveThenAllRecords() {
        val records = (1..7).map { record("lift$it", it) }
        mount(records = records)
        compose.onAllNodesWithTag(BodyTrendTags.record("lift6")).assertCountEquals(0)
        compose.onNodeWithTag(BodyTrendTags.record("lift5")).assertExists()
        compose.onNodeWithText("All records (7)").performClick()
        compose.onNodeWithTag(BodyTrendTags.record("lift7")).assertExists()
        compose.onNodeWithTag(BodyTrendTags.record("lift1")).performClick()
        assertEquals(listOf("lift1"), opened)
    }

    @Test
    fun weeklyVolumeCountsWorkingSetsPerMuscleOverEightIsoWeeks() {
        val first = BodyTrendsMath.firstWeekStart(today)
        assertEquals(LocalDate.of(2026, 9, 14).toEpochDay(), first) // Monday, 7 weeks before this one
        val catalog = listOf(
            Exercise("bench", "Bench", "Chest", "", false),
            Exercise("squat", "Squat", "Quads", "", false),
        )
        val day = { epochDay: Long -> epochDay * 86_400_000L }
        val rows = listOf(
            WorkingSetCountRow("bench", day(first - 1), 9), // before the window
            WorkingSetCountRow("bench", day(first), 3),
            WorkingSetCountRow("bench", day(today), 4),
            WorkingSetCountRow("bench", day(today - 1), 1),
            WorkingSetCountRow("squat", day(first + 7), 5),
        )
        val weeks = BodyTrendsMath.weeklySets(rows, catalog, first) { it / 86_400_000L }
        val chest = weeks.single { it.muscle == CanonicalMuscle.CHEST }
        assertEquals(listOf(3, 0, 0, 0, 0, 0, 0, 5), chest.sets)
        assertEquals(8, weeks.first().sets.size)

        mount(weekly = weeks)
        compose.onNodeWithTag(BodyTrendTags.volume(CanonicalMuscle.CHEST)).assertIsDisplayed()
    }

    @Test
    fun emptyVolumeSaysSo() {
        mount()
        compose.onNodeWithText("No working sets in the last 8 weeks.").assertExists()
    }

    private fun record(id: String, n: Int) = PrSummaryRow(
        exerciseId = id, exerciseName = "Lift $n", kind = PersonalRecordKind.WEIGHT,
        valueKg = 100.0 + n, reps = 5, achievedAt = 1_790_000_000_000L - n,
    )

    private fun mount(
        bodyweight: List<BodyweightEntry> = emptyList(),
        records: List<PrSummaryRow> = emptyList(),
        weekly: List<MuscleWeeks> = emptyList(),
    ) {
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Column {
                        BodyweightTrendCard(bodyweight, WeightUnit.KG)
                        RecordsCard(records = records, unit = WeightUnit.KG, onOpenExercise = { opened += it })
                        WeeklyVolumeCard(weekly)
                    }
                }
            }
        }
    }
}
