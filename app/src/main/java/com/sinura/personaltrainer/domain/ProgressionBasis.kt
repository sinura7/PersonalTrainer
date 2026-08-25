package com.sinura.personaltrainer.domain

/** The fields of a logged working set that decide whether it is the session's top set. */
data class WorkingSetCandidate(
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
)

/**
 * Picks the set a progression decision is judged on.
 *
 * ## The rule
 * Among the **non-warmup** sets logged for one exercise in the **last finished session** that
 * contains it, the top set is the **hardest** one. Which set is hardest depends on what the
 * stored `weightKg` actually measures for that lift (see [WeightMeaning]):
 *
 *  - [WeightMeaning.LIFTED] / [WeightMeaning.ADDED] — the kilograms are load on the lifter, so
 *    the hardest set is the **heaviest** `weightKg`, tie-broken by the **most** reps, then the
 *    later `completedAt`.
 *  - [WeightMeaning.ASSISTANCE] — the kilograms are machine help *taken off* the lifter, so
 *    more of it is an **easier** set. The hardest set is the one with the **least** `weightKg`
 *    (least assistance), tie-broken by the **most** reps, then the later `completedAt`.
 *  - [WeightMeaning.NONE] — pure bodyweight has no external kilograms to compare, so reps are
 *    the measure: the hardest set is the one with the **most** reps, tie-broken by the later
 *    `completedAt`.
 *
 * ## Why not the chronologically last set
 * The engine used to read `ORDER BY completedAt DESC LIMIT 1` — literally the last set logged.
 * Any session ending on a lighter set therefore misread the whole workout. The canonical case
 * is a top set followed by a back-off: log 100 kg × 5 (target 5) then 80 kg × 8, and the app
 * concluded the lifter had cleared their target at 80 kg and prefilled 82.5 kg for the next
 * squat session — a 20 kg regression presented as progress.
 *
 * Anchoring on the heaviest set fixes that, and the reps tie-break means the fatigued final
 * set of a straight-sets session (100×5, 100×5, 100×4) is not the sole signal either: the
 * decision is made on the best reps achieved at the working weight.
 *
 * ## Why the meaning must be threaded in
 * `max(weightKg)` is only "hardest" when kilograms are load. For an assisted lift, where
 * `weightKg` is machine assistance, it picks the set with the *most* help — the easiest set —
 * as the basis: the exact mirror of the back-off bug this object was built to fix.
 * [ProgressionCalculator] already inverts the suggestion *direction* for assistance, but it can
 * only be right if it is fed the right basis set. So the caller — which already resolves the
 * exercise's [LoadClass] — passes the [WeightMeaning] here rather than the function guessing.
 *
 * Kept pure so the failure cases above are unit-testable without a database. The DAO's job is
 * only to find *which* session was last; the choice of set within it happens here.
 */
object ProgressionBasis {
    /**
     * @param meaning what the stored `weightKg` IS for this lift. Required, not defaulted,
     * because getting it wrong picks the easiest set as the basis — see the class doc.
     */
    fun topWorkingSet(
        candidates: List<WorkingSetCandidate>,
        meaning: WeightMeaning,
    ): WorkingSetCandidate? = candidates.maxWithOrNull(comparatorFor(meaning))

    private fun comparatorFor(meaning: WeightMeaning): Comparator<WorkingSetCandidate> =
        when (meaning) {
            // Load on the lifter: heavier is harder.
            WeightMeaning.LIFTED, WeightMeaning.ADDED ->
                compareBy<WorkingSetCandidate> { it.weightKg }
                    .thenBy { it.reps }
                    .thenBy { it.completedAt }
            // Machine help taken off the lifter: less assistance is harder.
            WeightMeaning.ASSISTANCE ->
                compareByDescending<WorkingSetCandidate> { it.weightKg }
                    .thenBy { it.reps }
                    .thenBy { it.completedAt }
            // No external load: reps are the only measure of a harder set.
            WeightMeaning.NONE ->
                compareBy<WorkingSetCandidate> { it.reps }
                    .thenBy { it.completedAt }
        }
}
