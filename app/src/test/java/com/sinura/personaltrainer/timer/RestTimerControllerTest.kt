package com.sinura.personaltrainer.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestFinishFlash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The gold flash and lock glance key on a completion id, not on running
 * going false. Skip must not look finished.
 */
@RunWith(RobolectricTestRunner::class)
class RestTimerControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun completeIfCurrentPublishesTheIdBeforeTheStoreClears() {
        val store = RestTimerStore()
        val controller = RestTimerController(context, store)
        try {
            controller.start(90, "session-1")
            val timerId = store.current().timerId
            assertTrue(timerId.isNotBlank())
            assertNull(controller.lastCompletedTimerId.value)

            assertTrue(controller.completeIfCurrent(timerId, fromService = true))
            assertFalse(store.current().running)
            assertEquals(timerId, controller.lastCompletedTimerId.value)
            assertTrue(
                RestFinishFlash.lockShowsFinished(
                    running = store.current().running,
                    finishedLaunch = false,
                    completedTimerId = controller.lastCompletedTimerId.value,
                ),
            )
        } finally {
            controller.stop()
        }
    }

    @Test
    fun skipClearsAPriorCompletionSoTheLockDoesNotStayFinished() {
        val store = RestTimerStore()
        val controller = RestTimerController(context, store)
        try {
            controller.start(90, "session-1")
            val first = store.current().timerId
            assertTrue(controller.completeIfCurrent(first, fromService = true))
            assertEquals(first, controller.lastCompletedTimerId.value)

            controller.start(90, "session-1")
            assertNull(controller.lastCompletedTimerId.value)
            controller.stop()
            assertFalse(store.current().running)
            assertNull(controller.lastCompletedTimerId.value)
            assertFalse(
                RestFinishFlash.lockShowsFinished(
                    running = store.current().running,
                    finishedLaunch = false,
                    completedTimerId = controller.lastCompletedTimerId.value,
                ),
            )
            assertTrue(
                RestFinishFlash.lockShouldDismiss(
                    running = store.current().running,
                    finishedLaunch = false,
                    completedTimerId = controller.lastCompletedTimerId.value,
                ),
            )
        } finally {
            controller.stop()
        }
    }

    @Test
    fun completeIfCurrentLeavesANewerTimerAlone() {
        var n = 0
        val store = RestTimerStore(ids = { "timer-${++n}" })
        val controller = RestTimerController(context, store)
        try {
            controller.start(90, "session-1")
            val first = store.current().timerId
            controller.adjust(15)
            val second = store.current().timerId
            assertTrue(store.current().running)
            assertTrue(first != second)

            assertFalse(controller.completeIfCurrent(first, fromService = true))
            assertTrue(store.current().running)
            assertEquals(second, store.current().timerId)
            assertNull(controller.lastCompletedTimerId.value)
        } finally {
            controller.stop()
        }
    }
}
