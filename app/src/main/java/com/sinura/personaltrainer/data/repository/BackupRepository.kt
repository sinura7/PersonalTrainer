package com.sinura.personaltrainer.data.repository

import android.app.Activity
import android.content.IntentSender
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupSummary
import com.sinura.personaltrainer.data.backup.BackupValidation
import com.sinura.personaltrainer.data.backup.BackupValidator
import com.sinura.personaltrainer.data.backup.DriveAuthClient
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.data.backup.DriveRestClient
import com.sinura.personaltrainer.data.backup.DriveSession
import com.sinura.personaltrainer.data.backup.NetworkChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupRepository(
    private val localBackupRepository: LocalBackupRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dbMaintenance: DbMaintenance,
    private val driveAuthClient: DriveAuthClient,
    private val driveRestClient: DriveRestClient,
    private val networkChecker: NetworkChecker,
) {
    suspend fun signIn(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): DriveSession {
        networkChecker.requireOnline()
        val session = driveAuthClient.authorize(activity, launchResolution)
        preferencesRepository.setDriveAccountEmail(session.email)
        return session
    }

    suspend fun signOut(activity: Activity) {
        driveAuthClient.signOut(activity)
        preferencesRepository.clearDriveSession()
    }

    suspend fun createBackup(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): DriveBackupFile = withContext(Dispatchers.IO) {
        networkChecker.requireOnline()
        val session = driveAuthClient.authorize(activity, launchResolution)
        preferencesRepository.setDriveAccountEmail(session.email)
        val snapshot = localBackupRepository.createSnapshot()
        val json = BackupJson.encode(snapshot)
        val fileName = BackupJson.fileName()
        val folderId = driveRestClient.ensureBackupFolder(
            accessToken = session.accessToken,
            knownFolderId = preferencesRepository.driveFolderId(),
        )
        preferencesRepository.setDriveFolderId(folderId)
        val uploaded = driveRestClient.uploadBackup(
            accessToken = session.accessToken,
            folderId = folderId,
            fileName = fileName,
            json = json,
        )
        preferencesRepository.setLastBackup(uploaded.name, System.currentTimeMillis())
        uploaded
    }

    suspend fun listBackups(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        networkChecker.requireOnline()
        val session = driveAuthClient.authorize(activity, launchResolution)
        preferencesRepository.setDriveAccountEmail(session.email)
        val folderId = driveRestClient.ensureBackupFolder(
            accessToken = session.accessToken,
            knownFolderId = preferencesRepository.driveFolderId(),
        )
        preferencesRepository.setDriveFolderId(folderId)
        driveRestClient.listBackups(session.accessToken, folderId)
    }

    suspend fun restoreBackup(
        activity: Activity,
        file: DriveBackupFile,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): RestoreResult = withContext(Dispatchers.IO) {
        networkChecker.requireOnline()
        val session = driveAuthClient.authorize(activity, launchResolution)
        val json = driveRestClient.downloadBackup(session.accessToken, file.id)
        val result = restoreFromJson(json, sourceName = file.name)
        preferencesRepository.setLastRestore(
            file.name,
            file.modifiedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        result
    }

    /** Serialises the current database for a local file export. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        BackupJson.encode(localBackupRepository.createSnapshot())
    }

    /**
     * True when a workout is in progress. Snapshots deliberately exclude unfinished sessions,
     * so the UI can say so rather than letting the user assume today's session is in the file.
     */
    suspend fun hasUnfinishedWorkout(): Boolean =
        localBackupRepository.inProgressSessionId() != null

    /**
     * The single validated restore path. Drive downloads and local file imports both land
     * here, so neither can skip a check the other performs.
     *
     * Order matters: refuse while a workout is live, decode, validate, and only then wipe.
     */
    suspend fun restoreFromJson(
        json: String,
        sourceName: String,
        allowEmptyDestructiveRestore: Boolean = false,
    ): RestoreResult = withContext(Dispatchers.IO) {
        if (localBackupRepository.inProgressSessionId() != null) {
            throw BackupException(
                "You have a workout in progress. Finish or discard it before restoring, " +
                    "so a restore can't delete the session you're standing in.",
            )
        }
        val document = BackupJson.decode(json)
        val validated = validateOrThrow(document, allowEmptyDestructiveRestore)
        // Decode, validate and wipe are one unit against the catalog: the startup seed pass is
        // launched fire-and-forget from Application.onCreate and would otherwise be free to
        // upsert into the middle of the delete pass.
        val outcome = dbMaintenance.withMaintenanceLock {
            val result = localBackupRepository.replaceWith(document)
            // Every restore ends here, v1 file or v2. A restored database carries whatever
            // catalog the backup was taken from — an old build's 37, a phone that never had
            // some of them — so the catalog is brought back in line with what THIS build knows
            // before anyone reads it.
            dbMaintenance.reconcileCatalogLocked()
            result
        }
        RestoreResult(
            sourceName = sourceName,
            summary = validated,
            preferencesRestored = outcome.preferencesRestored,
            safetySnapshotPath = outcome.safetySnapshotPath,
        )
    }

    private suspend fun validateOrThrow(
        document: BackupDocument,
        allowEmptyDestructiveRestore: Boolean,
    ): BackupSummary {
        val validation = BackupValidator.validate(
            document = document,
            localHasData = localBackupRepository.hasLocalData(),
            allowEmptyDestructiveRestore = allowEmptyDestructiveRestore,
        )
        return when (validation) {
            is BackupValidation.Valid -> validation.summary
            is BackupValidation.Invalid -> throw BackupException(validation.reason)
        }
    }
}

data class RestoreResult(
    val sourceName: String,
    val summary: BackupSummary,
    val preferencesRestored: Boolean,
    val safetySnapshotPath: String?,
)
