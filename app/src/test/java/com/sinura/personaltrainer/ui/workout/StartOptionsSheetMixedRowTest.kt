package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tapping "Mixed session" opens the composer, once, and leaves nothing behind.
 *
 * The row used to call `onDismiss()` and THEN `viewModel.openComposer("mixed")`. The only
 * reader of that flow is a `LaunchedEffect` inside this sheet, which the dismiss had just
 * taken out of composition, so the effect never ran: the tap closed the sheet and opened
 * nothing. The effect is also what calls `onComposerNavigationHandled()`, so the flow kept
 * `"mixed"` — and because [StartOptionsViewModel] is scoped to the Activity rather than to
 * the sheet, the NEXT open fired that stale value on first composition and jumped to the
 * composer before the user had chosen anything. Including while a session was live, which is
 * the one state this sheet promises will start nothing.
 *
 * The fix shipped with view-model tests only, because nothing in the JVM lane could tap a row
 * at the time. It can now — `testImplementation` carries compose-ui-test — so the row itself
 * is covered here rather than left resting on the contract underneath it. A defect in a
 * composable wants a test that composes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class StartOptionsSheetMixedRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<StartOptionsViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.close()
        Dispatchers.resetMain()
    }

    /**
     * One tap, one composer. The sheet closes and the host is told, in that order and once.
     *
     * Before the fix this assertion read `0`: the tap did nothing at all.
     */
    @Test
    fun tappingMixedSessionOpensTheComposerExactlyOnce() {
        var mixedOpened = 0
        var visible by mutableStateOf(true)

        compose.setContent {
            PersonalTrainerTheme {
                if (visible) {
                    StartOptionsSheet(
                        onDismiss = { visible = false },
                        onWorkoutStarted = {},
                        onLogMixed = { mixedOpened += 1 },
                        viewModel = newViewModel(),
                    )
                }
            }
        }

        compose.onNodeWithText("Mixed session").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("the tap must reach the host", 1, mixedOpened)
    }

    /**
     * Re-opening the sheet must not spend a navigation the last open abandoned.
     *
     * This is the half that bit hardest: the jump happened on FIRST COMPOSITION of the second
     * open, so the user saw the composer instead of the sheet they had just asked for.
     */
    @Test
    fun reopeningTheSheetDoesNotFireAStaleNavigation() {
        var mixedOpened = 0
        var visible by mutableStateOf(true)
        val shared = newViewModel()

        compose.setContent {
            PersonalTrainerTheme {
                if (visible) {
                    StartOptionsSheet(
                        onDismiss = { visible = false },
                        onWorkoutStarted = {},
                        onLogMixed = { mixedOpened += 1 },
                        // The same instance both times: on the phone this view model is scoped
                        // to the Activity, which is the whole reason a stale value survived.
                        viewModel = shared,
                    )
                }
            }
        }

        compose.onNodeWithText("Mixed session").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, mixedOpened)

        // The user opens the sheet again.
        visible = true
        compose.waitForIdle()

        assertEquals("re-opening must not re-fire the last tap", 1, mixedOpened)
        // And the sheet is actually on screen, rather than having navigated away from itself.
        assertEquals(
            "the sheet the user asked for is the one they get",
            1,
            compose.onAllNodesWithText("Mixed session").fetchSemanticsNodes().size,
        )
    }

    private fun newViewModel(): StartOptionsViewModel =
        StartOptionsViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
        ).also { viewModels.add(it) }
}
