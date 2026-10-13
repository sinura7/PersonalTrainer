package com.sinura.personaltrainer.ui.home

import android.content.Intent
import com.google.gson.JsonParser
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftRead
import com.sinura.personaltrainer.data.draft.HomeDayCodec
import com.sinura.personaltrainer.data.draft.RememberedHomeDay
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.activity.ActivityDetailTags
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Actual MainActivity navigation and separate-process reconstruction after a completed keep. */
@RunWith(AndroidJUnit4::class)
class HomeDayContinuityInstrumentedTest {
    private lateinit var container: AppContainer
    private lateinit var marker: File
    private var original: RememberedHomeDay? = null
    private var selected = 0L
    private var today = 0L
    private var kept = false
    private var sessionId: String? = null
    private var completedStrength: String? = null
    private var completedActivity: String? = null
    private val phase = InstrumentationRegistry.getArguments().getString("homeDayRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment("Synthetic P05")
    private val seed = object : ExternalResource() {
        override fun before() {
            check(phase in setOf("connected", "stage", "resume"))
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            environment.prepare(container)
            marker = File(app.noBackupFilesDir, "synthetic-p05-home-day.txt")
            try {
                read {
                    container.workoutRepository.getInProgress()?.let { orphan ->
                        // The retained native01 failure stopped this exact seed before its
                        // first hold result. Never discard another fixture or user session.
                        check(orphan.routineName == "Synthetic P05 completed strength" && orphan.sets.isEmpty())
                        container.discardWorkout(orphan.id)
                    }
                    container.preferencesRepository.setOnboardingComplete(true)
                    container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                    container.preferencesRepository.setLaunchPermissionsAsked(true)
                    container.preferencesRepository.markRestAlertsAsked()
                    container.preferencesRepository.markRestBatteryHintShown()
                    if (phase == "resume") {
                        val fields = marker.readLines()
                        check(fields.size == 4)
                        selected = fields[0].toLong(); today = fields[1].toLong()
                        check(today == LocalDate.now().toEpochDay()) { "Repeat staged fixture after civil-day rollover" }
                        original = if (fields[2] == "none") null else RememberedHomeDay(fields[2].toLong(), fields[3].toLong())
                    } else {
                        check(!marker.exists())
                        val handle = container.homeDayDrafts.open()
                        check(handle.readable)
                        original = handle.remembered
                        container.homeDayDrafts.reset(handle)
                        today = LocalDate.now().toEpochDay()
                        if (phase == "connected") {
                            seedCompleted()
                            File(app.noBackupFilesDir, "draft-checkpoints").listFiles()!!
                                .filter { it.name.endsWith(".json") && it.name != "generation.json" }
                                .single { JsonParser.parseString(it.readText()).asJsonObject.get("owner").asString == "selected-day" }
                                .writeText("synthetic malformed Home preference")
                        }
                    }
                }
            } catch (failure: Throwable) {
                try { restore() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }
        override fun after() = restore()
    }
    private val scenario = ActivityScenarioRule<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
    private val compose = AndroidComposeTestRule(scenario) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    @Test fun chosenDayPersistsAndReturningToALiveWorkoutKeepsItsIdentity() {
        await(HomeTags.DATE)
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        if (phase == "connected") {
            await(HomeDayTags.ERROR)
            compose.onNodeWithTag(HomeDayTags.RETRY).performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(HomeDayTags.RESET) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(HomeDayTags.RESET).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsDisplayed().performClick()
            compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(HomeDayTags.ERROR)).fetchSemanticsNodes().isEmpty() }
            assertDate(today)
            for ((id, back) in listOf(checkNotNull(completedStrength) to SessionDetailTestTags.BACK,
                checkNotNull(completedActivity) to ActivityDetailTags.BACK)) {
                await(HomeTags.loggedSession(id))
                compose.onNodeWithTag(HomeTags.loggedSession(id)).performScrollTo().assertIsDisplayed().performClick()
                await(back)
                compose.onNodeWithTag(back).assertIsDisplayed().performClick()
                await(HomeTags.DATE)
                assertDate(today)
            }
            assertTrue(read { container.workoutRepository.getSession(checkNotNull(completedStrength))!!.isFinished })
            assertTrue(read { container.activityRepository.get(checkNotNull(completedActivity))!!.isCompleted })
            capture("completed-returns-after-private-reset")
        }
        if (phase == "resume") {
            assertDate(selected)
            compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertIsDisplayed()
            capture("reopened-selected-day")
        } else {
            compose.onNodeWithTag(HomeDayTags.CHOOSE).assertIsDisplayed().performClick()
            await(HomeDayTags.PICKER)
            val days = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton,
            )).fetchSemanticsNodes().mapNotNull { node ->
                (node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) ?: "")
                    .removePrefix("home-day-choice-").toLongOrNull()
            }
            assertEquals(7, days.size)
            selected = days.first { it != today }
            compose.onNodeWithTag(HomeDayTags.day(selected)).performScrollTo().performClick()
            assertDate(selected)
            // Read without claiming another Home lease while its ViewModel remains active.
            compose.waitUntil(30000) { remembered() == RememberedHomeDay(selected, today) }
            capture("kept-selected-day")
            if (phase == "stage") {
                marker.writeText(listOf(selected.toString(), today.toString(), original?.selectedEpochDay?.toString() ?: "none",
                    original?.todayEpochDay?.toString() ?: "none").joinToString("\n"))
                kept = true
                return
            }
            compose.onNodeWithTag("navigation-history").performClick()
            compose.onNodeWithTag("navigation-home").performClick()
            await(HomeTags.DATE)
            assertDate(selected)
            scenario.scenario.recreate()
            await(HomeTags.DATE)
            assertDate(selected)
        }
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed().performClick()
        await(HomeStartTags.DATE)
        compose.onNodeWithTag(HomeStartTags.DATE).assertTextEquals("Starts now · ${DateCopy.civilDate(today)}")
        compose.onNodeWithTag(HomeStartTags.FREE).performScrollTo().performClick()
        compose.waitUntil(30000) {
            read { container.workoutRepository.getInProgress()?.also { sessionId = it.id } } != null
        }
        val live = read { checkNotNull(container.workoutRepository.getSession(checkNotNull(sessionId))) }
        assertEquals(LocalDate.ofEpochDay(today), Instant.ofEpochMilli(live.startedAt).atZone(ZoneId.systemDefault()).toLocalDate())
        compose.onNodeWithContentDescription("Exit workout").assertIsDisplayed().performClick()
        await(LiveSessionBarTestTags.ROOT)
        assertDate(selected)
        compose.onNodeWithTag(HomeTags.START).assertDoesNotExist()
        compose.onNodeWithTag(LiveSessionBarTestTags.ROOT).performClick()
        compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(LiveSessionBarTestTags.ROOT)).fetchSemanticsNodes().isEmpty() }
        assertEquals(sessionId, read { container.workoutRepository.getInProgress()?.id })
        assertTrue(read { container.workoutRepository.getSession(checkNotNull(sessionId))!!.sets.isEmpty() })
        capture("same-live-workout")
    }

    private fun remembered(): RememberedHomeDay? = read {
        val binding = container.durableDrafts.bind(DraftOwner(DraftForm.HOME, "selected-day"), HomeDayCodec)
        val loaded = binding.read()
        check(loaded is DraftRead.PresentCheckpoint)
        assertNull(loaded.checkpoint.pending)
        loaded.checkpoint.draft
    }
    private fun await(tag: String) {
        if (tag == HomeTags.DATE) {
            // Detail returns preserve the user's prior agenda scroll. Reveal the masthead
            // before reading its Text child inside the merged date-action button.
            compose.waitUntil(30000) { compose.onAllNodes(hasTestTag(HomeTags.BOARD)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(HomeTags.BOARD).performScrollToIndex(0)
        }
        compose.waitUntil(30000) {
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = tag == HomeTags.DATE).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun assertDate(day: Long) {
        await(HomeTags.DATE)
        compose.waitUntil(30000) {
            compose.onAllNodes(hasTestTag(HomeTags.DATE), useUnmergedTree = true).fetchSemanticsNodes().any {
                (it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text) ?: emptyList())
                    .any { text -> text.text == DateCopy.weekdayFullDate(LocalDate.ofEpochDay(day)).uppercase(Locale.ENGLISH) }
            }
        }
        compose.onNodeWithTag(HomeTags.DATE, useUnmergedTree = true).assertIsDisplayed()
    }
    private fun capture(name: String) = NativeArtifacts.write("home-$name", checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()))
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30000) { block() } }
    private suspend fun seedCompleted() {
        val exercise = container.exerciseRepository.observeAll().first { it.isNotEmpty() }
            .first { !HoldWork.isHold(it) && LoadClass.of(it.loadType) == LoadClass.LOADED }
        val strength = container.workoutRepository.startFreeWorkout("Synthetic P05 completed strength")
        completedStrength = strength.id
        container.workoutRepository.addExerciseToSession(strength.id, exercise, 1, 8, 40.0, 90)
        container.workoutRepository.logSet(strength.id, exercise.id, 40.0, 8, 8, false)
        container.workoutRepository.finishSession(strength.id, "Synthetic P05 receipt")
        val now = JvmTime.captureNow()
        val activity = container.confirmActivity(
            ActivityDraft(
                origin = ActivityOrigin.BACKDATED, title = "Synthetic P05 completed cardio",
                performedStart = JvmTime.capture(now.instantMillis - 600000), performedEnd = now,
                blocks = listOf(CardioBlock(
                    id = "synthetic-p05-cardio-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                    elapsedSeconds = 600, movingSeconds = null, distanceMeters = 1000.0,
                    elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
                )),
            ), now,
        )
        completedActivity = (activity as ActivityWrite.Accepted).session.id
    }
    private fun restore() {
        try {
            if (::container.isInitialized && !kept) read {
                sessionId?.let { id ->
                    check(container.workoutRepository.getInProgress()?.id == id)
                    container.discardWorkout(id)
                }
                completedStrength?.let { id ->
                    val saved = container.workoutRepository.getSession(id)
                    if (saved?.isFinished == true) container.workoutRepository.deleteFinishedSession(id)
                    else if (saved != null) {
                        check(container.workoutRepository.getInProgress()?.id == id)
                        container.discardWorkout(id)
                    }
                }
                completedActivity?.let { container.activityRepository.deleteCompleted(it) }
                val handle = container.homeDayDrafts.open()
                original?.let { container.homeDayDrafts.keep(handle, it) } ?: container.homeDayDrafts.reset(handle)
                if (::marker.isInitialized && marker.exists()) check(marker.delete())
            }
        } finally { environment.restore() }
    }
}
