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
                var pending: Throwable? = null
                try {
                    try {
                        base.evaluate()
                    } catch (t: Throwable) {
                        pending = t
                    }
                } finally {
                    try {
                        try {
                            RestTimerGlobals.assertClean(RestTimerGlobals.Phase.END, skipLedger = ownsLedger)
                        } catch (leak: AssertionError) {
                            if (pending != null) {
                                pending!!.addSuppressed(leak)
                            } else {
                                pending = leak
                            }
                        }
                    } finally {
                        try {
                            RestTimerGlobals.resetAll()
                        } catch (resetError: Throwable) {
                            if (pending != null) {
                                pending!!.addSuppressed(resetError)
                            } else {
                                pending = resetError
                            }
                        }
                    }
                }
                if (pending != null) {
                    throw pending
                }
            }
        }
}
