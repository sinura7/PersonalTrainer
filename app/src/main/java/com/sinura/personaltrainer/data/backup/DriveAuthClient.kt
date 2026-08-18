package com.sinura.personaltrainer.data.backup

import android.app.Activity
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DriveSession(
    val accessToken: String,
    val email: String?,
)

class DriveAuthClient {
    @Volatile
    private var session: DriveSession? = null

    suspend fun authorize(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): DriveSession {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        val first = try {
            Identity.getAuthorizationClient(activity).authorize(request).await()
        } catch (error: Exception) {
            throw mapAuthError(error)
        }
        val resolved = if (first.hasResolution()) {
            val sender = first.pendingIntent?.intentSender
                ?: throw BackupException("Google sign-in could not continue.")
            val accepted = launchResolution(sender)
            if (!accepted) {
                throw BackupException("Google sign-in was cancelled.")
            }
            try {
                Identity.getAuthorizationClient(activity).authorize(request).await()
            } catch (error: Exception) {
                throw mapAuthError(error)
            }
        } else {
            first
        }
        if (resolved.hasResolution()) {
            throw BackupException("Google sign-in was cancelled.")
        }
        val token = resolved.accessToken
            ?: throw BackupException("Google did not return a Drive access token. Try again.")
        val email = resolved.toGoogleSignInAccount()?.email
            ?: lastSignedInEmail(activity)
        val next = DriveSession(accessToken = token, email = email)
        session = next
        return next
    }

    suspend fun signOut(activity: Activity) {
        try {
            Identity.getSignInClient(activity).signOut().await()
        } catch (_: Exception) {
            // Local session is cleared either way.
        }
        session = null
    }

    private fun mapAuthError(error: Exception): BackupException {
        if (error is CancellationException) throw error
        val api = error as? ApiException
        return when (api?.statusCode) {
            CommonStatusCodes.CANCELED,
            CommonStatusCodes.SIGN_IN_REQUIRED,
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED,
            ->
                BackupException("Google sign-in was cancelled.")
            CommonStatusCodes.NETWORK_ERROR ->
                BackupException("Connect to the internet to use Google Drive.")
            CommonStatusCodes.DEVELOPER_ERROR ->
                BackupException(
                    "Google Drive sign-in isn’t configured for this install. Add an Android OAuth client for com.sinura.personaltrainer in Google Cloud Console.",
                )
            else -> BackupException(error.message?.ifBlank { null } ?: "Google sign-in failed.")
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> continuation.resume(value) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener {
            continuation.resumeWithException(BackupException("Google sign-in was cancelled."))
        }
    }

    @Suppress("DEPRECATION")
    private fun lastSignedInEmail(activity: Activity): String? =
        GoogleSignIn.getLastSignedInAccount(activity)?.email

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
