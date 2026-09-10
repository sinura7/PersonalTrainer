package com.sinura.personaltrainer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.activity.ActivityComposerScreen
import com.sinura.personaltrainer.ui.activity.ActivityComposerViewModel
import com.sinura.personaltrainer.ui.activity.ActivityDetailScreen
import com.sinura.personaltrainer.ui.activity.ActivityDetailTags
import com.sinura.personaltrainer.ui.activity.ActivityDetailViewModel
import com.sinura.personaltrainer.ui.activity.CardioTags
import com.sinura.personaltrainer.ui.activity.ComposerTags
import com.sinura.personaltrainer.ui.activity.LiveCardioScreen
import com.sinura.personaltrainer.ui.activity.LiveCardioViewModel
import com.sinura.personaltrainer.ui.components.SessionLogTags
import com.sinura.personaltrainer.ui.history.HistoryScreen
import com.sinura.personaltrainer.ui.history.HistoryTags
import com.sinura.personaltrainer.ui.history.HistoryViewModel
import com.sinura.personaltrainer.ui.home.HomeScreen
import com.sinura.personaltrainer.ui.home.HomeTags
import com.sinura.personaltrainer.ui.home.HomeViewModel
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.util.JvmTime
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R16: the production screens, not a button in a Column.
 *
 * `RemainingPagesPassInstrumentedTest` proves that isolated controls carrying a production
 * tag render at 360 dp / font 2.0. It cannot prove that the real page keeps that control
 * reachable once it has a populated list, a pinned dock, an open keyboard and a ViewModel
 * behind it. These tests mount the actual screen composables with their real ViewModels
 * against the real `AppContainer` and Room, seeded with a populated fixture (three months
 * of activities, a finished session, a record older than the insight window), at
 * 360 / 412 / 600 dp and font scales 1.0 / 1.3 / 2.0.
 *
 * Fixtures are prefixed `Screens ` and removed afterwards; the reset at the start also
 * clears leftovers from an interrupted run. Physical TalkBack traversal stays a manual
 * gate: nothing here can hear an announcement.
 *
 * Widths beyond the emulator's own (411 dp on the `temper-tests-api29` profile) are
 * mounted in a box wider than the viewport, so those assertions use `assertExists` and
 * click actions rather than `assertIsDisplayed`, which would fail for a node clipped by
 * the physical screen rather than by the page.
 */
