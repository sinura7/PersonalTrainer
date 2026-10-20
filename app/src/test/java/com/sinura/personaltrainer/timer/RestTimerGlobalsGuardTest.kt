package com.sinura.personaltrainer.timer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.testutil.RestTimerGlobals
import com.sinura.personaltrainer.testutil.RestTimerGlobalsRule
import org.junit.Assert.assertThrows
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

    @Test
    fun ruleFailsWhenForegroundFlagWasLeftDirty() {
        assertThrows(AssertionError::class.java) {
            evaluateRule(
                object : Statement() {
                    override fun evaluate() {
                        RestTimerAppForeground.setForegroundForTest(true)
                    }
                },
            )
        }
    }

    @Test
    fun ruleFailsWhenStartedActivitiesWereLeftDirty() {
        assertThrows(AssertionError::class.java) {
            evaluateRule(
                object : Statement() {
                    override fun evaluate() {
                        RestTimerAppForeground.setStartedActivitiesForTest(2)
                    }
                },
            )
        }
    }

    @Test
    fun ruleFailsWhenListenersWereLeftDirty() {
        assertThrows(AssertionError::class.java) {
            evaluateRule(
                object : Statement() {
                    override fun evaluate() {
                        RestTimerAppForeground.addListener { }
                    }
                },
            )
        }
    }

    @Test
    fun ruleFailsWhenApplicationWasLeftDirty() {
        assertThrows(AssertionError::class.java) {
            evaluateRule(
                object : Statement() {
                    override fun evaluate() {
                        val app = ApplicationProvider.getApplicationContext<Application>()
                        RestTimerAppForeground.setApplicationForTest(app)
                    }
                },
            )
        }
    }

    @Test
    fun ruleFailsWhenOverlayLastSnapshotWasLeftDirty() {
        assertThrows(AssertionError::class.java) {
            evaluateRule(
                object : Statement() {
                    override fun evaluate() {
                        RestTimerOverlayController.setLastSnapshotForTest(
                            RestTimerSnapshot(
                                timerId = "guard-test",
                                running = true,
                                totalSeconds = 60,
                            ),
                        )
                    }
                },
            )
        }
    }

    @Test
    fun ruleFailsWhenTickObserverWasLeftDirty() {
        assertThrows(AssertionError::class.java) {
            evaluateRule(
                object : Statement() {
                    override fun evaluate() {
                        RestTimerService.tickObserver = { }
                    }
                },
            )
        }
    }

    @Test
    fun ruleFailsWhenLedgerClaimsWereLeftDirty() {
        assertThrows(AssertionError::class.java) {
            evaluateRule(
                object : Statement() {
                    override fun evaluate() {
                        RestTimerCompletion.ledger.recordClaimForTest("guard-test-id")
                    }
                },
            )
        }
    }
}
