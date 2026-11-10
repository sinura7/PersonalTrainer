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
 * Strength starts require a non-null [gymId] (D5). Cardio and Extra warm-up packs skip the pick.
 */
interface StartGate {
    val startState: StateFlow<HomeStartState>
    val blockedByInProgress: StateFlow<HomeViewModel.BlockedStart?>
    val homeReads: StateFlow<com.sinura.personaltrainer.ui.home.HomeUiState>
    /** Gym picked on the Start chooser this opening; reset to null whenever the sheet opens. */
    val chooserGymId: StateFlow<String?>
    /** A start that still needs a gym before it can launch (GymPickSheet). */
    val pendingGymCommand: StateFlow<StartCommand?>

    fun retryRead(): Boolean
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

    /** Called when the Start chooser sheet opens so nothing is preselected (D5). */
    fun onChooserOpened()

    fun setChooserGym(gymId: String)

    fun clearPendingGymPick()

    /** Complete a [pendingGymCommand] with the chosen gym and admit the start. */
    fun fulfillGymPick(gymId: String, expectedAttemptId: Long? = null): Boolean

    fun startFreeWorkout(gymId: String): Boolean
    fun startRoutine(routineId: String, gymId: String): Boolean
    fun startCardio(type: CardioType): Boolean
    fun startAux(packId: String, gymId: String): Boolean
    fun startOccurrence(occurrenceId: String, gymId: String, deliveryId: String? = null): Boolean
    fun startSuggestedDay(day: SuggestedTrainingDay, gymId: String): Boolean

    fun resumeBlocked(expectedBlocked: HomeViewModel.BlockedStart? = null, expectedAttemptId: Long? = null)
    fun discardBlockedAndStart(
        expectedBlocked: HomeViewModel.BlockedStart? = null,
        expectedAttemptId: Long? = null,
    ): Boolean
    fun dismissBlockedStart(expectedBlocked: HomeViewModel.BlockedStart? = null, expectedAttemptId: Long? = null)

    suspend fun previewWeights(routine: Routine, gymId: String): PreviewWeights

    fun startToday(start: TodaySheetStart, gymId: String): Boolean =
        start.occurrenceId?.let { startOccurrence(occurrenceId = it, gymId = gymId) }
            ?: start.leftover?.let { startSuggestedDay(day = it, gymId = gymId) }
            ?: false

    /** Central admission: refuses strength commands without [StartCommand.gymId]. */
    fun requestStart(command: StartCommand, expectedAttemptId: Long? = null): Boolean
}
