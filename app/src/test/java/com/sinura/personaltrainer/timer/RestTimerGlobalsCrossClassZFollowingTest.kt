package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.testutil.RestTimerGlobals
import com.sinura.personaltrainer.testutil.RestTimerGlobalsRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestTimerGlobalsCrossClassZFollowingTest {
    @get:Rule
    val restTimerGlobals = RestTimerGlobalsRule()

    @Test
    fun globalsAreCleanAfterPriorClassPollution() {
        RestTimerGlobals.assertClean()
    }
}
