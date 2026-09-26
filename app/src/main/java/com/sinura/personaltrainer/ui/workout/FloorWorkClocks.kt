package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.FloorTimerCue
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.SetStopwatchWork
import com.sinura.personaltrainer.workout.SavedStateFloorTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TIMED_TICK_MS = 250L

/**
 * The workout floor's timed work ([ActiveWorkoutViewModel]): the hold countdown and the set
 * clock, the tickers that move them, the generation that retires a commit drawn before a timed
 * change, the cues the screen turns into a haptic or a tone, and their saved-state copy for when
 * Android stops the app.
 *
 * The clocks read elapsed realtime at every tick and never count ticks, so a stall or a restore
 * cannot make them drift. The hold is one at a time; each lift keeps its own set clock in the
 * saved state, so switching back to a lift brings its seconds back, paused.
 *
 * What a start or a stop means for the rest of the floor is the ViewModel's: the entry gate, the
 * rest timer (a start stops it, and so does a clock that comes back running), the prompt that
 * holds up a lift switch while a clock runs, and when a log, a correction, a removal or a rest
 * ends the clocks. So this class takes the saved state, the ViewModel's scope (the tickers live
 * and die with the screen, and tests wake them on its clock), its clock read, the selected lift
 * (read at every save, never once), the sound setting (read when a hold reaches its target) and
 * the cancel of the rest a logged set is still waiting to start, which every generation bump ends
 * with. It holds no gate and no error slot, like [FloorUndoOffers] and [FloorSetSaves].
 *
 * The ViewModel restores the clocks at the point of its `init` where it always has ([restore]).
 */
