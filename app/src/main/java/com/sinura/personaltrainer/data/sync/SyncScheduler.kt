package com.sinura.personaltrainer.data.sync

/** Schedules background sync passes. Production uses WorkManager. */
fun interface SyncScheduler {
    fun enqueueOneShot()
}

object NoOpSyncScheduler : SyncScheduler {
    override fun enqueueOneShot() = Unit
}
