package com.sinura.personaltrainer.testutil

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the budget [ComposeIdleBudget] installs. The full-suite failure this exists
 * for burned the 60-second master timeout inside one Espresso drain and then threw
 * before a second idle check, with no busy resource named.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ComposeIdleBudgetTest {
    @Test
    fun theMasterBudgetOutlastsOneEspressoDrain() {
        ComposeIdleBudget.install()
        val seconds = ComposeIdleBudget.masterSeconds()
        assertTrue("master idle budget is $seconds seconds", seconds >= ComposeIdleBudget.MASTER_SECONDS)
    }
}
