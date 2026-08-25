package com.sinura.personaltrainer.domain

/**
 * Tap-order cart for multi-add.
 *
 * A [Set] can say which lifts are chosen. It cannot say which was first. The
 * routine stores that as [RoutineExercise.sortOrder], and Home and Plan read
 * the same order, so the picker has to keep it.
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

    fun toggle(order: List<String>, id: String): List<String> {
        val clean = sanitize(order)
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return clean
        return if (trimmed in clean) clean.filter { it != trimmed } else clean + trimmed
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

    fun planConfirm(
        order: List<String>,
        sources: List<Exercise>,
        already: Set<String>,
    ): CartConfirm {
        val selected = sanitize(order)
        val byId = sources.associateBy { it.id }
        val missingIds = selected.filter { it !in byId }
        if (missingIds.isNotEmpty()) {
            return CartConfirm(selected = selected, toAdd = emptyList(), missingIds = missingIds)
        }
        val toAdd = selected.mapNotNull { byId[it] }.filter { it.id !in already }
        return CartConfirm(selected = selected, toAdd = toAdd, missingIds = emptyList())
    }
}

data class CartConfirm(
    val selected: List<String>,
    val toAdd: List<Exercise>,
    val missingIds: List<String>,
) {
    val blocked: Boolean get() = missingIds.isNotEmpty()
    val nothingNew: Boolean get() = !blocked && toAdd.isEmpty()
}
