package com.sinura.personaltrainer.ui.exercise

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseFloorStats
import com.sinura.personaltrainer.domain.ExerciseFloorStatsCalculator
import com.sinura.personaltrainer.domain.ExerciseFloorStatsPresentation
import com.sinura.personaltrainer.domain.ExerciseHistory
import com.sinura.personaltrainer.domain.ExerciseHistoryBuilder
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/ExerciseDetailVM"

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    /** The row is gone — deleted from the library, or restored over. Terminal. */
    val missing: Boolean = false,
    val exercise: Exercise? = null,
    val history: ExerciseHistory = ExerciseHistory(exerciseId = ""),
    /** Every routine, each already knowing whether it holds this lift. */
    val routines: List<RoutineMembership> = emptyList(),
    val notice: String? = null,
    /**
     * The floor's Best set and Volume for this lift in the workout in progress, once a working set
     * of it is logged there: at large text the floor shows Last alone and these two are read here
     * (ADR-030, owner decision of 23 September 2026). Null otherwise, and whenever that workout
     * cannot be read: the card is optional, and never holds the screen.
     */
    val sessionInProgress: SessionInProgressStats? = null,
)

/** The floor's own stats for this lift, and the unit they were worded in. */
data class SessionInProgressStats(
    val stats: ExerciseFloorStats,
    val unit: WeightUnit,
)

/**
 * A routine, and whether this lift is already in it.
 *
 * Carried together rather than resolved in the sheet, because "already in this routine" is the
 * answer to the question the sheet is asking and it has to be visible *before* the tap. The
 * repository's own add is a silent no-op on a duplicate, which as a user experience is a row
 * that accepts a press and does nothing.
 */
data class RoutineMembership(
    val routine: Routine,
    val alreadyHolds: Boolean,
)

class ExerciseDetailViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val exerciseId: String = savedStateHandle.get<String>("exerciseId").orEmpty()

    /**
     * Same reason Active Workout tracks this: "exercise is null" alone cannot tell "the query
     * has not answered" from "the row is gone", and collapsing them leaves the screen spinning
     * on a deleted lift with no way out.
     */
    private val resolved = MutableStateFlow(exerciseId.isBlank())

    private val notice = MutableStateFlow<String?>(null)

    private data class RoutinesAndNotice(val routines: List<Routine>, val notice: String?)

    fun dismissNotice() {
        notice.value = null
    }

    /**
     * Put this lift into a routine, from the screen that made the case for training it.
     *
     * This screen used to be a terminus: several hundred lines of records, trends and session
     * history, and the only things you could press were Back and a row that opened one of those
     * sessions. Reading that your bench has not moved in six weeks and having nowhere to go
     * with it is the app stopping one step short of being useful.
     */
    fun addToRoutine(routineId: String) {
        val exercise = uiState.value.exercise ?: return
        val routine = uiState.value.routines.firstOrNull { it.routine.id == routineId } ?: return
        if (routine.alreadyHolds) {
            notice.value = "${exercise.name} is already in ${routine.routine.name}."
            return
        }
        viewModelScope.launch {
            val defaults = AddDefaults.forExercise(
                exercise,
                goal = container.preferencesRepository.coachPreferences.first().goal,
            )
            runCatchingCancellable {
                container.routineRepository.addExercise(
                    routineId = routineId,
                    exercise = exercise,
                    targetSets = defaults.sets,
                    targetReps = defaults.reps,
                    // No target weight: this screen knows the lift's history, but a number the
                    // user did not choose, presented as their plan, is the app guessing out
                    // loud. The routine editor is where a target gets set deliberately.
                    targetWeightKg = null,
                    restSeconds = defaults.restSeconds,
                )
                notice.value = "Added to ${routine.routine.name}."
            }.onFailure { thrown ->
                AppLog.w(TAG, "addToRoutine failed", thrown)
                notice.value = "Could not add it to that routine. Try again."
            }
        }
    }

    /** The workout in progress and the unit it is worded in, or null: none, finished, or unreadable. */
    private data class LiveInputs(val session: WorkoutSession, val unit: WeightUnit)

    /**
     * The workout in progress, read for the optional "Session in progress" card. It starts as
     * null and falls back to null on any failure: the in-progress read drops a failed value
     * rather than emitting one, and a Details screen that waited on it would spin for good.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveInputs: Flow<LiveInputs?> = combine(
        container.workoutRepository.observeInProgress()
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else container.workoutRepository.observeSession(id) }
            // The in-progress read and the session row can disagree for a moment when a workout
            // is finished; a finished workout is History's, not in progress.
            .map { session -> session?.takeIf { it.finishedAt == null } },
        container.preferencesRepository.weightUnit,
    ) { session, unit -> session?.let { LiveInputs(it, unit) } }
        .onStart { emit(null) }
        .catch { thrown ->
            AppLog.w(TAG, "the workout in progress could not be read for Details", thrown)
            emit(null)
        }
        .distinctUntilChanged()

    private data class SetsAndLive(val entries: List<ExerciseSetEntry>, val live: LiveInputs?)

    /**
     * The floor's Best set and Volume for this lift, from the floor's calculator, once the workout
     * in progress holds a working set of it: the floor's own rule for when those two appear
     * (ADR-030 decision 2). Before that set they would be last time's best and an empty volume
     * under a heading that says this session.
     */
    private fun sessionInProgress(live: LiveInputs?, entries: List<ExerciseSetEntry>): SessionInProgressStats? {
        if (live == null) return null
        if (ExerciseFloorStatsPresentation.workingSetsLoggedToday(live.session, exerciseId) <= 0) return null
        val stats = ExerciseFloorStatsCalculator.of(
            session = live.session,
            exerciseId = exerciseId,
            lastPerformance = null,
            // The floor judges Best set against workouts' finished working sets
            // (WorkoutRepository.historyBefore); a backdated activity is History's, not the
            // floor's record to beat.
            priorHistory = entries.filter { it.kind == HistoryKind.WORKOUT }.map { it.record },
            unit = live.unit,
        )
        return SessionInProgressStats(stats = stats, unit = live.unit)
    }

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        container.exerciseRepository.observeById(exerciseId).onEach { resolved.value = true },
        combine(container.completedTrainingRepository.observeExerciseSets(exerciseId), liveInputs) { entries, live ->
            SetsAndLive(entries, live)
        },
        container.preferencesRepository.schedulePreferences,
        resolved,
        combine(container.routineRepository.observeAll(), notice) { routines, message ->
            // A named pair rather than `to`: five heterogeneous flows is where combine's type
            // inference starts needing help, and a destructured Pair in the outer lambda is
            // exactly the shape that trips it.
            RoutinesAndNotice(routines, message)
        },
    ) { exercise, setsAndLive, preferences, isResolved, extras ->
        val entries = setsAndLive.entries
        ExerciseDetailUiState(
            isLoading = !isResolved,
            missing = isResolved && exercise == null,
            exercise = exercise,
            history = ExerciseHistoryBuilder.fromEntries(
                exerciseId = exerciseId,
                entries = entries,
                // From the library row, which this screen already has: a push-up's history is
                // counted in reps and a bench press's in kilograms.
                loadClass = LoadClass.of(exercise?.loadType),
                time = time,
                // The tonnage weeks have to start where the planner's weeks start, or "this
                // week's volume" means two different spans in two places in the same app.
                weekStart = preferences.weekStart,
            ),
            routines = extras.routines.map { routine ->
                RoutineMembership(
                    routine = routine,
                    alreadyHolds = routine.exercises.any { it.exercise.id == exerciseId },
                )
            },
            notice = extras.notice,
            sessionInProgress = sessionInProgress(setsAndLive.live, entries),
        )
    }
        // Bucketing a lift's whole history is real work and does not belong on the main thread,
        // and nor does the floor's calculator.
        .flowOn(container.computeDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseDetailUiState(),
        )
}
