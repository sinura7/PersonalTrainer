package com.sinura.personaltrainer.ui.plan

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToLog
import androidx.room.withTransaction
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.DraftChange
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftRead
import com.sinura.personaltrainer.data.draft.MissedWorkCodec
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.MissedWorkDecisionEntity
import com.sinura.personaltrainer.data.local.entity.ReminderDeliveryEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.data.repository.MissedWorkProposal
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import com.sinura.personaltrainer.ui.home.HomeTags
import java.io.File
import java.util.UUID
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

/** Real MainActivity/AppNav. Stage injects only durable admission after a real UI preview;
 * the external runner performs the actual force-stop before Resume's real UI Retry.
 */
@RunWith(AndroidJUnit4::class)
class MissedWorkWorkflowInstrumentedTest {
    private val phase = InstrumentationRegistry.getArguments().getString("missedRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment("Synthetic P08 schedule")
    private lateinit var container: AppContainer
    private lateinit var database: TemperDatabase
    private lateinit var marker: File
    private lateinit var inventory: ScheduleInventory
    private var retained = false
    private val gson = Gson()

    private data class ScheduleInventory(
        val rules: List<ScheduleRuleEntity>, val occurrences: List<ScheduleOccurrenceEntity>,
        val decisions: List<MissedWorkDecisionEntity>, val deliveries: List<ReminderDeliveryEntity>,
        val originalWeekStart: Weekday, val fixtureWeekStart: Long,
        var routineId: String? = null,
    )
    private val preparation = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            environment.prepare(container)
            app.markColdStartIntroShown()
            // Separate connection is fixture-only, closed after restoring its schedule snapshot.
            database = TemperDatabase.create(app)
            marker = File(app.noBackupFilesDir, "synthetic-p08-schedule-native.json")
            try { read {
                check(container.workoutRepository.getInProgress() == null && container.activityRepository.getLive() == null)
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                container.preferencesRepository.setLaunchPermissionsAsked(true)
                container.preferencesRepository.markRestAlertsAsked()
                container.preferencesRepository.markRestBatteryHintShown()
                if (phase == "resume") {
                    check(marker.exists())
                    inventory = gson.fromJson(marker.readText(), ScheduleInventory::class.java)
                } else {
                    check(!marker.exists()) { "An earlier synthetic fixture needs reconciliation" }
                    val today = container.time.captureNow().localEpochDay
                    val week = (1L..6L).map { today - it }.first {
                        container.plannerRepository.decisionFor(it) == null
                    }
                    val dao = database.plannerDao()
                    inventory = database.withTransaction {
                        ScheduleInventory(dao.getRules(), dao.getAllOccurrences(), dao.getDecisions(), dao.getDeliveries(),
                            container.preferencesRepository.schedulePreferences.first().weekStart, week)
                    }
                    marker.writeText(gson.toJson(inventory))
                    val routine = container.routineRepository.create("Synthetic P08 complete upper-body workout")
                    inventory.routineId = routine.id
                    marker.writeText(gson.toJson(inventory))
                    val exercise = container.exerciseRepository.observeAll().first().first {
                        it.name.contains("Bench Press", ignoreCase = true)
                    }
                    container.routineRepository.addExercise(routineId = routine.id, exercise = exercise,
                        targetSets = 3, targetReps = 8, targetWeightKg = 50.0, restSeconds = 120)
                    container.plannerRepository.addTimedRule(
                        weekday = Weekday.fromEpochDay(week), hour = 18, minute = 0,
                        modality = ScheduleModality.STRENGTH, routineId = routine.id,
                        nowMs = container.time.startOfDayMillis(CivilDate.fromEpochDay(week), container.time.defaultZoneId()) - 1,
                    )
                }
                container.preferencesRepository.setWeekStart(Weekday.fromEpochDay(inventory.fixtureWeekStart))
                container.plannerRepository.ensureWeek(CivilDate.fromEpochDay(inventory.fixtureWeekStart))
            } } catch (failure: Throwable) {
                try { cleanup() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }
        override fun after() { if (retained) { database.close(); environment.restore() } else cleanup() }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(preparation).around(compose)

    @Test fun previewRetainsAndConfirmsExactlyTheSameWeekAcrossHomeAndPlan() {
        awaitTag("navigation-home")
        compose.onNodeWithTag("navigation-home").performClick()
        if (phase == "resume") {
            scrollTo(MissedWorkPreviewTags.RESUME)
            val pending = readCheckpoint()
            assertTrue(pending.pending != null)
            assertEquals(pending.draft!!.occurrencesBefore, weekRows())
            assertNull(read { container.plannerRepository.decisionFor(inventory.fixtureWeekStart) })
            compose.onNodeWithTag(MissedWorkPreviewTags.RESUME).performClick()
        } else {
            scrollTo(MissedWorkTags.OTHER)
            compose.onNodeWithTag(MissedWorkTags.OTHER).performClick()
            scrollTo(MissedWorkTags.MOVE)
            compose.onNodeWithTag(MissedWorkTags.MOVE).performClick()
        }
        awaitVisible(MissedWorkPreviewTags.SHEET)
        var proposal = checkNotNull(readCheckpoint().draft)
        assertTrue(proposal.occurrencesBefore.any { it.ruleId in proposal.rulesBefore.filter { rule ->
            rule.routineId == inventory.routineId }.map { rule -> rule.id } })
        assertEquals(proposal.occurrencesBefore, weekRows())
        capture("preview")
        if (phase == "conflict") {
            val ownedRule = proposal.rulesBefore.single { it.routineId == inventory.routineId }
            read { container.plannerRepository.upsertRule(ownedRule.copy(reminderOffsetMinutes = 5,
                updatedAtMs = container.time.nowMillis())) }
            compose.onNodeWithTag(MissedWorkPreviewTags.CONFIRM).performScrollTo().performClick()
            awaitVisible(MissedWorkPreviewTags.REVIEW)
            assertNull(read { container.plannerRepository.decisionFor(inventory.fixtureWeekStart) })
            assertEquals(proposal, readCheckpoint().draft)
            assertEquals(proposal.occurrencesBefore, weekRows())
            capture("conflict")
            compose.onNodeWithTag(MissedWorkPreviewTags.REVIEW).performScrollTo().performClick()
            awaitVisible(MissedWorkPreviewTags.CONFIRM)
            val reviewed = checkNotNull(readCheckpoint().draft)
            assertEquals(5, reviewed.rulesBefore.single { it.id == ownedRule.id }.reminderOffsetMinutes)
            assertNull(readCheckpoint().pending)
            assertNull(read { container.plannerRepository.decisionFor(inventory.fixtureWeekStart) })
            proposal = reviewed
            capture("reviewed")
        }
        if (phase == "stage") {
            compose.onNodeWithTag(MissedWorkPreviewTags.CLOSE).performScrollTo().performClick()
            scrollTo(MissedWorkPreviewTags.RESUME)
            read {
                val binding = binding()
                val before = readCheckpoint()
                check(binding.beginSave(before.version, proposal, UUID.randomUUID().toString(), proposal) is DraftChange.Written)
            }
            assertNull(read { container.plannerRepository.decisionFor(inventory.fixtureWeekStart) })
            retained = true
            capture("durable-admission-before-stop")
            return
        }
        compose.onNodeWithTag(MissedWorkPreviewTags.CLOSE).performScrollTo().performClick()
        awaitTag("navigation-routines")
        compose.onNodeWithTag("navigation-routines").performClick()
        scrollTo(MissedWorkPreviewTags.RESUME)
        compose.onNodeWithTag(MissedWorkPreviewTags.RESUME).performClick()
        awaitVisible(MissedWorkPreviewTags.SHEET)
        assertEquals(proposal, readCheckpoint().draft)
        compose.onNodeWithTag(MissedWorkPreviewTags.CONFIRM).performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(30_000) { readCheckpoint().draft == null }
        assertEquals(proposal.occurrencesAfter, weekRows())
        assertEquals(proposal.decision, read { container.plannerRepository.decisionFor(inventory.fixtureWeekStart) })
        assertEquals(proposal.occurrencesBefore.filter { it.status == OccurrenceStatus.DONE },
            weekRows().filter { it.status == OccurrenceStatus.DONE })
        val ruleId = proposal.rulesBefore.single { it.routineId == inventory.routineId }.id
        val movedId = proposal.occurrencesAfter.single { it.ruleId == ruleId && it.status == OccurrenceStatus.PLANNED &&
            it.localEpochDay == proposal.todayEpochDay }.id
        scrollTo(PlanTags.agenda(movedId))
        compose.onNodeWithTag(PlanTags.agenda(movedId)).assertIsDisplayed()
        capture("plan-after-confirm")
        compose.onNodeWithTag("navigation-home").performClick()
        scrollTo(HomeTags.agendaRow(movedId))
        compose.onNodeWithTag(HomeTags.agendaRow(movedId)).assertIsDisplayed()
        assertEquals(proposal.occurrencesAfter, weekRows())
        capture("home-same-week")
    }

    /** Wait on the real scroll operation, rather than an unconditional loading container. */
    private fun scrollTo(tag: String) {
        try { compose.waitUntil(30_000) {
            runCatching {
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToNode(hasTestTag(tag))
            }.isSuccess
        } } catch (failure: Throwable) {
            capture("failed-$tag")
            compose.onRoot(useUnmergedTree = true).printToLog("P08-Readiness")
            throw failure
        }
    }
    private fun awaitTag(tag: String) = compose.waitUntil(30_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitVisible(tag: String) = compose.waitUntil(30_000) {
        runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
    }
    private fun binding() = container.durableDrafts.bind(DraftOwner(DraftForm.SCHEDULE, "schedule:missed-work"), MissedWorkCodec(container.time))
    private fun readCheckpoint() = read { (binding().read() as DraftRead.PresentCheckpoint).checkpoint }
    private fun weekRows() = read {
        container.plannerRepository.occurrencesBetween(inventory.fixtureWeekStart, inventory.fixtureWeekStart + 6).sortedBy { it.id }
    }
    private fun capture(name: String): String {
        compose.waitForIdle()
        val bitmap = if (name in setOf("preview", "conflict", "reviewed")) compose.onNodeWithTag(MissedWorkPreviewTags.SHEET)
            .captureToImage().asAndroidBitmap()
        else checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        return NativeArtifacts.write("scheduling-$name", bitmap)
    }
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30_000) { block() } }
    private fun cleanup() {
        try {
            if (::inventory.isInitialized && ::database.isInitialized) read {
                container.missedWorkDrafts.discard(container.missedWorkDrafts.open())
                inventory.routineId?.let { container.routineRepository.delete(it) }
                container.dbMaintenance.withMaintenanceLock {
                    database.withTransaction {
                        val dao = database.plannerDao()
                        dao.deleteAllDeliveries(); dao.deleteAllDecisions(); dao.deleteAllOccurrences(); dao.deleteAllRules()
                        dao.upsertRules(inventory.rules); dao.upsertOccurrences(inventory.occurrences)
                        inventory.decisions.forEach { dao.upsertDecision(it) }
                        dao.upsertDeliveries(inventory.deliveries)
                        assertEquals(inventory.rules, dao.getRules())
                        assertEquals(inventory.occurrences, dao.getAllOccurrences())
                        assertEquals(inventory.decisions, dao.getDecisions())
                        assertEquals(inventory.deliveries, dao.getDeliveries())
                    }
                }
                container.preferencesRepository.setWeekStart(inventory.originalWeekStart)
                check(marker.delete())
            }
        } finally {
            if (::database.isInitialized) database.close()
            environment.restore()
        }
    }
}
