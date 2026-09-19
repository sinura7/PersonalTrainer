package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.CompletedTrainingDetailLoad
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A failed observation is recoverable; it is never evidence of an empty workout. */
internal data class WorkoutSessionRead(
    val loadState: SessionLoadState = SessionLoadState.LOADING,
    val session: WorkoutSession? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkoutSessionReader(
    repository: WorkoutRepository,
    sessionId: String,
    scope: CoroutineScope,
) {
    private val revision = MutableStateFlow(0L)

    val observations = revision.flatMapLatest {
        flow {
            emit(WorkoutSessionRead())
            if (sessionId.isBlank()) {
                emit(WorkoutSessionRead(SessionLoadState.MISSING))
            } else {
                emitAll(repository.observeSessionHealth(sessionId).map { health ->
                    val read = CompletedTrainingDetailLoad.from(health)
                    WorkoutSessionRead(
                        loadState = when {
                            read.failed -> SessionLoadState.FAILED
                            read.missing -> SessionLoadState.MISSING
                            else -> SessionLoadState.FOUND
                        },
                        session = read.value,
                    )
                })
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, WorkoutSessionRead())

    fun retry() {
        revision.value += 1
    }
}
