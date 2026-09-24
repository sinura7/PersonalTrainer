package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.RestTimerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

data class RestTimerUiState(
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 90,
    val running: Boolean = false,
    val completedTimerId: String? = null,
    /** False while the rest row is not on disk; the floor says so in one line. */
    val persistenceHealthy: Boolean = true,
    /** First rest in-app: unrestricted battery, or Samsung kills the clock. */
    val batteryHint: Boolean = false,
    /** Exact alarm denied: rest page says best-effort, never "precise". */
    val exactAlarmBestEffort: Boolean = false,
)

/**
 * The rest commands the Log's dock ([ActiveWorkoutViewModel]) and the rest page
 * ([RestTimerViewModel]) both give: one clock, one saved last preset, one battery line.
 *
 * What a page plans to start next stays with the page, and so does the rule that merges a
 * seed, a pick and the echo of a pick into that plan. The two rules differ on purpose (X4,
 * 23 September 2026): the dock keys a pick to the lift it was made on, the rest page only
 * notes that one was made. This class is handed the plan's seconds and holds no plan.
 *
 * Nothing is launched when it is built, and its flows are cold: each page collects them
 * where it always did and keeps its own sharing.
 */
internal class RestCommands(
    container: AppDependencies,
    private val scope: CoroutineScope,
    private val sessionId: String,
) {
    private val restTimer = container.restTimerController
    private val preferences = container.preferencesRepository

    /**
     * The rest card: the clock while a rest runs, else the length [planned] names.
     *
     * Cold on purpose. The Log shares it through its own `stateIn`; the rest page combines it
     * straight into its screen state. A `stateIn` here would put a cached copy between the
     * rest page and the rest: the page's first state would show that copy's default (idle,
     * 1:30) before the real one (`lastSetLineAppearsAfterAWorkingSetOnTheSharedStore`).
     */
    fun <T> restState(planned: Flow<T>, seconds: (T) -> Int): Flow<RestTimerUiState> = combine(
        combine(
            restTimer.remainingSeconds,
            restTimer.snapshot,
            planned,
            restTimer.lastCompletedTimerId,
            restTimer.persistenceHealthy,
        ) { remaining, snapshot, plan, completedId, healthy ->
            RestTimerUiState(
                remainingSeconds = remaining,
                totalSeconds = if (snapshot.running) snapshot.totalSeconds else seconds(plan),
                running = snapshot.running,
                completedTimerId = completedId,
                persistenceHealthy = healthy,
            )
        },
        preferences.restBatteryHintShown,
        restTimer.exactAlarmAttempt,
    ) { rest, shown, attempt ->
        rest.copy(
            batteryHint = rest.running && !shown,
            exactAlarmBestEffort = attempt == ExactAlarmAttempt.BEST_EFFORT,
        )
    }

    /**
     * A length picked on either page, saved as the last preset. The other page hears it as
     * an echo, and it is the fallback when neither the coach nor the routine names a length.
     * The page writes its own plan first; this does not touch it.
     */
    fun rememberPick(seconds: Int) {
        scope.launch {
            preferences.setLastRestPresetSeconds(seconds)
        }
    }

    /**
     * Starts the rest a page planned, kept within the allowed range, and saves it as the last
     * preset.
     *
     * Starting owns the clock immediately. Preference IO must not queue a late start after
     * Skip, Time set, Finish, or another timer generation, so both writes wait in one
     * coroutine behind the start, the alarm prompt's eligibility second. Only a manual start
     * comes here: the rest a logged set starts saves no preset
     * (`ActiveWorkoutViewModel.startRestAfterSet`, ADR-012 decision 18).
     */
    fun startPlanned(plannedSeconds: Int) {
        val seconds = plannedSeconds.coerceIn(
            RestTimerPreferences.MIN_SECONDS,
            RestTimerPreferences.MAX_SECONDS,
        )
        restTimer.start(seconds, sessionId)
        scope.launch {
            preferences.setLastRestPresetSeconds(seconds)
            preferences.markRestAlarmEligible()
        }
    }

    /** The first rest's battery line was read; it does not come back. */
    fun acknowledgeBatteryHint() {
        scope.launch {
            preferences.markRestBatteryHintShown()
        }
    }

    /**
     * Each last preset saved after collection begins, while no rest runs: a pick on the other
     * page coming back. A pick heard during a rest is not the running rest, and is let go.
     *
     * The preset already saved when collection begins is not news and is dropped, so where
     * a page starts collecting decides what counts as an echo; each page starts at the point
     * in its `init` it always did. The guard reads the live clock, not a rendered state.
     */
    val presetEchoes: Flow<Int> = preferences.restTimerPreferences
        .map { it.lastPresetSeconds }
        .distinctUntilChanged()
        .drop(1)
        .mapNotNull { last -> if (last != null && !restTimer.snapshot.value.running) last else null }
}
