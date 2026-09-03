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
import kotlinx.coroutines.CoroutineDispatcher
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
    /** Nullable so tests without a planner skip the reminder rebuild. */
    private val plannerRepository: PlannerRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun signIn(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): DriveSession {
        networkChecker.requireOnline()
        return rememberAuthorizedSession(activity, launchResolution)
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
    ): DriveBackupFile = withContext(ioDispatcher) {
        networkChecker.requireOnline()
        val session = rememberAuthorizedSession(activity, launchResolution)
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
    ): List<DriveBackupFile> = withContext(ioDispatcher) {
        networkChecker.requireOnline()
        val session = rememberAuthorizedSession(activity, launchResolution)
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
    ): RestoreResult = withContext(ioDispatcher) {
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
    ): String = withContext(ioDispatcher) {
        networkChecker.requireOnline()
        val session = rememberAuthorizedSession(activity, launchResolution)
        driveRestClient.downloadBackup(session.accessToken, file.id)
    }

    suspend fun prepareDriveRestore(
        activity: Activity,
        file: DriveBackupFile,
        launchResolution: suspend (IntentSender) -> Boolean,
        password: CharArray? = null,
    ): RestorePlan = withContext(ioDispatcher) {
        val raw = downloadDriveBackup(activity, file, launchResolution)
        prepareRestore(raw, sourceName = file.name, password = password)
    }

    /** Serialises the current database for a local plaintext export. */
    suspend fun exportJson(): String = withContext(ioDispatcher) {
        BackupJson.encode(localBackupRepository.createSnapshot())
    }

    suspend fun exportProtected(
        password: CharArray,
        iterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
    ): String = withContext(ioDispatcher) {
        BackupEnvelope.wrap(exportJson(), password, iterations)
    }

    suspend fun authoredInventory(): AuthoredInventory = withContext(ioDispatcher) {
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
    ): RestorePlan = withContext(ioDispatcher) {
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
    suspend fun commitRestore(plan: RestorePlan): RestoreResult = withContext(ioDispatcher) {
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
                // Its own guard, outside the commit path below. A disk failure here throws
                // before anything on the phone has been touched, and the catch at the bottom
                // then read phase STAGED, fell through to "a restore was interrupted, Temper
                // is finishing it from the copy already on this phone" — about a restore that
                // never started — and left the STAGED journal open, which refuses every
                // workout start until the next launch runs recovery.
                try {
                    restoreJournal.mark(RestoreJournal.WIPING)
                } catch (thrown: kotlinx.coroutines.CancellationException) {
                    throw thrown
                } catch (_: Exception) {
                    restoreJournal.clear()
                    throw BackupException(RestoreJournal.NOTHING_STARTED)
                }
                try {
                    localBackupRepository.replaceRoom(plan.document)
                } catch (thrown: kotlinx.coroutines.CancellationException) {
                    throw thrown
                } catch (thrown: Exception) {
                    // The replace is one Room transaction: a throw here means it rolled
                    // back and the phone is unchanged. Close the journal (nothing to
                    // recover, and an open one blocks every workout start) and say the
                    // truth instead of RECOVERED_MIXED's "was replaced".
                    restoreJournal.clear()
                    throw BackupException(
                        (thrown as? BackupException)?.message ?: RestoreJournal.NOTHING_CHANGED,
                    )
                }
                restoreJournal.mark(RestoreJournal.ROOM)
                val preferencesRestored = localBackupRepository.applyPreferences(plan.document)
                if (preferencesRestored) restoreJournal.mark(RestoreJournal.PREFS)
                dbMaintenance.reconcileCatalogLocked()
                rebuildRestoredReminders()
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
    suspend fun recoverInterruptedRestore(): Boolean = withContext(ioDispatcher) {
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
        // WIPING recovery reaches here only when the fingerprint proved Room
        // already holds the incoming file — the crash landed between the
        // transaction commit and mark(ROOM) — so preferences are owed exactly
        // as they are for ROOM. Gating on ROOM alone skipped them.
        if (record.phase == RestoreJournal.WIPING || record.phase == RestoreJournal.ROOM) {
            val prefsOk = localBackupRepository.applyPreferences(document)
            if (prefsOk) restoreJournal.mark(RestoreJournal.PREFS)
        }
        dbMaintenance.reconcileCatalogLocked()
        rebuildRestoredReminders()
        restoreJournal.clear()
    }

    /**
     * Restored PENDING deliveries exist only as rows until something hands
     * them to the scheduler; without this they silently did not fire until
     * the next reboot or launch. Best-effort — a scheduling failure must not
     * fail a restore that already committed.
     */
    private suspend fun rebuildRestoredReminders() {
        try {
            plannerRepository?.rebuildReminders()
        } catch (_: Exception) {
            // The boot/time-change receiver rebuilds again later.
        }
    }

    /**
     * What to tell the owner about a restore that threw.
     *
     * The phase says how far it got, and only the phases past the wipe have changed anything.
     * STAGED and a closed journal have not: the honest answer there is that nothing was
     * changed, and it must not be allowed to inherit [RestoreJournal.INTERRUPTED] from a
     * journal-write failure underneath — "Temper is finishing it from the copy already on this
     * phone" describes a recovery that is not going to happen, about data that was never
     * touched.
     */
    private fun namedCommitFailure(thrown: Throwable): BackupException {
        val phase = restoreJournal.read()?.phase
        // A STAGED journal left open refuses every workout start until the next launch runs
        // recovery, and there is nothing in it to recover — close it here rather than making
        // the owner relaunch to lift a block they should never have hit.
        if (phase == RestoreJournal.STAGED) runCatching { restoreJournal.clear() }
        val message = RestoreJournal.commitFailureMessage(
            phase = phase,
            reported = (thrown as? BackupException)?.message ?: thrown.message,
        )
        return if (thrown is BackupException && thrown.message == message) {
            thrown
        } else {
            BackupException(message)
        }
    }

    suspend fun listSafetySnapshots(): List<SafetySnapshotMeta> = withContext(ioDispatcher) {
        localBackupRepository.listSafetySnapshots()
    }

    suspend fun readSafetySnapshot(id: String): String = withContext(ioDispatcher) {
        localBackupRepository.readSafetySnapshot(id)
    }

    suspend fun deleteSafetySnapshot(id: String) = withContext(ioDispatcher) {
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
                "You have a session in progress. Finish or discard it before restoring, " +
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

    /**
     * AuthorizationClient does not return an account email. Drive About
     * does, still under `drive.file`. A failed About read must not undo
     * a successful token; Settings treats a non-null email as signed in.
     */
    private suspend fun rememberAuthorizedSession(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): DriveSession {
        val session = driveAuthClient.authorize(activity, launchResolution)
        val email = runCatching { driveRestClient.fetchAccountEmail(session.accessToken) }
            .getOrNull()
            ?.ifBlank { null }
            ?: session.email
            ?: "Google Drive"
        val named = session.copy(email = email)
        preferencesRepository.setDriveAccountEmail(named.email)
        return named
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
