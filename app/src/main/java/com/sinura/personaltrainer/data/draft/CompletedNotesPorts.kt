package com.sinura.personaltrainer.data.draft

import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.domain.ActivityWrite

/** Internal adapter retains the two existing completed-training capabilities and identities. */
internal fun completedNotesDrafts(store: DurableDraftStore, container: AppDependencies, restoreOpen: () -> Boolean): CompletedNotesDrafts =
    CompletedNotesDrafts(
        store = store, maintenance = container.dbMaintenance, restoreOpen = restoreOpen,
        dispatcher = container.ioDispatcher, nowMillis = container.time::nowMillis,
        readRecord = { owner -> when (owner.kind) {
            CompletedNoteKind.STRENGTH -> container.workoutRepository.getSession(owner.id)?.let(CompletedNoteRecord::strength)
            CompletedNoteKind.ACTIVITY -> container.activityRepository.get(owner.id)?.let(CompletedNoteRecord::activity)
        } },
        writeLocked = { owner, before, intended, time -> when (owner.kind) {
            CompletedNoteKind.STRENGTH -> container.workoutRepository.updateCompletedNotesIfUnchanged(owner.id, before, intended)
            CompletedNoteKind.ACTIVITY -> {
                val write = container.activityRepository.updateCompletedNotesLocked(owner.id, intended, time, before)
                check(write is ActivityWrite.Accepted) { "Completed notes were refused" }
            }
        } },
    )
