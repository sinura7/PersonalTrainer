package com.sinura.personaltrainer.domain

/** Per-entity conflict rule: higher [SyncEntityVersion.revision] wins; tie → later updatedAtMs. */
object SyncRevision {
    fun remoteWins(local: SyncEntityVersion, remote: SyncEntityVersion): Boolean {
        if (remote.revision != local.revision) return remote.revision > local.revision
        return remote.updatedAtMs > local.updatedAtMs
    }

    fun localWins(local: SyncEntityVersion, remote: SyncEntityVersion): Boolean =
        !remoteWins(local, remote) && (local.revision != remote.revision || local.updatedAtMs != remote.updatedAtMs)

    /**
     * A weigh-in stays when it was recorded after the delete itself.
     * The row clock can move forward while the delete time stays put, and that must not win.
     */
    fun recordedWeighInBeatsDelete(recordedAtMs: Long, deletedAtMs: Long): Boolean =
        recordedAtMs > deletedAtMs
}
