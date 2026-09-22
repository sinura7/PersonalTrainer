package com.sinura.personaltrainer.domain

/**
 * What Temper Account may do while its sync is being repaired.
 *
 * The 22 September 2026 whole-app audit found that a sync pass can delete rows on this phone.
 * Pulling a row back replaces it, and SQLite's replace is a delete plus an insert, so the
 * delete cascades to what hangs off the row: an activity block's sets, a routine's lifts, and
 * the routine link on finished workouts. Until the pull stops replacing rows, no pass runs at
 * all. Edits keep landing in the upload queue, so nothing waiting to upload is lost; it goes
 * up when sync resumes.
 *
 * In-app account deletion is off for a different reason. It deletes the cloud rows first and
 * then calls an Auth endpoint Supabase does not offer to a signed-in user, so even a run that
 * reached the network would leave the account behind with its data gone. It comes back with a
 * server-side delete.
 *
 * Each flag is read once, where the app is wired, and passed down as a plain value, so tests
 * can exercise both sides of it.
 */
object AccountSyncGate {
    /** No sync pass runs and none is scheduled. The upload queue still records edits. */
    const val SYNC_PAUSED: Boolean = true

    /** Settings → Account offers deletion in the app. Off: it says how to ask instead. */
    const val IN_APP_DELETE_AVAILABLE: Boolean = false
}