internal class FloorWorkClocks(
    private val saved: SavedStateFloorTimer,
    private val scope: CoroutineScope,
    private val elapsedNow: () -> Long,
    /** The selected lift now; a clock is saved under whichever lift is selected when it is saved. */
    private val selectedLift: () -> String?,
    /** Whether the rest's sound is on; the hold's target cue carries it. */
    private val holdSoundEnabled: suspend () -> Boolean,
    /** Cancels the rest a logged set is waiting to start; the last thing every [bump] does. */
    private val cancelPendingRest: () -> Unit,
) {
    private val _hold = MutableStateFlow(HoldTimerUiState())

    /** The hold countdown. */
    val hold: StateFlow<HoldTimerUiState> = _hold.asStateFlow()
    private var holdJob: Job? = null
    private val _stopwatch = MutableStateFlow(SetStopwatchUiState())

    /** The selected lift's set clock. */
    val stopwatch: StateFlow<SetStopwatchUiState> = _stopwatch.asStateFlow()
    private var stopwatchJob: Job? = null
    private var timedGeneration = 0
    private val _generation = MutableStateFlow(0)

    /**
     * Part of the commit's identity. Moves with every [bump]: each start, a clock restored running,
     * a lift let go, a rest started. A tick, a stop or a lift switch never moves it.
     */
    val generation: StateFlow<Int> = _generation.asStateFlow()
    private val _cues = MutableSharedFlow<FloorTimerCue>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** One cue per hold start, hold target, clock start and clock stop. */
    val cues: SharedFlow<FloorTimerCue> = _cues.asSharedFlow()

    /** Either clock is running. */
    val timing: Boolean
        get() = _stopwatch.value.running || _hold.value.running

    /**
     * Starts the hold on [exerciseId] at [totalSeconds]. The lift's set clock is cleared, with its
     * saved copy, and the generation moves. The ViewModel has gated the tap and stopped the rest.
     */
    fun startHold(exerciseId: String, totalSeconds: Int) {
        saveStopwatchFor(exerciseId)
        discardStopwatch()
        bump()
        val gen = timedGeneration
        val now = elapsedNow()
        val deadline = HoldWork.deadlineElapsedRealtime(now, totalSeconds)
        _hold.value = HoldTimerUiState(
            running = true,
            remainingSeconds = totalSeconds,
            totalSeconds = totalSeconds,
            elapsedSeconds = 0,
            startElapsedRealtime = now,
            deadlineElapsedRealtime = deadline,
            targetReached = false,
        )
        persistHold()
        _cues.tryEmit(FloorTimerCue.HoldStarted)
        holdJob = scope.launch { runHoldTicker(gen) }
    }

    private suspend fun runHoldTicker(generation: Int) {
        while (generation == timedGeneration) {
            val hold = _hold.value
            if (!hold.running) return
            val now = elapsedNow()
            if (now < hold.startElapsedRealtime) {
                stopHold()
                return
            }
            val remaining = HoldWork.remainingFromDeadline(
                deadlineElapsedRealtime = hold.deadlineElapsedRealtime,
                nowElapsedRealtime = now,
                totalSeconds = hold.totalSeconds,
            )
            val elapsed = HoldWork.elapsedFromRealtime(
                startElapsedRealtime = hold.startElapsedRealtime,
                nowElapsedRealtime = now,
                totalSeconds = hold.totalSeconds,
            )
            if (remaining <= 0) {
                _hold.value = hold.copy(
                    running = false,
                    remainingSeconds = 0,
                    elapsedSeconds = hold.totalSeconds,
                    targetReached = true,
                )
                persistHold()
                val sound = holdSoundEnabled()
                _cues.tryEmit(FloorTimerCue.HoldTarget(soundEnabled = sound))
                return
            }
            _hold.value = hold.copy(
                remainingSeconds = remaining,
                elapsedSeconds = elapsed,
            )
            delay(TIMED_TICK_MS)
        }
    }

    /** Ends the hold and forgets its saved copy. The generation does not move. */
    fun stopHold() {
        bumpHoldJob()
        _hold.value = HoldTimerUiState()
        saved.clearHold()
    }

    /**
     * Starts [exerciseId]'s set clock, counting on from the seconds it already shows. The hold is
     * stopped and the generation moves. The ViewModel has gated the tap and stopped the rest.
     */
    fun startStopwatch(exerciseId: String) {
        stopHold()
        bump()
        val gen = timedGeneration
        val already = _stopwatch.value.elapsedSeconds.coerceAtLeast(0)
        val now = elapsedNow()
        _stopwatch.value = SetStopwatchUiState(
            running = true,
            elapsedSeconds = already,
            used = true,
            startElapsedRealtime = now,
            frozenElapsedSeconds = already,
            exerciseId = exerciseId,
        )
        saveStopwatchFor(exerciseId)
        _cues.tryEmit(FloorTimerCue.StopwatchStarted)
        stopwatchJob = scope.launch { runStopwatchTicker(gen) }
    }

    private suspend fun runStopwatchTicker(generation: Int) {
        while (generation == timedGeneration) {
            val watch = _stopwatch.value
            if (!watch.running) return
            val now = elapsedNow()
            val elapsed = SetStopwatchWork.elapsedFromRealtime(
                startElapsedRealtime = watch.startElapsedRealtime,
                frozenElapsedSeconds = watch.frozenElapsedSeconds,
                nowElapsedRealtime = now,
            )
            _stopwatch.value = watch.copy(elapsedSeconds = elapsed)
            if (elapsed >= HoldWork.MAX_SECONDS) {
                // The ViewModel's stopSetStopwatch() is only this call, so the cap stops the clock here.
                stopStopwatch()
                return
            }
            delay(TIMED_TICK_MS)
        }
    }

    /**
     * Freezes the set clock at its seconds and saves it under the selected lift; it stays used, so
     * Log writes them. Only a clock that was running cues its stop. The ticker calls this at the cap.
     */
    fun stopStopwatch() {
        val current = _stopwatch.value
        if (!current.used && !current.running) return
        bumpStopwatchJob()
        val now = elapsedNow()
        val elapsed = if (current.running) {
            SetStopwatchWork.elapsedFromRealtime(
                startElapsedRealtime = current.startElapsedRealtime,
                frozenElapsedSeconds = current.frozenElapsedSeconds,
                nowElapsedRealtime = now,
            )
        } else {
            current.elapsedSeconds
        }
        _stopwatch.value = current.copy(
            running = false,
            elapsedSeconds = elapsed,
            frozenElapsedSeconds = elapsed,
            startElapsedRealtime = 0L,
        )
        saveStopwatchFor(selectedLift())
        if (current.running) {
            _cues.tryEmit(FloorTimerCue.StopwatchStopped)
        }
    }

    /** Clears the set clock and forgets its lift's saved copy: after a log, a correction, a rest or a hold. */
    fun discardStopwatch() {
        bumpStopwatchJob()
        val id = _stopwatch.value.exerciseId ?: selectedLift()
        _stopwatch.value = SetStopwatchUiState()
        id?.let { saved.clearStopwatch(it) }
    }

    /**
     * Clears only what the screen shows of the set clock, keeping the lift's saved copy: when no
     * lift is selected any more. A removed lift that Undo brings back gets its seconds back.
     */
    fun resetStopwatchView() {
        _stopwatch.value = SetStopwatchUiState()
    }

    /**
     * A new generation: the commit drawn before it is retired, both tickers are cancelled, and so
     * is the rest a logged set is still waiting to start.
     */
    fun bump() {
        timedGeneration++
        _generation.value = timedGeneration
        holdJob?.cancel()
        holdJob = null
        stopwatchJob?.cancel()
        stopwatchJob = null
        cancelPendingRest()
    }

    private fun bumpHoldJob() {
        holdJob?.cancel()
        holdJob = null
    }

    private fun bumpStopwatchJob() {
        stopwatchJob?.cancel()
        stopwatchJob = null
    }

    private fun persistHold() {
        saved.writeHold(selectedLift(), _hold.value)
    }

    /** Saves the set clock under [exerciseId], or forgets that lift's copy when the clock was never used. */
    fun saveStopwatchFor(exerciseId: String?) {
        if (exerciseId.isNullOrBlank()) return
        val watch = _stopwatch.value
        if (!watch.used && !watch.running) {
            saved.clearStopwatch(exerciseId)
            return
        }
        saved.writeStopwatch(exerciseId, watch.copy(exerciseId = exerciseId))
    }

    /**
     * Brings back the hold and [exerciseId]'s set clock after Android stopped the app. A hold whose
     * deadline passed comes back done; a clock that was running runs on, unless a hold does. A clock
     * read backwards (a reboot) drops the hold and freezes the set clock. What it means for a rest
     * that started meanwhile is the ViewModel's ([timing]).
     */
    fun restore(exerciseId: String?) {
        val now = elapsedNow()
        val hold = saved.readHold(exerciseId, now)
        if (hold != null) {
            val remaining = HoldWork.remainingFromDeadline(
                deadlineElapsedRealtime = hold.deadlineElapsedRealtime,
                nowElapsedRealtime = now,
                totalSeconds = hold.totalSeconds,
            )
            val elapsed = HoldWork.elapsedFromRealtime(
                startElapsedRealtime = hold.startElapsedRealtime,
                nowElapsedRealtime = now,
                totalSeconds = hold.totalSeconds,
            )
            val reached = hold.targetReached || remaining <= 0
            _hold.value = hold.copy(
                running = hold.running && !reached,
                remainingSeconds = if (reached) 0 else remaining,
                elapsedSeconds = if (reached) hold.totalSeconds else elapsed,
                targetReached = reached,
            )
            if (_hold.value.running) {
                bump()
                val gen = timedGeneration
                holdJob = scope.launch { runHoldTicker(gen) }
            }
        }
        if (exerciseId != null) {
            restoreStopwatchFor(
                exerciseId = exerciseId,
                resumeRunning = !_hold.value.running,
            )
        }
    }

    /**
     * Shows [exerciseId]'s saved set clock, or a fresh one for that lift. A saved clock that was
     * running runs on only with [resumeRunning] and below the cap; otherwise it comes back paused.
     */
    fun restoreStopwatchFor(exerciseId: String, resumeRunning: Boolean) {
        bumpStopwatchJob()
        val now = elapsedNow()
        val stored = saved.readStopwatch(exerciseId, now)
        if (stored == null || !stored.used) {
            _stopwatch.value = SetStopwatchUiState(exerciseId = exerciseId)
            return
        }
        val live = if (stored.running && resumeRunning) {
            SetStopwatchWork.elapsedFromRealtime(
                startElapsedRealtime = stored.startElapsedRealtime,
                frozenElapsedSeconds = stored.frozenElapsedSeconds,
                nowElapsedRealtime = now,
            )
        } else {
            stored.elapsedSeconds.coerceAtLeast(stored.frozenElapsedSeconds)
        }
        val running = stored.running && resumeRunning && live < HoldWork.MAX_SECONDS
        _stopwatch.value = stored.copy(
            running = running,
            elapsedSeconds = live,
            frozenElapsedSeconds = if (running) stored.frozenElapsedSeconds else live,
            startElapsedRealtime = if (running) stored.startElapsedRealtime else 0L,
            exerciseId = exerciseId,
        )
        if (running) {
            bump()
            val gen = timedGeneration
            stopwatchJob = scope.launch { runStopwatchTicker(gen) }
        }
    }
}
