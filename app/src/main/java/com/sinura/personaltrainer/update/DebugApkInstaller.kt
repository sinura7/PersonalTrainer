package com.sinura.personaltrainer.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.io.File

private const val TAG = "PT/DebugUpdateInstall"

/** `src/debug`'s DebugInstallStatusActivity, named because gym-floor has no such class. */
private const val STATUS_SCREEN = "com.sinura.personaltrainer.update.DebugInstallStatusActivity"

/**
 * Hands a downloaded Temper Debug APK to Android's installer. Gym-floor never
 * constructs this.
 */
internal interface DebugApkInstaller {
    fun canInstall(): Boolean
    fun openInstallPermissionSettings()
    fun install(apk: File)

    /** Where [versionCode]'s download goes. Any other build downloaded before is deleted. */
    fun stagingFile(versionCode: Int): File

    /** Deletes every downloaded build (audit RM-6). */
    fun clearStaging() {}
}

internal class AndroidDebugApkInstaller(
    private val context: Context,
) : DebugApkInstaller {
    override fun canInstall(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatchingCancellable { context.startActivity(intent) }
            .onFailure { error ->
                AppLog.w(TAG, "Opening install permission settings failed", error)
                runCatchingCancellable {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { fallbackError ->
                    AppLog.w(TAG, "Opening app details for install permission failed", fallbackError)
                }
            }
    }

    override fun install(apk: File) {
        runCatchingCancellable { commitSession(apk) }
            .onFailure { sessionError ->
                AppLog.w(TAG, "PackageInstaller session failed; trying FileProvider", sessionError)
                viewThroughFileProvider(apk)
            }
    }

    override fun stagingFile(versionCode: Int): File {
        val dir = stagingDir().apply { mkdirs() }
        val file = File(dir, "PersonalTrainer-$versionCode-debug.apk")
        // Each build was kept until Android trimmed the cache, up to 96 MB apiece (audit RM-6).
        dir.listFiles()?.filter { it != file }?.forEach { stale ->
            if (!stale.delete()) AppLog.w(TAG, "An older download could not be deleted")
        }
        return file
    }

    override fun clearStaging() {
        if (!stagingDir().deleteRecursively()) AppLog.w(TAG, "The downloaded builds could not all be deleted")
    }

    private fun stagingDir() = File(context.cacheDir, "debug-update")

    private fun commitSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= 34) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("package.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            // Android answers here: first, for an app that is not the phone's installer, that the
            // owner must confirm, with its install sheet to open (audit RM-1). A Temper Debug-only
            // screen that is not exported, so no other app can hand it a screen to open.
            val status = Intent()
                .setClassName(context, STATUS_SCREEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getActivity(context, sessionId, status, flags)
            session.commit(pending.intentSender)
        } catch (error: Throwable) {
            runCatchingCancellable { session.abandon() }
            throw error
        } finally {
            session.close()
        }
    }

    private fun viewThroughFileProvider(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.debugupdate",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
