package com.sinura.personaltrainer.domain

/**
 * Child sync rows (activity blocks/sets/intervals, routine lifts) have no local revision.
 * Pull uses parent presence, queued local uploads, and remote [updatedAtMs] ordering.
 */
object SyncChildRow {
    /** Do not apply a remote change while the same row waits in the upload outbox. */
    fun remoteAppliesWhenNotLocallyQueued(hasLocalPendingUpload: Boolean): Boolean =
        !hasLocalPendingUpload
}
