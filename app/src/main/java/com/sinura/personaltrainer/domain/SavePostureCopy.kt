package com.sinura.personaltrainer.domain

object SavePostureCopy {
    const val SETTINGS_TITLE = "How you save"
    const val SETTINGS_SUMMARY_ACCOUNT = "Temper Account cloud save"
    const val SETTINGS_SUMMARY_LOCAL = "This phone · optional Drive backup"

    const val CHOOSER_KICKER = "Save your training"
    const val CHOOSER_HEADLINE = "Choose how Temper keeps your data"
    const val CHOOSER_BLURB =
        "You can train either way. Pick cloud sign-in for Temper Account, or keep " +
            "everything on this phone and optionally back up to Google Drive later."

    const val CHOOSE_ACCOUNT = "Temper Account"
    const val CHOOSE_ACCOUNT_HINT =
        "Sign in or create an account to keep your weekly schedule, routines, and cardio " +
            "in the cloud."
    const val CHOOSE_ACCOUNT_PAUSED =
        "Sync is paused for now, so an account keeps nothing in the cloud yet."
    const val CHOOSE_LOCAL = "Continue on this phone"
    const val CHOOSE_LOCAL_HINT =
        "Train without an account. Set up Google Drive backup anytime in Settings."
    const val SET_UP_DRIVE = "Set up Google Drive backup"

    const val SETTINGS_CURRENT = "Current choice"
    const val SETTINGS_USE_ACCOUNT = "Use Temper Account"
    const val SETTINGS_USE_LOCAL = "Save on this phone"
    const val SETTINGS_OPEN_ACCOUNT = "Open Account settings"
    const val SETTINGS_OPEN_BACKUP = "Open Backup & Drive"
    const val SETTINGS_ACCOUNT_CAPTION =
        "Your weekly schedule, routines, and cardio sync when you are signed in and online. " +
            "Workouts logged live stay on this phone for now."
    const val SETTINGS_ACCOUNT_PAUSED =
        "Sync is paused for now; Account says why. Training on this phone is unaffected."
    const val SETTINGS_LOCAL_CAPTION =
        "Your workouts stay on this phone. Google Drive whole-file backup is optional " +
            "and separate from Temper Account."

    fun settingsSummary(posture: SavePosture): String = when (posture) {
        SavePosture.ACCOUNT -> SETTINGS_SUMMARY_ACCOUNT
        SavePosture.LOCAL -> SETTINGS_SUMMARY_LOCAL
    }
}
