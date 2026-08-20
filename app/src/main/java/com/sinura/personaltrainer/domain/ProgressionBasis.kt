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
 * contains it, the top set is:
 *  1. the heaviest `weightKg`;
 *  2. tie-broken by the **most** reps at that weight;
 *  3. tie-broken again by the later `completedAt`.
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
 * Kept pure so the failure cases above are unit-testable without a database. The DAO's job is
 * only to find *which* session was last; the choice of set within it happens here.
 */
object ProgressionBasis {
    fun topWorkingSet(candidates: List<WorkingSetCandidate>): WorkingSetCandidate? =
        candidates.maxWithOrNull(
            compareBy<WorkingSetCandidate> { it.weightKg }
                .thenBy { it.reps }
                .thenBy { it.completedAt },
        )
}
