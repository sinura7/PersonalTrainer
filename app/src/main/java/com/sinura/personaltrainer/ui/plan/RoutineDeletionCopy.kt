package com.sinura.personaltrainer.ui.plan

import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate

/** Weekly rules and dated planned work can differ after an explicit move. */
internal fun routineDeletionBody(state: PlanUiState, routineId: String): String {
    val rules = state.rules.filter { it.routineId == routineId }
    val ids = rules.map { it.id }.toSet()
    val weekly = rules.filter { it.enabled }.map { it.weekday }.distinct()
        .sortedBy { it.isoValue }.joinToString(", ") { it.titleLabel() }
    val dated = state.occurrences.filter { it.ruleId in ids && it.status == OccurrenceStatus.PLANNED }
        .map { it.localEpochDay }.distinct().sorted()
        .joinToString(", ") { DateCopy.weekdayShort(LocalDate.ofEpochDay(it)) }
    return "This cannot be undone. All planned sessions for this routine are removed and their reminders stop. " +
        (if (weekly.isEmpty()) "" else "Weekly schedule: $weekly. ") +
        (if (dated.isEmpty()) "" else "Planned dates in this week: $dated. ") +
        "Completed sessions stay saved in History. Past schedule outcomes stay unchanged."
}
