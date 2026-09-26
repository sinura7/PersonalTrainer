package com.sinura.personaltrainer.update

import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/**
 * Android's answer to a Temper Debug install session, read from the status the session was
 * committed with ([AndroidDebugApkInstaller]).
 */
sealed interface DebugInstallAnswer {
    /** The owner must confirm: [sheet] is Android's install sheet, for the app to open. */
    data class Confirm(val sheet: Intent) : DebugInstallAnswer

    data object Installed : DebugInstallAnswer

    /** The owner cancelled Android's sheet. */
    data object Cancelled : DebugInstallAnswer

    /** Android refused the build ([status], with its [message] when it gave one). */
    data class Failed(val status: Int, val message: String?) : DebugInstallAnswer

    companion object {
        /** Null when [intent] carries no install status. */
        fun from(intent: Intent?): DebugInstallAnswer? {
            if (intent == null || !intent.hasExtra(PackageInstaller.EXTRA_STATUS)) return null
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            return when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION ->
                    sheet(intent)?.let(::Confirm) ?: Failed(status, "No install sheet came with the answer")
                PackageInstaller.STATUS_SUCCESS -> Installed
                PackageInstaller.STATUS_FAILURE_ABORTED -> Cancelled
                else -> Failed(status, message)
            }
        }

        private fun sheet(intent: Intent): Intent? =
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
    }
}
