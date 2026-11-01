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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.PersonalRecords
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.ui.history.HistoryTags
import com.sinura.personaltrainer.ui.components.SessionLogTags
import java.time.ZoneId
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
    fun noBodyweightOffersTheCheckInAction() {
        var logged = 0
        mount(onLogWeight = { logged++ })
        compose.onNodeWithText("No bodyweight logged yet.").assertExists()
        compose.onNodeWithTag(BodyTrendTags.LOG_WEIGHT).performClick()
        assertEquals(1, logged)
    }

    @Test
    fun bodyweightUsesTheLastTwelveEntriesNotADateWindow() {
        // 14 entries a month apart: the oldest two drop, the twelve kept span almost a year.
        val entries = (0 until 14).map { BodyweightEntry(today - 30L * (13 - it), 90.0 - it) }
        assertEquals(12, BodyTrendsMath.recentBodyweight(entries).size)
        assertEquals(today - 30L * 11, BodyTrendsMath.recentBodyweight(entries).first().epochDay)
        mount(bodyweight = entries)
        compose.onNodeWithText("77 kg", substring = true).assertExists()
        compose.onNodeWithText("11 kg since", substring = true).assertExists()
    }

    @Test
    fun aFailedBodyweightReadShowsRetryNotNothingYet() {
        var retried = 0
        mount(bodyweightRead = CardRead.FAILED, onRetry = { retried++ })
        assertFailedCard(BodyTrendTags.BODYWEIGHT, "No bodyweight logged yet.")
        compose.onNodeWithTag(BodyTrendTags.retry(BodyTrendTags.BODYWEIGHT)).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun aFailedRecordsReadShowsRetryNotNothingYet() {
        mount(recordsRead = CardRead.FAILED)
        assertFailedCard(BodyTrendTags.RECORDS, "No records yet.")
    }

    @Test
    fun aFailedVolumeReadShowsRetryNotNothingYet() {
        mount(volumeRead = CardRead.FAILED)
        assertFailedCard(BodyTrendTags.VOLUME, "No working sets in the last 8 weeks.")
    }

    @Test
    fun aStaleReadKeepsTheValueAndMarksIt() {
        mount(records = listOf(record("lift1", 1)), recordsRead = CardRead.STALE)
        compose.onNodeWithTag(BodyTrendTags.stale(BodyTrendTags.RECORDS)).assertExists()
        compose.onNodeWithTag(BodyTrendTags.record("lift1")).assertExists()
    }

    @Test
    fun anEstimatedOneRepMaxSaysItIsDerivedAndFromWhichSet() {
        mount(records = listOf(record("bench", 0).copy(kind = PersonalRecordKind.ESTIMATED_ONE_REP_MAX, valueKg = 60.0, reps = 8)))
        val estimate = WeightConverter.formatDisplayNumber(checkNotNull(PersonalRecords.estimatedOneRepMaxKg(60.0, 8)))
        compose.onNodeWithText("Est. 1RM $estimate kg from 60 kg × 8", substring = true).assertExists()
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
    fun groupsByIsoWeekAndMuscle() {
        val first = BodyTrendsMath.firstWeekStart(today)
        val catalog = listOf(
            Exercise("bench", "Bench", "Chest", "", false),
            Exercise("fly", "Fly", "Chest", "", false),
            Exercise("squat", "Squat", "Quads", "", false),
        )
        val rows = listOf(
            WorkingSetCountRow("bench", (first + 7) * DAY_MS, 2),
            WorkingSetCountRow("fly", (first + 13) * DAY_MS, 3), // the same ISO week's Sunday
            WorkingSetCountRow("squat", (first + 14) * DAY_MS, 4),
        )
        val weeks = BodyTrendsMath.weeklySets(rows, catalog, first) { it / DAY_MS }
        assertEquals(listOf(0, 5, 0, 0, 0, 0, 0, 0), weeks.single { it.muscle == CanonicalMuscle.CHEST }.sets)
        assertEquals(listOf(0, 0, 4, 0, 0, 0, 0, 0), weeks.single { it.muscle == CanonicalMuscle.QUADRICEPS }.sets)
    }

    @Test
    fun sunday2359AndMonday0000LandInDifferentWeeks() {
        val zone = ZoneId.of("America/Toronto")
        val first = BodyTrendsMath.firstWeekStart(today)
        val sunday = LocalDate.of(2026, 11, 1) // the Sunday before today's ISO week
        val lateSunday = sunday.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val monday = sunday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val catalog = listOf(Exercise("bench", "Bench", "Chest", "", false))
        val weeks = BodyTrendsMath.weeklySets(
            listOf(WorkingSetCountRow("bench", lateSunday, 2), WorkingSetCountRow("bench", monday, 3)),
            catalog, first,
        ) { ms -> java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay() }
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 2, 3), weeks.single().sets)
    }

    @Test
    fun musclesComeFromTheJunctionAndSortByThisWeek() {
        val first = BodyTrendsMath.firstWeekStart(today)
        // muscleGroup text says Chest, the exercise_muscles primary says Triceps: the junction wins.
        val dip = Exercise(id = "dip", name = "Dip", muscleGroup = "Chest", notes = "", isCustom = false, muscles = listOf(
            MuscleCredit(MuscleNormalizer.keyOf(CanonicalMuscle.TRICEPS), 1.0),
            MuscleCredit(MuscleNormalizer.keyOf(CanonicalMuscle.CHEST), 0.4),
        ))
        val squat = Exercise("squat", "Squat", "Quads", "", false)
        val rows = listOf(
            WorkingSetCountRow("squat", first * DAY_MS, 20), // big total, nothing this week
            WorkingSetCountRow("dip", today * DAY_MS, 2),
        )
        val weeks = BodyTrendsMath.weeklySets(rows, listOf(dip, squat), first) { it / DAY_MS }
        assertEquals(listOf(CanonicalMuscle.TRICEPS, CanonicalMuscle.QUADRICEPS), weeks.map { it.muscle })
    }

    @Test
    fun bodyHasNoSessionList() {
        val deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        try {
            val vm = ProgressViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
            compose.setContent {
                PersonalTrainerTheme {
                    CompositionLocalProvider(LocalReducedMotion provides true) {
                        ProgressScreen(onOpenLibrary = {}, onOpenExercise = {}, onOpenRoutines = {}, viewModel = vm)
                    }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag(BodyTags.MAP).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithTag(SessionLogTags.ROW).assertCountEquals(0)
            compose.onAllNodesWithTag(HistoryTags.LIST).assertCountEquals(0)
        } finally {
            deps.close()
        }
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

    private fun assertFailedCard(card: String, emptyCopy: String) {
        compose.onNodeWithTag(BodyTrendTags.failed(card)).assertExists()
        compose.onNodeWithTag(BodyTrendTags.retry(card)).assertExists()
        compose.onAllNodesWithText(emptyCopy).assertCountEquals(0)
    }

    private fun mount(
        bodyweight: List<BodyweightEntry> = emptyList(),
        records: List<PrSummaryRow> = emptyList(),
        weekly: List<MuscleWeeks> = emptyList(),
        bodyweightRead: CardRead = CardRead.OK,
        recordsRead: CardRead = CardRead.OK,
        volumeRead: CardRead = CardRead.OK,
        onRetry: () -> Unit = {},
        onLogWeight: () -> Unit = {},
    ) {
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Column {
                        BodyweightTrendCard(entries = bodyweight, unit = WeightUnit.KG, read = bodyweightRead, onRetry = onRetry, onLogWeight = onLogWeight)
                        RecordsCard(records = records, unit = WeightUnit.KG, onOpenExercise = { opened += it }, read = recordsRead, onRetry = onRetry)
                        WeeklyVolumeCard(weeks = weekly, read = volumeRead, onRetry = onRetry)
                    }
                }
            }
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
