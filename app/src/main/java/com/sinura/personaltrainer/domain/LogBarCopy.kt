package com.sinura.personaltrainer.domain

/**
 * What the pinned log button says (W-11).
 *
 * The Warm-up chip used to leave the Volt act looking like a working set.
 * The verb changes: [LOG_SET] vs [LOG_WARMUP]. The draft payload stays on
 * the button so the tap is never blind.
 */
object LogBarCopy {
    const val NEXT = "Next"
    const val LOG_SET = "Log set"
    const val LOG_WARMUP = "Log warm-up"
    const val SAVE_SET = "Save set"
    const val SAVE_WARMUP = "Save warm-up"

    /**
     * @param editing a logged row is open for repair; Save, not Log.
     * @param next the lift is done and the button advances; Next wins
     *   unless a repair is open.
     * @param warmup the Warm-up chip is on, or the row being saved was
     *   a warm-up.
     * @param draftLabel the load × reps about to be written, already
     *   formatted in the user's unit.
     */
    fun commit(
        editing: Boolean,
        next: Boolean,
        warmup: Boolean,
        draftLabel: String,
    ): String {
        if (next && !editing) return NEXT
        val verb = when {
            editing && warmup -> SAVE_WARMUP
            editing -> SAVE_SET
            warmup -> LOG_WARMUP
            else -> LOG_SET
        }
        val payload = draftLabel.trim()
        return if (payload.isEmpty()) verb else "$verb · $payload"
    }
}
