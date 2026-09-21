package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.SavePostureState
import com.sinura.personaltrainer.domain.inferLegacySavePosture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface SavePosturePrefs {
    val savePostureState: Flow<SavePostureState>

    suspend fun setSavePosture(posture: SavePosture)

    suspend fun ensureLegacyMigrated(accountSignedIn: Boolean)
}

internal class SavePosturePrefsStore(private val store: SettingsStore) : SavePosturePrefs {
    override val savePostureState: Flow<SavePostureState> = store.pref { prefs ->
        val chosen = prefs[SAVE_POSTURE_CHOSEN] ?: false
        val posture = SavePosture.fromStorage(prefs[SAVE_POSTURE]) ?: SavePosture.LOCAL
        SavePostureState(chosen = chosen, posture = posture)
    }

    override suspend fun setSavePosture(posture: SavePosture) {
        store.data.edit { prefs ->
            prefs[SAVE_POSTURE_CHOSEN] = true
            prefs[SAVE_POSTURE] = posture.name
        }
    }

    override suspend fun ensureLegacyMigrated(accountSignedIn: Boolean) {
        if (savePostureState.first().chosen) return
        val prefs = store.snapshot()
        val legacy = inferLegacySavePosture(
            launchPermissionsAsked = prefs[LAUNCH_PERMISSIONS_ASKED] ?: false,
            onboardingComplete = prefs[ONBOARDING_COMPLETE] ?: false,
            driveAccountEmail = prefs[DRIVE_ACCOUNT],
            accountSignedIn = accountSignedIn,
        ) ?: return
        setSavePosture(legacy)
    }
}
