package com.sinura.personaltrainer.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.logging.AppLog

private const val TAG = "PT/DebugUpdateInstall"

/**
 * Where Android answers a Temper Debug install session (audit RM-1). Its first answer, for an
 * app that is not the phone's installer, is that the owner must confirm, with Android's install
 * sheet to open. The answer used to go to the main screen, which never read it: the sheet never
 * opened and the phone stayed on the old build.
 *
 * Temper Debug only, and not exported (`src/debug/AndroidManifest.xml`): only the session's own
 * status, which Android sends, reaches it, so no other app can hand it a screen to open. It shows
 * nothing and closes at once; the banner hears how the install ended.
 */
class DebugInstallStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent) {
        val answer = DebugInstallAnswer.from(intent) ?: return
        val port = (application as? PersonalTrainerApp)?.container?.debugUpdate
        if (answer is DebugInstallAnswer.Confirm) {
            try {
                startActivity(answer.sheet)
            } catch (error: RuntimeException) {
                AppLog.w(TAG, "Android's install sheet could not be opened", error)
                port?.onInstallAnswer(DebugInstallAnswer.Failed(PackageInstaller.STATUS_FAILURE, error.message))
                return
            }
        }
        port?.onInstallAnswer(answer)
    }
}
