package com.sinura.personaltrainer.ui.start

import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.HomeStart
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule

/** Group 1 of the Start chooser (v4 blueprint 5.1). */
enum class StartTodayGroup { RESUME_LIVE, PLANNED_TODAY, REST_DAY, NO_PLAN }

/** The next planned day after today, for the rest-day hint ("Next: Thu, Push day"). */
data class StartNextPlanned(val epochDay: Long, val title: String)

object StartGroups {
    /** The chooser shows at most this many routine rows before "All routines". */
    const val ROUTINE_LIMIT = 5
    private const val LOOKAHEAD_DAYS = 7

    fun today(state: StartOptionsUiState): StartTodayGroup = when {
        state.inProgress != null || state.liveActivity != null -> StartTodayGroup.RESUME_LIVE
        state.todayStart != null -> StartTodayGroup.PLANNED_TODAY
        state.hasPlan -> StartTodayGroup.REST_DAY
        else -> StartTodayGroup.NO_PLAN
    }

    /** Starts other than Resume are disabled while a workout or activity is live (D2). */
    fun startsEnabled(state: StartOptionsUiState): Boolean =
        !state.isLoading && state.inProgress == null && state.liveActivity == null

    /** Named routines in the order Home has always used; aux packs stay on their own page. */
    fun routines(routines: List<Routine>): List<Routine> = HomeStart.namedRoutines(routines)

    fun nextPlanned(
        today: Long,
        occurrences: List<ScheduleOccurrence>,
        rules: List<ScheduleRule>,
        routineNames: Map<String, String>,
    ): StartNextPlanned? {
        for (ahead in 1..LOOKAHEAD_DAYS) {
            val day = today + ahead
            val item = DailyAgenda.startable(DailyAgenda.forDay(day, occurrences, rules, routineNames)).firstOrNull()
            if (item != null) return StartNextPlanned(epochDay = day, title = item.title)
        }
        return null
    }
}
