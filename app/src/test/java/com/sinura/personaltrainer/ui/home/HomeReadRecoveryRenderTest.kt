package com.sinura.personaltrainer.ui.home

import android.app.Application
import android.database.sqlite.SQLiteFullException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as HomeReachRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.ExtraEquipment
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
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.components.WeekStripTags
import com.sinura.personaltrainer.ui.plan.AuxiliaryPackTags
import com.sinura.personaltrainer.ui.plan.CardioPickTags
import com.sinura.personaltrainer.ui.plan.ExtraEquipmentTags
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.count
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** Actual HomeScreen and HomeViewModel over isolated Room and a failing required DAO read. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class HomeReadRecoveryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<ViewModel>()
    private val read = RequiredBodyweightRead()
    private val insights = MutableStateFlow(TrainingInsights())
    private val runId = UUID.randomUUID().toString()
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: HomeViewModel
    private lateinit var occurrenceId: String
    private lateinit var routineId: String
    private var profile = "interaction"
    private var openedSession: String? = null
    private var localToday = todayEpochDay()
    private var rtl = false
    private var reducedMotion = false

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        read.held?.complete(Unit)
        read.heldWrite?.complete(Unit)
        try {
            runBlocking { models.forEach { it.clearAndJoinForTest() } }
            models.clear()
        } finally {
            if (::deps.isInitialized) {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                deps.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallFont10() = verifyMatrix("360x640-font10", 1f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallFont16() = verifyMatrix("360x640-font16", 1.6f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallFont20() = verifyMatrix("360x640-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardFont10() = verifyMatrix("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standardFont16() = verifyMatrix("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standardFont20() = verifyMatrix("412x840-font20", 2f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeFont10() = verifyMatrix("800x360-font10", 1f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeFont16() = verifyMatrix("800x360-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeFont20() = verifyMatrix("800x360-font20", 2f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tabletLargestText() = verifyMatrix("600x960-font20", 2f)

    private fun verifyMatrix(name: String, font: Float) {
        profile = name
        assertEquals(font, RuntimeEnvironment.getApplication().resources.configuration.fontScale, .001f)
        graph(initialFailure = true)
        show()
        await(HomeReadState.UNAVAILABLE)
        compose.onNodeWithTag(HomeTags.START).assertDoesNotExist()
        compose.onNodeWithTag(WeekStripTags.STRIP).assertDoesNotExist()
        proveRecovery("initial-unavailable")
        retry(held = true)
        recoveryNode(HomeTags.RETRY).assertIsNotEnabled()
        assertTrue(vm.uiState.value.retryPending)
        capture("initial-retry-held")
        read.held!!.complete(Unit)
        await(HomeReadState.CURRENT)
        compose.onNodeWithTag(WeekStripTags.STRIP).assertExists()
        scrollTo(HomeTags.agendaRow(occurrenceId)).performClick()
        compose.mainClock.autoAdvance = false
        try {
            compose.settle()
            val oldConfirm = compose.onNodeWithTag(ConfirmActionTags.CONFIRM)
                .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            val before = inventory()
            failRead()
            // A queued action from the previous enabled frame still asks the VM before
            // clearing the confirmation. It must refuse against the authoritative read state.
            compose.runOnUiThread { oldConfirm() }
            compose.settle()
            compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsNotEnabled()
            assertEquals(before, inventory())
            assertNull(openedSession)
            proveRecovery("planned-confirm-stale")
            val lastBoard = vm.uiState.value
            retry(held = true)
            assertEquals(lastBoard.occurrences, vm.uiState.value.occurrences)
            assertEquals(lastBoard.routines, vm.uiState.value.routines)
            compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsNotEnabled()
            capture("planned-confirm-retry-held")
            read.held!!.complete(Unit)
            await(HomeReadState.CURRENT)
            compose.settle()
            reachable(compose.onNodeWithTag(ConfirmActionTags.CONFIRM)).assertIsEnabled()
            reachable(compose.onNodeWithText("Cancel")).performClick()
            compose.settle()
        } finally { compose.mainClock.autoAdvance = true }
        failRead()
        proveRecovery("retained-board-stale")
        val row = scrollTo(HomeTags.agendaRow(occurrenceId)).assertIsNotEnabled()
        assertEquals(Role.Button, row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertNull(openedSession)
        retry()
        verifyBodyweightWriteFailure()
    }

    @Test fun bodyweightSetAndImeFromAnOldEnabledFrameKeepTheTypedValueOnRefusal() {
        graph()
        show()
        await(HomeReadState.CURRENT)
        scrollTo(HomeTags.BODYWEIGHT_CHECK_IN)
        withNumberClock {
            compose.onNodeWithText("Log weight").performClick()
            compose.settle()
            val field = compose.onNodeWithTag(NumberEntryTags.FIELD)
            field.performTextReplacement("87.5")
            compose.settle()
            val oldSet = compose.onNodeWithTag(NumberEntryTags.CONFIRM)
                .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            val oldIme = field.fetchSemanticsNode().config[SemanticsActions.OnImeAction].action!!
            val before = inventory()
            val attemptsBefore = read.writes.get()
            failRead()
            compose.runOnUiThread { oldSet(); oldIme() }
            compose.settle()
            field.assertTextContains("87.5")
            field.performTextReplacement("88.5")
            compose.settle()
            field.assertTextContains("88.5")
            field.performTextReplacement("87.5")
            compose.settle()
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsNotEnabled()
            assertEquals(before, inventory())
            assertEquals(attemptsBefore, read.writes.get())
            proveRecovery("bodyweight-draft-stale")
            retry(held = true)
            field.assertTextContains("87.5")
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsNotEnabled()
            read.held!!.complete(Unit)
            await(HomeReadState.CURRENT)
            compose.settle()
            field.assertTextContains("87.5")
            reachable(compose.onNodeWithTag(NumberEntryTags.CONFIRM)).performClick()
            // Real Room and preference writes must finish before closing frames. Advancing
            // the UI clock alone cannot establish completion of worker-thread persistence.
            compose.awaitThat("the one accepted bodyweight save finishes", vm.uiState::value) {
                vm.bodyweightSaved.value && !vm.uiState.value.bodyweightSavePending &&
                    vm.uiState.value.bodyweightSaveError == null &&
                    runBlocking { deps.database.bodyweightDao().getAll().any { it.epochDay == todayEpochDay() && it.kg == 87.5 } }
            }
            compose.settle()
            compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
            val rows = runBlocking { deps.database.bodyweightDao().getAll() }
            assertEquals(1, rows.count { it.epochDay == todayEpochDay() })
            assertEquals(87.5, rows.single { it.epochDay == todayEpochDay() }.kg, 0.0)
            assertEquals(attemptsBefore + 1, read.writes.get())
            assertNull(vm.uiState.value.bodyweightDraftKg)
            assertFalse(vm.bodyweightSaved.value)
        }
    }

    @Test fun bodyweightWriteFailureKeepsExactTextAndRetrySavesOnceAfterTheActualWrite() {
        graph()
        show()
        await(HomeReadState.CURRENT)
        verifyBodyweightWriteFailure()
    }

    @Test fun queuedSetReadsTheLatestAuthoredWeightBeforeAdmission() = verifyQueuedWeightConfirmation(useIme = false)

    @Test fun queuedImeReadsTheLatestAuthoredWeightBeforeAdmission() = verifyQueuedWeightConfirmation(useIme = true)

    private fun verifyQueuedWeightConfirmation(useIme: Boolean) {
        graph()
        show()
        await(HomeReadState.CURRENT)
        scrollTo(HomeTags.BODYWEIGHT_CHECK_IN)
        withNumberClock {
            compose.onNodeWithText("Log weight").performClick()
            compose.settle()
            val field = compose.onNodeWithTag(NumberEntryTags.FIELD)
            field.performTextReplacement("87.5")
            compose.settle()
            val oldEdit = field.fetchSemanticsNode().config[SemanticsActions.SetText].action!!
            val oldConfirm = if (useIme) {
                field.fetchSemanticsNode().config[SemanticsActions.OnImeAction].action!!
            } else {
                compose.onNodeWithTag(NumberEntryTags.CONFIRM)
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            }
            val attemptsBefore = read.writes.get()
            read.heldWrite = CompletableDeferred()
            // No recomposition separates the two queued actions. The confirmation must
            // parse the live field rather than the old 87.5 frame's parsed value.
            compose.runOnUiThread {
                oldEdit(AnnotatedString("88.5"))
                oldConfirm()
            }
            compose.awaitThat("the latest authored weight owns one pending write", vm.uiState::value) {
                vm.uiState.value.bodyweightSavePending && vm.uiState.value.bodyweightDraftKg == 88.5 &&
                    read.writes.get() == attemptsBefore + 1
            }
            compose.settle()
            releaseNumberFocus(stage = if (useIme) "queued-ime-pending" else "queued-set-pending", expectedText = "88.5")
            field.assertTextContains("88.5")
            read.heldWrite!!.complete(Unit)
            compose.awaitThat("the latest authored weight actually persists", vm.uiState::value) {
                !vm.uiState.value.bodyweightSavePending && vm.uiState.value.bodyweightSaveError == null &&
                    runBlocking { deps.database.bodyweightDao().getAll().any { it.epochDay == todayEpochDay() && it.kg == 88.5 } }
            }
            compose.settle()
            compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
            val rows = runBlocking { deps.database.bodyweightDao().getAll() }
            assertEquals(1, rows.count { it.epochDay == todayEpochDay() })
            assertEquals(88.5, rows.single { it.epochDay == todayEpochDay() }.kg, 0.0)
            assertEquals(attemptsBefore + 1, read.writes.get())
        }
    }

    private fun verifyBodyweightWriteFailure() {
        scrollTo(HomeTags.BODYWEIGHT_CHECK_IN)
        withNumberClock {
            compose.onNodeWithText("Log weight").performClick()
            compose.settle()
            val field = compose.onNodeWithTag(NumberEntryTags.FIELD)
            field.performTextReplacement("87.5")
            compose.settle()
            val oldSet = compose.onNodeWithTag(NumberEntryTags.CONFIRM)
                .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            val oldIme = field.fetchSemanticsNode().config[SemanticsActions.OnImeAction].action!!
            val oldCancel = compose.onNodeWithText("Cancel")
                .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            val oldEdit = field.fetchSemanticsNode().config[SemanticsActions.SetText].action!!
            val before = inventory()
            val attemptsBefore = read.writes.get()
            read.heldWrite = CompletableDeferred()
            read.failWrites.set(1)
            compose.runOnUiThread { oldSet(); oldEdit(AnnotatedString("88.5")); oldSet(); oldIme(); oldCancel() }
            compose.awaitThat("one admitted bodyweight write waits", { vm.uiState.value to read.writes.get() }) {
                vm.uiState.value.bodyweightSavePending && read.writes.get() == attemptsBefore + 1
            }
            compose.settle()
            releaseNumberFocus(stage = "bodyweight-guarded-pending", expectedText = "87.5")
            field.assertTextContains("87.5")
            assertEquals("the write's submitted value remains visible despite queued editing", "87.5",
                field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsNotEnabled()
            compose.onNodeWithText("Cancel").assertIsNotEnabled()
            proveWords(
                compose.onNodeWithTag(HomeTags.BODYWEIGHT_SAVING), "Saving weight…", TextSecondary,
                "bodyweight-pending-copy",
            )
            assertTouchTarget(compose.onNodeWithTag(NumberEntryTags.CONFIRM))
            assertTouchTarget(compose.onNodeWithText("Cancel"))
            assertEquals(before, inventory())
            capture("bodyweight-actual-write-pending")
            read.heldWrite!!.complete(Unit)
            compose.awaitThat("the actual DAO failure keeps a retryable draft", vm.uiState::value) {
                !vm.uiState.value.bodyweightSavePending && vm.uiState.value.bodyweightSaveError != null
            }
            settleNumberLayout(stage = "bodyweight-write-failure-layout")
            field.assertTextContains("87.5")
            proveWords(
                compose.onNodeWithTag(HomeTags.BODYWEIGHT_SAVE_ERROR),
                checkNotNull(vm.uiState.value.bodyweightSaveError), Danger,
                "bodyweight-write-failure-copy",
            )
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsEnabled()
            compose.onNodeWithText("Cancel").assertIsEnabled()
            assertEquals(before, inventory())
            assertEquals(attemptsBefore + 1, read.writes.get())
            capture("bodyweight-write-failed-exact-draft")
            read.heldWrite = CompletableDeferred()
            reachable(compose.onNodeWithTag(NumberEntryTags.CONFIRM)).performClick()
            compose.awaitThat("one retry waits on the actual write", { vm.uiState.value to read.writes.get() }) {
                vm.uiState.value.bodyweightSavePending && read.writes.get() == attemptsBefore + 2
            }
            settleNumberLayout(stage = "bodyweight-retry-pending-layout")
            field.assertTextContains("87.5")
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsNotEnabled()
            read.heldWrite!!.complete(Unit)
            compose.awaitThat("actual successful bodyweight commit closes the field", vm.uiState::value) {
                !vm.uiState.value.bodyweightSavePending && vm.uiState.value.bodyweightSaveError == null &&
                    runBlocking { deps.database.bodyweightDao().getAll().any { it.epochDay == todayEpochDay() && it.kg == 87.5 } }
            }
            compose.settle()
            compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
            assertEquals(attemptsBefore + 2, read.writes.get())
            val rows = runBlocking { deps.database.bodyweightDao().getAll() }
            assertEquals(1, rows.count { it.epochDay == todayEpochDay() })
            assertEquals(87.5, rows.single { it.epochDay == todayEpochDay() }.kg, 0.0)
            assertNull(vm.uiState.value.bodyweightDraftKg)
            assertFalse(vm.bodyweightSaved.value)
        }
    }

    @Test fun routineCardioAndExtraKeepTheirPageAndEquipmentWhileReadsRecover() {
        graph()
        show()
        await(HomeReadState.CURRENT)
        scrollTo(HomeTags.START).performClick()
        compose.onNodeWithTag(HomeStartTags.ROUTINE).performClick()
        val oldRoutine = compose.onNodeWithTag(HomeStartTags.routine(routineId))
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val before = inventory()
        failRead()
        compose.runOnUiThread { oldRoutine() }
        compose.onNodeWithTag(HomeStartTags.SHEET).assertExists()
        compose.onNodeWithTag(HomeStartTags.routine(routineId)).assertIsNotEnabled()
        assertEquals(before, inventory())
        proveRecovery("routine-page-stale")
        retry()
        compose.onNodeWithTag(HomeStartTags.routine(routineId)).assertIsEnabled()
        reachable(compose.onNodeWithText("Cancel")).performClick()
        compose.onNodeWithTag(HomeStartTags.CARDIO).performClick()
        val oldCardio = compose.onNodeWithTag(CardioPickTags.card(CardioType.WALK))
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        failRead()
        compose.runOnUiThread { oldCardio() }
        compose.onNodeWithTag(HomeStartTags.CARDIO_PAGE).assertExists()
        compose.onNodeWithTag(CardioPickTags.card(CardioType.WALK)).assertIsNotEnabled()
        assertEquals(before, inventory())
        retry()
        reachable(compose.onNodeWithText("Cancel")).performClick()
        compose.onNodeWithTag(HomeStartTags.EXTRA).performClick()
        compose.onNodeWithTag(ExtraEquipmentTags.choice(ExtraEquipment.NONE)).performClick()
        val pack = AuxiliaryPacks.visibleFor(ExtraEquipment.NONE, emptySet()).first()
        val oldExtra = compose.onNodeWithTag(AuxiliaryPackTags.row(pack.id))
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        failRead()
        compose.runOnUiThread { oldExtra() }
        compose.onNodeWithTag(ExtraEquipmentTags.PAGE).assertDoesNotExist()
        compose.onNodeWithTag(AuxiliaryPackTags.row(pack.id)).assertIsNotEnabled()
        assertEquals(before, inventory())
        proveRecovery("extra-equipment-choice-retained")
        retry(held = true)
        compose.onNodeWithTag(AuxiliaryPackTags.row(pack.id)).assertIsNotEnabled()
        read.held!!.complete(Unit)
        await(HomeReadState.CURRENT)
        compose.onNodeWithTag(ExtraEquipmentTags.PAGE).assertDoesNotExist()
        compose.onNodeWithTag(AuxiliaryPackTags.row(pack.id)).assertIsEnabled()
        // Navigation back to equipment stays usable even while the final pick is disabled.
        failRead()
        reachable(compose.onNodeWithText("Cancel")).performClick()
        compose.onNodeWithTag(ExtraEquipmentTags.PAGE).assertExists()
        compose.onNodeWithTag(ExtraEquipmentTags.choice(ExtraEquipment.FREE_WEIGHTS)).assertIsEnabled().performClick()
        assertEquals(before, inventory())
        assertNull(openedSession)
    }

    @Test fun freeStartFromAnOldEnabledFrameDoesNotCloseTheStartSheet() {
        graph()
        show()
        await(HomeReadState.CURRENT)
        scrollTo(HomeTags.START).performClick()
        val oldFree = compose.onNodeWithTag(HomeStartTags.FREE)
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val before = inventory()
        failRead()
        compose.runOnUiThread { oldFree() }
        compose.onNodeWithTag(HomeStartTags.SHEET).assertExists()
        compose.onNodeWithTag(HomeStartTags.FREE).assertIsNotEnabled()
        compose.onNodeWithTag(HomeStartTags.ROUTINE).assertIsEnabled()
        compose.onNodeWithTag(HomeStartTags.CARDIO).assertIsEnabled()
        compose.onNodeWithTag(HomeStartTags.EXTRA).assertIsEnabled()
        assertEquals(before, inventory())
        assertNull(openedSession)
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun fallbackConfirmationFromAnOldEnabledFrameStaysOpenUntilAnAcceptedStart() {
        graph()
        val weekStart = CivilDate.fromEpochDay(todayEpochDay()).previousOrSame(Weekday.MONDAY).epochDay
        val plan = restWeek(weekStart).let { empty ->
            empty.copy(days = empty.days.map { day ->
                if (day.epochDay != todayEpochDay()) day else day.copy(
                    isRest = false, focusKind = SessionFocusKind.UPPER, focusTitle = "Upper",
                    routineId = routineId, routineName = "Upper B",
                )
            })
        }
        runBlocking { deps.database.plannerDao().deleteAllOccurrences() }
        insights.value = insights.value.copy(weekPlan = plan)
        val restoration = StateRestorationTester(compose)
        show(restoration)
        compose.awaitThat("the actual empty-agenda leftover", vm.uiState::value) {
            vm.uiState.value.readState == HomeReadState.CURRENT && vm.uiState.value.occurrences.isEmpty() && vm.uiState.value.weekPlan == plan
        }
        addVirtualizationBoundary()
        scrollTo(HomeTags.SESSION).performClick()
        val oldConfirm = compose.onNodeWithTag(ConfirmActionTags.CONFIRM)
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val before = inventory()
        failRead()
        scrollBoardBy(amount = -100_000f)
        compose.onNodeWithTag(HomeTags.SESSION).assertDoesNotExist()
        compose.runOnUiThread { oldConfirm() }
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsNotEnabled()
        assertEquals(before, inventory())
        assertNull(openedSession)
        retry(held = true)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsNotEnabled()
        compose.onNodeWithText("Start Upper B?").assertExists()
        assertEquals(before, inventory())
        read.held!!.complete(Unit)
        await(HomeReadState.CURRENT)
        reachable(compose.onNodeWithTag(ConfirmActionTags.CONFIRM)).assertIsEnabled().performClick()
        compose.awaitThat("the deliberately accepted fallback start", { openedSession }) { openedSession != null }
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertDoesNotExist()
        val session = runBlocking { checkNotNull(deps.workoutRepository.getSession(checkNotNull(openedSession))) }
        assertEquals(routineId, session.routineId)
        assertEquals(1, runBlocking { deps.database.workoutDao().getAllSessions().size })
    }

    @Test fun queuedDaySelectionCannotRetargetTheOpenFallbackConfirmation() {
        graph()
        val secondRoutine = createSecondFallbackRoutine()
        val weekStart = CivilDate.fromEpochDay(todayEpochDay()).previousOrSame(Weekday.MONDAY).epochDay
        val secondDay = if (todayEpochDay() < weekStart + 6) todayEpochDay() + 1 else todayEpochDay() - 1
        val plan = fallbackPlan(
            targets = mapOf(todayEpochDay() to (routineId to "Upper B"), secondDay to (secondRoutine to "Lower B")),
        )
        runBlocking { deps.database.plannerDao().deleteAllOccurrences() }
        insights.value = insights.value.copy(weekPlan = plan)
        show()
        awaitFallbackPlan(plan = plan)
        val oldSecondCell = compose.onNodeWithTag(WeekStripTags.cell(secondDay))
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        scrollTo(HomeTags.SESSION).performClick()
        val oldConfirm = compose.onNodeWithTag(ConfirmActionTags.CONFIRM)
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val before = inventory()
        compose.runOnUiThread { oldSecondCell() }
        compose.settle()
        compose.onNodeWithText("Start Upper B?").assertExists()
        compose.onNodeWithText("Start Lower B?").assertDoesNotExist()
        assertEquals(before, inventory())
        assertNull(openedSession)
        // The callback belongs to the still-open original target, regardless of the cell
        // selected behind it. Confirming it must save Upper B, never the distinct Lower B.
        compose.runOnUiThread { oldConfirm() }
        compose.awaitThat("the original fallback target starts", { openedSession }) { openedSession != null }
        val saved = runBlocking { checkNotNull(deps.workoutRepository.getSession(checkNotNull(openedSession))) }
        assertEquals(routineId, saved.routineId)
        assertEquals(1, runBlocking { deps.database.workoutDao().getAllSessions().size })
        assertEquals(0, runBlocking { deps.database.workoutDao().getAllSessions().count { it.routineId == secondRoutine } })
    }

    @Test fun healthySameDayReplacementInvalidatesFallbackAndRejectsItsQueuedConfirmation() {
        graph()
        val secondRoutine = createSecondFallbackRoutine()
        val original = fallbackPlan(targets = mapOf(todayEpochDay() to (routineId to "Upper B")))
        val replacement = fallbackPlan(targets = mapOf(todayEpochDay() to (secondRoutine to "Lower B")))
        runBlocking { deps.database.plannerDao().deleteAllOccurrences() }
        insights.value = insights.value.copy(weekPlan = original)
        show()
        awaitFallbackPlan(plan = original)
        scrollTo(HomeTags.SESSION).performClick()
        val oldConfirm = compose.onNodeWithTag(ConfirmActionTags.CONFIRM)
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val before = inventory()
        compose.mainClock.autoAdvance = false
        try {
            insights.value = insights.value.copy(weekPlan = replacement)
            // Hold Compose frames while the real VM receives the new healthy board. This
            // exercises the captured old callback before its owner's invalidation effect.
            compose.awaitThat("the healthy replacement reaches Home", vm.uiState::value) {
                vm.uiState.value.readState == HomeReadState.CURRENT && vm.uiState.value.weekPlan == replacement
            }
            compose.runOnUiThread { oldConfirm() }
            compose.settle()
            assertNull(openedSession)
            assertEquals(before, inventory())
            compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertDoesNotExist()
            scrollTo(HomeTags.SESSION).performClick()
            compose.settle()
            compose.onNodeWithText("Start Lower B?").assertExists()
            // A replaced owner's callback cannot dismiss or admit the new confirmation.
            compose.runOnUiThread { oldConfirm() }
            compose.settle()
            compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsEnabled()
            compose.onNodeWithText("Start Lower B?").assertExists()
            assertNull(openedSession)
            assertEquals(before, inventory())
            reachable(compose.onNodeWithTag(ConfirmActionTags.CONFIRM)).performClick()
            compose.awaitThat(
                what = "the newly reviewed replacement commits and requests navigation",
                now = vm.navigateToSession::value,
            ) { vm.navigateToSession.value != null }
            val committedId = checkNotNull(vm.navigateToSession.value)
            val saved = runBlocking { checkNotNull(deps.workoutRepository.getSession(committedId)) }
            assertEquals(secondRoutine, saved.routineId)
            assertEquals(1, runBlocking { deps.database.workoutDao().getAllSessions().size })
            assertEquals(0, runBlocking { deps.database.workoutDao().getAllSessions().count { it.routineId == routineId } })
            // Database work uses the real repository; Home's collected navigation value
            // and LaunchedEffect require a frame even when the deliberately held clock
            // has allowed the VM to commit already.
            compose.settle()
            compose.awaitThat("Home delivers the committed replacement session", { openedSession }) { openedSession == committedId }
            assertNull(vm.navigateToSession.value)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun cancelledPlannedOwnerCannotConfirmOrDismissReopenedOrDifferentPlannedChoices() {
        graph()
        val secondRoutine = createSecondFallbackRoutine()
        val secondOccurrence = runBlocking {
            val first = checkNotNull(deps.plannerRepository.getOccurrence(occurrenceId))
            val rule = checkNotNull(deps.plannerRepository.getRule(first.ruleId))
            val secondRuleId = "home-read-second-planned-rule"
            deps.plannerRepository.upsertRule(rule.copy(id = secondRuleId, routineId = secondRoutine, hour = 19))
            val week = CivilDate.fromEpochDay(todayEpochDay()).previousOrSame(Weekday.MONDAY)
            deps.plannerRepository.ensureWeek(week)
            deps.plannerRepository.occurrencesBetween(todayEpochDay(), todayEpochDay()).single { it.ruleId == secondRuleId }.id
        }
        show()
        compose.awaitThat("two actual planned workout targets", vm.uiState::value) {
            vm.uiState.value.readState == HomeReadState.CURRENT &&
                vm.uiState.value.occurrences.any { it.id == occurrenceId } &&
                vm.uiState.value.occurrences.any { it.id == secondOccurrence }
        }
        scrollTo(HomeTags.agendaRow(occurrenceId)).performClick()
        val oldConfirm = compose.onNodeWithTag(ConfirmActionTags.CONFIRM)
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val oldDismiss = compose.onNodeWithText("Cancel")
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val before = inventory()
        reachable(compose.onNodeWithText("Cancel")).performClick()
        // Reopening the same occurrence creates another modal owner. Its ID alone cannot
        // authorize the old owner's queued confirmation or cancellation.
        scrollTo(HomeTags.agendaRow(occurrenceId)).performClick()
        compose.runOnUiThread { oldConfirm(); oldDismiss() }
        compose.settle()
        compose.onNodeWithText("Start Upper B?").assertExists()
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsEnabled()
        assertNull(openedSession)
        assertEquals(before, inventory())
        reachable(compose.onNodeWithText("Cancel")).performClick()
        scrollTo(HomeTags.agendaRow(secondOccurrence)).performClick()
        compose.runOnUiThread { oldConfirm(); oldDismiss() }
        compose.settle()
        compose.onNodeWithText("Start Lower B?").assertExists()
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsEnabled()
        assertNull(openedSession)
        assertEquals(before, inventory())
        reachable(compose.onNodeWithTag(ConfirmActionTags.CONFIRM)).performClick()
        compose.awaitThat("the current planned owner starts its own workout", { openedSession }) { openedSession != null }
        val saved = runBlocking { checkNotNull(deps.workoutRepository.getSession(checkNotNull(openedSession))) }
        assertEquals(secondRoutine, saved.routineId)
        assertEquals(secondOccurrence, runBlocking { PendingOccurrence.followedBy(deps, saved.id) })
        assertNull(runBlocking { PendingOccurrence.followedBy(deps, "synthetic-unrelated-session") })
        assertEquals(1, runBlocking { deps.database.workoutDao().getAllSessions().size })
        assertEquals(0, runBlocking { deps.database.workoutDao().getAllSessions().count { it.routineId == routineId } })
    }

    @Test fun staleUndoKeepsItsOfferInsteadOfRunningTheDwellTimer() {
        graph()
        runBlocking {
            val original = checkNotNull(deps.database.plannerDao().getOccurrence(occurrenceId))
            deps.database.plannerDao().upsertOccurrence(original.copy(localEpochDay = todayEpochDay() - 1))
        }
        show()
        compose.awaitThat("the actual leftover row", vm.uiState::value) {
            vm.uiState.value.readState == HomeReadState.CURRENT && vm.uiState.value.occurrences.any { it.id == occurrenceId && it.localEpochDay == todayEpochDay() - 1 }
        }
        val skip = scrollTo(HomeTags.skipRow(occurrenceId))
        compose.mainClock.autoAdvance = false
        try {
            skip.performClick()
            compose.awaitThat("Skip saved and offers the same row", { vm.uiState.value to vm.skippedDay.value }) {
                vm.skippedDay.value?.occurrenceId == occurrenceId && vm.uiState.value.occurrences.any { it.id == occurrenceId && it.status == OccurrenceStatus.SKIPPED }
            }
            val before = inventory()
            failRead()
            compose.settle()
            scrollTo(HomeTags.SKIP_UNDO)
            compose.onNodeWithText("Undo").assertIsNotEnabled()
            dispatcher.scheduler.advanceTimeBy(Motion.STATUS_DWELL_MS.toLong() + 1L)
            dispatcher.scheduler.runCurrent()
            compose.settle()
            assertEquals("stale readout does not expire the durable undo offer", occurrenceId, vm.skippedDay.value?.occurrenceId)
            assertEquals(before, inventory())
            retry()
            scrollTo(HomeTags.SKIP_UNDO)
            compose.onNodeWithText("Undo").assertIsEnabled()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun plannedConfirmationSurvivesBoardVirtualizationAndRestoreDuringRetry() {
        graph()
        val restoration = StateRestorationTester(compose)
        show(restoration)
        await(HomeReadState.CURRENT)
        addVirtualizationBoundary()
        scrollTo(HomeTags.agendaRow(occurrenceId)).performClick()
        val oldConfirm = compose.onNodeWithTag(ConfirmActionTags.CONFIRM)
            .assertIsEnabled().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val before = inventory()
        failRead()
        // This deliberately removes the owner row from the measured lazy composition.
        // A stable item key alone is insufficient: the confirmation must live above it.
        scrollBoardBy(amount = -100_000f)
        compose.onNodeWithTag(HomeTags.agendaRow(occurrenceId)).assertDoesNotExist()
        compose.runOnUiThread { oldConfirm() }
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsNotEnabled()
        assertEquals(before, inventory())
        assertNull(openedSession)
        retry(held = true)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsNotEnabled()
        assertEquals(before, inventory())
        read.held!!.complete(Unit)
        await(HomeReadState.CURRENT)
        reachable(compose.onNodeWithTag(ConfirmActionTags.CONFIRM)).assertIsEnabled().performClick()
        compose.awaitThat("the deliberate recovered planned start", { openedSession }) { openedSession != null }
        val saved = runBlocking { checkNotNull(deps.workoutRepository.getSession(checkNotNull(openedSession))) }
        assertEquals(routineId, saved.routineId)
        assertEquals(1, runBlocking { deps.database.workoutDao().getAllSessions().size })
    }

    @Test fun unsubmittedNumericTextSurvivesRestorationDuringReadFailureAndHeldRetry() {
        graph()
        val restoration = StateRestorationTester(compose)
        show(restoration)
        await(HomeReadState.CURRENT)
        scrollTo(HomeTags.BODYWEIGHT_CHECK_IN)
        withNumberClock {
            compose.onNodeWithText("Log weight").performClick()
            compose.settle()
            val field = compose.onNodeWithTag(NumberEntryTags.FIELD)
            field.performTextReplacement("87.50")
            compose.settle()
            val before = inventory()
            val writesBefore = read.writes.get()
            failRead()
            restoration.emulateSavedInstanceStateRestore()
            compose.settle()
            field.assertTextContains("87.50")
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsNotEnabled()
            assertNull(vm.uiState.value.bodyweightDraftKg)
            assertEquals(before, inventory())
            retry(held = true)
            restoration.emulateSavedInstanceStateRestore()
            compose.settle()
            field.assertTextContains("87.50")
            compose.onNodeWithTag(NumberEntryTags.CONFIRM).assertIsNotEnabled()
            assertEquals(writesBefore, read.writes.get())
            assertEquals(before, inventory())
            read.held!!.complete(Unit)
            await(HomeReadState.CURRENT)
            field.assertTextContains("87.50")
            // Recovery did not submit the draft. A deliberate Set persists one numeric value.
            reachable(compose.onNodeWithTag(NumberEntryTags.CONFIRM)).performClick()
            compose.awaitThat("the restored authored weight commits once", vm.uiState::value) {
                vm.bodyweightSaved.value && !vm.uiState.value.bodyweightSavePending &&
                    vm.uiState.value.bodyweightSaveError == null &&
                    runBlocking { deps.database.bodyweightDao().getAll().any { it.epochDay == todayEpochDay() && it.kg == 87.5 } }
            }
            compose.settle()
            compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
            assertEquals(writesBefore + 1, read.writes.get())
            assertEquals(1, runBlocking { deps.database.bodyweightDao().getAll().count { it.epochDay == todayEpochDay() } })
        }
    }

    @Test fun selectedDayAndOpenExtraDraftSurviveRestorationDuringRetry() {
        localToday = LocalDate.of(2026, 9, 1).toEpochDay()
        val firstDay = LocalDate.of(2026, 8, 31).toEpochDay()
        graph()
        insights.value = insights.value.copy(weekPlan = restWeek(firstDay))
        val restoration = StateRestorationTester(compose)
        show(restoration)
        await(HomeReadState.CURRENT)
        compose.onNodeWithTag(WeekStripTags.cell(firstDay)).performClick()
        compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertExists()
        scrollTo(HomeTags.START).performClick()
        compose.onNodeWithTag(HomeStartTags.EXTRA).performClick()
        compose.onNodeWithTag(ExtraEquipmentTags.choice(ExtraEquipment.NONE)).performClick()
        val pack = AuxiliaryPacks.visibleFor(ExtraEquipment.NONE, emptySet()).first()
        failRead()
        retry(held = true)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(HomeStartTags.EXTRA_PAGE).assertExists()
        compose.onNodeWithTag(ExtraEquipmentTags.PAGE).assertDoesNotExist()
        compose.onNodeWithTag(AuxiliaryPackTags.row(pack.id)).assertIsNotEnabled()
        read.held!!.complete(Unit)
        await(HomeReadState.CURRENT)
        reachable(compose.onNodeWithText("Cancel")).performClick()
        reachable(compose.onNodeWithText("Cancel")).performClick()
        // Sheet dismissal through its real modal dismiss action leaves the chosen cell intact.
        compose.runOnUiThread { checkNotNull(ShadowDialog.getLatestDialog()).onBackPressed() }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag(WeekStripTags.cell(firstDay)).fetchSemanticsNode().config[SemanticsProperties.Selected])
        compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertExists()
    }

    @Test @Config(qualifiers = "ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlAndReducedMotionKeepRecoveryAndReadOnlyNavigationAvailable() {
        rtl = true
        reducedMotion = true
        profile = "360x640-rtl-font20-reduced-motion"
        graph()
        show()
        await(HomeReadState.CURRENT)
        failRead()
        proveRecovery("stale-rtl")
        compose.onNodeWithTag(WeekStripTags.cell(vm.uiState.value.weekStartEpochDay)).performClick()
        compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertIsEnabled().performClick()
        scrollTo(HomeTags.START).assertIsEnabled().performClick()
        compose.onNodeWithTag(HomeStartTags.FREE).assertIsNotEnabled()
        compose.onNodeWithTag(HomeStartTags.EXTRA).assertIsEnabled().performClick()
        compose.onNodeWithTag(ExtraEquipmentTags.choice(ExtraEquipment.MIXED)).assertIsEnabled()
        proveRecovery("sheet-rtl")
    }

    private fun graph(initialFailure: Boolean = false) {
        read.fail.value = initialFailure
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), insights = insights, scheduler = dispatcher,
            bodyweightDaoDecorator = { read.decorate(it) },
        )
        runBlocking {
            deps.preferencesRepository.setOnboardingComplete(true)
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            deps.preferencesRepository.setBodyweightCheckInWeekday(Weekday.fromEpochDay(todayEpochDay()))
            deps.preferencesRepository.recordBodyweight(kg = 70.0, epochDay = todayEpochDay() - 8)
            val routine = deps.routineRepository.create("Upper B")
            val lift = insertTestExercise(deps, "home-read-bench", "Incline Dumbbell Bench Press")
            deps.routineRepository.addExercise(routine.id, lift, 3, 8, 27.5, 120)
            routineId = routine.id
            deps.scheduleRepository.pin(routine.id, null, Weekday.fromEpochDay(todayEpochDay()))
            deps.plannerRepository.importSlotsIfNeeded(1_791_446_400_000L)
            val week = CivilDate.fromEpochDay(todayEpochDay()).previousOrSame(Weekday.MONDAY)
            deps.plannerRepository.ensureWeek(week)
            occurrenceId = deps.plannerRepository.occurrencesBetween(todayEpochDay(), todayEpochDay()).single().id
            insights.value = TrainingInsights(routines = deps.routineRepository.observeAll().first())
        }
        vm = HomeViewModel(ApplicationProvider.getApplicationContext(), deps).also(models::add)
    }

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(
                LocalWeightUnit provides WeightUnit.KG,
                LocalTodayEpochDay provides localToday,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides reducedMotion,
            ) {
                PersonalTrainerTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Pit) {
                        Box(Modifier.fillMaxSize()) {
                            HomeScreen(onResumeWorkout = { openedSession = it }, onOpenPlan = {}, viewModel = vm)
                        }
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun await(expected: HomeReadState) {
        compose.awaitThat("Home required reads are $expected", vm.uiState::value) {
            vm.uiState.value.readState == expected && (expected != HomeReadState.CURRENT || !vm.uiState.value.retryPending)
        }
        compose.settle()
    }

    private fun failRead() {
        read.fail.value = true
        await(HomeReadState.STALE)
    }

    private fun retry(held: Boolean = false) {
        read.fail.value = false
        read.held = if (held) CompletableDeferred() else null
        val before = read.collections.get()
        scrollBoardRecoveryIntoComposition(HomeTags.RETRY)
        reachable(recoveryNode(HomeTags.RETRY)).assertIsEnabled().performClick()
        compose.awaitThat("a fresh required DAO collection", read.collections::get) { read.collections.get() > before }
        if (!held) await(HomeReadState.CURRENT) else compose.settle()
    }

    private fun scrollTo(tag: String): SemanticsNodeInteraction {
        scrollBoardBy(amount = -100_000f)
        repeat(40) {
            if (compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) {
                return reachable(compose.onNodeWithTag(tag))
            }
            val board = compose.onNodeWithTag(HomeTags.BOARD).fetchSemanticsNode()
            val axis = checkNotNull(board.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
            if (axis.value() >= axis.maxValue()) throw AssertionError("$profile board has no $tag before its measured end")
            scrollBoardBy(amount = board.boundsInWindow.height * .75f)
        }
        throw AssertionError("$profile did not compose $tag within 40 real frame-bounded scrolls")
    }

    private fun scrollBoardBy(amount: Float) {
        val scroll = compose.onNodeWithTag(HomeTags.BOARD).fetchSemanticsNode()
            .config[SemanticsActions.ScrollBy].action!!
        assertTrue("the actual Home board accepts a scroll action", compose.runOnUiThread { scroll(0f, amount) })
        compose.settle()
    }

    private fun addVirtualizationBoundary() {
        // A real Room lookup for a missing reminder creates Home's existing independent
        // action-error item. The board then lies beyond the lazy prefetched neighbor;
        // absence asserts disposal rather than merely an offscreen cached row.
        vm.reviewOccurrence("synthetic-missing-reminder-for-virtualization")
        compose.awaitThat("a real missing-reminder read creates the independent Home error item", vm.uiState::value) {
            vm.uiState.value.readState == HomeReadState.CURRENT && vm.uiState.value.error != null
        }
        compose.settle()
    }

    private fun proveRecovery(stage: String) {
        val modal = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } != null
        val problem = if (modal) {
            recoveryNode(HomeTags.READ_PROBLEM)
        } else {
            scrollBoardRecoveryIntoComposition(HomeTags.READ_PROBLEM)
            recoveryNode(HomeTags.READ_PROBLEM)
        }
        val words = checkNotNull(vm.uiState.value.readProblem)
        proveWords(problem, words, TextPrimary, stage)
        val retry = reachable(recoveryNode(HomeTags.RETRY)).assertIsEnabled()
        val node = retry.fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertTouchTarget(retry)
        capture(stage)
    }

    private fun proveWords(node: SemanticsNodeInteraction, words: String, ink: Color, stage: String) {
        reachable(node)
        val target = node.fetchSemanticsNode()
        val layout = node.textLayout()
        assertEquals(words, layout.layoutInput.text.text)
        assertEquals(words.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        assertEquals(RuntimeEnvironment.getApplication().resources.configuration.fontScale, target.layoutInfo.density.fontScale, .001f)
        val frame = drawCurrentWindow()
        try {
            val visible = target.boundsInWindow.intersect(HomeReachRect(0f, 0f, frame.width.toFloat(), frame.height.toFloat()))
            val position = target.positionInWindow
            assertTrue("$stage full warning text fits measured target", layout.size.height <= visible.height + 1f)
            repeat(layout.lineCount) { line ->
                assertFalse("$stage warning line $line is complete", layout.isLineEllipsized(line))
                val glyphs = HomeReachRect(
                    position.x + layout.getLineLeft(line), position.y + layout.getLineTop(line),
                    position.x + layout.getLineRight(line), position.y + layout.getLineBottom(line),
                )
                assertTrue("$stage warning line $line stays in its real viewport", glyphs.top >= visible.top - 1f && glyphs.bottom <= visible.bottom + 1f)
                assertTrue("$stage warning line $line draws actual glyph ink", frame.count(glyphs.intersect(visible), ink) >= 5)
            }
        } finally { frame.recycle() }
    }

    private fun assertTouchTarget(interaction: SemanticsNodeInteraction) {
        val node = reachable(interaction).fetchSemanticsNode()
        val height = node.boundsInWindow.height / node.layoutInfo.density.density
        val width = node.boundsInWindow.width / node.layoutInfo.density.density
        assertTrue("full real 48 dp action target: measured ${width}×${height}dp at ${node.boundsInWindow}", height >= Metrics.touchMin.value - .5f)
        assertTrue("full real 48 dp action target width: measured ${width}×${height}dp at ${node.boundsInWindow}", width >= Metrics.touchMin.value - .5f)
    }

    /** Real ScrollBy actions with measured bounds and explicit frames; no offscreen click. */
    private fun reachable(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        compose.settle()
        repeat(25) { step ->
            val target = node.fetchSemanticsNode()
            val decor = currentDecor()
            val window = HomeReachRect(0f, 0f, decor.width.toFloat(), decor.height.toFloat())
            val visible = target.boundsInWindow.intersect(window)
            if (visible.width >= target.size.width - 1f && visible.height >= target.size.height - 1f) {
                return node.assertIsDisplayed()
            }
            val position = target.positionInWindow
            val full = HomeReachRect(position.x, position.y, position.x + target.size.width, position.y + target.size.height)
            var parent = target.parent
            var moved = false
            while (parent != null) {
                val candidate = parent
                val axis = candidate.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                val scroll = candidate.config.getOrNull(SemanticsActions.ScrollBy)?.action
                if (axis != null && scroll != null) {
                    val viewport = candidate.boundsInWindow.intersect(window)
                    val delta = when {
                        full.top < viewport.top - 1f -> full.top - viewport.top
                        full.bottom > viewport.bottom + 1f -> full.bottom - viewport.bottom
                        else -> 0f
                    }
                    val amount = if (axis.reverseScrolling) -delta else delta
                    if (amount < -1f && axis.value() > 0f || amount > 1f && axis.value() < axis.maxValue()) {
                        if (compose.runOnUiThread { scroll(0f, amount) }) {
                            compose.settle()
                            moved = true
                            break
                        }
                    }
                }
                parent = candidate.parent
            }
            if (!moved || step == 24) {
                capture("unreachable-${target.id}")
                throw AssertionError("$profile cannot reach full target ${target.config}: $full clipped $visible window $window")
            }
        }
        error("bounded reach failed")
    }

    private fun currentDecor(): View = compose.runOnUiThread {
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
    }

    private fun drawCurrentWindow(): Bitmap = compose.runOnIdle {
        val decor = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
            ?: compose.activity.window.decorView
        check(decor.width > 1 && decor.height > 1)
        Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
    }

    private fun recoveryNode(tag: String): SemanticsNodeInteraction {
        val dialogOpen = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } != null
        val selector = if (dialogOpen) hasTestTag(tag) and hasAnyAncestor(isDialog()) else hasTestTag(tag)
        return compose.onNode(selector)
    }

    private fun scrollBoardRecoveryIntoComposition(tag: String) {
        val modal = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } != null
        if (!modal && compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()) {
            scrollTo(tag)
        }
    }

    /** Keep the primary assertion if cleanup also encounters an idling failure. */
    private fun withNumberClock(block: () -> Unit) {
        compose.mainClock.autoAdvance = false
        var primary: Throwable? = null
        try {
            block()
        } catch (failure: Throwable) {
            primary = failure
            try { captureRawWindow(stage = "numeric-primary-failure", details = failure.stackTraceToString()) }
            catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
            throw failure
        } finally {
            try { closeNumberForCleanup() }
            catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
        }
    }

    private fun releaseNumberFocus(stage: String, expectedText: String) {
        // Preserve the focused native window before applying the same explicit host-focus
        // policy used by NotesTruthMatrix. This is JVM layout evidence, not phone IME proof.
        val stateBefore = vm.uiState.value
        val writesBefore = read.writes.get()
        val timeBefore = dispatcher.scheduler.currentTime
        captureRawWindow(stage = stage, details = "expectedText=$expectedText\nwrites=$writesBefore\nvirtualTime=$timeBefore\nstate=$stateBefore")
        compose.runOnUiThread {
            val decor = checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
            val group = decor as ViewGroup
            val focused = checkNotNull(decor.findFocus()) { "$stage has no focused native Compose host" }
            val policy = group.descendantFocusability
            try {
                group.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                focused.clearFocus()
            } finally { group.descendantFocusability = policy }
        }
        compose.settle()
        compose.onNodeWithTag(NumberEntryTags.FIELD).assertIsNotFocused().assertTextContains(expectedText)
        assertEquals("focus release preserves the actual draft and save state", stateBefore, vm.uiState.value)
        assertEquals("focus release starts no DAO write", writesBefore, read.writes.get())
        assertEquals("focus release advances no VM time", timeBefore, dispatcher.scheduler.currentTime)
    }

    private data class NumberLayoutProbe(val nativeRequested: Boolean, val composePending: Boolean, val report: String)

    /** Bounded real root traversal for a held-clock modal resize, without idle polling. */
    private fun settleNumberLayout(stage: String) {
        val trace = StringBuilder()
        val stateBefore = vm.uiState.value
        val writesBefore = read.writes.get()
        val timeBefore = dispatcher.scheduler.currentTime
        repeat(6) { round ->
            compose.settle()
            val probe = compose.runOnUiThread {
                val decor = checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
                val roots = mutableListOf<View>()
                fun visit(view: View) {
                    if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") roots += view
                    if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(decor)
                check(roots.isNotEmpty()) { "$stage contains no actual Compose view root" }
                fun pending(root: View): Boolean {
                    val getter = root.javaClass.methods.single { it.name == "getHasPendingMeasureOrLayout" && it.parameterCount == 0 }
                    return getter.invoke(root) as Boolean
                }
                val beforeFlags = roots.map(::pending)
                // Installed Compose 1.11.4 includes out-of-frame work in its pending
                // getter. Frame advancement alone does not drain that work on this
                // native JVM host. This public RootForTest traversal uses the root's
                // real measured constraints and executes its queued work, as normal
                // draw traversal does; it neither clears flags nor supplies fake sizes.
                roots.forEach { root ->
                    val traversal = root.javaClass.methods.single { it.name == "measureAndLayoutForTest" && it.parameterCount == 0 }
                    traversal.invoke(root)
                }
                val rootFlags = roots.map(::pending)
                NumberLayoutProbe(
                    nativeRequested = decor.isLayoutRequested,
                    composePending = rootFlags.any { it },
                    report = "round=$round explicitFrames=${(round + 1) * 20} nativeRequested=${decor.isLayoutRequested} " +
                        "beforeTraversal=$beforeFlags composePending=$rootFlags window=${decor.width}x${decor.height} focus=${decor.findFocus()?.javaClass?.name}",
                )
            }
            trace.appendLine(probe.report)
            assertEquals("real layout traversal preserves the actual draft and save state", stateBefore, vm.uiState.value)
            assertEquals("real layout traversal starts no DAO write", writesBefore, read.writes.get())
            assertEquals("real layout traversal advances no VM time", timeBefore, dispatcher.scheduler.currentTime)
            if (!probe.nativeRequested && !probe.composePending) {
                captureRawWindow(stage = stage, details = "$trace\nwrites=${read.writes.get()}\nvirtualTime=${dispatcher.scheduler.currentTime}\nstate=${vm.uiState.value}")
                return
            }
        }
        captureRawWindow(stage = stage, details = "$trace\nwrites=${read.writes.get()}\nvirtualTime=${dispatcher.scheduler.currentTime}\nstate=${vm.uiState.value}")
        throw AssertionError("$profile $stage still requests native/Compose layout after 120 explicit frames:\n$trace")
    }

    /** Bypasses Compose idle queries so a primary idling failure cannot erase its evidence. */
    private fun captureRawWindow(stage: String, details: String) {
        compose.runOnUiThread {
            val decor = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
                ?: compose.activity.window.decorView
            val directory = File("build/screen-renders/home-read-recovery/$runId/$profile")
            check(directory.isDirectory || directory.mkdirs())
            val focused = decor.findFocus()
            directory.resolve("$stage.txt").writeText(
                "nativeFocus=${focused?.javaClass?.name}\nhasFocus=${focused?.hasFocus()}\n" +
                    "windowFocus=${decor.hasWindowFocus()}\nlayoutRequested=${decor.isLayoutRequested}\n" +
                    "width=${decor.width}\nheight=${decor.height}\nautoAdvance=${compose.mainClock.autoAdvance}\n$details",
            )
            check(decor.width > 1 && decor.height > 1)
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            try {
                decor.draw(Canvas(bitmap))
                directory.resolve("$stage.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }

    private fun closeNumberForCleanup() {
        read.heldWrite?.complete(Unit)
        compose.runOnUiThread { ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.onBackPressed() }
        compose.settle()
        // An assertion during a deliberately held write may leave the guarded dialog up.
        // Its cursor must not make failure cleanup chase endless animation frames.
        compose.mainClock.autoAdvance = compose.runOnUiThread { ShadowDialog.getLatestDialog()?.isShowing != true }
    }

    private fun capture(stage: String) {
        compose.settle()
        val frame = drawCurrentWindow()
        try {
            val directory = File("build/screen-renders/home-read-recovery/$runId/$profile")
            check(directory.isDirectory || directory.mkdirs())
            val file = directory.resolve("$stage.png")
            file.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("measured native window frame saved", file.length() > 100)
        } finally { frame.recycle() }
    }

    private fun inventory(): List<Any?> = runBlocking {
        listOf(
            deps.database.workoutDao().getAllSessions(), deps.database.workoutDao().getAllSessionExercises(),
            deps.database.workoutDao().getAllSets(), deps.database.bodyweightDao().getAll(),
            deps.database.plannerDao().getAllOccurrences(), deps.database.routineDao().getAllRoutines(),
            deps.database.routineDao().getAllRoutineExercises(), deps.database.activityDao().getAllGraphs(),
            deps.rawPreferenceValues(), deps.pendingOccurrenceId.value, deps.restTimerStore.current(),
        )
    }

    private fun restWeek(start: Long) = WeeklySchedulePlan(
        weekStartEpochDay = start, generatedAtMs = 1L, preferences = SchedulePreferences(),
        resolvedSplit = SplitStyle.FULL_BODY, thinHistory = true, summary = "Synthetic cross-month week",
        days = (0L..6L).map { delta ->
            SuggestedTrainingDay(
                epochDay = start + delta, dayOfWeek = Weekday.fromEpochDay(start + delta), isRest = true,
                focusKind = SessionFocusKind.RECOVERY, focusTitle = "Rest", routineId = null,
                routineName = null, reason = "Synthetic fixture", emphasisMuscles = emptyList(),
                confidence = ScheduleConfidence.HIGH,
            )
        },
    )

    private fun createSecondFallbackRoutine(): String = runBlocking {
        val routine = deps.routineRepository.create("Lower B")
        val lift = insertTestExercise(deps, "home-read-squat", "Goblet Squat")
        deps.routineRepository.addExercise(routine.id, lift, 2, 10, 20.0, 90)
        insights.value = insights.value.copy(routines = deps.routineRepository.observeAll().first())
        routine.id
    }

    private fun fallbackPlan(targets: Map<Long, Pair<String, String>>): WeeklySchedulePlan {
        val weekStart = CivilDate.fromEpochDay(todayEpochDay()).previousOrSame(Weekday.MONDAY).epochDay
        return restWeek(weekStart).let { empty ->
            empty.copy(days = empty.days.map { day ->
                val target = targets[day.epochDay]
                if (target == null) day else day.copy(
                    isRest = false, focusKind = SessionFocusKind.FULL_BODY, focusTitle = target.second,
                    routineId = target.first, routineName = target.second,
                )
            })
        }
    }

    private fun awaitFallbackPlan(plan: WeeklySchedulePlan) {
        compose.awaitThat("the actual fallback plan with no generated occurrences", vm.uiState::value) {
            vm.uiState.value.readState == HomeReadState.CURRENT && vm.uiState.value.occurrences.isEmpty() && vm.uiState.value.weekPlan == plan
        }
        compose.settle()
    }

    private class RequiredBodyweightRead {
        val fail = MutableStateFlow(false)
        val collections = AtomicInteger()
        @Volatile var held: CompletableDeferred<Unit>? = null
        val writes = AtomicInteger()
        val failWrites = AtomicInteger()
        @Volatile var heldWrite: CompletableDeferred<Unit>? = null
        fun decorate(real: BodyweightDao): BodyweightDao = object : BodyweightDao by real {
            override fun observeAll(): Flow<List<BodyweightEntryEntity>> = flow {
                collections.incrementAndGet()
                held?.await()
                emitAll(real.observeAll().combine(fail) { rows, refuses ->
                    if (refuses) error("Synthetic required bodyweight read failure")
                    rows
                })
            }
            override suspend fun upsert(entry: BodyweightEntryEntity) {
                writes.incrementAndGet()
                heldWrite?.await()
                if (failWrites.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
                    throw SQLiteFullException("Synthetic Home bodyweight write failure")
                }
                real.upsert(entry)
            }
        }
    }
}
