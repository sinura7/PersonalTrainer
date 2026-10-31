package com.sinura.personaltrainer.ui.start

import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.ui.workout.PreviewWeights
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TodaySheetStart
import com.sinura.personaltrainer.ui.home.HomeStartState
import com.sinura.personaltrainer.ui.home.HomeViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * The one start admission every Start surface goes through (blueprint V2, rule 6.1).
 *
 * One Activity-scoped instance is shared by Today and the [StartChooser] on Plan, History and
 * Body, so a start from any tab gets the same guards: a start is reserved before it launches
 * (a second tap from the same frame is refused), a rendered action only acts on its own
 * [HomeStartState.attemptId], nothing new is admitted while a navigation is still queued, and
 * a start refused by a live workout is held in [blockedByInProgress] for the user to resolve.
 */
interface StartGate {
    val startState: StateFlow<HomeStartState>
    val blockedByInProgress: StateFlow<HomeViewModel.BlockedStart?>
    val navigateToSession: StateFlow<String?>
    val navigateToCardio: StateFlow<String?>
    val navigateToComposer: StateFlow<String?>

    fun onSessionNavigationHandled()
    fun onCardioNavigationHandled()
    fun onComposerNavigationHandled()

    fun canDismissStart(): Boolean
    fun canEditStartChoice(): Boolean
    fun dismissStart(expectedAttemptId: Long? = null): Boolean
    fun retryStart(expectedAttemptId: Long? = null): Boolean

    fun startFreeWorkout(): Boolean
    fun startRoutine(routineId: String): Boolean
    fun startCardio(type: CardioType): Boolean
    fun startAux(packId: String): Boolean
    fun startOccurrence(occurrenceId: String, deliveryId: String? = null): Boolean
    fun startSuggestedDay(day: SuggestedTrainingDay): Boolean

    fun resumeBlocked(expectedBlocked: HomeViewModel.BlockedStart? = null, expectedAttemptId: Long? = null)
    fun discardBlockedAndStart(
        expectedBlocked: HomeViewModel.BlockedStart? = null,
        expectedAttemptId: Long? = null,
    ): Boolean
    fun dismissBlockedStart(expectedBlocked: HomeViewModel.BlockedStart? = null, expectedAttemptId: Long? = null)

    suspend fun previewWeights(routine: Routine): PreviewWeights

    /** Today's plan: the occurrence when the agenda owns the day, the leftover day otherwise. */
    fun startToday(start: TodaySheetStart): Boolean =
        start.occurrenceId?.let { startOccurrence(occurrenceId = it) }
            ?: start.leftover?.let { startSuggestedDay(day = it) }
            ?: false
}
