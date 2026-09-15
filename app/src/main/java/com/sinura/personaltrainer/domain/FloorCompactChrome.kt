package com.sinura.personaltrainer.domain

/**
 * Gym-floor chrome after the live-58 compact floor.
 *
 * Phone height is the scarce resource. The expanded lift card is the
 * one current-lift copy (still, number, name, 0/4, overflow ⋮). Weight
 * and reps are stacked snap-scroll wheels, not one cramped pair and not
 * a typing well. The THIS LIFT dock strip stays off. Warm-up / RPE stay
 * collapsed while rest is idle. Log set is the one filled Volt; Start
 * next stays reachable without becoming a second Volt bar.
 */
object FloorCompactChrome {
    /** The expanded card is the identity. Do not pin a second THIS LIFT strip. */
    fun showSelectedLiftDock(): Boolean = false

    /** RPE chips belong to a running rest, not an idle half-screen optional panel. */
    fun showOptionalLogOptions(restRunning: Boolean): Boolean = restRunning

    /** Start next is a quiet keep-going, not a second filled Volt. */
    fun idleStartNextIsVolt(): Boolean = false

    /**
     * Packet A: do not compose idle Start next. It is a no-op when Next
     * lift is not valid, and a duplicate when Next lift is already the Volt.
     * Packet F removes the leftover idle-row callback.
     */
    fun showIdleStartNext(): Boolean = false

    /**
     * Packet A: an empty free workout has no rest, stopwatch, or Start next.
     * The dock Volt is Add a lift.
     */
    fun emptySessionHidesTimerDock(): Boolean = true

    /** Swap / remove live on the identity row, not a row of their own. */
    fun overflowOnHeaderRow(): Boolean = true

    /** Compact floor: a weight row, then a reps (or time) row. */
    fun stackWeightAboveReps(): Boolean = true

    /** Compact floor: swipe a live scroller, do not type the number. */
    fun weightAndRepsAreWheels(): Boolean = true

    /** Warm-up is not an RPE value; it sits outside the 6–10 track. */
    fun warmupOutsideRpeTrack(): Boolean = true

    /** RPE 6–10 share one non-scrolling row (360 dp / font 2.0). */
    fun rpeTrackFitsWithoutScroll(): Boolean = true

    /**
     * Packet 2: header is a read-only instrument strip; the dock owns
     * the timer, advance choice, and the one Volt Log set.
     */
    fun headerIsReadOnlyInstrumentStrip(): Boolean = true

    /** Rest and set clocks share one dock slot; modes never stack. */
    fun oneClockTwoModes(): Boolean = true

    /** Planned rest length edits inline with SnapValueWheel in the dock. */
    fun restLengthIsInlineWheel(): Boolean = true

    /**
     * Packet 3: optional count-up in the dock timer slot. Untimed
     * sets stay untimed. Not a second Volt.
     */
    fun manualSetStopwatch(): Boolean = true

    /**
     * Packet 4: HOLD / +N / BACK OFF sits in the Next row, not a
     * watermark behind the wheels. Why still opens the trace.
     */
    fun progressionKickerInline(): Boolean = true

    /**
     * Packet 4: weight, reps/time, RPE, and rest marks replace those
     * text labels on the floor. TalkBack still hears the words.
     */
    fun floorFieldGlyphsReplaceLabels(): Boolean = true

    /**
     * Packet 5: cheap destructives (delete set, remove lift, skip day)
     * run immediately and offer Undo for ~6s. Finish, discard, and leave
     * a live workout still ask first.
     */
    fun cheapDestructivesAreUndoable(): Boolean = true
}
