package com.sinura.personaltrainer.domain

/**
 * Home and Plan list a day's blocks in hour order. The hour is a hidden
 * sort key after ADR-020 — not a clock the gym floor has to set.
 *
 * Moving a row permutes existing clock slots; the repository separates tied
 * slots using existing minutes so the visual order is stored without a new column.
 */
object DayBlockOrder {
    /** Keep distinct clock slots exact; separate ties minimally, including at day end.
     * Existing minute fields are sort/reminder keys, not new visible clock controls.
     */
    internal fun distinctMinutes(slots: List<Int>): List<Int> {
        require(slots.size <= 24 * 60 && slots.all { it in 0 until 24 * 60 })
        require(slots.zipWithNext().all { (a, b) -> a <= b })
        var previous = -1
        return slots.mapIndexed { index, minute ->
            val latest = 24 * 60 - 1 - (slots.lastIndex - index)
            maxOf(previous + 1, minOf(minute, latest)).also { previous = it }
        }
    }

    data class HourMove(
        val occurrenceId: String,
        val ruleId: String,
        val hour: Int,
    )

    fun move(items: List<AgendaItem>, fromIndex: Int, delta: Int): List<HourMove> {
        if (items.size < 2) return emptyList()
        if (fromIndex !in items.indices) return emptyList()
        val selected = items[fromIndex]
        if (selected.occurrence.status != OccurrenceStatus.PLANNED) return emptyList()
        val planned = items.filter { it.occurrence.status == OccurrenceStatus.PLANNED }
        if (planned.size < 2) return emptyList()
        val from = planned.indexOfFirst { it.occurrence.id == selected.occurrence.id }
        val toIndex = (from + delta).coerceIn(0, planned.lastIndex)
        if (toIndex == from) return emptyList()
        val reordered = planned.toMutableList()
        val moved = reordered.removeAt(from)
        reordered.add(toIndex, moved)
        val hours = planned.map { it.occurrence.hour }
        return reordered.mapIndexedNotNull { index, item ->
            val ruleId = item.rule?.id ?: return@mapIndexedNotNull null
            HourMove(
                occurrenceId = item.occurrence.id,
                ruleId = ruleId,
                hour = hours[index],
            )
        }
    }
}
