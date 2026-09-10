package com.sinura.personaltrainer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The Activity a composable is running in.
 *
 * Drive authorization needs one: `AuthorizationClient` resolves consent through an
 * IntentSender the platform launches from an Activity, so any screen that can start a
 * backup has to hand one down. The app is single-Activity ([com.sinura.personaltrainer.MainActivity]),
 * so this always finds it — the walk exists because Compose hands screens a themed
 * ContextWrapper rather than the Activity itself.
 *
 * Shared rather than duplicated per screen: Settings owned a private copy of this until the
 * workout summary needed the same thing to back itself up.
 */
fun Context.findActivity(): Activity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    error("This screen must run in an Activity")
}
