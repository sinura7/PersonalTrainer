package com.sinura.personaltrainer.testutil

/**
 * The wall-clock ceiling for a test that waits on a flow to emit.
 *
 * These waits are time-boxed on purpose: a predicate that never matches must
 * fail the test rather than hang the build. The budget was 5 seconds at every
 * one of the 116 call sites, which is a bet on the machine rather than on the
 * code — and the bet lost twice on 2026-09-08, both times on
 * `ActiveWorkoutViewModelTest.editUpdatesExistingSetWithoutStartingAnotherRestOrRecord`:
 * once in a cloud container running four Gradle tasks across four cores, and
 * once on the two-core GitHub runner that was meant to publish live test 23.
 * The same suite passed on both machines when they were not busy, so the code
 * was never wrong; 5 seconds around Robolectric, Room and a cold JIT simply is
 * not a margin.
 *
 * Thirty seconds keeps the fail-fast property that matters — a genuine hang
 * still ends in half a minute instead of blocking until Gradle gives up — and
 * stops a loaded runner from being reported as a broken test. Raising a
 * ceiling can only let a slow-but-correct test finish; it cannot make a
 * passing test fail.
 */
object TestWaits {
    const val FLOW_MS = 30_000L
}
