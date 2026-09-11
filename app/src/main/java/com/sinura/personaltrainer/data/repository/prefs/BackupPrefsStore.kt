package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.data.repository.AutoBackupSettings
import kotlinx.coroutines.flow.Flow

/**
 * The Drive account, the automatic-backup arming, and the written / verified / restored stamps.
 *
 * Nineteen of the forty-six keys in `user_settings` are about backup, and they were mixed in
 * with the weight unit and the rest-timer default in one 1,070-line class. This is also the
 * only part of preferences another repository genuinely needs: `BackupRepository` takes a
 * whole `PreferencesRepository` to reach these nineteen, which is why it ended up with nine
 * constructor parameters.
 */
interface BackupPrefs {
    val driveAccountEmail: Flow<String?>
    val lastBackupAt: Flow<Long?>
    val lastBackupName: Flow<String?>

    /**
     * The last backup that was read back out of Drive, decrypted and found to carry the history
     * that was written. Device-local and deliberately absent from the backup document: a
     * verification is a statement about THIS phone's copy, and restoring one onto a new phone
     * would import a reassurance that had never been earned there.
     */
    val lastVerifiedBackupAt: Flow<Long?>
    val lastVerifiedBackupName: Flow<String?>
    val lastRestoreAt: Flow<Long?>
    val lastRestoreName: Flow<String?>

    /** A durable note from restore recovery, shown until the owner dismisses it. */
    val restoreRecoveryNote: Flow<String?>

    /**
     * Automatic backup after a finished workout. Device-local, all four keys: none of them
     * appears in [com.sinura.personaltrainer.data.backup.BackupPreferences], which is a
     * hand-listed set rather than a sweep, so they are excluded by construction. That is
     * deliberate — AUTO_BACKUP_SECRET is ciphertext under a non-exportable Keystore key, so
     * restoring it onto another phone, or onto this one after a reinstall, would write a
     * blob nothing can open over a passphrase the owner had just entered.
     */
    val autoBackupEnabled: Flow<Boolean>

    /** Set when an unattended copy found the Drive grant lapsed. Settings surfaces it. */
    val autoBackupNeedsSignIn: Flow<Boolean>

    suspend fun setDriveAccountEmail(email: String?)

    /**
     * One snapshot, read once. Separate `.first()` calls each re-collect and can observe
     * different write generations, so a toggle flipped as a workout ends could be seen as
     * enabled with no secret.
     */
    suspend fun autoBackupSettings(): AutoBackupSettings

    /** Arming is one write so a crash cannot leave the toggle on with no secret behind it. */
    suspend fun armAutoBackup(sealedPassphrase: String)

    /** Turning it off forgets the passphrase too: an unopenable secret helps nobody. */
    suspend fun disarmAutoBackup()
    suspend fun setAutoBackupNeedsSignIn(needsSignIn: Boolean)

    /** Written only after an upload returns, so a failed copy is retried rather than skipped. */
    suspend fun setAutoBackupLastSession(sessionId: String)

    /** One-shot, for the account-change guard, which must read before it writes. */
    suspend fun driveAccountEmailOnce(): String?
    suspend fun driveFolderId(): String?
    suspend fun setDriveFolderId(folderId: String?)
    suspend fun setRestoreRecoveryNote(note: String?)
    suspend fun setLastRestore(fileName: String, atMillis: Long)
    suspend fun setLastBackup(fileName: String, atMillis: Long)
    suspend fun setLastVerifiedBackup(fileName: String, atMillis: Long)
    suspend fun clearDriveSession()
}

internal class BackupPrefsStore(private val store: SettingsStore) : BackupPrefs {
    override val driveAccountEmail: Flow<String?> = store.pref { it[DRIVE_ACCOUNT] }
    override val lastBackupAt: Flow<Long?> = store.pref { it[LAST_BACKUP_AT] }
    override val lastBackupName: Flow<String?> = store.pref { it[LAST_BACKUP_NAME] }
    override val lastVerifiedBackupAt: Flow<Long?> = store.pref { it[LAST_VERIFIED_BACKUP_AT] }
    override val lastVerifiedBackupName: Flow<String?> =
        store.pref { it[LAST_VERIFIED_BACKUP_NAME] }
    override val lastRestoreAt: Flow<Long?> = store.pref { it[LAST_RESTORE_AT] }
    override val lastRestoreName: Flow<String?> = store.pref { it[LAST_RESTORE_NAME] }
    override val restoreRecoveryNote: Flow<String?> = store.pref { it[RESTORE_RECOVERY_NOTE] }
    override val autoBackupEnabled: Flow<Boolean> =
        store.pref { it[AUTO_BACKUP_ENABLED] ?: false }
    override val autoBackupNeedsSignIn: Flow<Boolean> =
        store.pref { it[AUTO_BACKUP_NEEDS_SIGN_IN] ?: false }

