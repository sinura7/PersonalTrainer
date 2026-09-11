package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.Weekday
import kotlinx.coroutines.flow.Flow

/** Days per week, split, week start, the chosen days, and which week is the lighter one. */
interface PlanningPrefs {
    val schedulePreferences: Flow<SchedulePreferences>
    val preferredDays: Flow<Set<Weekday>>

    /**
     * The week-start epoch day marked lighter, or null when none is.
     *
     * A past value is inert: readers compare it to this week's start. Clearing is writing
     * null, not deleting a row that no longer matches.
     */
    val lighterWeekStartEpochDay: Flow<Long?>

    suspend fun setTrainingDaysPerWeek(days: Int)
    suspend fun setSplitStyle(style: SplitStyle)
    suspend fun setWeekStart(day: Weekday)
    suspend fun setPreferredDays(days: Set<Weekday>)
    suspend fun setSchedulePreferences(value: SchedulePreferences)
    suspend fun setLighterWeekStartEpochDay(epochDay: Long?)
}

internal class PlanningPrefsStore(private val store: SettingsStore) : PlanningPrefs {
    override val schedulePreferences: Flow<SchedulePreferences> = store.pref { prefs ->
        SchedulePreferences(
            trainingDaysPerWeek = prefs[TRAINING_DAYS] ?: SchedulePreferences.DEFAULT_DAYS,
            splitStyle = SplitStyle.fromStorage(prefs[SPLIT_STYLE]),
            weekStart = SchedulePreferences.weekStartFromStorage(prefs[WEEK_START]),
        ).sanitized()
    }

    override val preferredDays: Flow<Set<Weekday>> =
        store.pref { prefs -> preferredDaysFrom(prefs[PREFERRED_DAYS]) }

    override val lighterWeekStartEpochDay: Flow<Long?> = store.pref { it[LIGHTER_WEEK_START] }

    override suspend fun setTrainingDaysPerWeek(days: Int) {
        store.data.edit { prefs ->
            val clean = days.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS)
            prefs[TRAINING_DAYS] = clean
            val preferred = preferredDaysFrom(prefs[PREFERRED_DAYS])
            if (preferred.size > clean) {
                val weekStart = SchedulePreferences.weekStartFromStorage(prefs[WEEK_START])
                val ordered = (0 until 7).map { weekStart.plus(it.toLong()) }
                prefs[PREFERRED_DAYS] =
                    ordered.filter { it in preferred }.take(clean).map { it.name }.toSet()
            }
        }
    }

    override suspend fun setSplitStyle(style: SplitStyle) {
        store.data.edit { prefs -> prefs[SPLIT_STYLE] = style.storageKey }
    }

    override suspend fun setWeekStart(day: Weekday) {
        store.data.edit { prefs -> prefs[WEEK_START] = day.name }
    }

    override suspend fun setPreferredDays(days: Set<Weekday>) {
        store.data.edit { prefs -> prefs[PREFERRED_DAYS] = days.map { it.name }.toSet() }
    }

    override suspend fun setSchedulePreferences(value: SchedulePreferences) {
        val clean = value.sanitized()
        store.data.edit { prefs ->
            prefs[TRAINING_DAYS] = clean.trainingDaysPerWeek
            prefs[SPLIT_STYLE] = clean.splitStyle.storageKey
            prefs[WEEK_START] = clean.weekStart.name
        }
    }

    override suspend fun setLighterWeekStartEpochDay(epochDay: Long?) {
        store.data.edit { prefs ->
            if (epochDay == null) {
                prefs.remove(LIGHTER_WEEK_START)
            } else {
                prefs[LIGHTER_WEEK_START] = epochDay
            }
        }
    }
}
