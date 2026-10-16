package com.sinura.personaltrainer.testutil

import java.util.concurrent.TimeUnit

/**
 * Compose's Robolectric idle loop uses Espresso's master timeout as its own
 * wall clock, and it spends that whole budget inside one Espresso.onIdle.
 * When that drain takes the full minute — a full-app screen under a loaded suite —
 * the next loop throws "Compose did not get idle after 1 attempts" even though the
 * busy-resource line is empty: the screen had just become idle, and the outer clock
 * was already spent. The master budget has to outlast one drain so the second idle
 * check can run.
 *
 * Espresso is on the unit-test runtime classpath through Compose, and it is not a
 * direct test dependency, so the call goes through reflection.
 */
object ComposeIdleBudget {
    const val MASTER_SECONDS = 180L

    fun install() {
        val policies = Class.forName("androidx.test.espresso.IdlingPolicies")
        val set = policies.getMethod(
            "setMasterPolicyTimeout",
            java.lang.Long.TYPE,
            TimeUnit::class.java,
        )
        set.invoke(null, MASTER_SECONDS, TimeUnit.SECONDS)
    }

    fun masterSeconds(): Long {
        val policies = Class.forName("androidx.test.espresso.IdlingPolicies")
        val policy = policies.getMethod("getMasterIdlingPolicy").invoke(null)
        val timeout = policy.javaClass.getMethod("getIdleTimeout").invoke(policy) as Long
        val unit = policy.javaClass.getMethod("getIdleTimeoutUnit").invoke(policy) as TimeUnit
        return unit.toSeconds(timeout)
    }
}
