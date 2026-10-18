package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class ExerciseHeaderSwipeRenderTest {
    @get:Rule val compose = createComposeRule()

    private var switched = 0
    private var swipedNext = 0
    private var swipedPrevious = 0
    private var swapped = 0

    @Test
    fun swipeLeftOnTheHeaderSelectsTheNextLiftInOrder() {
        showHeader(canSwipeNext = true)
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).performTouchInput { swipeLeft() }
        assertEquals(1, swipedNext)
        assertEquals(0, swipedPrevious)
    }

    @Test
    fun swipeRightOnTheHeaderSelectsThePreviousLiftInOrder() {
        showHeader(canSwipePrevious = true, number = 2)
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).performTouchInput { swipeRight() }
        assertEquals(1, swipedPrevious)
        assertEquals(0, swipedNext)
    }

    @Test
    fun theCounterStillOpensTheSwitcherWithoutSwiping() {
        showHeader(canSwipeNext = true)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).performClick()
        assertEquals(1, switched)
        assertEquals(0, swipedNext)
    }

    @Test
    fun verticalScrollOnTheHeaderDoesNotSwitchLifts() {
        showHeader(canSwipeNext = true, canSwipePrevious = true, number = 2)
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).performTouchInput {
            swipeUp()
            swipeDown()
        }
        assertEquals(0, swipedNext)
        assertEquals(0, swipedPrevious)
    }

    @Test
    fun swipingPastTheLastLiftBouncesWithoutSelectingTheNextLift() {
        showHeader(canSwipeNext = false, number = 1)
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).performTouchInput { swipeLeft() }
        assertEquals(0, swipedNext)
    }

    @Test
    fun swipingBeforeTheFirstLiftBouncesWithoutSelectingThePreviousLift() {
        showHeader(canSwipePrevious = false, number = 1)
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).performTouchInput { swipeRight() }
        assertEquals(0, swipedPrevious)
    }

    @Test
    fun headerSwapOpensTheSameActionAsTheOverflow() {
        showHeader(canSwap = true)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWAP).performClick()
        assertEquals(1, swapped)
    }

    private fun showHeader(
        canSwipePrevious: Boolean = false,
        canSwipeNext: Boolean = false,
        canSwap: Boolean = false,
        number: Int = 1,
    ) {
        switched = 0
        swipedNext = 0
        swipedPrevious = 0
        swapped = 0
        compose.showFloor {
            ExerciseHeader(
                lift = floorLift(targetSets = 3),
                number = number,
                total = 2,
                setContext = "Working set 1 of 3",
                draftWarmup = false,
                onWarmup = {},
                onOpenSwitcher = { switched += 1 },
                onDetails = {},
                enabled = true,
                focused = true,
                onSwipePrevious = { swipedPrevious += 1 },
                onSwipeNext = { swipedNext += 1 },
                canSwipePrevious = canSwipePrevious,
                canSwipeNext = canSwipeNext,
                onSwap = { swapped += 1 },
                canSwap = canSwap,
            )
        }
    }
}
