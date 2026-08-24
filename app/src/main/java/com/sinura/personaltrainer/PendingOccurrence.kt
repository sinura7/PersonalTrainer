package com.sinura.personaltrainer

import kotlinx.coroutines.flow.first

/**
 * The in-progress planned occurrence, if any.
 *
 * Cardio and mixed sessions write `occurrenceId` on the activity row.
 * Strength still goes through the legacy workout logger, so the binding
 * lives here and is persisted in DataStore. Process death must not drop
 * it: finishing that workout still marks the occurrence DONE.
 */
object PendingOccurrence {
    suspend fun bind(deps: AppDependencies, occurrenceId: String?) {
        deps.pendingOccurrenceId.value = occurrenceId
        deps.preferencesRepository.setPendingOccurrenceId(occurrenceId)
    }

    suspend fun restore(deps: AppDependencies) {
        deps.pendingOccurrenceId.value = deps.preferencesRepository.pendingOccurrenceId.first()
    }

    suspend fun complete(deps: AppDependencies, completedId: String) {
        val occ = deps.pendingOccurrenceId.value
            ?: deps.preferencesRepository.pendingOccurrenceId.first()
            ?: return
        deps.plannerRepository.markOccurrenceDone(occ, completedId)
        bind(deps, null)
    }

    suspend fun forget(deps: AppDependencies) {
        bind(deps, null)
    }
}
