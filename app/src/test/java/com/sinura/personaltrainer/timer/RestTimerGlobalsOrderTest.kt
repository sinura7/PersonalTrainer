package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.testutil.RestTimerGlobalsRule
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AC1: the shared rule clears globals between tests even when an earlier test in this class
 * leaves foreground true.
 */
@RunWith(RobolectricTestRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RestTimerGlobalsOrderTest {
    @get:Rule
    val restTimerGlobals = RestTimerGlobalsRule()

    @Test
    fun aLeavesForegroundTrueAtEndOfTest() {
        RestTimerAppForeground.setForegroundForTest(true)
    }

    @Test
    fun bForegroundIsFalseAfterRuleReset() {
        assertFalse(RestTimerAppForeground.isInForeground)
    }
}
