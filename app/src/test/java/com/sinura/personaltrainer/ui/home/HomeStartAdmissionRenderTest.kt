package com.sinura.personaltrainer.ui.home

import android.app.Application
import android.database.sqlite.SQLiteFullException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as UiRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.LiveBarCopy
import com.sinura.personaltrainer.domain.OccurrenceGenerator
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.workout.WorkoutPreviewTags
import com.sinura.personaltrainer.ui.components.WeekStripTags
import com.sinura.personaltrainer.ui.navigation.InstrumentNavBar
import com.sinura.personaltrainer.ui.navigation.Route
import com.sinura.personaltrainer.ui.navigation.shippingTabs
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.workout.StartOccurrence
import com.sinura.personaltrainer.workout.StartTrainingDay
import com.sinura.personaltrainer.workout.DiscardWorkout
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Actual Home, its admission owner, and isolated Room; native frames are not phone evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class HomeStartAdmissionRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val writes = StartWrites()
    private val reads = RoutineReads()
    private val insights = MutableStateFlow(TrainingInsights())
    private val runId = UUID.randomUUID().toString()
    private val today = todayEpochDay()
    private val nowMs = LocalDate.ofEpochDay(today).atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val openedSessions = mutableListOf<String>()
    private lateinit var base: FakeAppDependencies
    private lateinit var graph: AppDependencies
    private lateinit var vm: HomeViewModel
    private lateinit var routineId: String
    private var occurrenceId: String? = null
    private var profile = "interaction"
    private var rtl = false
    private var reducedMotion = false

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        writes.held?.complete(Unit)
        writes.discardHeld?.complete(Unit)
        reads.held?.complete(Unit)
        try {
            if (::vm.isInitialized) runBlocking { vm.clearAndJoinForTest() }
        } finally {
            if (::base.isInitialized) {
                base.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                base.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test @Config(fontScale = 1f) fun small10() = retryJourney("360x640-font10", 1f)
    @Test @Config(fontScale = 1.6f) fun small16() = retryJourney("360x640-font16", 1.6f)
    @Test @Config(fontScale = 2f) fun small20() = retryJourney("360x640-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standard10() = retryJourney("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standard16() = retryJourney("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standard20() = retryJourney("412x840-font20", 2f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscape10() = retryJourney("800x360-font10", 1f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscape16() = retryJourney("800x360-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscape20() = retryJourney("800x360-font20", 2f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun adaptive10() = retryJourney("600x960-font10", 1f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun adaptive16() = retryJourney("600x960-font16", 1.6f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun adaptive20() = retryJourney("600x960-font20", 2f)
    @Test @Config(fontScale = 2f)
    fun rtlReducedMotion() {
        rtl = true
        reducedMotion = true
        retryJourney("360x640-rtl-font20-reduced", 2f)
    }

    @Test @Config(fontScale = 1f) fun blockedSmall10() = blockerRetryJourney("360x640-font10", 1f)
    @Test @Config(fontScale = 1.6f) fun blockedSmall16() = blockerRetryJourney("360x640-font16", 1.6f)
    @Test @Config(fontScale = 2f) fun blockedSmall20() = blockerRetryJourney("360x640-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun blockedStandard10() = blockerRetryJourney("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun blockedStandard16() = blockerRetryJourney("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun blockedStandard20() = blockerRetryJourney("412x840-font20", 2f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun blockedLandscape10() = blockerRetryJourney("800x360-font10", 1f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun blockedLandscape16() = blockerRetryJourney("800x360-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun blockedLandscape20() = blockerRetryJourney("800x360-font20", 2f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun blockedAdaptive10() = blockerRetryJourney("600x960-font10", 1f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun blockedAdaptive16() = blockerRetryJourney("600x960-font16", 1.6f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun blockedAdaptive20() = blockerRetryJourney("600x960-font20", 2f)
    @Test @Config(fontScale = 2f)
    fun blockedRtlReducedMotion() {
        rtl = true
        reducedMotion = true
        blockerRetryJourney("360x640-rtl-font20-reduced", 2f)
    }

    private fun retryJourney(name: String, font: Float) {
        profile = name
        assertEquals(font, RuntimeEnvironment.getApplication().resources.configuration.fontScale, .001f)
        createGraph()
        show()
        openRoutinePage()
        val before = inventory()
        val start = enabledClick(HomeStartTags.routine(routineId))
        val cancel = enabledClick(HomeStartTags.CANCEL)
        writes.held = CompletableDeferred()
        writes.failNext.set(1)
        // Both callbacks were taken from one enabled frame. Recomposition cannot guard these.
        compose.runOnUiThread { start(); start(); cancel() }
        await("one admitted insert waits without navigation") {
            vm.startState.value.pending && writes.attempts.get() == 1
        }
        assertTrue(openedSessions.isEmpty())
        assertFalse(vm.canDismissStart())
        assertFalse(vm.canEditStartChoice())
        compose.onNodeWithTag(HomeStartTags.CANCEL).assertIsNotEnabled()
        compose.onNodeWithTag(HomeStartTags.routine(routineId)).assertExists()
        compose.onNodeWithTag(HomeStartTags.FREE).assertDoesNotExist()
        reachable(compose.onNodeWithTag(HomeStartTags.PENDING)).assertIsDisplayed()
        capture("insert-pending")
        attemptNativeDismiss()
        compose.onNodeWithTag(HomeStartTags.SHEET).assertExists()
        assertTrue(vm.startState.value.pending)
        assertEquals(1, writes.attempts.get())
        assertTrue(openedSessions.isEmpty())
        // Room's transaction is held: inventory queries belong only after its release.
        writes.held!!.complete(Unit)
        await("the failed insert retains its exact retry command") {
            !vm.startState.value.pending && vm.startState.value.error != null && vm.startState.value.canRetry
        }
        assertEquals(before, inventory())
        assertTrue(openedSessions.isEmpty())
        compose.onNodeWithTag(HomeStartTags.routine(routineId)).assertExists()
        compose.onNodeWithTag(HomeStartTags.FREE).assertDoesNotExist()
        reachable(compose.onNodeWithTag(HomeStartTags.ERROR)).assertIsDisplayed()
        capture("insert-failed")
        reachable(compose.onNodeWithTag(HomeStartTags.RETRY)).assertIsEnabled()
        capture("insert-failed-actions")

        writes.held = CompletableDeferred()
        val retry = enabledClick(HomeStartTags.RETRY)
        compose.runOnUiThread { retry(); retry() }
        await("one explicit retry waits") { vm.startState.value.pending && writes.attempts.get() == 2 }
        assertTrue(openedSessions.isEmpty())
        compose.onNodeWithTag(HomeStartTags.CANCEL).assertIsNotEnabled()
        reachable(compose.onNodeWithTag(HomeStartTags.PENDING)).assertIsDisplayed()
        capture("retry-pending")
        writes.held!!.complete(Unit)
        assertOneSavedStart()
        assertEquals(2, writes.attempts.get())
        val saved = runBlocking { base.database.workoutDao().getAllSessions().single() }
        assertEquals(routineId, saved.routineId)
        assertEquals(ROUTINE_NAME, saved.routineName)
        assertEquals(nowMs, saved.date)
        assertEquals(nowMs, saved.startedAt)
        val lifts = runBlocking { base.database.workoutDao().getAllSessionExercises() }
        assertEquals(1, lifts.size)
        assertEquals(3, lifts.single().targetSets)
        assertEquals(8, lifts.single().targetReps)
        assertEquals(27.5, checkNotNull(lifts.single().targetWeightKg), 0.0)
        assertNull(runBlocking { PendingOccurrence.followedBy(graph, saved.id) })
    }

    private fun blockerRetryJourney(name: String, font: Float) {
        profile = "blocker-$name"
        assertEquals(font, RuntimeEnvironment.getApplication().resources.configuration.fontScale, .001f)
        createGraph()
        show()
        openRoutinePage()
        val blocker = runBlocking { base.workoutRepository.startFreeWorkout() }
        await("Home observes the real workout that blocks the open routine choice") {
            vm.uiState.value.inProgress?.id == blocker.id
        }
        val before = inventory()
        val routineStart = enabledClick(HomeStartTags.routine(routineId))
        compose.runOnUiThread { routineStart() }
        await("the selected routine presents the actual blocker decision") {
            vm.blockedByInProgress.value?.sessionId == blocker.id && !vm.startState.value.pending
        }
        assertEquals(before, inventory())
        assertEquals(0, writes.attempts.get())
        assertEquals(0, writes.discards.get())
        val oldResume = enabledClick(dialogAction(LiveBarCopy.RESUME))
        val discard = enabledClick(dialogAction("Discard it and start this"))
        capture("blocker-choice")

        writes.discardHeld = CompletableDeferred()
        writes.held = CompletableDeferred()
        writes.failNext.set(1)
        compose.runOnUiThread { discard(); discard(); oldResume() }
        await("one deliberate discard waits before touching the saved blocker") {
            vm.startState.value.pending && writes.discards.get() == 1 && !vm.startState.value.discardedOriginal
        }
        assertEquals(0, writes.attempts.get())
        assertTrue(openedSessions.isEmpty())
        assertFalse(vm.canResumeBlockedStart())
        // The delete gate sits before Room; no transaction is held by this inventory read.
        assertEquals(before, inventory())
        assertTouchTarget(dialogAction(LiveBarCopy.RESUME)).assertIsNotEnabled()
        assertTouchTarget(dialogAction("Starting…")).assertIsNotEnabled()
        reachable(compose.onNodeWithTag(HomeStartTags.PENDING)).assertIsDisplayed()
        capture("discard-pending")
        attemptNativeDismiss()
        assertEquals(blocker.id, vm.blockedByInProgress.value?.sessionId)
        assertTrue(vm.startState.value.pending)
        assertEquals(1, writes.discards.get())
        assertEquals(before, inventory())

        writes.discardHeld!!.complete(Unit)
        await("acknowledged discard advances to the one held replacement insert") {
            vm.startState.value.pending && vm.startState.value.discardedOriginal && writes.attempts.get() == 1
        }
        assertEquals(1, writes.discards.get())
        assertTrue(openedSessions.isEmpty())
        assertFalse(vm.canResumeBlockedStart())
        dialogAction(LiveBarCopy.RESUME).assertDoesNotExist()
        reachable(compose.onNodeWithText(DISCARDED_BODY)).assertIsDisplayed()
        capture("acknowledged-discard-replacement-pending")
        compose.runOnUiThread { oldResume() }
        compose.settle()
        assertTrue(openedSessions.isEmpty())
        assertEquals(1, writes.attempts.get())
        // The replacement owns Room's transaction. Read inventory only after release.
        writes.held!!.complete(Unit)
        await("failed replacement retains the acknowledged discard and exact retry") {
            !vm.startState.value.pending && vm.startState.value.discardedOriginal &&
                vm.startState.value.error != null && vm.startState.value.canRetry
        }
        assertEquals("Could not start that session. Try again.", vm.startState.value.error)
        val afterDiscard = before.toMutableList().apply { this[0] = emptyList<WorkoutSessionEntity>() }
        assertEquals("only the acknowledged blocker deletion persists", afterDiscard, inventory())
        assertEquals(blocker.id, vm.blockedByInProgress.value?.sessionId)
        assertTrue(openedSessions.isEmpty())
        dialogAction(LiveBarCopy.RESUME).assertDoesNotExist()
        assertFalse(vm.canResumeBlockedStart())
        reachable(compose.onNodeWithText(DISCARDED_BODY)).assertIsDisplayed()
        reachable(compose.onNodeWithTag(HomeStartTags.ERROR)).assertIsDisplayed()
        capture("acknowledged-discard-replacement-failed")
        val retry = enabledClick(dialogAction("Retry start"))
        capture("acknowledged-discard-retry-actions")
        val failedState = vm.startState.value
        compose.runOnUiThread { discard(); oldResume() }
        compose.settle()
        assertEquals(failedState, vm.startState.value)
        assertEquals(afterDiscard, inventory())
        assertEquals(1, writes.discards.get())
        assertEquals(1, writes.attempts.get())
        assertTrue(openedSessions.isEmpty())

        writes.held = CompletableDeferred()
        compose.runOnUiThread { retry(); retry() }
        await("one fresh Retry resumes the replacement without discarding again") {
            vm.startState.value.pending && vm.startState.value.discardedOriginal && writes.attempts.get() == 2
        }
        assertEquals(1, writes.discards.get())
        assertTrue(openedSessions.isEmpty())
        dialogAction(LiveBarCopy.RESUME).assertDoesNotExist()
        assertTouchTarget(dialogAction("Starting…")).assertIsNotEnabled()
        reachable(compose.onNodeWithTag(HomeStartTags.PENDING)).assertIsDisplayed()
        capture("acknowledged-discard-retry-pending")
        writes.held!!.complete(Unit)
        assertOneSavedStart()
        assertEquals(1, writes.discards.get())
        assertEquals(2, writes.attempts.get())
        val saved = runBlocking { base.database.workoutDao().getAllSessions().single() }
        assertEquals(routineId, saved.routineId)
        assertEquals(ROUTINE_NAME, saved.routineName)
        assertEquals(nowMs, saved.date)
        assertEquals(nowMs, saved.startedAt)
        assertEquals(1, runBlocking { base.database.workoutDao().getAllSessionExercises().size })
        assertEquals(before[3], runBlocking { base.database.plannerDao().getAllOccurrences() })
        assertEquals(before[4], runBlocking { base.database.plannerDao().getRules() })
        assertNull(base.pendingOccurrenceId.value)
        assertNull(vm.blockedByInProgress.value)
    }

    @Test fun heldRoutineLookupFailureRetainsChosenPageAndRetriesTheSameRoutineOnce() {
        profile = "routine-lookup"
        createGraph()
        show()
        openRoutinePage()
        val before = inventory()
        reads.held = CompletableDeferred()
        reads.failNext.set(1)
        val start = enabledClick(HomeStartTags.routine(routineId))
        compose.runOnUiThread { start(); start() }
        await("one routine lookup waits") { vm.startState.value.pending && reads.attempts.get() == 1 }
        assertEquals(0, writes.attempts.get())
        assertTrue(openedSessions.isEmpty())
        capture("lookup-pending")
        reads.held!!.complete(Unit)
        await("routine lookup failed without dismissing its authored choice") {
            !vm.startState.value.pending && vm.startState.value.error != null && vm.startState.value.canRetry
        }
        assertEquals(before, inventory())
        compose.onNodeWithTag(HomeStartTags.routine(routineId)).assertExists()
        compose.onNodeWithTag(HomeStartTags.FREE).assertDoesNotExist()
        capture("lookup-failed")
        reads.held = CompletableDeferred()
        val retry = enabledClick(HomeStartTags.RETRY)
        compose.runOnUiThread { retry(); retry() }
        await("the exact lookup retry waits once") { vm.startState.value.pending && reads.attempts.get() == 2 }
        assertEquals(0, writes.attempts.get())
        assertTrue(openedSessions.isEmpty())
        reads.held!!.complete(Unit)
        assertOneSavedStart()
        assertEquals(2, reads.attempts.get())
        assertEquals(1, writes.attempts.get())
        assertEquals(routineId, runBlocking { base.database.workoutDao().getAllSessions().single().routineId })
    }

    @Test fun idleCancelClearsSynchronousFailureAndOldRoutinePageCannotStartAfterFreeChoice() {
        profile = "stale-routine-page"
        createGraph()
        show()
        openRoutinePage()
        val before = inventory()
        val oldRoutineStart = enabledClick(HomeStartTags.routine(routineId))
        val oldCancel = enabledClick(HomeStartTags.CANCEL)
        reads.failNext.set(1)
        compose.runOnUiThread {
            oldRoutineStart()
            assertFalse(vm.startState.value.pending)
            assertTrue(vm.startState.value.error != null)
            assertTrue(vm.startState.value.canRetry)
            oldCancel()
            oldRoutineStart()
        }
        compose.settle()
        assertTrue("the older Cancel cannot clear a newer synchronous failure", vm.startState.value.error != null)
        assertTrue(vm.startState.value.canRetry)
        assertEquals(1, reads.attempts.get())
        assertEquals(0, writes.attempts.get())
        assertEquals(before, inventory())
        val freshCancel = enabledClick(HomeStartTags.CANCEL)
        compose.runOnUiThread { freshCancel(); oldRoutineStart() }
        compose.settle()
        assertTrue("idle page navigation clears the failed attempt", vm.canEditStartChoice())
        assertNull(vm.startState.value.error)
        assertFalse(vm.startState.value.canRetry)
        assertFalse(vm.startState.value.pending)
        assertEquals(1, reads.attempts.get())
        assertEquals(0, writes.attempts.get())
        assertTrue(openedSessions.isEmpty())
        assertEquals(before, inventory())
        compose.onNodeWithTag(HomeStartTags.routine(routineId)).assertDoesNotExist()
        val free = enabledClick(HomeStartTags.FREE)
        capture("idle-cancel-fresh-kind")
        compose.runOnUiThread { free() }
        assertOneSavedStart()
        val saved = runBlocking { base.database.workoutDao().getAllSessions().single() }
        assertNull(saved.routineId)
        assertEquals("Free workout", saved.routineName)
        assertTrue(runBlocking { base.database.workoutDao().getAllSessionExercises().isEmpty() })
        assertEquals(1, writes.attempts.get())
        assertEquals(1, reads.attempts.get())
        assertNull(base.pendingOccurrenceId.value)
    }

    @Test fun yesterdayConfirmationExplainsStartsNowAndPersistsActualToday() = dateJourney(-1)
    @Test fun tomorrowConfirmationExplainsStartsNowAndPersistsActualToday() = dateJourney(1)

    @Test fun oldBlockedDiscardCannotActOnAReplacementBlockerButFreshDiscardStartsOnce() {
        profile = "stale-blocked-owner"
        createGraph()
        show()
        openRoutinePage()
        val blockerA = runBlocking { base.workoutRepository.startFreeWorkout() }
        await("Home observes the first real in-progress blocker") { vm.uiState.value.inProgress?.id == blockerA.id }
        val startA = enabledClick(HomeStartTags.routine(routineId))
        compose.runOnUiThread { startA() }
        await("the chosen routine is blocked by the first workout") {
            vm.blockedByInProgress.value?.sessionId == blockerA.id && !vm.startState.value.pending
        }
        val oldDiscard = enabledClick(compose.onNodeWithText("Discard it and start this"))
        capture("blocker-a-confirmation")
        compose.runOnUiThread { checkNotNull(ShadowDialog.getLatestDialog()).onBackPressed() }
        await("native Back explicitly retires the first blocked choice") { vm.blockedByInProgress.value == null }
        assertTrue(openedSessions.isEmpty())
        runBlocking { base.workoutRepository.finishSession(blockerA.id, "Retained finished blocker A") }
        await("Home observes the finished blocker before a new deliberate choice") { vm.uiState.value.inProgress == null }
        openRoutinePage()
        val blockerB = runBlocking { base.workoutRepository.startFreeWorkout() }
        assertNotEquals(blockerA.id, blockerB.id)
        await("Home observes the replacement in-progress workout") { vm.uiState.value.inProgress?.id == blockerB.id }
        val startB = enabledClick(HomeStartTags.routine(routineId))
        compose.runOnUiThread { startB() }
        await("the current choice is blocked by the replacement workout") {
            vm.blockedByInProgress.value?.sessionId == blockerB.id && !vm.startState.value.pending
        }
        val currentBlocked = checkNotNull(vm.blockedByInProgress.value)
        val currentAttempt = vm.startState.value.attemptId
        val beforeStaleAction = inventory()
        capture("blocker-b-confirmation")
        compose.runOnUiThread { oldDiscard() }
        compose.settle()
        assertEquals(currentBlocked, vm.blockedByInProgress.value)
        assertEquals(currentAttempt, vm.startState.value.attemptId)
        assertFalse(vm.startState.value.pending)
        assertEquals(0, writes.attempts.get())
        assertEquals(beforeStaleAction, inventory())
        assertTrue(openedSessions.isEmpty())
        val freshDiscard = enabledClick(compose.onNodeWithText("Discard it and start this"))
        compose.runOnUiThread { freshDiscard(); freshDiscard() }
        await("the current deliberate discard opens the selected routine once") {
            openedSessions.size == 1 && vm.navigateToSession.value == null && !vm.startState.value.pending
        }
        val sessions = runBlocking { base.database.workoutDao().getAllSessions() }
        assertEquals(2, sessions.size)
        assertTrue(sessions.single { it.id == blockerA.id }.finishedAt != null)
        assertTrue(sessions.none { it.id == blockerB.id })
        assertEquals(routineId, sessions.single { it.id == openedSessions.single() }.routineId)
        assertEquals(1, writes.attempts.get())
        assertNull(vm.blockedByInProgress.value)
        assertNull(vm.startState.value.error)
        compose.onNodeWithTag(HomeStartTags.SHEET).assertDoesNotExist()
        compose.settle()
        assertEquals("the current saved start is consumed once", 1, openedSessions.size)
    }

    private fun dateJourney(offset: Long) {
        profile = if (offset < 0) "yesterday" else "tomorrow"
        val selectedDay = today + offset
        createGraph(plannedDay = selectedDay)
        show()
        selectDay(selectedDay)
        boardNode(HomeTags.agendaRow(checkNotNull(occurrenceId))).performClick()
        val date = reachable(compose.onNodeWithTag(HomeStartTags.DATE))
        date.assertTextContains("Planned: ${DateCopy.civilDate(selectedDay)}", substring = true)
        date.assertTextContains("Starts now · ${DateCopy.civilDate(today)}", substring = true)
        capture("planned-date-confirm")
        reachable(compose.onNodeWithTag(WorkoutPreviewTags.BEGIN)).assertIsEnabled().performClick()
        assertOneSavedStart()
        val saved = runBlocking { base.database.workoutDao().getAllSessions().single() }
        assertEquals(nowMs, saved.date)
        assertEquals(nowMs, saved.startedAt)
        assertEquals(today, base.time.capture(saved.date).localEpochDay)
        if (offset > 0) {
            val unchanged = runBlocking { checkNotNull(base.plannerRepository.getOccurrence(checkNotNull(occurrenceId))) }
            assertEquals(selectedDay, unchanged.localEpochDay)
            assertEquals(OccurrenceStatus.PLANNED, unchanged.status)
        }
    }

    @Test fun failedLeftoverRetryBindsTheCreatedIdAndPreservesMovedRowsAndRecurrence() {
        profile = "leftover-moved-retry"
        createGraph(plannedDay = today - 1)
        val originalId = checkNotNull(occurrenceId)
        val vacantId = runBlocking {
            val original = checkNotNull(base.database.plannerDao().getOccurrence(originalId))
            val captured = base.time.resolveLocal(
                CivilDateTime(CivilDate.fromEpochDay(today), original.hour, original.minute),
                base.time.defaultZoneId(),
            )
            OccurrenceGenerator.occurrenceId(original.ruleId, today).also { id ->
                base.database.plannerDao().upsertOccurrence(original.copy(
                    id = id, status = OccurrenceStatus.MOVED.name, instantMs = captured.instantMillis,
                    zoneId = captured.zoneId, offsetSeconds = captured.offsetSeconds, localEpochDay = today,
                ))
            }
        }
        val rulesBefore = runBlocking { base.database.plannerDao().getRules() }
        show()
        boardNode(HomeTags.agendaRow(originalId)).performClick()
        writes.held = CompletableDeferred()
        writes.failNext.set(1)
        val confirm = enabledClick(WorkoutPreviewTags.BEGIN)
        compose.runOnUiThread { confirm(); confirm() }
        await("the relocated leftover owns one pending insert") {
            vm.startState.value.pending && writes.attempts.get() == 1
        }
        assertTrue(openedSessions.isEmpty())
        capture("leftover-insert-pending")
        writes.held!!.complete(Unit)
        await("the moved leftover keeps its confirmation after insert failure") {
            !vm.startState.value.pending && vm.startState.value.error != null && vm.startState.value.canRetry
        }
        assertEquals(originalId, vm.startState.value.occurrenceId)
        val afterFailure = runBlocking { base.database.plannerDao().getAllOccurrences() }
        assertEquals(OccurrenceStatus.MOVED.name, afterFailure.single { it.id == originalId }.status)
        assertEquals(OccurrenceStatus.MOVED.name, afterFailure.single { it.id == vacantId }.status)
        val created = afterFailure.single { it.localEpochDay == today && it.status == OccurrenceStatus.PLANNED.name }
        assertNotEquals(originalId, created.id)
        assertNotEquals(vacantId, created.id)
        assertTrue(runBlocking { base.database.workoutDao().getAllSessions().isEmpty() })
        assertNull(base.pendingOccurrenceId.value)
        val failedAttempt = vm.startState.value.attemptId
        val inventoryAfterFailure = inventory()
        // The old Start action must not reinterpret this newly rendered failure as Retry.
        compose.runOnUiThread { confirm() }
        compose.settle()
        assertEquals(1, writes.attempts.get())
        assertEquals(failedAttempt, vm.startState.value.attemptId)
        assertTrue(vm.startState.value.canRetry)
        assertFalse(vm.startState.value.pending)
        assertTrue(openedSessions.isEmpty())
        assertEquals(inventoryAfterFailure, inventory())
        // The banner is behind the native confirmation. Retain its real semantics action;
        // this is a stale-callback check, not a claim that the covered button can be tapped.
        val bannerDismiss = hasContentDescription("Dismiss") and hasAnyAncestor(hasTestTag(HomeTags.BOARD))
        if (compose.onAllNodes(bannerDismiss).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag(HomeTags.BOARD).performScrollToNode(hasContentDescription("Dismiss"))
        }
        val oldErrorDismiss = compose.onNode(bannerDismiss).assertIsEnabled().fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        assertEquals(failedAttempt, vm.startState.value.attemptId)
        assertEquals(inventoryAfterFailure, inventory())
        reachable(compose.onNodeWithTag(HomeStartTags.ERROR)).assertIsDisplayed()
        val retry = enabledClick(WorkoutPreviewTags.BEGIN)
        capture("leftover-failed-confirmation")
        writes.held = CompletableDeferred()
        writes.failNext.set(1)
        compose.runOnUiThread { retry(); retry() }
        await("retry uses the already relocated occurrence once") {
            vm.startState.value.pending && writes.attempts.get() == 2
        }
        assertNotEquals(failedAttempt, vm.startState.value.attemptId)
        assertTrue(openedSessions.isEmpty())
        capture("leftover-retry-pending")
        writes.held!!.complete(Unit)
        await("the failed explicit retry retains the same relocated command") {
            !vm.startState.value.pending && vm.startState.value.error != null && vm.startState.value.canRetry
        }
        val retryFailureAttempt = vm.startState.value.attemptId
        assertEquals(originalId, vm.startState.value.occurrenceId)
        assertEquals(inventoryAfterFailure, inventory())
        // The captured Retry belongs to its earlier attempt, not every later retryable error.
        compose.runOnUiThread { retry() }
        compose.settle()
        assertEquals(2, writes.attempts.get())
        assertEquals(retryFailureAttempt, vm.startState.value.attemptId)
        assertTrue(vm.startState.value.canRetry)
        assertFalse(vm.startState.value.pending)
        assertTrue(openedSessions.isEmpty())
        assertEquals(inventoryAfterFailure, inventory())
        val currentStart = vm.startState.value
        val currentError = vm.uiState.value.error
        compose.runOnUiThread { oldErrorDismiss() }
        compose.settle()
        assertEquals("the first banner cannot clear a newer failed start owner", currentStart, vm.startState.value)
        assertEquals(currentError, vm.uiState.value.error)
        assertEquals(inventoryAfterFailure, inventory())
        assertEquals(2, writes.attempts.get())
        assertTrue(openedSessions.isEmpty())
        reachable(compose.onNodeWithTag(HomeStartTags.ERROR))
            .assertTextContains(checkNotNull(currentStart.error), substring = true)
        reachable(compose.onNodeWithTag(WorkoutPreviewTags.BEGIN))
            .assertIsEnabled().assertTextContains("Retry start", substring = true)
        val freshRetry = enabledClick(WorkoutPreviewTags.BEGIN)
        capture("leftover-second-failed-confirmation")
        writes.held = CompletableDeferred()
        compose.runOnUiThread { freshRetry(); freshRetry() }
        await("a freshly rendered explicit retry starts the same relocated occurrence once") {
            vm.startState.value.pending && writes.attempts.get() == 3
        }
        assertNotEquals(retryFailureAttempt, vm.startState.value.attemptId)
        assertTrue(openedSessions.isEmpty())
        writes.held!!.complete(Unit)
        assertOneSavedStart()
        val savedId = openedSessions.single()
        assertEquals(created.id, runBlocking { PendingOccurrence.followedBy(graph, savedId) })
        assertEquals(afterFailure, runBlocking { base.database.plannerDao().getAllOccurrences() })
        assertEquals(rulesBefore, runBlocking { base.database.plannerDao().getRules() })
        assertEquals(3, writes.attempts.get())
    }

    @Test fun freeStartFromAnotherSelectedDayShowsNowAndCreatesNoPlanRow() {
        profile = "selected-day-free"
        createGraph()
        show()
        selectDay(today - 1)
        boardNode(HomeTags.START).performClick()
        reachable(compose.onNodeWithTag(HomeStartTags.DATE))
            .assertTextContains("Starts now · ${DateCopy.civilDate(today)}", substring = true)
        capture("selected-day-free-confirm")
        reachable(compose.onNodeWithTag(HomeStartTags.FREE)).performClick()
        assertOneSavedStart()
        val saved = runBlocking { base.database.workoutDao().getAllSessions().single() }
        assertEquals(nowMs, saved.date)
        assertEquals(nowMs, saved.startedAt)
        assertNull(saved.routineId)
        assertTrue(runBlocking { base.database.plannerDao().getAllOccurrences().isEmpty() })
        assertNull(base.pendingOccurrenceId.value)
    }

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun illustratedPlannedHomeKeepsGenericStartByNavigationAndStartsTheNamedRoutine() {
        profile = "412x840-font10-actual-home"
        createGraph(plannedDay = today, visual = true)
        show(withNavigation = true)
        val planned = boardNode(HomeTags.agendaRow(checkNotNull(occurrenceId)))
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        val navigation = compose.onNodeWithTag("primary-navigation").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val start = compose.onNodeWithTag(HomeTags.START).fetchSemanticsNode().boundsInWindow
        assertTrue("general Start stays above the real navigation", start.bottom <= navigation.top)
        val bitmap = compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
        }
        try {
            val file = File("build/screen-renders/home-agenda-composition/$runId/$profile/actual-loaded-home.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue(file.length() > 100)
        } finally { bitmap.recycle() }
        planned.performClick()
        reachable(compose.onNodeWithTag(WorkoutPreviewTags.BEGIN)).assertIsEnabled().performClick()
        assertOneSavedStart()
        val saved = runBlocking { checkNotNull(base.workoutRepository.getSession(openedSessions.single())) }
        assertEquals(listOf("Incline Dumbbell Bench Press", "Seated Cable Row"), saved.exercises.map { it.exercise.name })
        assertTrue("the persisted lifts retain their matching illustration keys", saved.exercises.all { !it.exercise.imageKey.isNullOrBlank() })
    }

    private suspend fun visualLift(name: String): Exercise {
        val seed = DefaultExercises.catalog().first { it.name == name }
        val id = "home-visual-${seed.id}"
        base.database.exerciseDao().insertAll(listOf(ExerciseEntity(
            id = id, name = name, muscleGroup = seed.muscleGroup, notes = "", isCustom = false,
            equipment = seed.equipment.name, loadType = seed.loadType.name,
            movementKey = seed.movementKey, imageKey = seed.imageKey,
            nameKey = MuscleNormalizer.nameKeyOf(name),
        )))
        return checkNotNull(base.exerciseRepository.getById(id))
    }

    private fun createGraph(plannedDay: Long? = null, visual: Boolean = false) {
        base = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            insights = insights, time = FrozenTime(nowMs),
        )
        val routines = RoutineRepository(reads.decorate(base.database.routineDao()), base.database, base.plannerRepository)
        // FakeAppDependencies deliberately retains its default workout clock. This fixture
        // rebuilds the real repository so date assertions inspect the injected literal now.
        val workouts = WorkoutRepository(
            database = base.database, workoutDao = writes.decorate(base.database.workoutDao()),
            dbMaintenance = base.dbMaintenance, restoreBlocksStart = { base.backupService.restoreBlocksStart() },
            time = base.time,
        )
        val dayStart = StartTrainingDay(workouts, routines)
        val discard = DiscardWorkout(workouts, base.restTimerController, base.workoutDraftCache)
        graph = object : AppDependencies by base {
            override val routineRepository = routines
            override val workoutRepository = workouts
            override val startTrainingDay = dayStart
            override val startOccurrence = StartOccurrence(base.plannerRepository, routines, dayStart, base.startLiveCardio)
            override val discardWorkout = discard
        }
        runBlocking {
            base.preferencesRepository.setOnboardingComplete(true)
            base.preferencesRepository.setWeightUnit(WeightUnit.KG)
            val target = base.routineRepository.create(ROUTINE_NAME)
            val lift = if (visual) visualLift("Incline Dumbbell Bench Press") else
                insertTestExercise(base, "home-admission-bench", "Incline Dumbbell Bench Press")
            base.routineRepository.addExercise(target.id, lift, 3, 8, 27.5, 120)
            if (visual) base.routineRepository.addExercise(target.id, visualLift("Seated Cable Row"), 3, 10, 45.0, 90)
            routineId = target.id
            val other = base.routineRepository.create("Lower B")
            base.routineRepository.addExercise(other.id, lift, 2, 10, 20.0, 90)
            if (plannedDay != null) {
                base.scheduleRepository.pin(routineId, null, Weekday.fromEpochDay(plannedDay))
                base.plannerRepository.importSlotsIfNeeded(nowMs)
                if (plannedDay < today) {
                    // A historical authored slot requires its rule to exist before that day.
                    val dao = base.database.plannerDao()
                    val rule = dao.getRules().single { it.routineId == routineId }
                    val dayStartMs = LocalDate.ofEpochDay(plannedDay)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    dao.upsertRule(rule.copy(createdAtMs = dayStartMs - 1))
                }
                val week = CivilDate.fromEpochDay(plannedDay).previousOrSame(Weekday.MONDAY)
                base.plannerRepository.ensureWeek(week)
                occurrenceId = base.plannerRepository.occurrencesBetween(plannedDay, plannedDay).single().id
            }
            insights.value = TrainingInsights(
                routines = base.routineRepository.observeAll().first(), weekPlan = centeredRestWeek(),
            )
        }
        vm = HomeViewModel(ApplicationProvider.getApplicationContext(), graph)
    }

    /** A valid chosen week-start keeps yesterday and tomorrow visible even at a week boundary. */
    private fun centeredRestWeek(): WeeklySchedulePlan {
        val start = today - 3
        return WeeklySchedulePlan(
            weekStartEpochDay = start, generatedAtMs = nowMs,
            preferences = SchedulePreferences(weekStart = Weekday.fromEpochDay(start)),
            resolvedSplit = SplitStyle.FULL_BODY, thinHistory = true, summary = "Synthetic admission week",
            days = (0L..6L).map { delta ->
                val day = start + delta
                SuggestedTrainingDay(
                    epochDay = day, dayOfWeek = Weekday.fromEpochDay(day), isRest = true,
                    focusKind = SessionFocusKind.RECOVERY, focusTitle = "Rest", routineId = null,
                    routineName = null, reason = "Synthetic fixture", emphasisMuscles = emptyList(),
                    confidence = ScheduleConfidence.HIGH,
                )
            },
        )
    }

    private fun show(withNavigation: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(
                LocalWeightUnit provides WeightUnit.KG, LocalTodayEpochDay provides today,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides reducedMotion,
            ) {
                PersonalTrainerTheme(reduceMotion = reducedMotion) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Pit) {
                        if (withNavigation) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    HomeScreen(onResumeWorkout = { openedSessions += it }, onOpenPlan = {}, viewModel = vm)
                                }
                                InstrumentNavBar(tabs = shippingTabs, isSelected = { it.route == Route.Home }, onSelect = {})
                            }
                        } else HomeScreen(onResumeWorkout = { openedSessions += it }, onOpenPlan = {}, viewModel = vm)
                    }
                }
            }
        }
        await("the real Home board is current") {
            vm.uiState.value.readState == HomeReadState.CURRENT && !vm.uiState.value.isLoading
        }
    }

    private fun openRoutinePage() {
        boardNode(HomeTags.START).performClick()
        reachable(compose.onNodeWithTag(HomeStartTags.ROUTINE)).performClick()
        reachable(compose.onNodeWithTag(HomeStartTags.routine(routineId))).assertIsEnabled()
    }

    private fun selectDay(day: Long) {
        boardNode(WeekStripTags.STRIP)
        reachable(compose.onNodeWithTag(WeekStripTags.cell(day))).performClick()
        compose.settle()
    }

    private fun boardNode(tag: String): SemanticsNodeInteraction {
        // Generic Start is anchored outside the day list. Other targets are lazy items.
        if (tag != HomeTags.START) {
            compose.onNodeWithTag(HomeTags.BOARD).performScrollToNode(hasTestTag(tag))
        }
        return reachable(compose.onNodeWithTag(tag))
    }

    private fun enabledClick(tag: String): () -> Boolean = enabledClick(compose.onNodeWithTag(tag))

    private fun enabledClick(interaction: SemanticsNodeInteraction): () -> Boolean {
        val node = assertTouchTarget(interaction).assertIsEnabled().fetchSemanticsNode()
        return node.config[SemanticsActions.OnClick].action!!
    }

    private fun assertTouchTarget(interaction: SemanticsNodeInteraction): SemanticsNodeInteraction {
        val node = reachable(interaction).fetchSemanticsNode()
        val density = node.layoutInfo.density.density
        assertTrue("the real action has a 48 dp height", node.size.height / density >= Metrics.touchMin.value - .5f)
        assertTrue("the real action has a 48 dp width", node.size.width / density >= Metrics.touchMin.value - .5f)
        return interaction
    }

    private fun dialogAction(label: String): SemanticsNodeInteraction =
        compose.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

    private fun await(what: String, condition: () -> Boolean) {
        val now: () -> Any? = { vm.uiState.value to vm.startState.value }
        compose.awaitThat(what = what, now = now, condition = condition)
        compose.settle()
    }

    private fun assertOneSavedStart() {
        await("one actual saved start navigates and is consumed") {
            openedSessions.size == 1 && !vm.startState.value.pending && vm.navigateToSession.value == null
        }
        assertNull(vm.startState.value.error)
        assertFalse(vm.startState.value.canRetry)
        assertEquals(openedSessions.single(), runBlocking { base.database.workoutDao().getAllSessions().single().id })
        compose.onNodeWithTag(HomeStartTags.SHEET).assertDoesNotExist()
        compose.onNodeWithTag(HomeStartTags.PENDING).assertDoesNotExist()
        compose.onNodeWithTag(HomeStartTags.ERROR).assertDoesNotExist()
        compose.settle()
        assertEquals("the acknowledged start is delivered only once", 1, openedSessions.size)
    }

    private fun inventory(): List<Any?> = runBlocking {
        listOf(
            base.database.workoutDao().getAllSessions(), base.database.workoutDao().getAllSessionExercises(),
            base.database.workoutDao().getAllSets(), base.database.plannerDao().getAllOccurrences(),
            base.database.plannerDao().getRules(), base.database.routineDao().getAllRoutines(),
            base.database.routineDao().getAllRoutineExercises(), base.database.activityDao().getAllGraphs(),
            base.rawPreferenceValues(), base.pendingOccurrenceId.value,
        )
    }

    /** Native Back and a real scrim touch, rather than directly calling a ViewModel guard. */
    private fun attemptNativeDismiss() {
        compose.runOnUiThread {
            val dialog = checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing })
            dialog.onBackPressed()
        }
        compose.settle()
        compose.runOnUiThread {
            val popup = currentWindow()
            val stamp = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(stamp, stamp, MotionEvent.ACTION_DOWN, 4f, 4f, 0)
            val up = MotionEvent.obtain(stamp, stamp + 16, MotionEvent.ACTION_UP, 4f, 4f, 0)
            try { popup.dispatchTouchEvent(down); popup.dispatchTouchEvent(up) }
            finally { down.recycle(); up.recycle() }
        }
        compose.settle()
    }

    /** Measured ScrollBy actions keep the whole action in its native viewport. */
    private fun reachable(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        compose.settle()
        repeat(25) { step ->
            val target = node.fetchSemanticsNode()
            val decor = compose.runOnUiThread { currentWindow() }
            val window = UiRect(0f, 0f, decor.width.toFloat(), decor.height.toFloat())
            val visible = target.boundsInWindow.intersect(window)
            if (visible.width >= target.size.width - 1f && visible.height >= target.size.height - 1f) {
                return node.assertIsDisplayed()
            }
            val origin = target.positionInWindow
            val full = UiRect(origin.x, origin.y, origin.x + target.size.width, origin.y + target.size.height)
            var parent = target.parent
            var moved = false
            while (parent != null) {
                val candidate = parent
                val scroll = candidate.config.getOrNull(SemanticsActions.ScrollBy)?.action
                if (scroll != null) {
                    val viewport = candidate.boundsInWindow.intersect(window)
                    val axes = listOf(
                        true to candidate.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange),
                        false to candidate.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange),
                    )
                    for ((horizontal, axis) in axes) {
                        if (axis == null) continue
                        val low = if (horizontal) full.left else full.top
                        val high = if (horizontal) full.right else full.bottom
                        val start = if (horizontal) viewport.left else viewport.top
                        val end = if (horizontal) viewport.right else viewport.bottom
                        val delta = when {
                            low < start - 1f -> low - start
                            high > end + 1f -> high - end
                            else -> 0f
                        }
                        val reverse = axis.reverseScrolling xor (horizontal && target.layoutInfo.layoutDirection == LayoutDirection.Rtl)
                        val amount = if (reverse) -delta else delta
                        if (amount < -1f && axis.value() > 0f || amount > 1f && axis.value() < axis.maxValue()) {
                            if (!compose.runOnUiThread { scroll(if (horizontal) amount else 0f, if (horizontal) 0f else amount) }) continue
                            compose.settle()
                            moved = true
                            break
                        }
                    }
                    if (moved) break
                }
                parent = candidate.parent
            }
            if (!moved || step == 24) {
                capture("unreachable")
                throw AssertionError("$profile cannot reach full target $full, visible $visible: ${target.config}")
            }
        }
        error("bounded reach failed")
    }

    private fun currentWindow(): View =
        WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it !== compose.activity.window.decorView }
            ?: compose.activity.window.decorView

    /** Composite actual popup windows; an activity-only bitmap would hide the changed sheet. */
    private fun capture(stage: String) {
        compose.settle()
        val bitmap = compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val popups = WindowInspector.getGlobalWindowViews().filter { it.isShown && it !== decor }
            assertTrue("the changed modal has a native popup window", popups.isNotEmpty())
            check(decor.width > 1 && decor.height > 1)
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                decor.draw(canvas)
                val baseLocation = IntArray(2)
                decor.getLocationOnScreen(baseLocation)
                popups.forEach { popup ->
                    val location = IntArray(2)
                    popup.getLocationOnScreen(location)
                    val saved = canvas.save()
                    canvas.translate((location[0] - baseLocation[0]).toFloat(), (location[1] - baseLocation[1]).toFloat())
                    popup.draw(canvas)
                    canvas.restoreToCount(saved)
                }
            }
        }
        try {
            val directory = File("build/screen-renders/home-start-admission/$runId/$profile")
            check(directory.isDirectory || directory.mkdirs())
            val file = directory.resolve("$stage.png")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("native popup evidence was written", file.length() > 100)
        } finally { bitmap.recycle() }
    }

    private class StartWrites {
        val attempts = AtomicInteger()
        val discards = AtomicInteger()
        val failNext = AtomicInteger()
        @Volatile var held: CompletableDeferred<Unit>? = null
        @Volatile var discardHeld: CompletableDeferred<Unit>? = null
        fun decorate(delegate: WorkoutDao): WorkoutDao = object : WorkoutDao by delegate {
            override suspend fun upsertSession(session: WorkoutSessionEntity) {
                attempts.incrementAndGet()
                held?.await()
                if (failNext.compareAndSet(1, 0)) throw SQLiteFullException("Synthetic Home start insert failure")
                delegate.upsertSession(session)
            }
            override suspend fun deleteInProgressSession(id: String): Int {
                discards.incrementAndGet()
                discardHeld?.await()
                return delegate.deleteInProgressSession(id)
            }
        }
    }

    private class RoutineReads {
        val attempts = AtomicInteger()
        val failNext = AtomicInteger()
        @Volatile var held: CompletableDeferred<Unit>? = null
        fun decorate(delegate: RoutineDao): RoutineDao = object : RoutineDao by delegate {
            override suspend fun getById(id: String): RoutineWithExercises? {
                attempts.incrementAndGet()
                held?.await()
                if (failNext.compareAndSet(1, 0)) throw SQLiteFullException("Synthetic Home routine lookup failure")
                return delegate.getById(id)
            }
        }
    }

    private companion object {
        const val ROUTINE_NAME = "Upper B · incline bench and controlled working sets"
        const val DISCARDED_BODY = "The previous workout was discarded. The selected workout has not opened yet."
    }
}
