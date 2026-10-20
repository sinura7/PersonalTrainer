package com.sinura.personaltrainer.testutil

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Resets rest-timer globals before and after each test. [RestTimerGlobals.assertClean] runs after
 * reset at the start (baseline) and before reset at the end (leak attribution to the test body).
 */
class RestTimerGlobalsRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                RestTimerGlobals.resetAll()
                RestTimerGlobals.assertClean()
                try {
                    base.evaluate()
                } finally {
                    RestTimerGlobals.assertClean()
                    RestTimerGlobals.resetAll()
                }
            }
        }
}