@RunWith(AndroidJUnit4::class)
class ProductionScreensPassInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var container: AppContainer
    private lateinit var fixture: ScreensFixture
    private val viewModels = mutableListOf<AppViewModel>()

    @Before
    fun seed() {
        container = ApplicationProvider.getApplicationContext<PersonalTrainerApp>().container
        // Soft keyboard animations never go idle on the SwiftShader emulator, and the
        // composer test needs the IME to actually appear.
        runShell("settings put secure show_ime_with_hard_keyboard 1")
        runShell("settings put global window_animation_scale 0")
        runShell("settings put global transition_animation_scale 0")
        runShell("settings put global animator_duration_scale 0")
        fixture = runBlocking { withContext(Dispatchers.IO) { seedFixture() } }
    }

    @After
    fun clean() {
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
        if (!::container.isInitialized) return
        runBlocking { withContext(Dispatchers.IO) { removeFixture() } }
    }

    @Test
    fun historyAt360Font2KeepsChipsReadoutRecordsAndRowsReachable() {
        val viewModel = HistoryViewModel(app(), container).track()
        mount(widthDp = 360, fontScale = 2f) {
            HistoryScreen(
                onOpenSession = {},
                onOpenExercise = {},
                onOpenActiveSession = {},
                viewModel = viewModel,
            )
        }
        awaitTag(HistoryTags.READOUT)
        compose.onNodeWithTag(HistoryTags.READOUT).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.DAY).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.ALL).assertIsDisplayed()

        // The lifetime record is the old activity's 120 kg, not the session's 100 kg (R08).
        compose.waitUntil(15_000) {
            viewModel.uiState.value.records.any { it.valueKg == 120.0 }
        }
        // The header is a GymSectionHeader, whose Kicker uppercases at the call site
        // (ADR-005: tracked caps, never tracked mixed case), so the drawn text is RECORDS.
        scrollPageTo(hasText("Records", ignoreCase = true))
        compose.onNodeWithText("Records", ignoreCase = true).assertIsDisplayed()

        scrollPageTo(hasTestTag(SessionLogTags.ROW))
        compose.onAllNodesWithTag(SessionLogTags.ROW).onFirst().assertIsDisplayed()
        compose.onAllNodesWithTag(SessionLogTags.ROW).onFirst().assertHasClickAction()
    }

    @Test
    fun historyAt412Font13SwitchesHorizonWithoutLosingTheReadout() {
        val viewModel = HistoryViewModel(app(), container).track()
        mount(widthDp = 412, fontScale = 1.3f) {
            HistoryScreen(
                onOpenSession = {},
                onOpenExercise = {},
                onOpenActiveSession = {},
                viewModel = viewModel,
            )
        }
        awaitTag(HistoryTags.READOUT)
        compose.onNodeWithTag(HistoryTags.YEAR).performClick()
        compose.waitUntil(15_000) {
            viewModel.uiState.value.horizonTotals?.sessionCount?.let { it >= 4 } == true
        }
        compose.onNodeWithTag(HistoryTags.READOUT).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.ALL).performClick()
        compose.onNodeWithTag(HistoryTags.READOUT).assertIsDisplayed()
    }

    @Test
    fun historyAt600Font1StillNamesEveryChip() {
        val viewModel = HistoryViewModel(app(), container).track()
        mount(widthDp = 600, fontScale = 1f) {
            HistoryScreen(
                onOpenSession = {},
                onOpenExercise = {},
                onOpenActiveSession = {},
                viewModel = viewModel,
            )
        }
        awaitTag(HistoryTags.READOUT)
        listOf(HistoryTags.DAY, HistoryTags.WEEK, HistoryTags.MONTH, HistoryTags.YEAR, HistoryTags.ALL)
            .forEach { tag ->
                compose.onNodeWithTag(tag).assertExists()
                compose.onNodeWithTag(tag).assertHasClickAction()
            }
    }

    @Test
    fun composerAt360Font2KeepsSaveAndCancelAboveTheKeyboard() {
        val viewModel = ActivityComposerViewModel(
            app(),
            SavedStateHandle(mapOf("mode" to "mixed")),
            container,
        ).track()
        mount(widthDp = 360, fontScale = 2f) {
            ActivityComposerScreen(onBack = {}, onSaved = {}, viewModel = viewModel)
        }
        awaitTag(ComposerTags.SAVE)
        // The first text field in the tree is the title; focusing it opens the IME.
        compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Leg day")
        compose.waitForIdle()
        compose.onNodeWithText("Leg day").assertExists()
        // imePadding keeps the dock above the keyboard; both actions stay on screen.
        compose.onNodeWithTag(ComposerTags.SAVE).assertIsDisplayed()
        compose.onNodeWithTag(ComposerTags.CANCEL).assertIsDisplayed()
        scrollPageTo(hasTestTag(ComposerTags.CHOOSE_LIFT))
        compose.onNodeWithTag(ComposerTags.CHOOSE_LIFT).assertHasClickAction()
        // The typed draft reached saved state (R09) without a save.
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun liveCardioAt360Font2KeepsTheDockAndReadout() {
        val liveId = runBlocking {
            withContext(Dispatchers.IO) {
                val now = JvmTime.captureNow()
                val write = container.startLiveActivity(
                    "${PREFIX}run ${fixture.suffix}",
                    listOf(cardioBlock(id = "screens-live-${fixture.suffix}", seconds = 0L)),
                    now,
                )
                (write as ActivityWrite.Accepted).session.id
            }
        }
        try {
            val viewModel = LiveCardioViewModel(
                app(),
                SavedStateHandle(mapOf("sessionId" to liveId)),
                container,
            ).track()
            mount(widthDp = 360, fontScale = 2f) {
                LiveCardioScreen(onExit = {}, onFinished = {}, viewModel = viewModel)
            }
            awaitTag(CardioTags.ELAPSED)
            compose.onNodeWithTag(CardioTags.ELAPSED).assertIsDisplayed()
            compose.onNodeWithTag(CardioTags.FINISH).assertIsDisplayed()
            compose.onNodeWithTag(CardioTags.LEAVE).assertIsDisplayed()
            compose.onNodeWithTag(CardioTags.DISCARD).assertIsDisplayed()
        } finally {
            runBlocking {
                withContext(Dispatchers.IO) {
                    container.discardActivity(liveId)
                    container.cardioTimerPersistence.clear()
                }
            }
        }
    }

    @Test
    fun activityReceiptAt600Font1KeepsDoneNamed() {
        val viewModel = ActivityDetailViewModel(
            app(),
            SavedStateHandle(mapOf("activityId" to fixture.activityIds.first())),
            container,
        ).track()
        mount(widthDp = 600, fontScale = 1f) {
            ActivityDetailScreen(onBack = {}, celebration = true, viewModel = viewModel)
        }
        awaitTag(ActivityDetailTags.DONE)
        compose.onNodeWithTag(ActivityDetailTags.DONE).assertExists()
        compose.onNodeWithTag(ActivityDetailTags.DONE).assertHasClickAction()
        compose.onNodeWithText("Done").assertExists()
    }

    @Test
    fun homeAt412Font13OffersAStart() {
        val viewModel = HomeViewModel(app(), container).track()
        mount(widthDp = 412, fontScale = 1.3f) {
            HomeScreen(
                onResumeWorkout = {},
                onOpenPlan = {},
                viewModel = viewModel,
            )
        }
        val starts = listOf(
            HomeTags.START,
            HomeTags.FREE,
            HomeTags.GET_STARTED,
            HomeTags.GENERATE,
            HomeTags.BUILD_WEEK,
            HomeTags.STARTER_WORKOUT,
        )
        compose.waitUntil(15_000) {
            starts.any { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun app(): PersonalTrainerApp = ApplicationProvider.getApplicationContext()

    private fun <T : AppViewModel> T.track(): T = also { viewModels += it }

    private fun mount(widthDp: Int, fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                    LocalWeightUnit provides WeightUnit.KG,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(widthDp.dp, 800.dp)) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The page's own list: the first scrollable in the tree is the screen's LazyColumn. */
    private fun scrollPageTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
        compose.waitForIdle()
    }

    private suspend fun seedFixture(): ScreensFixture {
        removeLeftovers()
        container.preferencesRepository.setOnboardingComplete(true)
        container.preferencesRepository.setWeightUnit(WeightUnit.KG)
        val suffix = UUID.randomUUID().toString().take(8)
        val exercise = createExercise("${PREFIX}bench $suffix")
        val routine = container.routineRepository.create("${PREFIX}upper $suffix")
        container.routineRepository.addExercise(
            routineId = routine.id,
            exercise = exercise,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = 100.0,
            restSeconds = 90,
        )
        val planned = checkNotNull(container.routineRepository.getById(routine.id))
        val live = container.workoutRepository.startRoutine(planned)
        container.workoutRepository.logSet(
            sessionId = live.id,
            exerciseId = exercise.id,
            weightKg = 100.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )
        container.workoutRepository.finishSession(sessionId = live.id, notes = "")

        // Three backdated days, one per month, each with a heavier bench than the session
        // and a run: month groups, calendar marks, cardio totals, and a record older than
        // the 32-day insight window.
        val now = JvmTime.captureNow()
        val activityIds = (1..3).map { monthsBack ->
            val day = CivilDate.fromEpochDay(now.localEpochDay - 30L * monthsBack)
            val start = JvmTime.resolveLocal(CivilDateTime(day, hour = 12, minute = 0), now.zoneId)
            val write = container.confirmActivity(
                ActivityDraft(
                    status = ActivityStatus.COMPLETED,
                    origin = ActivityOrigin.BACKDATED,
                    title = "${PREFIX}day $monthsBack $suffix",
                    performedStart = start,
                    performedEnd = start,
                    blocks = listOf(
                        StrengthBlock(
                            id = "screens-lift-$monthsBack-$suffix",
                            sortOrder = 0,
                            exerciseId = exercise.id,
                            exerciseName = exercise.name,
                            loadType = exercise.loadType,
                            equipment = exercise.equipment,
                            muscles = exercise.muscles,
                            sets = listOf(
                                StrengthSet(
                                    id = "screens-set-$monthsBack-$suffix",
                                    setNumber = 1,
                                    weightKg = 120.0,
                                    reps = 5,
                                    rpe = null,
                                    isWarmup = false,
                                    completedAtMs = start.instantMillis,
                                ),
                            ),
                        ),
                        cardioBlock(id = "screens-run-$monthsBack-$suffix", seconds = 1_800L),
                    ),
                ),
                now,
            )
            (write as ActivityWrite.Accepted).session.id
        }
        return ScreensFixture(
            suffix = suffix,
            exercise = exercise,
            routineId = routine.id,
            sessionId = live.id,
            activityIds = activityIds,
        )
    }

    private suspend fun removeFixture() {
        if (!::fixture.isInitialized) return
        runCatching { container.workoutRepository.deleteFinishedSession(fixture.sessionId) }
        fixture.activityIds.forEach { id ->
            runCatching { container.activityRepository.deleteCompleted(id) }
        }
        runCatching { container.routineRepository.delete(fixture.routineId) }
        runCatching { container.exerciseRepository.deleteCustom(fixture.exercise.id) }
    }

    private suspend fun removeLeftovers() {
        container.workoutRepository.getInProgress()?.let { container.discardWorkout(it.id) }
        container.activityRepository.getLive()?.let { container.discardActivity(it.id) }
        container.workoutRepository.observeHistory().first()
            .filter { it.routineName?.startsWith(PREFIX) == true }
            .forEach { runCatching { container.workoutRepository.deleteFinishedSession(it.id) } }
        container.activityRepository.all()
            .filter { it.title.startsWith(PREFIX) }
            .forEach { runCatching { container.activityRepository.deleteCompleted(it.id) } }
        container.routineRepository.observeAll().first()
            .filter { it.name.startsWith(PREFIX) }
            .forEach { runCatching { container.routineRepository.delete(it.id) } }
        container.exerciseRepository.observeAll().first()
            .filter { it.isCustom && it.name.startsWith(PREFIX) }
            .forEach { runCatching { container.exerciseRepository.deleteCustom(it.id) } }
    }

    private suspend fun createExercise(name: String): Exercise =
        when (val result = container.exerciseRepository.createCustom(name, "Chest")) {
            is SaveExerciseResult.Saved -> result.exercise
            is SaveExerciseResult.DuplicateName -> result.existing
            SaveExerciseResult.MissingMuscle -> error("screens fixture muscle rejected")
        }

    private fun cardioBlock(id: String, seconds: Long) = CardioBlock(
        id = id,
        sortOrder = 1,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = seconds,
        movingSeconds = seconds,
        distanceMeters = if (seconds > 0L) 5_000.0 else null,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )

    private fun runShell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .use { pipe -> FileInputStream(pipe.fileDescriptor).use { it.readBytes() } }
    }

    private data class ScreensFixture(
        val suffix: String,
        val exercise: Exercise,
        val routineId: String,
        val sessionId: String,
        val activityIds: List<String>,
    )

    private companion object {
        const val PREFIX = "Screens "
    }
}
