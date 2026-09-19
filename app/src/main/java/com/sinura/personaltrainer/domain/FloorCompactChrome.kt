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

    /**
     * Weight and reps (or time) sit side by side as two hero numerals split by a
     * hairline; [com.sinura.personaltrainer.ui.theme.LogLoopScale] stacks them once the
     * system font is large enough that a half-kilo three-digit value no longer fits.
     */
    fun stackWeightAboveReps(): Boolean = false

    /** The two hero numerals are the loudest elements on the floor; their plates are round and quiet. */
    fun heroNumeralsSideBySide(): Boolean = true

    /** Packet B: gym-floor weight / reps / hold draft are plates + keypad. */
    fun weightAndRepsAreWheels(): Boolean = false

    /** Warm-up is not an RPE value; it sits outside the 6–10 track. */
    fun warmupOutsideRpeTrack(): Boolean = true

    /** RPE choices reflow into rows without horizontal scrolling. */
    fun rpeTrackFitsWithoutScroll(): Boolean = true

    /**
     * Header is Close / title / Finish only. Session telemetry is in the
     * named summary. The dock owns a companion and one primary action.
     */
    fun headerIsReadOnlyInstrumentStrip(): Boolean = true

    /**
     * The identity is image-led again: the 112 dp still beside the name, equipment,
     * set ordinal and working count, with Details beside it and Working / Warm-up under it.
     * Switch, Skip, Swap and Remove live in the header overflow.
     */
    fun imageLedHero(): Boolean = true

    /** Last set · Best set · Volume (this exercise) sit under the identity, split by hairlines. */
    fun statsRowUnderIdentity(): Boolean = true

    /** The header's second line and segmented bar say where the session stands. */
    fun headerShowsSessionProgress(): Boolean = true

    /** Add a lift lives in the switcher once a session lift exists. */
    fun addLiftLivesInSwitcher(): Boolean = true

    /** The primary action stays anchored at a 72 dp minimum. */
    fun logButtonStaysAnchored(): Boolean = true

    /** Rest and set clocks share one dock slot; modes never stack. */
    fun oneClockTwoModes(): Boolean = true

    /**
     * REST / HOLD / SET share one compact instrument bar: countdown
     * fill, time, and mode controls. Not a tall card, not a second
     * clock, not a wheel. Reserved height is the 56 dp timer row.
     */
    fun timerIsCompactInstrumentBar(): Boolean = true

    /**
     * Rest is its own quiet card in the dock: a small countdown ring, REST, the time,
     * the target, and −15 / +15 / Skip. Idle is the same card at rest — dim, the planned
     * length, Start rest. Duration editing still lives in a sheet. HOLD and SET keep
     * the 56 dp instrument bar.
     */
    fun idleRestIsInstrumentBar(): Boolean = false

    fun restIsDockCard(): Boolean = true

    /** Packet E: planned rest is presets / ±15 / Custom, not a 15 s wheel. */
    fun restLengthIsInlineWheel(): Boolean = false

    /**
     * Packet 3: optional count-up in the dock timer slot. Untimed
     * sets stay untimed. Not a second Volt.
     */
    fun manualSetStopwatch(): Boolean = true

    /**
     * Recommendations are supporting context after entry and effort.
     * Why still opens the trace; Use explicitly applies the suggestion.
     */
    fun progressionKickerInline(): Boolean = true

    /**
     * Today's sets are a strip of chips under the recommendation, with the current set
     * ringed and Add set as the last chip once the plan is met. Edit opens the full sheet.
     */
    fun addSetHiddenOnFloor(): Boolean = false

    fun setHistoryOnFloor(): Boolean = true

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
     * F2 moves elapsed time and session totals into Session summary.
     */
    fun headerShowsMinuteTelemetryOnly(): Boolean = false

    /**
     * Packet 5: cheap destructives (delete set, remove lift, skip day)
     * run immediately and offer Undo for ~6s. Finish, discard, and leave
     * a live workout still ask first.
     */
    fun cheapDestructivesAreUndoable(): Boolean = true
}
