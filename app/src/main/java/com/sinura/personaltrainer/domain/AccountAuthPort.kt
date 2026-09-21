package com.sinura.personaltrainer.domain

import kotlinx.coroutines.flow.Flow

/** Signed-in Temper Account (Supabase Auth). Sync is out of scope for this port. */
data class AccountSession(val email: String)

/** User-visible failure bucket for Settings → Account. */
sealed interface AccountAuthError {
    data object NotConfigured : AccountAuthError
    data object InvalidCredentials : AccountAuthError
    data object Network : AccountAuthError
    data class Message(val text: String) : AccountAuthError
}

/**
 * Email sign-in for optional cloud account. Core training never calls this.
 *
 * Production uses Supabase when [configured]; otherwise [UnconfiguredAccountAuth].
 */
interface AccountAuthPort {
    val configured: Boolean
    val session: Flow<AccountSession?>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(email: String, password: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
}
