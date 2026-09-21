package com.sinura.personaltrainer.domain

object AccountAuthCopy {
    const val SECTION = "Account"
    const val INDEX_SUMMARY = "Optional cloud sign-in"
    const val NOT_CONFIGURED =
        "Temper Account is not configured for this build. Add SUPABASE_URL and " +
            "SUPABASE_ANON_KEY to supabase.properties on your machine, then rebuild."
    const val SIGNED_OUT_CAPTION =
        "Training on this phone does not require an account. Sign in when you want " +
            "to prepare for optional cloud sync later."
    const val SIGNED_IN_CAPTION =
        "Signed in for future sync. Workouts, Plan, and History still work the same offline."
    const val EMAIL = "Email"
    const val PASSWORD = "Password"
    const val SIGN_IN = "Sign in"
    const val CREATE_ACCOUNT = "Create account"
    const val SIGN_OUT = "Sign out"
    const val BUSY_SIGN_IN = "Signing in…"
    const val BUSY_SIGN_UP = "Creating account…"
    const val BUSY_SIGN_OUT = "Signing out…"

    fun errorMessage(error: AccountAuthError): String = when (error) {
        AccountAuthError.NotConfigured -> NOT_CONFIGURED
        AccountAuthError.InvalidCredentials ->
            "Email or password did not match. Check both and try again."
        AccountAuthError.Network ->
            "Connect to the internet to use Temper Account."
        is AccountAuthError.Message -> error.text
    }
}
