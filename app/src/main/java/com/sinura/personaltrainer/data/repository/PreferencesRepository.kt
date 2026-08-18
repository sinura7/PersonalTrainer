package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

class PreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.userSettingsDataStore

    val weightUnit: Flow<WeightUnit> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> WeightUnit.fromStorage(prefs[WEIGHT_UNIT]) }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { prefs ->
            prefs[WEIGHT_UNIT] = unit.storageKey
        }
    }

    val driveAccountEmail: Flow<String?> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> prefs[DRIVE_ACCOUNT] }

    val lastBackupAt: Flow<Long?> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> prefs[LAST_BACKUP_AT] }

    val lastBackupName: Flow<String?> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> prefs[LAST_BACKUP_NAME] }

    suspend fun setDriveAccountEmail(email: String?) {
        dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(DRIVE_ACCOUNT)
            } else {
                prefs[DRIVE_ACCOUNT] = email
            }
        }
    }

    suspend fun driveFolderId(): String? = dataStore.data.first()[DRIVE_FOLDER_ID]

    suspend fun setDriveFolderId(folderId: String?) {
        dataStore.edit { prefs ->
            if (folderId.isNullOrBlank()) {
                prefs.remove(DRIVE_FOLDER_ID)
            } else {
                prefs[DRIVE_FOLDER_ID] = folderId
            }
        }
    }

    suspend fun setLastBackup(fileName: String, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_BACKUP_NAME] = fileName
            prefs[LAST_BACKUP_AT] = atMillis
        }
    }

    suspend fun clearDriveSession() {
        dataStore.edit { prefs ->
            prefs.remove(DRIVE_ACCOUNT)
            prefs.remove(DRIVE_FOLDER_ID)
        }
    }

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val DRIVE_ACCOUNT = stringPreferencesKey("drive_account_email")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_NAME = stringPreferencesKey("last_backup_name")
    }
}
