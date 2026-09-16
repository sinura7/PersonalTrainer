package com.sinura.personaltrainer.domain

/**
 * Gym-floor chrome after the live-58 compact floor.
 *
 * Phone height is the scarce resource. The expanded lift card is the
 * one current-lift copy (still, number, name, 0/4, overflow ⋮). Weight
 * and reps are stacked stepper plates with tap-to-type, not live wheels
 * and not a side-by-side pair. The THIS LIFT dock strip stays off. RPE
 * chips belong to a working-set draft, not rest. Log set is the one filled Volt.
 */
object FloorCompactChrome {
    /** The expanded card is the identity. Do not pin a second THIS LIFT strip. */
    fun showSelectedLiftDock(): Boolean = false

    /**
     * Packet D: RPE chips belong to a working-set draft, independent of rest.
     * Warm-up hides them with a visible reason, not a missing row.
     */
    fun showOptionalLogOptions(isWarmup: Boolean = false): Boolean = !isWarmup

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

    /** Packet B: gym-floor weight / reps / hold draft are plates + keypad. */
    fun weightAndRepsAreWheels(): Boolean = false

    /** Warm-up is not an RPE value; it sits outside the 6–10 track. */
    fun warmupOutsideRpeTrack(): Boolean = true

    /** RPE 6–10 share one non-scrolling row (360 dp / font 2.0). */
    fun rpeTrackFitsWithoutScroll(): Boolean = true

    /**
     * Header is Close / title / Finish only. Minute telemetry sits on
     * the exercise hero. The dock owns the timer, context rail, and the
     * one Volt Log set.
     */
    fun headerIsReadOnlyInstrumentStrip(): Boolean = true

    /** 112 dp still, no neon outline, no overlay badge. */
    fun imageLedHero(): Boolean = true

    /** Add a lift lives in the switcher once a session lift exists. */
    fun addLiftLivesInSwitcher(): Boolean = true

    /** Log stays 72 dp filled Volt; Next / Finish are rail text. */
    fun logButtonStaysAnchored(): Boolean = true

    /** Rest and set clocks share one dock slot; modes never stack. */
    fun oneClockTwoModes(): Boolean = true

    /** Packet E: planned rest is presets / ±15 / Custom, not a 15 s wheel. */
    fun restLengthIsInlineWheel(): Boolean = false

    /**
     * Packet 3: optional count-up in the dock timer slot. Untimed
     * sets stay untimed. Not a second Volt.
     */
    fun manualSetStopwatch(): Boolean = true

    /**
     * Packet 4/F: HOLD / +N / BACK OFF sits in the entry surface above
     * the fields, not a watermark and not above Log. Why still opens the
     * trace.
     */
    fun progressionKickerInline(): Boolean = true

    /** Packet F: Add set under the table is gone; Another set lives in the dock. */
    fun addSetHiddenOnFloor(): Boolean = true

    /**
     * Completion uses the context rail. The timer row stays reserved so
     * Log does not jump.
     */
    fun liftCompleteReplacesClock(): Boolean = false

    /**
     * Packet B: weight, reps, and hold draft need word labels.
     * Glyphs may sit beside them as supporting marks. RPE / rest
     * marks stay until D / E.
     */
    fun floorFieldGlyphsReplaceLabels(): Boolean = false

    /**
     * Packet C: the log loop shows one current lift. Other lifts live in
     * the switcher sheet, not a vertical stack of cards.
     */
    fun oneCurrentLiftOnFloor(): Boolean = true

    /** Packet C: session notes are overflow / Finish, not a block in the set loop. */
    fun notesLeaveTheLogLoop(): Boolean = true

    /**
     * Packet C: header telemetry is minutes · sets · volume. No rest/hold
     * / stopwatch numeral. The dock is the only seconds clock.
     */
    fun headerShowsMinuteTelemetryOnly(): Boolean = true

    /**
     * Packet 5: cheap destructives (delete set, remove lift, skip day)
     * run immediately and offer Undo for ~6s. Finish, discard, and leave
     * a live workout still ask first.
     */
    fun cheapDestructivesAreUndoable(): Boolean = true
}
