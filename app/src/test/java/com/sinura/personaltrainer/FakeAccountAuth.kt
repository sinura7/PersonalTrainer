package com.sinura.personaltrainer

import com.sinura.personaltrainer.domain.AccountAuthPort
import com.sinura.personaltrainer.domain.AccountSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAccountAuth(
    configured: Boolean = true,
    initialSession: AccountSession? = null,
) : AccountAuthPort {
    override val configured: Boolean = configured
    private val sessionState = MutableStateFlow(initialSession)
    override val session: Flow<AccountSession?> = sessionState.asStateFlow()

    var signInCalls = 0
    var signUpCalls = 0
    var signOutCalls = 0
    var deleteAccountCalls = 0
    var nextFailure: Exception? = null
    var acceptPassword: String = "correct"

    val currentSession: AccountSession? get() = sessionState.value

    fun setSession(session: AccountSession?) {
        sessionState.value = session
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signInCalls++
        nextFailure?.let { failure ->
            nextFailure = null
            return Result.failure(failure)
        }
        if (password != acceptPassword) {
            return Result.failure(IllegalStateException("Invalid login credentials"))
        }
        sessionState.value = AccountSession(email.trim(), userId = "fake-user-id")
        return Result.success(Unit)
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> {
        signUpCalls++
        nextFailure?.let { failure ->
            nextFailure = null
            return Result.failure(failure)
        }
        sessionState.value = AccountSession(email.trim(), userId = "fake-user-id")
        return Result.success(Unit)
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCalls++
        nextFailure?.let { failure ->
            nextFailure = null
            return Result.failure(failure)
        }
        sessionState.value = null
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        deleteAccountCalls++
        nextFailure?.let { failure ->
            nextFailure = null
            return Result.failure(failure)
        }
        sessionState.value = null
        return Result.success(Unit)
    }
}
