// com.google.android.gms (Play Services auth / tasks) — declaration-only stubs.
// See compile-stubs/README.md. Nothing here executes.
//
// Used by exactly one file, data/backup/DriveAuthClient.kt. The nullability is the part that
// matters: `pendingIntent` and `accessToken` are nullable because the real results are, and
// DriveAuthClient's error handling (`?: throw BackupException(...)`) exists only because of
// that. Declaring them non-null would delete two genuine failure paths from the type-check.

package com.google.android.gms.common.api

/** statusCode is Int, matching the real class, so the `when (api?.statusCode)` in
 *  DriveAuthClient compares against the CommonStatusCodes constants below. */
open class ApiException(val statusCode: Int) : Exception()

class Scope(scopeUri: String)

object CommonStatusCodes {
    const val CANCELED: Int = 16
    const val SIGN_IN_REQUIRED: Int = 4
    const val NETWORK_ERROR: Int = 7
    const val DEVELOPER_ERROR: Int = 10
}
