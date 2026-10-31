package com.sinura.personaltrainer.ui.start

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.TodaySheetStart
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.ownedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v4 blueprint 3.1: the one Start chooser, rendered from its state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h1600dp-xhdpi", sdk = [34])
class StartChooserRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val taps = mutableListOf<String>()

    @Test
    fun plannedTodayIsFirst() {
        show(StartOptionsUiState(isLoading = false, hasPlan = true, todayStart = TodaySheetStart(title = "Push"), routines = listOf(routine("a", "Pull"))))
        val today = top(StartTags.START_TODAY)
        assertTrue("today sits above routines", today < top(StartTags.routine("a")))
        assertTrue("today sits above empty", today < top(StartTags.START_EMPTY))
        compose.onNodeWithTag(StartTags.START_TODAY).assertIsEnabled().performClick()
        assertEquals(listOf("today"), taps)
    }

    @Test
    fun routinesThenEmptyOrder() {
        show(StartOptionsUiState(isLoading = false, routines = listOf(routine("a", "Pull"), routine("b", "Legs"))))
        val a = top(StartTags.routine("a"))
        val b = top(StartTags.routine("b"))
        assertTrue(a < b)
        assertTrue("Empty follows routines", b < top(StartTags.START_EMPTY))
        assertTrue("Other is last", top(StartTags.START_EMPTY) < top(StartTags.START_OTHER))
    }

    @Test
    fun restDayShowsNextHint() {
        show(
            StartOptionsUiState(
                isLoading = false,
                hasPlan = true,
                nextPlanned = StartNextPlanned(epochDay = 20_000, title = "Legs"),
            ),
        )
        compose.onNodeWithTag(StartTags.START_TODAY_REST).assertIsDisplayed()
        compose.onNodeWithText("Rest day. Nothing planned.").assertIsDisplayed()
        compose.onNodeWithText("Legs", substring = true).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag(StartTags.START_TODAY).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun liveSessionShowsResumeAndDisablesOthers() {
        val live = WorkoutSession(
            id = "live", routineId = null, routineName = null, date = 0, notes = "",
            durationMinutes = 0, startedAt = 0, finishedAt = null, exercises = emptyList(), sets = emptyList(),
        )
        show(
            StartOptionsUiState(isLoading = false, inProgress = live, routines = listOf(routine("a", "Pull"))),
            enabled = false,
        )
        val resume = top(StartTags.START_RESUME)
        assertTrue("Resume comes first", resume < top(StartTags.routine("a")))
        compose.onNodeWithTag(StartTags.START_RESUME).assertIsEnabled().performClick()
        assertEquals(listOf("resume:live"), taps)
        compose.onNodeWithTag(StartTags.routine("a")).assertIsNotEnabled()
        compose.onNodeWithTag(StartTags.START_EMPTY).assertIsNotEnabled()
        assertTrue(compose.onAllNodesWithTag(StartTags.START_TODAY).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun noPlanOffersPlanWeek() {
        show(StartOptionsUiState(isLoading = false, hasPlan = false))
        compose.onNodeWithTag(StartTags.START_TODAY_NONE).assertIsDisplayed()
        compose.onNodeWithTag(StartTags.START_PLAN_WEEK).performClick()
        assertEquals(listOf("plan"), taps)
    }

    /** Home, Plan, History and Body all open the one chooser through one guarded opener. */
    @Test
    fun sameChooserFromEveryTab() {
        val nav = ownedSource("ui/navigation/AppNav.kt")
        assertEquals("one chooser call site", 1, Regex("""\bStartChooser\(""").findAll(nav).count())
        assertEquals("all four tabs share the opener", 4, Regex("""onOpenStartSheet = openStartSheet""").findAll(nav).count())
        val opener = nav.substringAfter("val openStartSheet = {").substringBefore("}")
        assertTrue("the opener asks the shared gate first, was $opener", "startGate.canEditStartChoice()" in opener && "startGate.dismissStart()" in opener)
        assertTrue("the chooser starts through the shared gate", "gate = startGate" in nav)
    }

    private fun top(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun show(state: StartOptionsUiState, enabled: Boolean = StartGroups.startsEnabled(state)) {
        compose.setContent {
            PersonalTrainerTheme {
                Column {
                    MainPage(
                        state = state,
                        enabled = enabled,
                        onResume = { taps += "resume:$it" },
                        onResumeActivity = { taps += "activity:$it" },
                        onDiscard = { taps += "discard" },
                        onStartToday = { taps += "today" },
                        onStartRoutine = { taps += "routine:$it" },
                        onStartEmpty = { taps += "empty" },
                        onOther = { taps += "other" },
                        onOpenPlan = { taps += "plan" },
                        onOpenRoutines = { taps += "routines" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun routine(id: String, name: String) = Routine(
        id = id, name = name, notes = "", createdAt = 0, updatedAt = 0,
        exercises = listOf(
            RoutineExercise(
                id = "$id-1", routineId = id,
                exercise = Exercise(id = "$id-lift", name = "Lift", muscleGroup = "Chest", notes = "", isCustom = false),
                sortOrder = 0, targetSets = 3, targetReps = 8, targetWeightKg = null, restSeconds = 90,
            ),
        ),
    )
}
