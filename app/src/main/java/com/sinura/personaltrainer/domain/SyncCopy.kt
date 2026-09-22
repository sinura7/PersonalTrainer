package com.sinura.personaltrainer.domain

/**
 * Owner-facing sync status and error copy (Settings → Account when signed in).
 */
object SyncCopy {
    const val PAUSED_TITLE = "Sync paused"
    const val PAUSED_BODY =
        "Temper is fixing how Account saves to the cloud, so nothing uploads or downloads " +
            "for now. Training on this phone is unaffected."

    /**
     * Signed in only: the queue is written only with a session, and sign-out empties it
     * ([SyncStatusPort.abandonOutboxOnSignOut]), so the promise holds only while signed in.
     */
    const val PAUSED_QUEUE =
        "Changes you make wait here while you stay signed in and go up when sync resumes. " +
            "Signing out clears them; nothing on this phone is deleted."

    /** What a pass replicates ([SyncEntityType]) and, as plainly, what it does not. */
    const val SCOPE =
        "When it runs, Temper Account covers your weekly schedule, routines, cardio and " +
            "sessions logged after the fact, custom lifts, weigh-ins, goals, and coach, " +
            "reminder, and display settings. Workouts logged live on the floor, plan setup, " +
            "and rest timer settings stay on this phone for now, so keep a Backup of those."

    fun ownerFacingError(raw: String?): String {
        val message = raw?.trim().orEmpty()
        if (message.isEmpty()) {
            return "Sync could not finish. Try again when you are online."
        }
        return when {
            message.contains("401") || message.contains("403") ->
                "Cloud access was denied. Sign out and sign in again."
            message.contains("Supabase", ignoreCase = true) ||
                message.contains("failed (", ignoreCase = true) ->
                "Could not reach Temper Account. Check internet and try again."
            message.length > 120 ->
                "Sync could not finish. Try again when you are online."
            else -> message
        }
    }

    fun syncStatusLine(pending: Int, lastSuccessAtMs: Long?, lastError: String?): String {
        val parts = mutableListOf<String>()
        if (lastError != null) {
            parts.add(ownerFacingError(lastError))
        }
        if (pending > 0) {
            parts.add(
                if (pending == 1) {
                    "1 change waiting to upload."
                } else {
                    "$pending changes waiting to upload."
                },
            )
        }
        if (parts.isNotEmpty()) return parts.joinToString(" ")
        return when {
            lastSuccessAtMs != null -> "Last synced successfully."
            else -> "Sync has not run yet on this phone."
        }
    }
}
