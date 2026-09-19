package com.sinura.personaltrainer.domain

import kotlin.math.abs
import kotlin.math.round

/**
 * Gym-floor weight, reps, hold-time, and planned rest as snap-scroll wheels.
 *
 * Same motion as the Settings reminder wheel: flick a vertical column, it
 * settles on one value. Weight steps with the plates the −5/+5 row already
 * used (2.5 kg, or 5 lbs). Reps move by 1. Holds are seconds in 5s, never
 * a fake 1-rep stand-in. Rest length steps by 15s to match the floor ±15.
 *
 * The first settle after open is pager noise, not a choice — including a
 * neighbour page. After the thumb has left the parked page, settling back
 * on it is a choice and must write the shown numeral.
 */
object FloorEntryWheels {
    const val MAX_KG = 400.0
    const val MAX_LBS = 1_000.0
    const val REST_STEP_SECONDS = 15

    fun weightStep(unit: WeightUnit): Double = unit.step

    fun weightDisplays(unit: WeightUnit, currentKg: Double = 0.0): List<Double> {
        val step = weightStep(unit)
        val max = when (unit) {
            WeightUnit.KG -> MAX_KG
            WeightUnit.LBS -> MAX_LBS
        }
        val steps = buildList {
            var value = 0.0
            while (value <= max + 1e-9) {
                add(roundToTenth(value))
                value += step
            }
        }
        val current = WeightConverter.toDisplayValue(currentKg, unit)
        if (steps.any { nearly(it, current) }) return steps
        return (steps + current).sorted()
    }

    fun weightPage(kg: Double, unit: WeightUnit): Int {
        val displays = weightDisplays(unit, kg)
        val current = WeightConverter.toDisplayValue(kg, unit)
        val exact = displays.indexOfFirst { nearly(it, current) }
        if (exact >= 0) return exact
        return displays.indices.minBy { abs(displays[it] - current) }
    }

    fun weightKgAt(page: Int, unit: WeightUnit, currentKg: Double = 0.0): Double {
        val displays = weightDisplays(unit, currentKg)
        val display = displays[page.coerceIn(0, displays.lastIndex)]
        return WeightConverter.toKg(display, unit)
    }

    /**
     * One flick of [pageDelta] pages. +1 is the next heavier plate
     * (the value below the window on a vertical scroller).
     */
    fun swipeWeightKg(currentKg: Double, unit: WeightUnit, pageDelta: Int): Double {
        val page = weightPage(currentKg, unit)
        return weightKgAt(page + pageDelta, unit, currentKg)
    }

    fun repsValues(): List<Int> = (1..NumericEntry.MAX_REPS).toList()

    fun repsPage(reps: Int): Int = reps.coerceIn(1, NumericEntry.MAX_REPS) - 1

    fun repsAt(page: Int): Int =
        repsValues()[page.coerceIn(0, NumericEntry.MAX_REPS - 1)]

    fun swipeReps(current: Int, pageDelta: Int): Int =
        repsAt(repsPage(current) + pageDelta)

    fun holdSecondsValues(currentSeconds: Int = HoldWork.DEFAULT_SECONDS): List<Int> {
        val steps = (HoldWork.MIN_SECONDS..HoldWork.MAX_SECONDS step HoldWork.STEP_SECONDS).toList()
        val current = currentSeconds.coerceAtLeast(0)
        if (current in steps) return steps
        return (steps + current).sorted()
    }

    fun holdPage(seconds: Int): Int {
        val values = holdSecondsValues(seconds)
        val exact = values.indexOf(seconds)
        if (exact >= 0) return exact
        return values.indices.minBy { abs(values[it] - seconds) }
    }

    fun holdSecondsAt(page: Int, currentSeconds: Int = HoldWork.DEFAULT_SECONDS): Int {
        val values = holdSecondsValues(currentSeconds)
        return values[page.coerceIn(0, values.lastIndex)]
    }

    fun swipeHoldSeconds(current: Int, pageDelta: Int): Int =
        holdSecondsAt(holdPage(current) + pageDelta, current)

    fun restSecondsValues(
        currentSeconds: Int = RestTimerPreferences.DEFAULT_SECONDS,
    ): List<Int> {
        val steps = (
            RestTimerPreferences.MIN_SECONDS..RestTimerPreferences.MAX_SECONDS
                step REST_STEP_SECONDS
            ).toList()
        val current = currentSeconds.coerceIn(
            RestTimerPreferences.MIN_SECONDS,
            RestTimerPreferences.MAX_SECONDS,
        )
        if (current in steps) return steps
        return (steps + current).sorted()
    }

    fun restPage(seconds: Int): Int {
        val values = restSecondsValues(seconds)
        val exact = values.indexOf(seconds)
        if (exact >= 0) return exact
        return values.indices.minBy { abs(values[it] - seconds) }
    }

    fun restSecondsAt(
        page: Int,
        currentSeconds: Int = RestTimerPreferences.DEFAULT_SECONDS,
    ): Int {
        val values = restSecondsValues(currentSeconds)
        return values[page.coerceIn(0, values.lastIndex)]
    }

    fun swipeRestSeconds(current: Int, pageDelta: Int): Int =
        restSecondsAt(restPage(current) + pageDelta, current)

    data class WheelSettleMemory(
        val firstSettleConsumed: Boolean = false,
        val hasLeftInitialPage: Boolean = false,
    )

    /**
     * Floor wheels only. Reminder and onboarding keep their own parked-page
     * skip and must not call this.
     *
     * Ignore the first settle (open / recovered draft). After that, every
     * settle that is not already the selected index is a choice — including
     * returning to the page the wheel opened on, once the thumb has left it.
     */
    fun shouldCommitSettledPage(
        settledPage: Int,
        initialPage: Int,
        memory: WheelSettleMemory,
        selectedIndex: Int,
    ): Boolean {
        if (!memory.firstSettleConsumed) return false
        if (settledPage == selectedIndex) return false
        if (settledPage == initialPage && !memory.hasLeftInitialPage) return false
        return true
    }

    fun afterWheelSettle(
        settledPage: Int,
        initialPage: Int,
        memory: WheelSettleMemory,
    ): WheelSettleMemory = WheelSettleMemory(
        firstSettleConsumed = true,
        hasLeftInitialPage = memory.hasLeftInitialPage || settledPage != initialPage,
    )

    private fun nearly(a: Double, b: Double): Boolean = abs(a - b) < 0.001

    private fun roundToTenth(value: Double): Double = round(value * 10.0) / 10.0
}
