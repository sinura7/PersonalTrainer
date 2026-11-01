package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 3.5 (A-F4-1, A-F4-2, A-F4-5): explicit Edit mode on a finished session. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class SessionDetailEditRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var vm: SessionDetailViewModel? = null

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
    }

    @After fun tearDown() {
        try {
            runBlocking { vm?.clearAndJoinForTest() }
        } finally {
            deps.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun readModeRowsAreNotClickable() {
        val session = seed(sets = 2)
        mount(session)
        val set = session.sets.first()
        compose.onNodeWithTag(SessionDetailTestTags.EDIT_SET).assertDoesNotExist()
        compose.onNodeWithTag(SessionDetailTestTags.sessionSetDelete(set.id)).assertDoesNotExist()
        compose.onNodeWithTag(SessionDetailTestTags.sessionSetRepsUp(set.id)).assertDoesNotExist()
        enterEdit()
        compose.onNodeWithTag(SessionDetailTestTags.sessionSetDelete(set.id), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(SessionDetailTestTags.sessionSetRepsUp(set.id), useUnmergedTree = true).assertExists()
    }

    @Test
    fun editChangesWeightAndReps() {
        val session = seed(sets = 2)
        mount(session)
        enterEdit()
        val target = session.sets.minBy { it.setNumber }
        val tags = listOf(
            SessionDetailTestTags.sessionSetWeightDown(target.id),
            SessionDetailTestTags.sessionSetWeightUp(target.id),
            SessionDetailTestTags.sessionSetRepsDown(target.id),
            SessionDetailTestTags.sessionSetRepsUp(target.id),
        )
        compose.onNodeWithTag(SessionDetailTestTags.CONTENT).performScrollToNode(hasTestTag(tags.last()))
        val root = compose.onRoot().getBoundsInRoot()
        // At 360dp every stepper stays on screen and keeps its 48dp target.
        for (tag in tags) {
            val node = compose.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = node.getBoundsInRoot()
            assertTrue("$tag inside the root: $bounds vs $root", bounds.left >= root.left && bounds.right <= root.right)
            assertTrue("$tag is 48dp wide: $bounds", bounds.right - bounds.left >= 48.dp)
        }

        compose.onNodeWithTag(SessionDetailTestTags.sessionSetRepsUp(target.id)).performClick()
        compose.onNodeWithTag(SessionDetailTestTags.sessionSetWeightUp(target.id)).performClick()
        compose.onNodeWithText("× 6", substring = true).assertIsDisplayed()
        dispatcher.scheduler.advanceTimeBy(INLINE_DEBOUNCE_MS + 1)
        dispatcher.scheduler.runCurrent()
        val stored: () -> Any? = { stored(session.id, target.id) }
        compose.awaitThat(what = "the nudge is stored", now = stored) {
            stored(session.id, target.id)?.let { it.reps == 6 && it.weightKg > 100.0 } == true
        }
        val saved = checkNotNull(stored(session.id, target.id))
        assertEquals(target.completedAt, saved.completedAt)
        assertEquals(target.setNumber, saved.setNumber)
    }

    @Test
    fun deleteSetOffersUndo() {
        val session = seed(sets = 2)
        mount(session)
        enterEdit()
        val removed = session.sets.minBy { it.setNumber }
        compose.onNodeWithTag(SessionDetailTestTags.sessionSetDelete(removed.id)).performClick()
        val offer: () -> Any? = { vm?.deletedSet?.value }
        compose.awaitThat(what = "the undo offer", now = offer) { vm?.deletedSet?.value != null }
        compose.onNodeWithText("Undo", ignoreCase = true).assertIsDisplayed().performClick()
        val sets: () -> Any? = { count(session.id) }
        compose.awaitThat(what = "the set is back", now = sets) { count(session.id) == 2 }
    }

    @Test
    fun deleteLastSetOfSessionIsBlocked() {
        val session = seed(sets = 1)
        mount(session)
        enterEdit()
        val only = session.sets.single()
        compose.onNodeWithTag(SessionDetailTestTags.sessionSetDelete(only.id)).performClick()
        compose.settle()
        compose.onNodeWithText("Delete the session instead").assertIsDisplayed()
        assertNull(vm?.deletedSet?.value)
        assertEquals(1, count(session.id))
    }

    private fun seed(sets: Int): WorkoutSession = runBlocking {
        seedTestWorkout(
            deps = deps,
            loggedSets = List(sets) { TestSetInput(weightKg = 100.0, reps = 5, rpe = 8) },
            finish = true,
        ).session
    }

    private fun stored(sessionId: String, setId: String) =
        runBlocking { deps.workoutRepository.getSession(sessionId) }?.sets?.firstOrNull { it.id == setId }

    private fun count(sessionId: String): Int =
        runBlocking { deps.workoutRepository.getSession(sessionId) }?.sets?.size ?: -1

    private fun enterEdit() {
        compose.onNodeWithTag(SessionDetailTestTags.SESSION_EDIT).performClick()
        compose.settle()
    }

    private fun mount(session: WorkoutSession) {
        val model = SessionDetailViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to session.id)),
            container = deps,
        ).also { vm = it }
        compose.setContent {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                PersonalTrainerTheme {
                    SessionDetailScreen(onBack = {}, onOpenExercise = {}, onOpenActiveSession = {}, viewModel = model)
                }
            }
        }
        val state: () -> Any? = { model.uiState.value }
        compose.awaitThat(what = "detail loads", now = state) { !model.uiState.value.isLoading }
        compose.settle()
    }
}
