package com.sinura.personaltrainer.domain

/**
 * Owner-facing sync status and error copy (Settings → Account when signed in).
 */
object SyncCopy {
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
