package com.sinura.personaltrainer.domain

object AccountAuthCopy {
    const val SECTION = "Account"
    const val INDEX_SUMMARY = "Optional cloud sign-in"
    const val ENTRY_KICKER = "Temper Account"
    const val ENTRY_HEADLINE = "Open your account"
    const val ENTRY_BLURB =
        "Load your cloud history on this phone. Finished workouts and your plan " +
            "travel with you when you sign in online."
    const val ENTRY_OPEN = "Open account"
    const val ENTRY_NOT_NOW = "Not now"
    const val CREDENTIALS_BACK = "Back"
    const val CREDENTIALS_TITLE = "Sign in or create account"
    const val NOT_CONFIGURED =
        "Temper Account is not available in this build yet. Training on this phone still works."
    const val SIGNED_OUT_CAPTION =
        "Training on this phone does not require an account. Sign in to copy finished " +
            "workouts and your plan to Temper Account when you are online."
    const val SIGNED_IN_CAPTION =
        "Sync runs in the background when you are online. Workouts, Plan, and History " +
            "still work offline on this phone."
    fun syncStatusLine(pending: Int, lastSuccessAtMs: Long?, lastError: String?): String =
        SyncCopy.syncStatusLine(pending, lastSuccessAtMs, lastError)
    const val EMAIL = "Email"
    const val PASSWORD = "Password"
    const val SIGN_IN = "Sign in"
    const val CREATE_ACCOUNT = "Create account"
    const val SIGN_OUT = "Sign out"
    const val DELETE_ACCOUNT = "Delete Temper Account…"
    const val BUSY_SIGN_IN = "Signing in…"
    const val BUSY_SIGN_UP = "Creating account…"
    const val BUSY_SIGN_OUT = "Signing out…"
    const val BUSY_DELETE_ACCOUNT = "Deleting account…"
    const val DELETE_ACCOUNT_TITLE = "Delete Temper Account?"
    const val DELETE_ACCOUNT_BODY =
        "This permanently removes your cloud sign-in and every workout and plan row " +
            "Temper Account stored for you on the server. Training on this phone stays " +
            "here unless you delete it yourself. This cannot be undone."
    const val DELETE_ACCOUNT_CONFIRM = "Delete account"
    const val DELETE_ACCOUNT_TYPE_EMAIL =
        "Type your account email to confirm."
    const val DELETE_ACCOUNT_EMAIL_MISMATCH = "Email does not match the signed-in account."

    fun errorMessage(error: AccountAuthError): String = when (error) {
        AccountAuthError.NotConfigured -> NOT_CONFIGURED
        AccountAuthError.InvalidCredentials ->
            "Email or password did not match. Check both and try again."
        AccountAuthError.Network ->
            "Connect to the internet to use Temper Account."
        AccountAuthError.DeleteFailed ->
            "Could not delete your Temper Account. Check your connection and try again."
        is AccountAuthError.Message -> error.text
    }
}
