package com.sinura.personaltrainer.ui.activity

/**
 * A hand-wound clock for the cardio ViewModel. Finish is gated at [CardioFinishGate.MIN_SECONDS]
 * in the ViewModel too, so a test that finishes winds this past the gate first, rather than
 * finishing at 0 s.
 */
internal class TestCardioClock(wallStartMs: Long, elapsedStartMs: Long = 1_000_000L) {
    var elapsedMs: Long = elapsedStartMs
        private set
    var wallMs: Long = wallStartMs
        private set

    fun advance(ms: Long) {
        elapsedMs += ms
        wallMs += ms
    }
}

/** Winds [clock] past the finish gate; finish() reads the clock itself. */
@Suppress("UnusedReceiverParameter")
internal fun LiveCardioViewModel.runPastTheGate(clock: TestCardioClock) {
    clock.advance(CardioFinishGate.MIN_SECONDS * 1_000L)
}
