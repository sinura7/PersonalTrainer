package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gold flash is a completion id, not running going false. Skip used
 * to look like finished.
 */
class RestFinishFlashTest {
    @Test
    fun aNewCompletionIdFlashesAndARepeatDoesNot() {
        assertTrue(RestFinishFlash.shouldFlash("timer-1", lastFlashedTimerId = null))
        assertFalse(RestFinishFlash.shouldFlash("timer-1", lastFlashedTimerId = "timer-1"))
        assertTrue(RestFinishFlash.shouldFlash("timer-2", lastFlashedTimerId = "timer-1"))
        assertFalse(RestFinishFlash.shouldFlash(null, lastFlashedTimerId = "timer-1"))
        assertFalse(RestFinishFlash.shouldFlash("", lastFlashedTimerId = null))
    }

    @Test
    fun skipDoesNotRenderFinishedOnTheLockGlance() {
        assertFalse(
            RestFinishFlash.lockShowsFinished(
                running = false,
                finishedLaunch = false,
                completedTimerId = null,
            ),
        )
        assertTrue(
            RestFinishFlash.lockShouldDismiss(
                running = false,
                finishedLaunch = false,
                completedTimerId = null,
            ),
        )
    }

    @Test
    fun aDoneNotificationOrCompletionIdShowsBackToTheBar() {
        assertTrue(
            RestFinishFlash.lockShowsFinished(
                running = false,
                finishedLaunch = true,
                completedTimerId = null,
            ),
        )
        assertTrue(
            RestFinishFlash.lockShowsFinished(
                running = false,
                finishedLaunch = false,
                completedTimerId = "timer-1",
            ),
        )
        assertFalse(
            RestFinishFlash.lockShouldDismiss(
                running = false,
                finishedLaunch = true,
                completedTimerId = null,
            ),
        )
    }

    @Test
    fun aLiveRestNeverFlashesFinished() {
        assertFalse(
            RestFinishFlash.lockShowsFinished(
                running = true,
                finishedLaunch = true,
                completedTimerId = "timer-1",
            ),
        )
        assertEquals(
            false,
            RestFinishFlash.lockShouldDismiss(
                running = true,
                finishedLaunch = false,
                completedTimerId = null,
            ),
        )
    }
}