    override suspend fun setDriveAccountEmail(email: String?) {
        store.data.edit { prefs ->
            if (email.isNullOrBlank()) prefs.remove(DRIVE_ACCOUNT) else prefs[DRIVE_ACCOUNT] = email
        }
    }

    override suspend fun autoBackupSettings(): AutoBackupSettings {
        val prefs = store.snapshot()
        return AutoBackupSettings(
            enabled = prefs[AUTO_BACKUP_ENABLED] ?: false,
            sealedPassphrase = prefs[AUTO_BACKUP_SECRET]?.takeIf { it.isNotBlank() },
            lastBackedUpSessionId = prefs[AUTO_BACKUP_LAST_SESSION]?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun armAutoBackup(sealedPassphrase: String) {
        store.data.edit { prefs ->
            prefs[AUTO_BACKUP_ENABLED] = true
            prefs[AUTO_BACKUP_SECRET] = sealedPassphrase
            prefs.remove(AUTO_BACKUP_NEEDS_SIGN_IN)
        }
    }

    override suspend fun disarmAutoBackup() {
        store.data.edit { prefs ->
            prefs.remove(AUTO_BACKUP_ENABLED)
            prefs.remove(AUTO_BACKUP_SECRET)
            prefs.remove(AUTO_BACKUP_NEEDS_SIGN_IN)
            prefs.remove(AUTO_BACKUP_LAST_SESSION)
        }
    }

    override suspend fun setAutoBackupNeedsSignIn(needsSignIn: Boolean) {
        store.data.edit { prefs ->
            if (needsSignIn) {
                prefs[AUTO_BACKUP_NEEDS_SIGN_IN] = true
            } else {
                prefs.remove(AUTO_BACKUP_NEEDS_SIGN_IN)
            }
        }
    }

    override suspend fun setAutoBackupLastSession(sessionId: String) {
        store.data.edit { prefs -> prefs[AUTO_BACKUP_LAST_SESSION] = sessionId }
    }

    override suspend fun driveAccountEmailOnce(): String? =
        store.snapshot()[DRIVE_ACCOUNT]?.takeIf { it.isNotBlank() }

    override suspend fun driveFolderId(): String? = store.snapshot()[DRIVE_FOLDER_ID]

    override suspend fun setDriveFolderId(folderId: String?) {
        store.data.edit { prefs ->
            if (folderId.isNullOrBlank()) {
                prefs.remove(DRIVE_FOLDER_ID)
            } else {
                prefs[DRIVE_FOLDER_ID] = folderId
            }
        }
    }

    override suspend fun setRestoreRecoveryNote(note: String?) {
        store.data.edit { prefs ->
            if (note.isNullOrBlank()) {
                prefs.remove(RESTORE_RECOVERY_NOTE)
            } else {
                prefs[RESTORE_RECOVERY_NOTE] = note
            }
        }
    }

    override suspend fun setLastRestore(fileName: String, atMillis: Long) {
        store.data.edit { prefs ->
            prefs[LAST_RESTORE_AT] = atMillis
            prefs[LAST_RESTORE_NAME] = fileName
        }
    }

    override suspend fun setLastBackup(fileName: String, atMillis: Long) {
        store.data.edit { prefs ->
            prefs[LAST_BACKUP_AT] = atMillis
            prefs[LAST_BACKUP_NAME] = fileName
        }
    }

    override suspend fun setLastVerifiedBackup(fileName: String, atMillis: Long) {
        store.data.edit { prefs ->
            prefs[LAST_VERIFIED_BACKUP_AT] = atMillis
            prefs[LAST_VERIFIED_BACKUP_NAME] = fileName
        }
    }

    override suspend fun clearDriveSession() {
        store.data.edit { prefs ->
            prefs.remove(DRIVE_ACCOUNT)
            prefs.remove(DRIVE_FOLDER_ID)
        }
    }
}
