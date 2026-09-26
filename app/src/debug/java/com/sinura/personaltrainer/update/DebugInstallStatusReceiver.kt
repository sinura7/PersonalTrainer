package com.sinura.personaltrainer.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.PersonalTrainerApp

/**
 * Where Android answers a Temper Debug install session (audit RM-1). Its first answer, for an
 * app that is not the phone's installer, is that the owner must confirm, with Android's install
 * sheet to open. The answer used to go to the main screen, which never read it: the sheet never
 * opened and the phone stayed on the old build.
 *
 * A receiver, not a screen: Android sends the answer without leave to start a screen from the
 * background, and apps targeting Android 15 lose their own, so a screen here could simply not
 * open. The banner's monitor keeps the sheet, and the main screen opens it when it is in front:
 * at once, or when the owner comes back to the app.
 *
 * Temper Debug only, and not exported (`src/debug/AndroidManifest.xml`): only the session's own
 * status reaches it, so no other app can hand it a screen to open.
 */
class DebugInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val answer = DebugInstallAnswer.from(intent) ?: return
        (context.applicationContext as? PersonalTrainerApp)?.container?.debugUpdate?.onInstallAnswer(answer)
    }
}
