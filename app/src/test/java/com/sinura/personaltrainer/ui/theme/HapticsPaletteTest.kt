package com.sinura.personaltrainer.ui.theme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-06: tick light, last-3s medium, complete strong, log-set click,
 * error double. System haptic intensity stays the off switch.
 */
class HapticsPaletteTest {
    @Test
    fun paletteNamesTheFiveGymActs() {
        val haptics = readOwned("ui/theme/Haptics.kt")
        assertTrue(haptics.contains("fun tick("))
        assertTrue(haptics.contains("CLOCK_TICK"))
        assertTrue(haptics.contains("fun warn("))
        assertTrue(haptics.contains("CONTEXT_CLICK"))
        assertTrue(haptics.contains("fun commit("))
        assertTrue(haptics.contains("fun reject("))
        assertTrue(haptics.contains("postDelayed"))
        assertTrue(haptics.contains("ERROR_BEAT_GAP_MS"))
        assertTrue(haptics.contains("fun holdDone("))
        assertTrue(haptics.contains("fun recordAccent("))
        assertTrue(haptics.contains("performHapticFeedback"))
        assertTrue(haptics.contains("LocalView.current"))
    }

    @Test
    fun restLastFiveSecondsLiveOnTheServiceNotCompose() {
        val rest = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(rest.contains("RestTick.isWarn(safeRemaining)"))
        assertFalse(
            "Compose must not fire RestTick haptics",
            rest.contains("if (RestTick.isWarn"),
        )
        val restCard = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse(
            "the dock's rest card must not fire RestTick haptics either",
            restCard.contains("RestTick.isWarn"),
        )

        val alerts = readOwned("timer/RestTimerAlerts.kt")
        assertTrue(alerts.contains("RestTick.pulseMs(second)"))
        assertTrue(alerts.contains("COMPLETE_PATTERN"))
        assertTrue(alerts.contains("createWaveform(COMPLETE_PATTERN"))
        assertTrue(alerts.contains("fun holdDone") || alerts.contains("holdTargetTone"))

        val service = readOwned("timer/RestTimerService.kt")
        assertTrue(service.contains("second = second"))
        assertTrue(service.contains("RestTimerAlerts.tick("))
    }

    @Test
    fun aRefusedEntryIsTheDoubleBeat() {
        val composer = readOwned("ui/activity/ActivityComposerScreen.kt")
        assertTrue(composer.contains("Haptics.reject(view)"))
        val restUi = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(restUi.contains("Haptics.reject(view)"))
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("Haptics.commit(view)"))
        assertTrue(workout.contains("Haptics.reject(view)"))
        assertTrue(workout.contains("Haptics.recordAccent(view)"))
        assertTrue(workout.contains("Motion.PR_ACCENT_DELAY_MS"))
        assertFalse(workout.contains("Haptics.celebrate"))
        assertTrue(workout.contains("logFeedback"))
        assertTrue(workout.contains("LogCommitFeedback.SUCCESS -> Haptics.commit(view)"))
        assertTrue(workout.contains("LogCommitFeedback.REJECT -> Haptics.reject(view)"))
        // The dock's Volt hands the act to the view model through onPrimary; commit and
        // reject arrive from logFeedback after the durable write, never from the tap.
        val onPrimaryStart = workout.indexOf("onPrimary = { action ->")
        val onPrimaryEnd = workout.indexOf("onEditFailedSave =")
        assertTrue(onPrimaryStart >= 0 && onPrimaryEnd > onPrimaryStart)
        val onPrimary = workout.substring(onPrimaryStart, onPrimaryEnd)
        assertFalse(
            "Log press must not commit before the write",
            onPrimary.contains("Haptics.commit"),
        )
        assertFalse(onPrimary.contains("Haptics.reject"))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(
            "the Volt's own press haptic stays off so the commit is the write's",
            dock.contains("hapticFeedback = false"),
        )
        assertFalse(dock.contains("Haptics.commit"))
        assertFalse(dock.contains("Haptics.reject"))
    }

    @Test
    fun stepperTapTicksAndHoldRepeatIsTickLight() {
        val stepper = readOwned("ui/components/StepperButton.kt")
        assertTrue(stepper.contains("Haptics.tick(view)"))
        assertTrue(stepper.contains("Haptics.tickLight(view)"))
        assertTrue(stepper.contains("StepperRepeat.HOLD_BEFORE_REPEAT_MS"))
        assertTrue(stepper.contains("StepperRepeat.REPEAT_MS"))
        assertFalse(stepper.contains("FAST_REPEAT_MS"))
    }

    @Test
    fun rpeAndWarmupChipsTickOnTheFieldSettle() {
        val chip = readOwned("ui/components/InstrumentChip.kt")
        assertTrue(chip.contains("Haptics.tick(view)"))
        assertTrue(chip.contains("Motion.FIELD_MS"))
        // Working | Warm-up and the RPE track are InstrumentChip radios: the tick comes
        // from the chip itself on the field settle, never from a caller.
        val header = readOwned("ui/workout/ExerciseHeader.kt")
        assertTrue(header.contains("fun SetTypeToggle("))
        assertTrue(header.contains("InstrumentChip("))
        assertTrue(header.contains("label = \"Working\""))
        assertTrue(header.contains("label = \"Warm-up\""))
        assertTrue(header.contains("role = Role.RadioButton"))
        // Last-time hint on the identity may tick when applying last session's set (ADR-030).
        assertTrue(header.contains("onApplyLastSetHint"))
        val rpe = readOwned("ui/workout/RpeSelector.kt")
        assertTrue(rpe.contains("InstrumentChip("))
        assertTrue(rpe.contains("RpeCopy.VALUES.forEach"))
        assertTrue(rpe.contains("role = Role.RadioButton"))
        assertFalse(rpe.contains("Haptics."))
    }

    @Test
    fun deleteWarnsAfterTheWriteAndUndoConfirms() {
        // HA-22: delete/remove land with a light warning *after* the write, never before.
        // HA-23: a landed undo confirms. Failures and expiries stay silent.
        val vm = readOwned("ui/workout/ActiveWorkoutViewModel.kt")
        assertTrue(vm.contains("_deleteFeedback.tryEmit(DeleteFeedback.DELETED)"))
        assertTrue(vm.contains("_deleteFeedback.tryEmit(DeleteFeedback.REMOVED)"))
        assertTrue(vm.contains("_deleteFeedback.tryEmit(DeleteFeedback.UNDO)"))

        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("deleteFeedback.collect"))
        assertTrue(workout.contains("DeleteFeedback.DELETED, DeleteFeedback.REMOVED -> Haptics.warn(view)"))
        assertTrue(workout.contains("DeleteFeedback.UNDO -> Haptics.commit(view)"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
