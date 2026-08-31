package com.sinura.personaltrainer.domain

/**
 * Home and Plan list a day's blocks in hour order. The hour is a hidden
 * sort key after ADR-020 — not a clock the gym floor has to set.
 *
 * Moving a row permutes the existing hours so the new visual order is
 * the stored order, without inventing a sort column.
 */
object DayBlockOrder {
    data class HourMove(
        val occurrenceId: String,
        val ruleId: String,
        val hour: Int,
    )

    fun move(items: List<AgendaItem>, fromIndex: Int, delta: Int): List<HourMove> {
        if (items.size < 2) return emptyList()
        if (fromIndex !in items.indices) return emptyList()
        val toIndex = (fromIndex + delta).coerceIn(0, items.lastIndex)
        if (toIndex == fromIndex) return emptyList()
        val reordered = items.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        val hours = items.map { it.occurrence.hour }
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
