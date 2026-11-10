package com.sinura.personaltrainer.ui.start

import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.ui.home.HomeStartState
import com.sinura.personaltrainer.ui.home.HomeViewModel
import com.sinura.personaltrainer.ui.workout.PreviewWeights
import kotlinx.coroutines.flow.MutableStateFlow

/** A [StartGate] that admits everything and records what the chooser asked it to start. */
class RecordingStartGate : StartGate {
    override val startState = MutableStateFlow(HomeStartState())
    override val homeReads = MutableStateFlow(com.sinura.personaltrainer.ui.home.HomeUiState(readState = com.sinura.personaltrainer.ui.home.HomeReadState.CURRENT))
    override val chooserGymId = MutableStateFlow<String?>(null)
    override val pendingGymCommand = MutableStateFlow<StartCommand?>(null)
    override fun retryRead(): Boolean = false
    override val blockedByInProgress = MutableStateFlow<HomeViewModel.BlockedStart?>(null)
    override val navigateToSession = MutableStateFlow<String?>(null)
    override val navigateToCardio = MutableStateFlow<String?>(null)
    override val navigateToComposer = MutableStateFlow<String?>(null)
    val starts = mutableListOf<String>()

    override fun onSessionNavigationHandled() { navigateToSession.value = null }
    override fun onCardioNavigationHandled() { navigateToCardio.value = null }
    override fun onComposerNavigationHandled() { navigateToComposer.value = null }
    override fun onChooserOpened() { chooserGymId.value = null }
    override fun setChooserGym(gymId: String) { chooserGymId.value = gymId }
    override fun clearPendingGymPick() { pendingGymCommand.value = null }
    override fun fulfillGymPick(gymId: String, expectedAttemptId: Long?): Boolean {
        val pending = pendingGymCommand.value ?: return false
        pendingGymCommand.value = null
        return requestStart(pending.withGym(gymId), expectedAttemptId)
    }
    override fun canDismissStart(): Boolean = true
    override fun canEditStartChoice(): Boolean = true
    override fun dismissStart(expectedAttemptId: Long?): Boolean = true
    override fun retryStart(expectedAttemptId: Long?): Boolean = false
    override fun startFreeWorkout(gymId: String): Boolean = starts.add("free:$gymId").let { true }
    override fun startRoutine(routineId: String, gymId: String): Boolean = starts.add("routine:$routineId:$gymId").let { true }
    override fun startCardio(type: CardioType): Boolean = starts.add("cardio:${type.name}").let { true }
    override fun startAux(packId: String): Boolean = starts.add("aux:$packId").let { true }
    override fun startOccurrence(occurrenceId: String, gymId: String, deliveryId: String?): Boolean =
        starts.add("occurrence:$occurrenceId:$gymId").let { true }
    override fun startSuggestedDay(day: SuggestedTrainingDay, gymId: String): Boolean =
        starts.add("suggested:$gymId").let { true }
    override fun resumeBlocked(expectedBlocked: HomeViewModel.BlockedStart?, expectedAttemptId: Long?) = Unit
    override fun discardBlockedAndStart(expectedBlocked: HomeViewModel.BlockedStart?, expectedAttemptId: Long?): Boolean = false
    override fun dismissBlockedStart(expectedBlocked: HomeViewModel.BlockedStart?, expectedAttemptId: Long?) = Unit
    override suspend fun previewWeights(routine: Routine, gymId: String): PreviewWeights =
        PreviewWeights(emptyMap(), emptyMap())
    override fun requestStart(command: StartCommand, expectedAttemptId: Long?): Boolean {
        if (command.requiresGym() && command.gymId == null) {
            pendingGymCommand.value = command
            return false
        }
        starts.add("request:${command::class.simpleName}")
        return true
    }
}
