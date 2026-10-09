package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.database.sqlite.SQLiteFullException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as NotesReachRect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.EndWorkoutTags
import com.sinura.personaltrainer.ui.components.NotesLeaveDialog
import com.sinura.personaltrainer.ui.components.NotesSaveState
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import com.sinura.personaltrainer.ui.components.NotesTestTags
import com.sinura.personaltrainer.ui.history.SessionDetailScreen
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.history.SessionDetailViewModel
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** ADR-032 notes states in real screen/modal windows. Actual IME evidence remains native. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class NotesTruthMatrixRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<ViewModel>()
    private val failures = AtomicInteger(0)
    private val writes = AtomicInteger(0)
    private var heldWrite: CompletableDeferred<Unit>? = null
    private lateinit var deps: FakeAppDependencies
    private lateinit var active: ActiveWorkoutViewModel
    private lateinit var detail: SessionDetailViewModel
    private val surface = mutableStateOf(Surface.ACTIVE)
    private val guardOpen = mutableStateOf(false)
    private val liveGuard = mutableStateOf(true)
    private var retryTaps = 0
    private var keepTaps = 0
    private var leaveTaps = 0
    private var finishedId: String? = null
    private var historyExits = 0
    private lateinit var profile: String
    private lateinit var workoutId: String
    private val runId = UUID.randomUUID().toString()
    private var reachabilityFailures = 0

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun updateSessionNotes(id: String, notes: String) {
                        writes.incrementAndGet()
                        heldWrite?.await()
                        if (failures.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
                            throw SQLiteFullException("Synthetic notes matrix failure")
                        }
                        real.updateSessionNotes(id, notes)
                    }
                }
            },
        )
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            deps.preferencesRepository.markRestBatteryHintShown()
        }
    }

    @After fun tearDown() {
        heldWrite?.complete(Unit)
        try {
            runBlocking { models.forEach { it.clearAndJoinForTest() } }
        } finally {
            deps.restTimerController.stop()
            dispatcher.scheduler.advanceUntilIdle()
            deps.close()
            Dispatchers.resetMain()
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallFont10() = verifyProfile(expectedFont = 1f, name = "360x640-font1.0")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallFont16() = verifyProfile(expectedFont = 1.6f, name = "360x640-font1.6")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallFont20() = verifyProfile(expectedFont = 2f, name = "360x640-font2.0")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardFont10() = verifyProfile(expectedFont = 1f, name = "412x840-font1.0")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standardFont16() = verifyProfile(expectedFont = 1.6f, name = "412x840-font1.6")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standardFont20() = verifyProfile(expectedFont = 2f, name = "412x840-font2.0")
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeFont10() = verifyProfile(expectedFont = 1f, name = "800x360-font1.0")
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeFont16() = verifyProfile(expectedFont = 1.6f, name = "800x360-font1.6")
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeFont20() = verifyProfile(expectedFont = 2f, name = "800x360-font2.0")

    private fun verifyProfile(expectedFont: Float, name: String) {
        profile = name
        assertEquals(expectedFont, RuntimeEnvironment.getApplication().resources.configuration.fontScale, .001f)
        val sessionId = runBlocking { seedLegExtension(deps = deps, loggedSets = floorSets(1)) }
        workoutId = sessionId
        runBlocking { deps.workoutRepository.updateSessionNotes(sessionId, "Stored original") }
        active = floorViewModel(deps = deps, sessionId = sessionId).also(models::add)
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                    Box(Modifier.fillMaxSize()) {
                        when (surface.value) {
                            Surface.ACTIVE -> ActiveWorkoutScreen(
                                onExit = {}, onFinished = { finishedId = it }, viewModel = active,
                                restNotificationsEnabledOverride = true,
                            )
                            Surface.HISTORY -> SessionDetailScreen(
                                onBack = { historyExits += 1; surface.value = Surface.EMPTY },
                                onOpenExercise = {}, onOpenActiveSession = {}, viewModel = detail,
                            )
                            Surface.GUARD -> if (guardOpen.value) NotesLeaveDialog(
                                saveState = NotesSaveState(status = NotesSaveStatus.FAILED, canRetry = true),
                                liveDraft = liveGuard.value,
                                onRetry = { retryTaps += 1; guardOpen.value = false },
                                onKeepEditing = { keepTaps += 1; guardOpen.value = false },
                                onLeave = { leaveTaps += 1; guardOpen.value = false },
                            )
                            Surface.EMPTY -> Unit
                        }
                    }
                }
            }
        }
        compose.awaitThat("hydrated matrix workout", active.uiState::value) {
            active.uiState.value.canFinish && active.uiState.value.notesSave.status == NotesSaveStatus.SAVED
        }
        val original = runBlocking { checkNotNull(deps.workoutRepository.getSession(sessionId)) }
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
        compose.waitForIdle()
        compose.holdingTheClock {
            compose.onNodeWithText("Session notes").performClick()
            compose.settle()
            exerciseFailureAndRetry(
                draft = "  Seat 5  ", state = { active.uiState.value.notesSave },
                flush = active::persistDraftForExit, prefix = "live",
            )
            assertEquals("Seat 5", storedNotes(sessionId))
            clearAndConfirm(state = { active.uiState.value.notesSave }, flush = active::persistDraftForExit, prefix = "live")
            assertEquals("", storedNotes(sessionId))
            action(node = compose.onNodeWithText("Done"), modal = true).performTouchInput { click() }
            compose.settle()

            compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
            compose.settle()
            capture("end-before-notes-toggle")
            dumpEndSemantics("end-before-notes-toggle")
            // At landscape font 2 the real scroll viewport ends one pixel above this
            // toggle. Reach its whole 48 dp target before injecting an actual touch;
            // tapping its offscreen semantics does not open the editor.
            val endNotesToggle = action(node = compose.onNodeWithText("Session notes"), modal = true)
            capture("end-notes-toggle-reached")
            dumpEndSemantics("end-notes-toggle-reached")
            endNotesToggle.performTouchInput { click() }
            compose.settle()
            capture("end-after-notes-toggle")
            dumpEndSemantics("end-after-notes-toggle")
            exerciseFailureAndRetry(
                draft = "  Seat 6  ", state = { active.uiState.value.notesSave },
                flush = active::persistDraftForExit, prefix = "end",
            )
            assertEquals("Seat 6", storedNotes(sessionId))
            clearAndConfirm(state = { active.uiState.value.notesSave }, flush = active::persistDraftForExit, prefix = "end")
            action(node = compose.onNodeWithTag(EndWorkoutTags.SAVE), modal = true)
            action(node = compose.onNodeWithTag(EndWorkoutTags.DISCARD), modal = true)
            capture("end-actions-cleared")
            action(node = compose.onNodeWithTag(EndWorkoutTags.SAVE), modal = true).performTouchInput { click() }
            // Room and the VM complete without Compose frames. Navigation is a lifecycle
            // collection followed by LaunchedEffect, so draw its frame after that result.
            compose.awaitThat("Finish completed in the actual VM", { active.exitRequested.value to finishedId }) {
                active.exitRequested.value == WorkoutExit.Finished(sessionId) || finishedId == sessionId
            }
            val finished = runBlocking { checkNotNull(deps.workoutRepository.getSession(sessionId)) }
            assertEquals("", finished.notes)
            assertEquals(original.sets, finished.sets)
            assertEquals(original.startedAt, finished.startedAt)
            assertNotNull(finished.finishedAt)
            compose.settle()
            compose.awaitThat("the screen delivered Finish to History", { active.exitRequested.value to finishedId }) {
                finishedId == sessionId
            }
            assertEquals("the screen acknowledged the actual Finish event", null, active.exitRequested.value)

            detail = SessionDetailViewModel(
                application = ApplicationProvider.getApplicationContext(),
                savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)), container = deps,
            ).also(models::add)
            compose.runOnUiThread { surface.value = Surface.HISTORY }
            compose.settle()
            compose.awaitThat("hydrated matrix History", detail.uiState::value) {
                detail.uiState.value.session?.id == sessionId && detail.uiState.value.notesSave.status == NotesSaveStatus.SAVED
            }
            action(node = compose.onNodeWithText("Session notes"), modal = false).performTouchInput { click() }
            compose.settle()
            exerciseFailureAndRetry(
                draft = "  Seat 7  ", state = { detail.uiState.value.notesSave },
                flush = detail::persistNotesForExit, prefix = "history",
            )
            assertEquals("Seat 7", storedNotes(sessionId))
            clearAndConfirm(state = { detail.uiState.value.notesSave }, flush = detail::persistNotesForExit, prefix = "history")
            assertEquals("", storedNotes(sessionId))

            // This is the actual History screen's guard and explicit discard choice, not a
            // callback-only plate: two real failed writes leave the exact durable row alone.
            failures.set(2)
            noteField().performTextReplacement("Leave draft")
            releaseEditorFocus("history")
            detail.persistNotesForExit()
            compose.awaitThat("failed History draft before exit", detail.uiState::value) {
                detail.uiState.value.notesSave.status == NotesSaveStatus.FAILED
            }
            compose.onNodeWithTag(SessionDetailTestTags.BACK).performClick()
            compose.awaitThat("real History exit guard", { detail.notesExitBlocked.value }) { detail.notesExitBlocked.value }
            compose.settle()
            assertGuardActions()
            proveGuardBody(words = HISTORY_GUARD_BODY, stage = "history-real-exit-guard")
            capture("history-real-exit-guard")
            val attemptsBeforeLeave = writes.get()
            action(node = compose.onNodeWithTag(NotesTestTags.LEAVE), modal = true).performTouchInput { click() }
            compose.awaitThat("the actual History VM accepted explicit leaving", { detail.notesExitRequested.value to historyExits }) {
                detail.notesExitRequested.value || historyExits == 1
            }
            compose.settle()
            compose.awaitThat("explicit History leave navigated once", { historyExits }) { historyExits == 1 }
            assertFalse("the screen acknowledged the actual History leave event", detail.notesExitRequested.value)
            dispatcher.scheduler.advanceTimeBy(401)
            dispatcher.scheduler.runCurrent()
            assertEquals(attemptsBeforeLeave, writes.get())
            val after = runBlocking { checkNotNull(deps.workoutRepository.getSession(sessionId)) }
            assertEquals("", after.notes)
            assertEquals(finished.sets, after.sets)
            assertEquals(finished.startedAt, after.startedAt)
            assertEquals(finished.finishedAt, after.finishedAt)
            compose.settle()
        }
        verifyIsolatedGuard(live = true)
        verifyIsolatedGuard(live = false)
    }

    /** Real VM/Room pending -> held SQL -> failure -> touch Retry -> confirmed saved. */
    private fun exerciseFailureAndRetry(draft: String, state: () -> NotesSaveState, flush: () -> Unit, prefix: String) {
        val before = runBlocking { checkNotNull(deps.workoutRepository.getSession(workoutId)) }
        failures.set(1)
        heldWrite = CompletableDeferred()
        noteField().performTextReplacement(draft)
        compose.settle()
        compose.awaitThat("$prefix pending text", state) { state().status == NotesSaveStatus.PENDING }
        readable(node = noteField(), whole = false).assertTextContains(draft)
        capture("$prefix-pending-values")
        // A focused multiline editor requests bring-into-view when its ancestor scrolls.
        // Release input focus before scrolling to adjacent status/actions: these JVM frames
        // cover the editor with input closed; the native suite separately exercises its IME.
        releaseEditorFocus(prefix)
        readable(node = compose.onNodeWithTag(NotesTestTags.STATUS)).assertTextContains("Notes not saved yet")
        capture("$prefix-pending")
        flush()
        compose.awaitThat("$prefix held writer", state) { state().status == NotesSaveStatus.SAVING }
        assertEquals("the held write changed no record", before, runBlocking { deps.workoutRepository.getSession(workoutId) })
        readable(node = compose.onNodeWithTag(NotesTestTags.STATUS)).assertTextContains("Saving notes…")
        capture("$prefix-saving")
        heldWrite!!.complete(Unit)
        heldWrite = null
        compose.awaitThat("$prefix failed writer", state) { state().status == NotesSaveStatus.FAILED && state().canRetry }
        assertEquals("the failed write changed no record", before, runBlocking { deps.workoutRepository.getSession(workoutId) })
        readable(node = noteField(), whole = false).assertTextContains(draft)
        capture("$prefix-failed-values")
        readable(node = compose.onNodeWithTag(NotesTestTags.STATUS)).assertTextContains("Notes not saved. Your text is kept here.")
        val retry = action(node = compose.onNodeWithTag(NotesTestTags.RETRY), modal = prefix != "history")
        capture("$prefix-failed-retry")
        retry.performTouchInput { click() }
        compose.awaitThat("$prefix Retry confirmed", state) { state().status == NotesSaveStatus.SAVED && !state().busy }
        assertEquals("Retry changed exactly the intended notes", before.copy(notes = draft.trim()),
            runBlocking { deps.workoutRepository.getSession(workoutId) })
        readable(node = compose.onNodeWithTag(NotesTestTags.STATUS)).assertTextContains("Notes saved")
        capture("$prefix-saved")
    }

    private fun clearAndConfirm(state: () -> NotesSaveState, flush: () -> Unit, prefix: String) {
        readable(node = noteField(), whole = false).performTextReplacement("")
        releaseEditorFocus(prefix)
        flush()
        compose.awaitThat("$prefix confirmed clear", state) { state().status == NotesSaveStatus.SAVED && state().cleared && !state().busy }
        readable(node = compose.onNodeWithTag(NotesTestTags.STATUS)).assertTextContains("Notes cleared")
        capture("$prefix-cleared")
    }

    /** Isolated new component checks both copy variants and all three distinct touch choices. */
    private fun verifyIsolatedGuard(live: Boolean) {
        for (tag in listOf(NotesTestTags.EXIT_RETRY, NotesTestTags.KEEP_EDITING, NotesTestTags.LEAVE)) {
            compose.runOnUiThread { surface.value = Surface.GUARD; liveGuard.value = live; guardOpen.value = true }
            compose.waitForIdle()
            assertGuardActions()
            val stage = "${if (live) "live" else "history"}-guard-$tag"
            proveGuardBody(words = if (live) LIVE_GUARD_BODY else HISTORY_GUARD_BODY, stage = stage)
            capture(stage)
            val before = Triple(retryTaps, keepTaps, leaveTaps)
            action(node = compose.onNodeWithTag(tag), modal = true).performTouchInput { click() }
            compose.waitForIdle()
            assertFalse(guardOpen.value)
            assertEquals(before.first + if (tag == NotesTestTags.EXIT_RETRY) 1 else 0, retryTaps)
            assertEquals(before.second + if (tag == NotesTestTags.KEEP_EDITING) 1 else 0, keepTaps)
            assertEquals(before.third + if (tag == NotesTestTags.LEAVE) 1 else 0, leaveTaps)
        }
    }

    private fun assertGuardActions() {
        for (tag in listOf(NotesTestTags.EXIT_RETRY, NotesTestTags.KEEP_EDITING, NotesTestTags.LEAVE)) {
            action(node = compose.onNodeWithTag(tag), modal = true)
        }
    }

    /** A Text's own scroll box can fit while its final warning line is still hidden. */
    private fun proveGuardBody(words: String, stage: String) {
        val node = compose.onNodeWithText(text = words, useUnmergedTree = true)
        readable(node = node)
        val target = node.fetchSemanticsNode()
        val layout = node.textLayout()
        val selfScroll = target.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        val frame = compose.runOnIdle {
            val decor = checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
            check(decor.width > 1 && decor.height > 1) { "The guard needs its measured Android modal" }
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
        }
        try {
            val position = target.positionInWindow
            val window = NotesReachRect(0f, 0f, frame.width.toFloat(), frame.height.toFloat())
            val visible = target.boundsInWindow.intersect(window)
            val lines = List(layout.lineCount) { line ->
                NotesReachRect(
                    position.x + layout.getLineLeft(line), position.y + layout.getLineTop(line),
                    position.x + layout.getLineRight(line), position.y + layout.getLineBottom(line),
                )
            }
            val ink = lines.map { frame.count(it.intersect(visible), TextSecondary) }
            val directory = File("build/screen-renders/workout-notes-truth/$runId/$profile")
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "$stage-body-modal.png").outputStream().use {
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save the actual guard modal" }
            }
            File(directory, "$stage-body-geometry.txt").writeText(buildString {
                appendLine("text=$words")
                appendLine("modal=${frame.width}x${frame.height} font=${target.layoutInfo.density.fontScale}")
                appendLine("position=$position layout=${target.size} textLayout=${layout.size} viewport=$visible")
                appendLine("selfScroll=${selfScroll?.value()}/${selfScroll?.maxValue()}")
                lines.forEachIndexed { line, bounds ->
                    appendLine("line=$line bounds=$bounds inkPixels=${ink[line]} " +
                        "visibleEnd=${layout.getLineEnd(line, visibleEnd = true)} ellipsis=${layout.isLineEllipsized(line)}")
                }
            })

            assertEquals("$stage uses the complete expected warning", words, layout.layoutInput.text.text)
            assertEquals("$stage uses the actual OS font",
                RuntimeEnvironment.getApplication().resources.configuration.fontScale, target.layoutInfo.density.fontScale, .001f)
            assertNotNull("$stage exposes the warning's real self scroll axis", selfScroll)
            assertNotNull("$stage exposes its real ScrollBy action", target.config.getOrNull(SemanticsActions.ScrollBy)?.action)
            assertEquals("$stage starts at the warning's real scroll origin", 0f, checkNotNull(selfScroll).value(), .01f)
            assertTrue("$stage has warning lines", layout.lineCount > 0)
            assertEquals("$stage retains the final character", words.length,
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            assertTrue("$stage full text layout fits the real clipped viewport",
                layout.size.height <= visible.height + 1f && layout.getLineBottom(layout.lineCount - 1) <= visible.height + 1f)
            assertTrue("$stage retains its full text target in the native modal",
                visible.width >= target.size.width - 1f && visible.height >= target.size.height - 1f)
            lines.forEachIndexed { line, bounds ->
                assertFalse("$stage line $line has no ellipsis", layout.isLineEllipsized(line))
                assertTrue("$stage line $line fits the actual text width", bounds.width <= target.size.width + 1f)
                assertTrue("$stage line $line is wholly inside the real text viewport: $bounds vs $visible",
                    bounds.left >= visible.left - 1f && bounds.top >= visible.top - 1f &&
                        bounds.right <= visible.right + 1f && bounds.bottom <= visible.bottom + 1f)
                assertTrue("$stage line $line has actual warning glyph ink in its native modal: ${ink[line]}", ink[line] >= 5)
            }
        } finally { frame.recycle() }
    }

    private fun noteField() = compose.onNode(hasSetTextAction())
    private fun releaseEditorFocus(prefix: String) {
        // A touch-mode Material button need not accept RequestFocus. Clear the Android
        // Compose host's focus in the editor's own window instead: its onFocusChanged
        // releases that composition's focus owner, including the multiline editor.
        // Temporarily block native descendant focus while clearing, so Android cannot
        // immediately select the same host again. Restore the window's policy before
        // measuring or drawing. The field stays expanded and the VM clock stays held.
        compose.settle()
        val textBefore = if (prefix == "history") detail.uiState.value.notes else active.uiState.value.notes
        val saveBefore = if (prefix == "history") detail.uiState.value.notesSave else active.uiState.value.notesSave
        val writesBefore = writes.get()
        val timeBefore = dispatcher.scheduler.currentTime
        compose.runOnUiThread {
            val decor = if (prefix == "history") compose.activity.window.decorView
                else checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
            val group = decor as ViewGroup
            val focused = checkNotNull(decor.findFocus()) { "$prefix editor's window has no focused Compose host" }
            val previousPolicy = group.descendantFocusability
            try {
                group.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                focused.clearFocus()
            } finally {
                group.descendantFocusability = previousPolicy
            }
        }
        compose.settle()
        noteField().assertIsNotFocused()
        noteField().assertTextContains(textBefore)
        assertEquals("focus release keeps the exact draft", textBefore,
            if (prefix == "history") detail.uiState.value.notes else active.uiState.value.notes)
        assertEquals("focus release does not flush or change save state", saveBefore,
            if (prefix == "history") detail.uiState.value.notesSave else active.uiState.value.notesSave)
        assertEquals("focus release starts no DAO write", writesBefore, writes.get())
        assertEquals("focus release advances no typing pause", timeBefore, dispatcher.scheduler.currentTime)
    }
    private fun storedNotes(id: String) = runBlocking { checkNotNull(deps.workoutRepository.getSession(id)).notes }

    private fun readable(node: SemanticsNodeInteraction, whole: Boolean = true): SemanticsNodeInteraction {
        // VM/Room predicates may become true before their Compose frame. With input held,
        // explicitly draw that frame before semantics queries can wait for UI idleness.
        compose.settle()
        reachWithFrames(node, whole)
        val target = node.assertIsDisplayed().fetchSemanticsNode()
        val bounds = target.boundsInWindow
        // Multiline editors have their own scroll; the value is asserted and captured while
        // reached. Status text and action targets must fit wholly in the visible viewport.
        if (whole) assertTrue("essential content fully unclipped: $bounds vs ${target.size}",
            bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f)
        return node
    }

    /**
     * Compose's performScrollTo loops without supplying animation frames when this test's
     * clock is held. Use the same real ScrollBy action, one measured delta at a time, with
     * an explicit frame boundary and a finite cap. Every measurement comes from the actual
     * layout and the native window; impossible clipping must fail with its frame intact.
     */
    private fun reachWithFrames(node: SemanticsNodeInteraction, whole: Boolean) {
        val trace = mutableListOf<String>()
        repeat(MAX_SCROLL_STEPS + 1) { step ->
            val target = node.fetchSemanticsNode()
            val position = target.positionInWindow
            val full = NotesReachRect(position.x, position.y,
                position.x + target.size.width, position.y + target.size.height)
            val window = compose.runOnUiThread {
                val decor = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
                    ?: compose.activity.window.decorView
                NotesReachRect(0f, 0f, decor.width.toFloat(), decor.height.toFloat())
            }
            val clipped = target.boundsInWindow.intersect(window)
            val fullVisible = clipped.width >= target.size.width - 1f && clipped.height >= target.size.height - 1f
            if (fullVisible) return

            var ancestor = target.parent
            var scrolled = false
            while (ancestor != null) {
                val current = ancestor
                val axis = current.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                val scroll = current.config.getOrNull(SemanticsActions.ScrollBy)?.action
                if (axis != null && scroll != null) {
                    val viewport = current.boundsInWindow.intersect(window)
                    val above = full.top - viewport.top
                    val below = full.bottom - viewport.bottom
                    // An editor larger than its viewport fills it; status and actions still
                    // have to fit in full. The final existing unclipped assertion enforces it.
                    val delta = when {
                        above < -1f && below > 1f -> 0f
                        above < -1f -> above
                        below > 1f -> below
                        else -> 0f
                    }
                    val requested = if (axis.reverseScrolling) -delta else delta
                    val value = axis.value()
                    val maximum = axis.maxValue()
                    trace += "step=$step ancestor=${current.id} viewport=$viewport target=$full clipped=$clipped " +
                        "range=$value/$maximum delta=$requested"
                    val canMove = requested < -1f && value > 0f || requested > 1f && value < maximum
                    if (canMove && viewport.width > 0f && viewport.height > 0f && step < MAX_SCROLL_STEPS) {
                        val accepted = compose.runOnUiThread { scroll(0f, requested) }
                        trace += "ScrollBy accepted=$accepted"
                        if (accepted) {
                            compose.settle()
                            scrolled = true
                            break
                        }
                    }
                }
                ancestor = current.parent
            }
            if (!scrolled) {
                if (!whole && clipped.width > 0f && clipped.height > 0f) return
                val label = target.config.getOrNull(SemanticsProperties.TestTag) ?: "node-${target.id}"
                val failure = AssertionError(
                    "$profile cannot fully reach $label after $step of $MAX_SCROLL_STEPS bounded scrolls; " +
                        "actual=$full clipped=$clipped size=${target.size} window=$window\n${trace.joinToString("\n")}",
                )
                runCatching { capture("unreachable-$label-${reachabilityFailures++}") }
                    .onFailure(failure::addSuppressed)
                throw failure
            }
        }
    }

    private fun action(node: SemanticsNodeInteraction, modal: Boolean): SemanticsNodeInteraction {
        readable(node = node).assertIsEnabled()
        val target = node.fetchSemanticsNode()
        val bounds = target.boundsInWindow
        assertEquals(RuntimeEnvironment.getApplication().resources.configuration.fontScale, target.layoutInfo.density.fontScale, .001f)
        assertTrue("full 48 dp action required: $bounds", bounds.height / target.layoutInfo.density.density >= 47.5f)
        val size = compose.runOnIdle {
            val decor = if (modal) checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
                else compose.activity.window.decorView
            decor.width to decor.height
        }
        assertTrue("action stays in its actual window", bounds.left >= -1f && bounds.top >= -1f &&
            bounds.right <= size.first + 1f && bounds.bottom <= size.second + 1f)
        return node
    }

    private fun capture(state: String) {
        compose.settle()
        val modal = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
        val bitmap = if (modal == null) compose.drawWindow() else compose.runOnIdle {
            assertTrue("modal was actually measured", modal.width > 1 && modal.height > 1)
            Bitmap.createBitmap(modal.width, modal.height, Bitmap.Config.ARGB_8888).also { modal.draw(Canvas(it)) }
        }
        try {
            val directory = File("build/screen-renders/workout-notes-truth/$runId/$profile")
            check(directory.isDirectory || directory.mkdirs())
            val file = File(directory, "$state.png")
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("actual window frame saved", file.length() > 100)
        } finally { bitmap.recycle() }
    }

    /** Keep the measured modal and its clipped/unclipped geometry before a failed edit. */
    private fun dumpEndSemantics(state: String) {
        val window = compose.runOnUiThread {
            val decor = checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
            "modal=${decor.width}x${decor.height} font=${decor.resources.configuration.fontScale}"
        }
        val nodes = compose.onAllNodes(SemanticsMatcher("all measured nodes") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
        val directory = File("build/screen-renders/workout-notes-truth/$runId/$profile")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$state-semantics.txt").writeText(buildString {
            appendLine(window)
            nodes.forEach { node ->
                appendLine("id=${node.id} parent=${node.parent?.id} position=${node.positionInWindow} " +
                    "size=${node.size} clipped=${node.boundsInWindow} " +
                    "density=${node.layoutInfo.density.density} font=${node.layoutInfo.density.fontScale}")
                appendLine(node.config)
            }
        })
    }

    private enum class Surface { ACTIVE, HISTORY, GUARD, EMPTY }

    private companion object {
        const val MAX_SCROLL_STEPS = 24
        const val LIVE_GUARD_BODY = "Notes are not saved. Closing the app may lose this draft."
        const val HISTORY_GUARD_BODY = "Notes are not saved. Leaving discards these changes."
    }
}
