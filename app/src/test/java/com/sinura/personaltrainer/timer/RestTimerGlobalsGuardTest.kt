package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.testutil.RestTimerGlobals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestTimerGlobalsGuardTest {
    /** Reset only: these tests intentionally dirty globals to exercise [RestTimerGlobals.assertClean]. */
    @get:Rule
    val resetOnly =
        object : TestWatcher() {
            override fun starting(description: Description) {
                RestTimerGlobals.resetAll()
            }

            override fun finished(description: Description) {
                RestTimerGlobals.resetAll()
            }
        }

    @Test
    fun assertCleanFailsWhenForegroundWasLeftTrue() {
        RestTimerAppForeground.setForegroundForTest(true)
        assertThrows(AssertionError::class.java) {
            RestTimerGlobals.assertClean()
        }
    }

    @Test
    fun assertCleanFailsWhenTickObserverWasLeftSet() {
        RestTimerService.tickObserver = { }
        assertThrows(AssertionError::class.java) {
            RestTimerGlobals.assertClean()
        }
    }
}
