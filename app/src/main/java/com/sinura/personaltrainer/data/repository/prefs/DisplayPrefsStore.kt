package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.flow.Flow

/** How numbers and times are shown, and which day the bodyweight prompt lands on. */
interface DisplayPrefs {
    val weightUnit: Flow<WeightUnit>
    val clockFormat: Flow<ClockFormat>

    /** Null is Auto: first training day of the week. */
    val bodyweightCheckInWeekday: Flow<Weekday?>

    suspend fun setWeightUnit(unit: WeightUnit)
    suspend fun setClockFormat(format: ClockFormat)
    suspend fun setBodyweightCheckInWeekday(day: Weekday?)
}

internal class DisplayPrefsStore(private val store: SettingsStore) : DisplayPrefs {
    override val weightUnit: Flow<WeightUnit> =
        store.pref { prefs -> WeightUnit.fromStorage(prefs[WEIGHT_UNIT]) }

    override val clockFormat: Flow<ClockFormat> =
        store.pref { prefs -> ClockFormat.fromStorage(prefs[CLOCK_FORMAT]) }

    override val bodyweightCheckInWeekday: Flow<Weekday?> =
        store.pref { prefs -> Weekday.fromStorage(prefs[BODYWEIGHT_CHECK_IN_WEEKDAY]) }

    override suspend fun setWeightUnit(unit: WeightUnit) {
        store.data.edit { prefs -> prefs[WEIGHT_UNIT] = unit.storageKey }
    }

    override suspend fun setClockFormat(format: ClockFormat) {
        store.data.edit { prefs -> prefs[CLOCK_FORMAT] = format.storageKey }
    }

    override suspend fun setBodyweightCheckInWeekday(day: Weekday?) {
        store.data.edit { prefs ->
            if (day == null) {
                prefs.remove(BODYWEIGHT_CHECK_IN_WEEKDAY)
            } else {
                prefs[BODYWEIGHT_CHECK_IN_WEEKDAY] = day.name
            }
        }
    }
}
