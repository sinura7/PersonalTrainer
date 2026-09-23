package com.sinura.personaltrainer.domain

/**
 * Tap-order cart for multi-add.
 *
 * A [Set] can say which lifts are chosen. It cannot say which was first. The
 * routine stores that as [RoutineExercise.sortOrder], and Home and Plan read
 * the same order, so the picker has to keep it.
 *
 * The cart is now a view of what is already stored rather than a staging area in
 * front of it: a tap writes the lift through to the routine or the week's day, and
 * the numbers count the session as it stands. See [picked] for the two lists that
 * make it, and [PendingPick] for the gap between them.
 */
object LiftCart {
    fun sanitize(order: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (id in order) {
            val trimmed = id.trim()
            if (trimmed.isNotEmpty()) seen += trimmed
        }
        return seen.toList()
    }

    fun cartNumber(order: List<String>, id: String): Int? {
        val index = sanitize(order).indexOf(id.trim())
        return if (index >= 0) index + 1 else null
    }

    fun mergeSources(primary: List<Exercise>, extra: List<Exercise>): List<Exercise> {
        val have = primary.map { it.id }.toSet()
        return primary + extra.filter { it.id !in have }
    }

    /**
     * A just-created lift has to appear in the picker before Room's catalog
     * flow catches up. Once it is in [results], extra is a duplicate, not a pin.
     */
    fun visibleResults(
        results: List<Exercise>,
        extra: List<Exercise>,
        query: String,
    ): List<Exercise> {
        val needle = query.trim()
        val gap = extra.filter { exercise ->
            results.none { it.id == exercise.id } &&
                (needle.isEmpty() || exercise.name.contains(needle, ignoreCase = true))
        }
        return mergeSources(gap, results)
    }

    fun resolve(order: List<String>, sources: List<Exercise>): List<Exercise> {
        val byId = sources.associateBy { it.id }
        return sanitize(order).mapNotNull { byId[it] }
    }

    /**
     * The order the picker draws: the lifts the session already holds, plus the taps
     * whose write has not landed yet, minus the taps that are taking one back out.
     *
     * [committed] is the truth — the routine's rows, or the day's — and [pending] only
     * covers the moment between a finger leaving the screen and the store catching up.
     * Without it a tap would show nothing for a frame or two and the second tap on the
     * same row would add the lift twice.
     */
    fun picked(committed: List<String>, pending: List<PendingPick>): List<String> {
        val dropping = pending.filterNot { it.adding }.map { it.id }.toSet()
        val adding = pending.filter { it.adding }.map { it.id }
        return sanitize(sanitize(committed).filterNot { it in dropping } + adding)
    }

    /** True when the next tap on [id] should add it, false when it should take it out. */
    fun addsOnTap(committed: List<String>, pending: List<PendingPick>, id: String): Boolean =
        id.trim() !in picked(committed, pending)

    /**
     * Records a tap, replacing whatever intent was held for the same lift. [tap] names this
     * tap, so its write can later mark exactly it as landed and not a newer tap that has
     * replaced it.
     */
    fun record(pending: List<PendingPick>, id: String, adding: Boolean, tap: Long = 0L): List<PendingPick> {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return pending
        return pending.filterNot { it.id == trimmed } + PendingPick(trimmed, adding, tap)
    }

    /**
     * Marks tap [tap] on [id] as written. A newer tap on the same lift has replaced it and is
     * still waiting for its own write, so it is left as it is.
     */
    fun land(pending: List<PendingPick>, id: String, tap: Long): List<PendingPick> {
        val trimmed = id.trim()
        return pending.map { if (it.id == trimmed && it.tap == tap) it.copy(landed = true) else it }
    }

    /**
     * Drops the taps whose write has landed and that [committed] now agrees with, and only
     * those.
     *
     * A landed tap the screen agrees with is indistinguishable from one that never happened,
     * so holding it any longer would be holding a second opinion about the same lift. A tap
     * still waiting for its write stays even when [committed] happens to agree already: a
     * second tap on a lift whose add is still writing says "take it out", the screen says
     * "not there" until the add lands, and dropping it then would show the lift chosen again
     * once the add arrives.
     */
    fun settle(committed: List<String>, pending: List<PendingPick>): List<PendingPick> {
        val stored = sanitize(committed).toSet()
        return pending.filter { !it.landed || (it.id in stored) != it.adding }
    }

    /**
     * Forgets one lift's tap: the write failed, so the store's answer is the only one. With
     * [tap], only that tap; a newer tap on the same lift keeps its own intent.
     */
    fun forget(pending: List<PendingPick>, id: String, tap: Long? = null): List<PendingPick> =
        pending.filterNot { it.id == id.trim() && (tap == null || it.tap == tap) }
}

/**
 * One tap that has been made but not yet stored.
 *
 * [adding] is what the tap meant, not what is stored: false is a lift being taken back
 * out of the session, which is the same list and the same wait as putting one in. [tap]
 * tells two taps on the same lift apart, and [landed] says this tap's own write is done.
 */
data class PendingPick(
    val id: String,
    val adding: Boolean,
    val tap: Long = 0L,
    val landed: Boolean = false,
)
