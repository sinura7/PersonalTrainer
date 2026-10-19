package com.sinura.personaltrainer.testutil

import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Resets rest-timer globals before and after each test, and fails fast when a prior test left
 * them dirty (AC2 guard).
 */
class RestTimerGlobalsRule : TestWatcher() {
    override fun starting(description: Description) {
        RestTimerGlobals.assertClean()
        RestTimerGlobals.resetAll()
    }

    override fun finished(description: Description) {
        RestTimerGlobals.resetAll()
    }
}
