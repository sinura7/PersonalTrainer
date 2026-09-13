package com.sinura.personaltrainer.domain

/**
 * Temper Debug can notice a newer live drop. Gym-floor Temper does not.
 *
 * The copy never asks anyone to turn off Play Protect or "install unknown
 * apps". Opening the drop still goes through Android's install gate.
 */
object DebugUpdateCopy {
    const val TITLE = "Update available"
    const val ACTION = "Open"
    const val BANNER_BODY =
        "A newer Temper Debug is on GitHub. Android will still ask you to allow the install."
    const val SETTINGS_SUMMARY = "A newer Temper Debug is on GitHub"

    fun settingsSummary(versionCode: Int): String = "Live $versionCode is ready"
}
