package com.sinura.personaltrainer.ui.history

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.HistoryPeriodCodec
import com.sinura.personaltrainer.domain.HistoryPeriodRange
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.HistoryPeriodMath
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Real AppNav period selection. The runner terminates the process between stage and resume. */
@RunWith(AndroidJUnit4::class)
class HistoryPeriodContinuityInstrumentedTest {
    private val phase = InstrumentationRegistry.getArguments().getString("historyRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment("Synthetic P06")
    private lateinit var container: AppContainer
    private lateinit var marker: File
    private lateinit var today: LocalDate
    private lateinit var priorAnchor: LocalDate
    private var kept = false
    private val synthetic = linkedMapOf<LocalDate, String>()
    private val denseIds = mutableListOf<String>()
    private val admittedIds = mutableListOf<String>()
    private var summaries = emptyList<SessionSummary>()
    private var originalActivities = ""
    private var weekStart = Weekday.MONDAY
    private var originalPeriod: HistoryPeriodSelection? = null
    private val preparation = object : ExternalResource() {
        override fun before() {
          try {
            check(phase in setOf("connected", "connected-dense", "stage", "resume", "stage-baseline", "resume-baseline"))
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            environment.prepare(container)
            marker = File(app.noBackupFilesDir, "synthetic-p06-history-period.txt")
            today = LocalDate.now()
            priorAnchor = today.minusMonths(1)
            app.markColdStartIntroShown()
            if (phase.startsWith("resume")) {
                val saved = marker.readLines()
                check(saved.size == 3 && saved[0].toLong() == today.toEpochDay()) {
                    "Repeat the staged fixture after a civil-day rollover"
                }
                priorAnchor = LocalDate.ofEpochDay(saved[1].toLong())
                originalPeriod = saved[2].takeUnless { it == "null" }?.let {
                    HistoryPeriodCodec.decodeDraft(JsonParser.parseString(it))
                }
            } else {
                check(!marker.exists()) { "Do not overwrite an unfinished synthetic stage" }
                originalPeriod = read {
                    val handle = container.historyPeriodDrafts.open()
                    check(handle.readable) { "Do not replace an unreadable original preference" }
                    handle.remembered
                }
            }
            read {
                check(container.workoutRepository.getInProgress() == null) { "Do not replace another live workout" }
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                container.preferencesRepository.setLaunchPermissionsAsked(true)
                container.preferencesRepository.markRestAlertsAsked()
                container.preferencesRepository.markRestBatteryHintShown()
            }
            if (phase.startsWith("connected")) read {
                originalActivities = container.activityRepository.all().toString()
                weekStart = container.preferencesRepository.schedulePreferences.first().weekStart
                for (date in listOf(today, today.minusYears(1).withDayOfMonth(5), today.minusYears(3).withDayOfMonth(5))) {
                    val at = if (date == today) container.time.captureNow().instantMillis - 600_000L
                        else container.time.startOfDayMillis(CivilDate.fromEpochDay(date.toEpochDay()), container.time.defaultZoneId()) + 43_200_000L
                    val id = "synthetic-p06-${UUID.randomUUID()}"
                    admittedIds += id
                    val outcome = container.confirmActivity(ActivityDraft(
                        id = id, status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
                        title = "Synthetic walk · ${date.year}", performedStart = container.time.capture(at),
                        performedEnd = container.time.capture(at + 600_000L),
                        blocks = listOf(CardioBlock(
                            id = "$id-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                            elapsedSeconds = 600, movingSeconds = null, distanceMeters = 1000.0,
                            elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
                        )),
                    ), container.time.captureNow())
                    check(outcome is ActivityWrite.Accepted) { outcome.toString() }
                    synthetic[date] = outcome.session.id
                }
                if (phase == "connected-dense") {
                    val lift = container.exerciseRepository.observeAll().first { it.isNotEmpty() }
                        .first { it.loadType == LoadType.EXTERNAL }
                    for (index in 0 until 247) {
                        val date = when (index) {
                            0 -> today.minusYears(4).withMonth(1).withDayOfMonth(15)
                            1 -> today.minusYears(2).withMonth(4).withDayOfMonth(15)
                            else -> today.minusYears(5).withMonth(2).withDayOfMonth(14)
                        }
                        val at = container.time.startOfDayMillis(CivilDate.fromEpochDay(date.toEpochDay()),
                            container.time.defaultZoneId()) + 43_200_000L + index * 1_000L
                        val id = "synthetic-p06-dense-${UUID.randomUUID()}"
                        admittedIds += id
                        val blocks = if (index % 2 == 0) listOf(StrengthBlock(
                            id = "$id-block", sortOrder = 0, exerciseId = lift.id, exerciseName = lift.name,
                            loadType = lift.loadType, equipment = lift.equipment, muscles = emptyList(),
                            sets = listOf(StrengthSet(id = "$id-set", setNumber = 1,
                                weightKg = 50.0 + index, reps = 5, rpe = 8, isWarmup = false, completedAtMs = at + 6_000L)),
                        )) else listOf(CardioBlock(
                            id = "$id-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                            elapsedSeconds = 600, movingSeconds = null, distanceMeters = 1000.0,
                            elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
                        ))
                        val outcome = container.confirmActivity(ActivityDraft(
                            id = id, status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
                            title = "Synthetic long history $index", performedStart = container.time.capture(at),
                            performedEnd = container.time.capture(at + 600_000L), blocks = blocks,
                        ), container.time.captureNow())
                        check(outcome is ActivityWrite.Accepted) { outcome.toString() }
                        denseIds += outcome.session.id
                    }
                    assertEquals(250, synthetic.size + denseIds.size)
                }
                val workouts = container.workoutRepository.observeSessionSummariesHealth().first { it is DataHealth.Available }
                val activities = container.activityRepository.observeCompletedSummariesHealth().first { it is DataHealth.Available }
                summaries = (workouts as DataHealth.Available).value + (activities as DataHealth.Available).value
            }
          } catch (failure: Throwable) {
              // ExternalResource.after is not called when before fails. Stable IDs are
              // reserved before submission, so even a lost acknowledgement is inspectable.
              try { clearSeededActivities() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
              try { environment.restore() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
              throw failure
          }
        }

        override fun after() {
            try {
                if (!kept) {
                    // The inner Activity rule is closed before this cleanup takes ownership.
                    read {
                        val handle = container.historyPeriodDrafts.open()
                        val original = originalPeriod
                        if (original == null) container.historyPeriodDrafts.reset(handle)
                        else container.historyPeriodDrafts.keep(handle, original)
                    }
                    if (marker.exists()) check(marker.delete())
                }
            } finally {
                try {
                    clearSeededActivities()
                } finally { environment.restore() }
            }
        }
    }

    private fun clearSeededActivities() {
        if (admittedIds.isEmpty()) return
        read {
            for (id in admittedIds) {
                if (container.activityRepository.get(id) != null) {
                    check(container.activityRepository.deleteCompleted(id) is ActivityWrite.Accepted)
                }
            }
            assertEquals("Navigation preserves every original activity", originalActivities,
                container.activityRepository.all().toString())
        }
    }
    private val activityRule = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
    )
    private val compose = AndroidComposeTestRule(activityRule) { scenario ->
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(preparation).around(compose)

    @Test
    fun selectedPeriodReturnsAcrossNavigationAndFreshProcess() {
        awaitTag("navigation-history")
        compose.onNodeWithTag("navigation-history").performClick()
        awaitTag(HistoryTags.LIST)
        if (phase.startsWith("resume")) {
            if (phase == "resume-baseline") {
                assertRange(currentRange())
                capture("baseline-current-month-after-reopen")
            }
            capture("before-reopened-period-assertion")
            assertRange(previousRange())
            capture("reopened-selected-period")
            return
        }
        awaitTag(HistoryTags.RANGE_TITLE)
        scrollTo(HistoryTags.CURRENT)
        compose.onNodeWithTag(HistoryTags.CURRENT).performClick()
        scrollTo(HistoryTags.MONTH)
        compose.onNodeWithTag(HistoryTags.MONTH).performClick()
        assertRange(currentRange())
        scrollTo(HistoryTags.PREVIOUS)
        compose.onNodeWithTag(HistoryTags.PREVIOUS).performClick()
        assertRange(previousRange())
        compose.onNodeWithTag("navigation-home").performClick()
        awaitTag("navigation-history")
        compose.onNodeWithTag("navigation-history").performClick()
        assertRange(previousRange())
        activityRule.scenario.recreate()
        awaitTag(HistoryTags.LIST)
        assertRange(previousRange())
        if (phase != "stage-baseline") awaitKeptPeriod()
        capture("kept-selected-period")
        if (phase.startsWith("stage")) {
            val original = originalPeriod?.let { HistoryPeriodCodec.encodeDraft(it).toString() } ?: "null"
            marker.writeText("${today.toEpochDay()}\n${priorAnchor.toEpochDay()}\n$original\n")
            kept = true
        } else {
            scrollTo(HistoryTags.CURRENT)
            compose.onNodeWithTag(HistoryTags.CURRENT).performClick()
            assertRange(currentRange())
            exerciseAllPeriods()
        }
    }

    private fun exerciseAllPeriods() {
        fun choose(tag: String) { scrollTo(tag); compose.onNodeWithTag(tag).performClick() }
        fun expected(horizon: AnalyticsHorizon, anchor: LocalDate, following: Boolean = false) = HistoryPeriodMath.resolve(
            selection = HistoryPeriodSelection(horizon, anchor.toEpochDay(), following),
            today = CivilDate.fromEpochDay(today.toEpochDay()), weekStart = weekStart,
            earliestEpochDay = summaries.minOfOrNull { it.localEpochDay },
            completedEpochDays = summaries.map { it.localEpochDay }.toSet(),
        )
        fun receipt(range: HistoryPeriodRange) {
            assertRange(range)
            val expected = summaries.filter { it.localEpochDay in range }
            val trainedDays = expected.map { it.localEpochDay }.distinct().size
            compose.waitUntil(30_000) {
                val spoken = compose.onNodeWithTag(HistoryTags.READOUT).fetchSemanticsNode()
                    .config[SemanticsProperties.ContentDescription].joinToString()
                val fields = spoken.split(", ")
                fields.getOrNull(0) == DateCopy.periodRange(range) &&
                    fields.getOrNull(1) == "${expected.size} ${HistoryCopy.sessionsLabel(expected.size)}" &&
                    fields.getOrNull(2) == "$trainedDays ${if (trainedDays == 1) "day" else "days"}" &&
                    fields.getOrNull(4) == HistoryCopy.activeDuration(expected.sumOf { it.durationMinutes })
            }
            val owned = synthetic.filterKeys { it.toEpochDay() in range }
            for (id in owned.values) {
                val tag = HistoryTags.row(HistoryKind.ACTIVITY, id)
                scrollTo(tag)
                compose.onNodeWithTag(tag).assertIsDisplayed()
            }
            for (id in synthetic.filterKeys { it.toEpochDay() !in range }.values) {
                val tag = HistoryTags.row(HistoryKind.ACTIVITY, id)
                val absent = runCatching { scrollTo(tag) }.exceptionOrNull()
                assertTrue("Off-period row $tag was reachable", absent is AssertionError || absent is IllegalArgumentException)
                assertTrue("An unrelated scroll failure cannot prove absence: $absent", absent?.message?.contains(tag) == true)
            }
        }
        choose(HistoryTags.DAY)
        receipt(expected(AnalyticsHorizon.DAY, today, true))
        choose(HistoryTags.WEEK)
        receipt(expected(AnalyticsHorizon.WEEK, today, true))
        choose(HistoryTags.PREVIOUS)
        receipt(expected(AnalyticsHorizon.WEEK, today.minusWeeks(1)))
        choose(HistoryTags.NEXT)
        receipt(expected(AnalyticsHorizon.WEEK, today))
        choose(HistoryTags.MONTH)
        choose(HistoryTags.CURRENT)
        receipt(currentRange())
        assertRange(currentRange())
        capture("compact-current-period")
        choose(HistoryTags.YEAR)
        receipt(expected(AnalyticsHorizon.YEAR, today, true))
        for (years in 1L..3L) {
            choose(HistoryTags.PREVIOUS)
            receipt(expected(AnalyticsHorizon.YEAR, today.minusYears(years)))
        }
        val old = synthetic.keys.last()
        choose(HistoryTags.month(CivilYearMonth(old.year, old.monthValue)))
        receipt(expected(AnalyticsHorizon.MONTH, old.withDayOfMonth(1)))
        scrollTo(HistoryTags.CALENDAR)
        compose.onNodeWithTag(HistoryTags.day(old.toEpochDay())).performClick()
        receipt(expected(AnalyticsHorizon.DAY, old))
        capture("captured-calendar-day")
        choose(HistoryTags.ALL)
        receipt(expected(AnalyticsHorizon.ALL_TIME, old))
        compose.onNodeWithTag(HistoryTags.PREVIOUS).assertDoesNotExist()
        compose.onNodeWithTag(HistoryTags.NEXT).assertDoesNotExist()
        choose(HistoryTags.LIFETIME_RECORDS)
        awaitTag(HistoryTags.SECONDARY_CLOSE)
        compose.onNodeWithTag(HistoryTags.SECONDARY_CLOSE).performClick()
        receipt(expected(AnalyticsHorizon.ALL_TIME, old))
        // Rapid changes must settle on the newest public selection and the same saved rows.
        choose(HistoryTags.MONTH)
        choose(HistoryTags.WEEK)
        choose(HistoryTags.DAY)
        choose(HistoryTags.CURRENT)
        receipt(expected(AnalyticsHorizon.DAY, today, true))
        choose(HistoryTags.MONTH)
        receipt(currentRange())
        assertRange(currentRange())
        capture("current-period-after-complete-navigation")
    }

    private fun awaitKeptPeriod() {
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        compose.waitUntil(30_000) {
            val matching = File(app.noBackupFilesDir, "draft-checkpoints").listFiles().orEmpty().any { file ->
                runCatching {
                    if (!file.name.endsWith(".json") || file.name == "generation.json") return@runCatching false
                    val envelope = JsonParser.parseString(file.readText()).asJsonObject
                    if (envelope.get("form").asString != "HISTORY" ||
                        envelope.get("owner").asString != "selected-period"
                    ) return@runCatching false
                    val draft = envelope.getAsJsonObject("draft")
                    draft.get("horizon").asString == "MONTH" &&
                        draft.get("anchorEpochDay").asLong == priorAnchor.toEpochDay() &&
                        !draft.get("followToday").asBoolean
                }.getOrDefault(false)
            }
            matching && compose.onAllNodes(hasTestTag("history-period-keeping")).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun currentRange() = HistoryPeriodRange(
        today.withDayOfMonth(1).toEpochDay(), today.toEpochDay() + 1,
    )

    private fun previousRange() = HistoryPeriodRange(
        priorAnchor.withDayOfMonth(1).toEpochDay(), today.withDayOfMonth(1).toEpochDay(),
    )

    private fun assertRange(range: HistoryPeriodRange) {
        awaitTag(HistoryTags.LIST)
        compose.onNodeWithTag(HistoryTags.LIST).performScrollToIndex(0)
        try {
            compose.waitUntil(30_000) {
                runCatching {
                    compose.onNodeWithTag(HistoryTags.RANGE_TITLE).assertTextEquals(DateCopy.periodRange(range))
                }.isSuccess
            }
        } catch (failure: Throwable) {
            capture("range-failure")
            throw failure
        }
        assertEquals(DateCopy.periodRange(range),
            compose.onNodeWithTag(HistoryTags.RANGE_TITLE).fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text })
    }

    private fun scrollTo(tag: String) = compose.onNodeWithTag(HistoryTags.LIST)
        .performScrollToNode(hasTestTag(tag))

    private fun awaitTag(tag: String) = compose.waitUntil(30_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(name: String) = NativeArtifacts.write("history-$name",
        checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()))

    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) {
        withTimeout(30_000) { block() }
    }
}
