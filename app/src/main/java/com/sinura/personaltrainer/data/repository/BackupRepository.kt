package com.sinura.personaltrainer.data.repository

import android.app.Activity
import android.content.IntentSender
import com.sinura.personaltrainer.data.backup.AuthoredInventory
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
import com.sinura.personaltrainer.domain.DataHealthCopy
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
        val plan = prepareDriveRestore(activity, file, launchResolution)
        val result = commitRestore(plan)
        preferencesRepository.setLastRestore(
            file.name,
            file.modifiedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        result
    }

    suspend fun prepareDriveRestore(
        activity: Activity,
        file: DriveBackupFile,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): RestorePlan = withContext(Dispatchers.IO) {
        networkChecker.requireOnline()
        val session = driveAuthClient.authorize(activity, launchResolution)
        val json = driveRestClient.downloadBackup(session.accessToken, file.id)
        prepareRestore(json, sourceName = file.name)
    }

    /** Serialises the current database for a local file export. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        BackupJson.encode(localBackupRepository.createSnapshot())
    }

    suspend fun authoredInventory(): AuthoredInventory = withContext(Dispatchers.IO) {
        localBackupRepository.authoredInventory()
    }

    /**
     * True when a workout is in progress. Snapshots deliberately exclude unfinished sessions,
     * so the UI can say so rather than letting the user assume today's session is in the file.
     */
    suspend fun hasUnfinishedWorkout(): Boolean =
        try {
            localBackupRepository.inProgressSessionId() != null
        } catch (thrown: kotlinx.coroutines.CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            throw BackupException(DataHealthCopy.RESTORE_UNAVAILABLE)
        }

    /**
     * Decode, validate, and compare authored counts. Does not write.
     *
     * The confirm dialog reads [RestorePlan.incoming] and [RestorePlan.local].
     * First tap must land here, not in [commitRestore].
     */
    suspend fun prepareRestore(
        json: String,
        sourceName: String,
        allowEmptyDestructiveRestore: Boolean = false,
    ): RestorePlan = withContext(Dispatchers.IO) {
        refuseIfLive()
        val document = BackupJson.decode(json)
        val local = localBackupRepository.authoredInventory()
        val summary = validateOrThrow(document, local, allowEmptyDestructiveRestore)
        RestorePlan(
            sourceName = sourceName,
            document = document,
            incoming = AuthoredInventory.fromDocument(document),
            local = local,
            summary = summary,
        )
    }

    /** Writes the already-prepared document. Re-checks the live-session refuse. */
    suspend fun commitRestore(plan: RestorePlan): RestoreResult = withContext(Dispatchers.IO) {
        refuseIfLive()
        val outcome = dbMaintenance.withMaintenanceLock {
            val result = localBackupRepository.replaceWith(plan.document)
            dbMaintenance.reconcileCatalogLocked()
            result
        }
        RestoreResult(
            sourceName = plan.sourceName,
            summary = plan.summary,
            incoming = plan.incoming,
            local = plan.local,
            preferencesRestored = outcome.preferencesRestored,
            safetySnapshotPath = outcome.safetySnapshotPath,
        )
    }

    /**
     * The single validated restore path. Drive downloads and local file imports both land
     * here, so neither can skip a check the other performs.
     *
     * Order: prepare (no write) then commit. Tests that need a one-shot call still use this.
     */
    suspend fun restoreFromJson(
        json: String,
        sourceName: String,
        allowEmptyDestructiveRestore: Boolean = false,
    ): RestoreResult = commitRestore(
        prepareRestore(json, sourceName, allowEmptyDestructiveRestore),
    )

    private suspend fun refuseIfLive() {
        val liveId = try {
            localBackupRepository.inProgressSessionId()
        } catch (thrown: kotlinx.coroutines.CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            throw BackupException(DataHealthCopy.RESTORE_UNAVAILABLE)
        }
        if (liveId != null) {
            throw BackupException(
                "You have a workout in progress. Finish or discard it before restoring, " +
                    "so a restore can't delete the session you're standing in.",
            )
        }
    }

    private suspend fun validateOrThrow(
        document: BackupDocument,
        local: AuthoredInventory,
        allowEmptyDestructiveRestore: Boolean,
    ): BackupSummary {
        val validation = BackupValidator.validate(
            document = document,
            localAuthored = local,
            allowEmptyDestructiveRestore = allowEmptyDestructiveRestore,
        )
        return when (validation) {
            is BackupValidation.Valid -> validation.summary
            is BackupValidation.Invalid -> throw BackupException(validation.reason)
        }
    }
}

data class RestorePlan(
    val sourceName: String,
    val document: BackupDocument,
    val incoming: AuthoredInventory,
    val local: AuthoredInventory,
    val summary: BackupSummary,
) {
    val confirmBody: String
        get() = AuthoredInventory.confirmBody(sourceName, incoming, local)
}

data class RestoreResult(
    val sourceName: String,
    val summary: BackupSummary,
    val preferencesRestored: Boolean,
    val safetySnapshotPath: String?,
    val incoming: AuthoredInventory = AuthoredInventory.EMPTY,
    val local: AuthoredInventory = AuthoredInventory.EMPTY,
)
