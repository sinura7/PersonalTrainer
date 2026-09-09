package com.sinura.personaltrainer.data.backup

import android.app.Activity
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PT/DriveAuth"

data class DriveSession(
    val accessToken: String,
    val email: String?,
)

/**
 * Drive authorization through [AuthorizationClient] and `drive.file` only.
 *
 * Google Sign-In types are gone. The authorization result does not carry
 * an account email; [DriveRestClient.fetchAccountEmail] reads it from
 * Drive About with the same access token.
 */
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
            throw mapAuthError(error, activity.packageName)
        }
        val resolved = if (first.hasResolution()) {
            val sender = first.pendingIntent?.intentSender
                ?: throw BackupException("Google sign-in could not continue.")
            val accepted = launchResolution(sender)
            if (!accepted) {
                AppLog.e(TAG, "consent resolution declined or timed out")
                throw BackupException("Google sign-in was cancelled.")
            }
            try {
                Identity.getAuthorizationClient(activity).authorize(request).await()
            } catch (error: Exception) {
                throw mapAuthError(error, activity.packageName)
            }
        } else {
            first
        }
        if (resolved.hasResolution()) {
            // Approving the consent should leave the retry grantable. Still asking means the
            // grant did not stick — a distinct state from a cancel, and one that reads as a
            // phantom cancel if it borrows that copy.
            AppLog.e(TAG, "authorize still wanted resolution after an accepted consent")
            throw BackupException(
                "Google needed another approval step and it did not complete. Try again.",
            )
        }
        val token = resolved.accessToken
            ?: throw BackupException("Google did not return a Drive access token. Try again.")
        val next = DriveSession(accessToken = token, email = null)
        session = next
        return next
    }

    suspend fun signOut(activity: Activity) {
        val client = Identity.getAuthorizationClient(activity)
        val token = session?.accessToken
        if (token != null) {
            try {
                client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
            } catch (_: Exception) {
                // Local session is cleared either way.
            }
        }
        try {
            client.revokeAccess(RevokeAccessRequest.builder().build()).await()
        } catch (_: Exception) {
            // Local session is cleared either way.
        }
        session = null
    }

    /**
     * @param packageName the INSTALLED package. Temper Debug is `com.sinura.personaltrainer.debug`
     * and needs its own OAuth client; naming the release package here sent the owner to
     * register the wrong one.
     */
    private fun mapAuthError(error: Exception, packageName: String): BackupException {
        if (error is CancellationException) throw error
        val api = error as? ApiException
        // Every one of these used to reach Settings as the same sentence, so a Cloud Console
        // problem and a genuine cancel were indistinguishable on the phone. The status code is
        // the only evidence there is; log it before it is flattened into user-facing copy.
        AppLog.e(TAG, "Drive authorize failed (status=${api?.statusCode})", error)
        return when (api?.statusCode) {
            CommonStatusCodes.CANCELED ->
                BackupException("Google sign-in was cancelled.")
            CommonStatusCodes.SIGN_IN_REQUIRED ->
                BackupException(
                    "No Google account is available to this app. Add your Google account in " +
                        "Android Settings, then try again.",
                )
            CommonStatusCodes.NETWORK_ERROR ->
                BackupException("Connect to the internet to use Google Drive.")
            CommonStatusCodes.DEVELOPER_ERROR ->
                BackupException(
                    "Google Drive sign-in isn’t configured for this install. Add an Android OAuth " +
                        "client for $packageName, with this install's signing SHA-1, in Google Cloud Console.",
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

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
