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
    override val blockedByInProgress = MutableStateFlow<HomeViewModel.BlockedStart?>(null)
    override val navigateToSession = MutableStateFlow<String?>(null)
    override val navigateToCardio = MutableStateFlow<String?>(null)
    override val navigateToComposer = MutableStateFlow<String?>(null)
    val starts = mutableListOf<String>()

    override fun onSessionNavigationHandled() { navigateToSession.value = null }
    override fun onCardioNavigationHandled() { navigateToCardio.value = null }
    override fun onComposerNavigationHandled() { navigateToComposer.value = null }
    override fun canDismissStart(): Boolean = true
    override fun canEditStartChoice(): Boolean = true
    override fun dismissStart(expectedAttemptId: Long?): Boolean = true
    override fun retryStart(expectedAttemptId: Long?): Boolean = false
    override fun startFreeWorkout(): Boolean = starts.add("free")
    override fun startRoutine(routineId: String): Boolean = starts.add("routine:$routineId")
    override fun startCardio(type: CardioType): Boolean = starts.add("cardio:${type.name}")
    override fun startAux(packId: String): Boolean = starts.add("aux:$packId")
    override fun startOccurrence(occurrenceId: String, deliveryId: String?): Boolean = starts.add("occurrence:$occurrenceId")
    override fun startSuggestedDay(day: SuggestedTrainingDay): Boolean = starts.add("suggested")
    override fun resumeBlocked(expectedBlocked: HomeViewModel.BlockedStart?, expectedAttemptId: Long?) = Unit
    override fun discardBlockedAndStart(expectedBlocked: HomeViewModel.BlockedStart?, expectedAttemptId: Long?): Boolean = false
    override fun dismissBlockedStart(expectedBlocked: HomeViewModel.BlockedStart?, expectedAttemptId: Long?) = Unit
    override suspend fun previewWeights(routine: Routine): PreviewWeights = PreviewWeights(emptyMap(), emptyMap())
}
