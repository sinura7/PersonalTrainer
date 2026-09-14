package com.sinura.personaltrainer.domain

/**
 * Temper Debug can notice a newer live drop and install it in-app. Gym-floor
 * Temper does not.
 *
 * The copy never asks anyone to turn off Play Protect or "install unknown
 * apps". Downloading is ours; the system install sheet is still Android's.
 * Obtainium is not a required step.
 */
object DebugUpdateCopy {
    const val TITLE = "Update Temper Debug"
    const val ACTION = "Update"
    const val ACTION_ALLOW = "Allow"
    const val ACTION_RETRY = "Try again"
    const val BANNER_BODY =
        "Temper Debug will download the new package, then Android will ask you to install it."
    const val DOWNLOADING = "Downloading the update…"
    const val INSTALLING = "Android will ask you to install this update."
    const val NEEDS_PERMISSION =
        "Android needs permission for Temper Debug to install this update."
    const val FAILED = "The download didn’t finish. Try again."
    const val SETTINGS_SUMMARY = "A newer Temper Debug is ready"

    fun settingsSummary(versionCode: Int): String = "Live $versionCode is ready"

    fun downloading(percent: Int?): String =
        if (percent == null) DOWNLOADING else "$DOWNLOADING $percent%"
}
