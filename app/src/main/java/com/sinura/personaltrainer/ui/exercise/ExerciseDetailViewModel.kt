package com.sinura.personaltrainer.ui.exercise

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseHistory
import com.sinura.personaltrainer.domain.ExerciseHistoryBuilder
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
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
            val defaults = AddDefaults.forExercise(exercise)
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

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        container.exerciseRepository.observeById(exerciseId).onEach { resolved.value = true },
        container.workoutRepository.observeExerciseSets(exerciseId),
        container.preferencesRepository.schedulePreferences,
        resolved,
        combine(container.routineRepository.observeAll(), notice) { routines, message ->
            // A named pair rather than `to`: five heterogeneous flows is where combine's type
            // inference starts needing help, and a destructured Pair in the outer lambda is
            // exactly the shape that trips it.
            RoutinesAndNotice(routines, message)
        },
    ) { exercise, entries, preferences, isResolved, extras ->
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
        )
    }
        // Bucketing a lift's whole history is real work and does not belong on the main thread.
        .flowOn(container.computeDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseDetailUiState(),
        )
}
