package com.sinura.personaltrainer.data.repository

import android.app.Activity
import android.content.IntentSender
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupEnvelope
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupSummary
import com.sinura.personaltrainer.data.backup.BackupValidation
import com.sinura.personaltrainer.data.backup.BackupValidator
import com.sinura.personaltrainer.data.backup.RestoreJournal
import com.sinura.personaltrainer.data.backup.RestoreJournalRecord
import com.sinura.personaltrainer.data.backup.RestoreJournalStore
import com.sinura.personaltrainer.data.backup.SafetySnapshotMeta
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
    private val restoreJournal: RestoreJournalStore,
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
        password: CharArray? = null,
        iterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
    ): DriveBackupFile = withContext(Dispatchers.IO) {
        networkChecker.requireOnline()
        val session = driveAuthClient.authorize(activity, launchResolution)
        preferencesRepository.setDriveAccountEmail(session.email)
        val snapshot = localBackupRepository.createSnapshot()
        val json = BackupJson.encode(snapshot)
        val payload = if (password != null) {
            BackupEnvelope.wrap(json, password, iterations)
        } else {
            json
        }
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
            json = payload,
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

    suspend fun downloadDriveBackup(
        activity: Activity,
        file: DriveBackupFile,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): String = withContext(Dispatchers.IO) {
        networkChecker.requireOnline()
        val session = driveAuthClient.authorize(activity, launchResolution)
        driveRestClient.downloadBackup(session.accessToken, file.id)
    }

    suspend fun prepareDriveRestore(
        activity: Activity,
        file: DriveBackupFile,
        launchResolution: suspend (IntentSender) -> Boolean,
        password: CharArray? = null,
    ): RestorePlan = withContext(Dispatchers.IO) {
        val raw = downloadDriveBackup(activity, file, launchResolution)
        prepareRestore(raw, sourceName = file.name, password = password)
    }

    /** Serialises the current database for a local plaintext export. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        BackupJson.encode(localBackupRepository.createSnapshot())
    }

    suspend fun exportProtected(
        password: CharArray,
        iterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
    ): String = withContext(Dispatchers.IO) {
        BackupEnvelope.wrap(exportJson(), password, iterations)
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
        password: CharArray? = null,
    ): RestorePlan = withContext(Dispatchers.IO) {
        refuseIfLive()
        val document = BackupJson.decode(BackupEnvelope.open(json, password))
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
        dbMaintenance.withMaintenanceLock {
            recoverInterruptedRestoreLocked()
            refuseIfLive()
            val snapshot = localBackupRepository.writeVerifiedSafetySnapshot()
            val incomingJson = BackupJson.encode(plan.document)
            restoreJournal.stage(
                RestoreJournalRecord(
                    phase = RestoreJournal.STAGED,
                    sourceName = plan.sourceName,
                    snapshotId = snapshot.id,
                    beforeFingerprint = localBackupRepository.roomFingerprint(),
                    afterFingerprint = RestoreJournal.fingerprint(plan.document),
                ),
                incomingJson,
            )
            try {
                restoreJournal.mark(RestoreJournal.WIPING)
                localBackupRepository.replaceRoom(plan.document)
                restoreJournal.mark(RestoreJournal.ROOM)
                val preferencesRestored = localBackupRepository.applyPreferences(plan.document)
                if (preferencesRestored) restoreJournal.mark(RestoreJournal.PREFS)
                dbMaintenance.reconcileCatalogLocked()
                restoreJournal.clear()
                RestoreResult(
                    sourceName = plan.sourceName,
                    summary = plan.summary,
                    incoming = plan.incoming,
                    local = plan.local,
                    preferencesRestored = preferencesRestored,
                    safetySnapshotId = snapshot.id,
                )
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: BackupException) {
                throw namedCommitFailure(thrown)
            } catch (thrown: Exception) {
                throw namedCommitFailure(thrown)
            }
        }
    }

    /**
     * Finish a restore that died after Room committed. Safe to call on every
     * process start. Holds the same lock as start and restore.
     */
    suspend fun recoverInterruptedRestore(): Boolean = withContext(Dispatchers.IO) {
        dbMaintenance.withMaintenanceLock { recoverInterruptedRestoreLocked() }
    }

    fun restoreInProgress(): Boolean = restoreJournal.isOpen()

    private suspend fun recoverInterruptedRestoreLocked(): Boolean {
        val record = restoreJournal.read() ?: return false
        return when (record.phase) {
            RestoreJournal.STAGED -> {
                restoreJournal.clear()
                false
            }
            RestoreJournal.WIPING -> {
                val current = localBackupRepository.roomFingerprint()
                when (current) {
                    record.afterFingerprint -> {
                        finishFromRoom(record)
                        true
                    }
                    else -> {
                        restoreJournal.clear()
                        false
                    }
                }
            }
            RestoreJournal.ROOM, RestoreJournal.PREFS -> {
                finishFromRoom(record)
                true
            }
            else -> {
                restoreJournal.clear()
                false
            }
        }
    }

    private suspend fun finishFromRoom(record: RestoreJournalRecord) {
        val document = BackupJson.decode(restoreJournal.readIncoming())
        if (record.phase == RestoreJournal.ROOM) {
            val prefsOk = localBackupRepository.applyPreferences(document)
            if (prefsOk) restoreJournal.mark(RestoreJournal.PREFS)
        }
        dbMaintenance.reconcileCatalogLocked()
        restoreJournal.clear()
    }

    private fun namedCommitFailure(thrown: Throwable): BackupException {
        val phase = restoreJournal.read()?.phase
        val message = when (phase) {
            RestoreJournal.ROOM, RestoreJournal.PREFS, RestoreJournal.WIPING ->
                RestoreJournal.RECOVERED_MIXED
            else -> (thrown as? BackupException)?.message ?: thrown.message
                ?: "Restore failed. Nothing was changed."
        }
        return if (thrown is BackupException && thrown.message == message) {
            thrown
        } else {
            BackupException(message)
        }
    }

    suspend fun listSafetySnapshots(): List<SafetySnapshotMeta> = withContext(Dispatchers.IO) {
        localBackupRepository.listSafetySnapshots()
    }

    suspend fun readSafetySnapshot(id: String): String = withContext(Dispatchers.IO) {
        localBackupRepository.readSafetySnapshot(id)
    }

    suspend fun deleteSafetySnapshot(id: String) = withContext(Dispatchers.IO) {
        localBackupRepository.deleteSafetySnapshot(id)
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
        password: CharArray? = null,
    ): RestoreResult = commitRestore(
        prepareRestore(json, sourceName, allowEmptyDestructiveRestore, password),
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
    val safetySnapshotId: String?,
    val incoming: AuthoredInventory = AuthoredInventory.EMPTY,
    val local: AuthoredInventory = AuthoredInventory.EMPTY,
)
