// com.google.android.gms.auth.api.identity — declaration-only stubs. See compile-stubs/README.md.
//
// AuthorizationResult.pendingIntent and .accessToken are NULLABLE, as the real API declares
// them. That is the whole reason DriveAuthClient has "Google sign-in could not continue" and
// "Google did not return a Drive access token" branches; a non-null stub would type-check the
// file while quietly deleting those two checks from the compiler's view.

package com.google.android.gms.auth.api.identity

import android.app.Activity
import android.app.PendingIntent
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task

class AuthorizationRequest internal constructor() {
    class Builder internal constructor() {
        fun setRequestedScopes(requestedScopes: List<Scope>): Builder = TODO("compile-only stub")
        fun build(): AuthorizationRequest = TODO("compile-only stub")
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = TODO("compile-only stub")
    }
}

class AuthorizationResult internal constructor() {
    val accessToken: String? get() = TODO("compile-only stub")
    val pendingIntent: PendingIntent? get() = TODO("compile-only stub")
    fun hasResolution(): Boolean = TODO("compile-only stub")
}

class ClearTokenRequest internal constructor() {
    class Builder internal constructor() {
        fun setToken(token: String): Builder = TODO("compile-only stub")
        fun build(): ClearTokenRequest = TODO("compile-only stub")
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = TODO("compile-only stub")
    }
}

class RevokeAccessRequest internal constructor() {
    class Builder internal constructor() {
        fun build(): RevokeAccessRequest = TODO("compile-only stub")
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = TODO("compile-only stub")
    }
}

interface AuthorizationClient {
    fun authorize(request: AuthorizationRequest): Task<AuthorizationResult>
    fun clearToken(request: ClearTokenRequest): Task<Void>
    fun revokeAccess(request: RevokeAccessRequest): Task<Void>
}

object Identity {
    @JvmStatic
    fun getAuthorizationClient(activity: Activity): AuthorizationClient = TODO("compile-only stub")
}
