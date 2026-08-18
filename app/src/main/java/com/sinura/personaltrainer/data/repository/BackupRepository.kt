package com.sinura.personaltrainer.data.repository

import android.app.Activity
import android.content.IntentSender
import com.sinura.personaltrainer.data.backup.BackupJson
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
    ) = withContext(Dispatchers.IO) {
        networkChecker.requireOnline()
        val session = driveAuthClient.authorize(activity, launchResolution)
        val json = driveRestClient.downloadBackup(session.accessToken, file.id)
        val document = BackupJson.decode(json)
        localBackupRepository.replaceWith(document)
        preferencesRepository.setLastBackup(file.name, file.modifiedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
    }
}
