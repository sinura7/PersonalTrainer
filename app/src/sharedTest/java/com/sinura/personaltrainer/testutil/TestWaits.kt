package com.sinura.personaltrainer.testutil

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

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

/**
 * The bounded `first { }`: returns the first value the predicate accepts, or fails the
 * test after [TestWaits.FLOW_MS] naming the last value it saw.
 *
 * Every ViewModel test used to wait with a bare `first { }`. On 9 Sep 2026 two of
 * those waits wedged CI for the job's full thirty minutes: the state they waited for
 * had been written and overwritten before the collector ran, and under `runBlocking`
 * nothing else ends a wait. A `TimeoutCancellationException` alone says when it gave
 * up; the last value says what the ViewModel was actually showing, which is the whole
 * diagnosis when the predicate is one field off. `tools/check-unbounded-waits.py`
 * fails preflight on a ViewModel wait that goes back to the bare form.
 */
suspend fun <T> Flow<T>.awaitFirst(predicate: (T) -> Boolean): T {
    var last: Any? = NOTHING_YET
    return try {
        withTimeout(TestWaits.FLOW_MS) {
            first { value ->
                last = value
                predicate(value)
            }
        }
    } catch (timedOut: TimeoutCancellationException) {
        val seen = if (last === NOTHING_YET) "no value was ever emitted" else "last value was $last"
        throw AssertionError(
            "awaitFirst gave up after ${TestWaits.FLOW_MS} ms; $seen\n${stalledThreads()}",
            timedOut,
        )
    }
}

/**
 * Every thread that could be holding a wait up, rendered for a failure message.
 *
 * Read once, from a catch block, after the ceiling has already been hit — so it cannot
 * perturb the race it describes. That is the same reasoning that lets these waits read
 * `uiState.value` on the way out, and it is exactly what a `println` inside the code under
 * test cannot claim: probes added to `RoutineEditorViewModel.leave()` on 10 Sep 2026 made a
 * two-in-four wedge vanish for eight consecutive runs. A wait that has already lost has
 * nothing left to disturb.
 *
 * The wedge this exists for is a stall, not a thrown read: it was seen in
 * `leaveAnywayStillDiscardsAnEmptyStubCreatedThisSession`, whose exit catches every
 * exception and then sets its flag unconditionally, so no throw can produce it. What is
 * still unknown is which thread is parked and why — in particular whether Room's
 * single-slot `TransactionExecutor` is BLOCKED on a connection or sitting IDLE in
 * `getTask` with work still queued, which is a leaked slot and a different fault entirely.
 *
 * Filtered to the frames that can answer that, because an unfiltered dump of a Robolectric
 * JVM is fifty threads of noise. A thread is kept when its name or any frame names the app,
 * Room, SQLite or coroutines; the state and the top frames of each are what distinguish
 * parked-and-waiting from parked-and-stuck.
 */
fun stalledThreads(): String {
    val interesting = Regex(
        "personaltrainer|androidx\\.room|sqlite|kotlinx\\.coroutines|room-txn|room-query",
        RegexOption.IGNORE_CASE,
    )
    val threads = Thread.getAllStackTraces()
        .filterKeys { it != Thread.currentThread() }
        .filter { (thread, stack) ->
            interesting.containsMatchIn(thread.name) ||
                stack.any { interesting.containsMatchIn(it.className) }
        }
        .toSortedMap(compareBy { it.name })
    if (threads.isEmpty()) return "no app, Room, SQLite or coroutine thread was alive to blame"
    return buildString {
        append("threads that could be holding this up (${threads.size}):")
        threads.forEach { (thread, stack) ->
            append("\n  \"${thread.name}\" ${thread.state}")
            stack.take(FRAMES_PER_THREAD).forEach { append("\n      at $it") }
            if (stack.size > FRAMES_PER_THREAD) append("\n      ... ${stack.size - FRAMES_PER_THREAD} more")
        }
    }
}

/** Deep enough to name the park site and who called it; short enough to read in a CI log. */
private const val FRAMES_PER_THREAD = 12

private val NOTHING_YET = Any()
