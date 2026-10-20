package com.sinura.personaltrainer.timer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.testutil.RestTimerGlobals
import com.sinura.personaltrainer.testutil.RestTimerGlobalsRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestTimerGlobalsGuardTest {
    private fun evaluateRule(body: Statement) {
        RestTimerGlobalsRule().apply(body, Description.EMPTY).evaluate()
    }

    private fun expectEndLeak(dirtyBody: () -> Unit) {
        val error =
            assertThrows(AssertionError::class.java) {
                evaluateRule(
                    object : Statement() {
                        override fun evaluate() {
                            dirtyBody()
                        }
                    },
                )
            }
        assertTrue(
            "expected end-phase leak message but was: ${error.message}",
            error.message?.contains(RestTimerGlobals.LEAK_AT_TEST_END) == true,
        )
    }

    private fun expectStartResetClears(dirtyBeforeRule: () -> Unit) {
        dirtyBeforeRule()
        evaluateRule(
            object : Statement() {
                override fun evaluate() {
                    RestTimerGlobals.assertClean(RestTimerGlobals.Phase.START)
                }
            },
        )
    }

    @Test
    fun endLeakForegroundFlag() {
        expectEndLeak { RestTimerAppForeground.setForegroundForTest(true) }
    }

    @Test
    fun startResetClearsForegroundFlag() {
        expectStartResetClears { RestTimerAppForeground.setForegroundForTest(true) }
    }

    @Test
    fun endLeakStartedActivities() {
        expectEndLeak { RestTimerAppForeground.setStartedActivitiesForTest(2) }
    }

    @Test
    fun startResetClearsStartedActivities() {
        expectStartResetClears { RestTimerAppForeground.setStartedActivitiesForTest(2) }
    }

    @Test
    fun endLeakListeners() {
        expectEndLeak { RestTimerAppForeground.addListener { } }
    }

    @Test
    fun startResetClearsListeners() {
        expectStartResetClears { RestTimerAppForeground.addListener { } }
    }

    @Test
    fun endLeakApplicationNotInstalled() {
        expectEndLeak { RestTimerAppForeground.setApplicationForTest(null) }
    }

    @Test
    fun startResetReinstallsApplication() {
        expectStartResetClears { RestTimerAppForeground.setApplicationForTest(null) }
    }

    @Test
    fun endLeakOverlayLastSnapshot() {
        expectEndLeak {
            RestTimerOverlayController.setLastSnapshotForTest(
                RestTimerSnapshot(
                    timerId = "guard-test",
                    running = true,
                    totalSeconds = 60,
                ),
            )
        }
    }

    @Test
    fun startResetClearsOverlayLastSnapshot() {
        expectStartResetClears {
            RestTimerOverlayController.setLastSnapshotForTest(
                RestTimerSnapshot(
                    timerId = "guard-test",
                    running = true,
                    totalSeconds = 60,
                ),
            )
        }
    }

    @Test
    fun endLeakTickObserver() {
        expectEndLeak { RestTimerService.tickObserver = { } }
    }

    @Test
    fun startResetClearsTickObserver() {
        expectStartResetClears { RestTimerService.tickObserver = { } }
    }

    @Test
    fun endLeakLedgerClaims() {
        expectEndLeak { RestTimerCompletion.ledger.recordClaimForTest("guard-test-id") }
    }

    @Test
    fun startResetClearsLedgerClaims() {
        expectStartResetClears { RestTimerCompletion.ledger.recordClaimForTest("guard-test-id") }
    }
}
