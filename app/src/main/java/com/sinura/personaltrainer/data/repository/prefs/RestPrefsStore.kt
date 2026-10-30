package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.RestTimerPreferences
import kotlinx.coroutines.flow.Flow

/** Sound, vibration, ticks, the default rest and the last preset. */
interface RestPrefs {
    val restTimerPreferences: Flow<RestTimerPreferences>

    /**
     * Exact-alarm special-access is requested only after rest is used or
     * configured. Onboarding must never write this. Restore leaves it alone.
     */
    val restAlarmEligible: Flow<Boolean>

    /**
     * First-rest Samsung battery mention (T-16). Device-local: restore and
     * [setRestTimerPreferences] leave it alone, so a backup cannot hide the
     * line on a new phone.
     */
    val restBatteryHintShown: Flow<Boolean>

    /** Sessions that have shown the in-workout rest floor (D13 caption budget). Device-local. */
    val restFloorCaptionSessions: Flow<Int>

    /**
     * The "Rest alerts" sentence before Android's notification prompt has been answered on this
     * phone (Continue, Not now, or closed) and is not put up again (audit RT-2). Device-local, like
     * [restBatteryHintShown]: restore and [setRestTimerPreferences] leave it alone.
     */
    val restAlertsAsked: Flow<Boolean>

    /** Rest-exterior prompt (overlay, exact alarm) answered on this phone; device-local. */
    val restExteriorAsked: Flow<Boolean>

    suspend fun markRestAlarmEligible()
    suspend fun markRestBatteryHintShown()
    /** Counts at most once per workout session id (D13 caption budget). */
    suspend fun recordRestFloorCaptionForSession(sessionId: String)
    suspend fun markRestAlertsAsked()
    suspend fun markRestExteriorAsked()
    suspend fun setRestSoundEnabled(enabled: Boolean)
    suspend fun setRestVibrationEnabled(enabled: Boolean)

    /**
     * Device-local on purpose: [setRestTimerPreferences] and the restore
     * path leave this key alone, so a backup from another phone cannot
     * switch the ticks on or off here.
     */
    suspend fun setRestTickEnabled(enabled: Boolean)

    /**
     * GET READY before a hold (P2b). Device-local like the tick: [setRestTimerPreferences] and the
     * restore path leave this key alone.
     */
    suspend fun setHoldLeadInSeconds(seconds: Int)
    suspend fun setDefaultRestSeconds(seconds: Int)
    suspend fun setLastRestPresetSeconds(seconds: Int)
    suspend fun setRestTimerPreferences(value: RestTimerPreferences)
}

internal class RestPrefsStore(private val store: SettingsStore) : RestPrefs {
    companion object {
        const val REST_FLOOR_CAPTION_SESSION_BUDGET = 3
    }
    override val restTimerPreferences: Flow<RestTimerPreferences> = store.pref { prefs ->
        RestTimerPreferences(
            soundEnabled = prefs[REST_SOUND] ?: true,
            vibrationEnabled = prefs[REST_VIBRATE] ?: true,
            tickEnabled = prefs[REST_TICK] ?: true,
            defaultRestSeconds = prefs[REST_DEFAULT] ?: RestTimerPreferences.DEFAULT_SECONDS,
            lastPresetSeconds = prefs[REST_LAST_PRESET],
            leadInSeconds = prefs[REST_LEAD_IN] ?: HoldWork.LEAD_IN_DEFAULT_SECONDS,
        ).sanitized()
    }

    override val restAlarmEligible: Flow<Boolean> =
        store.pref { prefs -> prefs[REST_ALARM_ELIGIBLE] ?: false }

    override val restBatteryHintShown: Flow<Boolean> =
        store.pref { prefs -> prefs[REST_BATTERY_HINT] ?: false }

    override val restFloorCaptionSessions: Flow<Int> =
        store.pref { prefs ->
            val ids = prefs[REST_FLOOR_CAPTION_SESSION_IDS]
            if (ids != null) {
                ids.size
            } else {
                prefs[REST_FLOOR_CAPTION_SESSIONS] ?: 0
            }
        }

    override val restAlertsAsked: Flow<Boolean> =
        store.pref { prefs -> prefs[REST_ALERTS_ASKED] ?: false }

