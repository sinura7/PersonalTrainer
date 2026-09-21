package com.sinura.personaltrainer.data.auth

import com.sinura.personaltrainer.domain.AccountAuthPort
import com.sinura.personaltrainer.domain.AccountSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Placeholder when Supabase URL/key were not supplied at build time. */
class UnconfiguredAccountAuth : AccountAuthPort {
    override val configured: Boolean = false
    override val session: Flow<AccountSession?> = flowOf(null)

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        Result.failure(NotConfiguredException)

    override suspend fun signUp(email: String, password: String): Result<Unit> =
        Result.failure(NotConfiguredException)

    override suspend fun signOut(): Result<Unit> = Result.failure(NotConfiguredException)

    override suspend fun deleteAccount(): Result<Unit> = Result.failure(NotConfiguredException)

    internal object NotConfiguredException : Exception() {
        private fun readResolve(): Any = NotConfiguredException
    }
}
