package com.sinura.personaltrainer.domain

/** Per-entity conflict rule: higher [SyncEntityVersion.revision] wins; tie → later updatedAtMs. */
object SyncRevision {
    fun remoteWins(local: SyncEntityVersion, remote: SyncEntityVersion): Boolean {
        if (remote.revision != local.revision) return remote.revision > local.revision
        return remote.updatedAtMs > local.updatedAtMs
    }

    fun localWins(local: SyncEntityVersion, remote: SyncEntityVersion): Boolean =
        !remoteWins(local, remote) && (local.revision != remote.revision || local.updatedAtMs != remote.updatedAtMs)
}