    override val restExteriorAsked: Flow<Boolean> =
        store.pref { prefs -> prefs[REST_EXTERIOR_ASKED] ?: false }

    override suspend fun markRestAlarmEligible() {
        store.data.edit { prefs -> prefs[REST_ALARM_ELIGIBLE] = true }
    }

    override suspend fun markRestBatteryHintShown() {
        store.data.edit { prefs -> prefs[REST_BATTERY_HINT] = true }
    }

    override suspend fun recordRestFloorCaptionForSession(sessionId: String) {
        store.data.edit { prefs ->
            val existing = prefs[REST_FLOOR_CAPTION_SESSION_IDS]
            if (existing != null) {
                if (sessionId in existing) return@edit
                // One past the budget is kept so session budget+1 reads as over the budget.
                if (existing.size > REST_FLOOR_CAPTION_SESSION_BUDGET) return@edit
                prefs[REST_FLOOR_CAPTION_SESSION_IDS] = existing + sessionId
                return@edit
            }
            if (prefs[REST_FLOOR_CAPTION_LAST_SESSION] == sessionId) return@edit
            val legacyCount = prefs[REST_FLOOR_CAPTION_SESSIONS] ?: 0
            val previousLast = prefs[REST_FLOOR_CAPTION_LAST_SESSION]
            // The legacy count included the last session; pad with stand-in ids up to it so the
            // sessions already captioned before the migration still use up the budget.
            val padded = buildSet {
                previousLast?.let { add(it) }
                var pad = 0
                while (size < legacyCount && size <= REST_FLOOR_CAPTION_SESSION_BUDGET) {
                    add("legacy-$pad")
                    pad += 1
                }
            }
            val migrated = if (padded.size > REST_FLOOR_CAPTION_SESSION_BUDGET) padded else padded + sessionId
            prefs[REST_FLOOR_CAPTION_SESSION_IDS] = migrated
            prefs.remove(REST_FLOOR_CAPTION_SESSIONS)
            prefs.remove(REST_FLOOR_CAPTION_LAST_SESSION)
        }
    }

    override suspend fun markRestAlertsAsked() {
        store.data.edit { prefs -> prefs[REST_ALERTS_ASKED] = true }
    }

    override suspend fun markRestExteriorAsked() {
        store.data.edit { prefs -> prefs[REST_EXTERIOR_ASKED] = true }
    }

    override suspend fun setRestSoundEnabled(enabled: Boolean) {
        store.data.edit { prefs ->
            prefs[REST_SOUND] = enabled
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    override suspend fun setRestVibrationEnabled(enabled: Boolean) {
        store.data.edit { prefs ->
            prefs[REST_VIBRATE] = enabled
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    override suspend fun setRestTickEnabled(enabled: Boolean) {
        store.data.edit { prefs ->
            prefs[REST_TICK] = enabled
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    override suspend fun setHoldLeadInSeconds(seconds: Int) {
        store.data.edit { prefs -> prefs[REST_LEAD_IN] = HoldWork.leadInSeconds(seconds) }
    }

    override suspend fun setDefaultRestSeconds(seconds: Int) {
        store.data.edit { prefs ->
            prefs[REST_DEFAULT] = seconds.coerceIn(
                RestTimerPreferences.MIN_SECONDS,
                RestTimerPreferences.MAX_SECONDS,
            )
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    override suspend fun setLastRestPresetSeconds(seconds: Int) {
        store.data.edit { prefs ->
            prefs[REST_LAST_PRESET] = seconds.coerceIn(
                RestTimerPreferences.MIN_SECONDS,
                RestTimerPreferences.MAX_SECONDS,
            )
        }
    }

    override suspend fun setRestTimerPreferences(value: RestTimerPreferences) {
        val clean = value.sanitized()
        store.data.edit { prefs ->
            prefs[REST_SOUND] = clean.soundEnabled
            prefs[REST_VIBRATE] = clean.vibrationEnabled
            prefs[REST_DEFAULT] = clean.defaultRestSeconds
            if (clean.lastPresetSeconds == null) {
                prefs.remove(REST_LAST_PRESET)
            } else {
                prefs[REST_LAST_PRESET] = clean.lastPresetSeconds
            }
        }
    }
}
