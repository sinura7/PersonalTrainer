package com.sinura.personaltrainer.testutil

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Resets rest-timer globals before and after each test. [RestTimerGlobals.assertClean] runs after
 * reset at the start (baseline) and before reset at the end (leak attribution to the test body).
 */
class RestTimerGlobalsRule(
    private val ownsLedger: Boolean = false,
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                RestTimerGlobals.resetAll()
                RestTimerGlobals.assertClean(RestTimerGlobals.Phase.START, skipLedger = ownsLedger)
                var bodyError: Throwable? = null
                try {
                    try {
                        base.evaluate()
                    } catch (t: Throwable) {
                        bodyError = t
                    }
                } finally {
                    try {
                        try {
                            RestTimerGlobals.assertClean(RestTimerGlobals.Phase.END, skipLedger = ownsLedger)
                        } catch (leak: AssertionError) {
                            if (bodyError != null) {
                                bodyError!!.addSuppressed(leak)
                            } else {
                                throw leak
                            }
                        }
                    } finally {
                        RestTimerGlobals.resetAll()
                    }
                }
                if (bodyError != null) {
                    throw bodyError
                }
            }
        }
}
