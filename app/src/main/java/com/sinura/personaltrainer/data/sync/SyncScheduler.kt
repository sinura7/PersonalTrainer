package com.sinura.personaltrainer.data.sync

/** Schedules background sync passes. Production uses WorkManager. */
fun interface SyncScheduler {
    fun enqueueOneShot()
}

object NoOpSyncScheduler : SyncScheduler {
    override fun enqueueOneShot() = Unit
}

/**
 * Enqueues nothing while [paused] ([com.sinura.personaltrainer.domain.AccountSyncGate]).
 *
 * Every path that asks for a pass — an authoring hook after a local commit, sign-in, the cold
 * start — goes through the one scheduler the container builds, so wrapping it here stops them
 * all without touching the hooks, which must keep writing the upload queue.
 */
class PausableSyncScheduler(
    private val delegate: SyncScheduler,
    private val paused: Boolean,
) : SyncScheduler {
    override fun enqueueOneShot() {
        if (paused) return
        delegate.enqueueOneShot()
    }
}
