package com.sinura.personaltrainer.data.auth

import com.sinura.personaltrainer.domain.AccountAuthError
import com.sinura.personaltrainer.domain.AccountAuthPort
import com.sinura.personaltrainer.domain.AccountSession
import com.sinura.personaltrainer.logging.AppLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.gotrue.SessionStatus
import com.sinura.personaltrainer.data.sync.SupabaseRestClient
import com.sinura.personaltrainer.domain.SyncEntityType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "PT/AccountAuth"

/**
 * Supabase Auth (email + password) for Settings. Session persistence uses the library's
 * Android storage; sync uses the same client's access token.
 */
class SupabaseAccountAuth(
    private val client: SupabaseClient,
    private val rest: SupabaseRestClient,
) : AccountAuthPort {
    override val configured: Boolean = true

    override val session: Flow<AccountSession?> =
        client.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val user = status.session.user ?: return@map null
                    val email = user.email?.trim().orEmpty()
                    val userId = user.id.trim()
                    if (email.isEmpty() || userId.isEmpty()) null else AccountSession(email, userId)
                }
                SessionStatus.LoadingFromStorage -> null
                is SessionStatus.NotAuthenticated -> null
                is SessionStatus.NetworkError -> null
            }
        }

    override suspend fun accessTokenOrNull(): String? =
        client.auth.currentSessionOrNull()?.accessToken

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        runAuthAction("signIn") {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }

    override suspend fun signUp(email: String, password: String): Result<Unit> =
        runAuthAction("signUp") {
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }

    override suspend fun signOut(): Result<Unit> =
        runAuthAction("signOut") {
            client.auth.signOut()
        }

    override suspend fun deleteAccount(): Result<Unit> =
        runAuthAction("deleteAccount") {
            val token = accessTokenOrNull() ?: error("Not signed in")
            deleteChildTablesFirst(token)
            rest.deleteAuthUser(token)
            client.auth.clearSession()
        }

    private fun deleteChildTablesFirst(accessToken: String) {
        val order = listOf(
            SyncEntityType.ACTIVITY_STRENGTH_SET,
            SyncEntityType.ACTIVITY_CARDIO_INTERVAL,
            SyncEntityType.ACTIVITY_BLOCK,
            SyncEntityType.ACTIVITY_SESSION,
            SyncEntityType.SCHEDULE_OCCURRENCE,
            SyncEntityType.SCHEDULE_RULE,
            SyncEntityType.ROUTINE_EXERCISE,
            SyncEntityType.ROUTINE,
            SyncEntityType.ACTIVITY_TEMPLATE,
            SyncEntityType.EXERCISE_MUSCLE,
            SyncEntityType.CUSTOM_EXERCISE,
            SyncEntityType.BODYWEIGHT_ENTRY,
        )
        order.forEach { type -> rest.deleteAllRows(type.remoteTable, accessToken) }
    }

    private suspend fun runAuthAction(label: String, block: suspend () -> Unit): Result<Unit> {
        return try {
            block()
            Result.success(Unit)
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.e(TAG, "$label failed", thrown)
            Result.failure(thrown)
        }
    }
}

internal fun Throwable.toAccountAuthError(configured: Boolean): AccountAuthError {
    if (!configured || this is UnconfiguredAccountAuth.NotConfiguredException) {
        return AccountAuthError.NotConfigured
    }
    if (this is UnknownHostException || this is HttpRequestTimeoutException || this is HttpRequestException) {
        return AccountAuthError.Network
    }
    val rawMessage = message.orEmpty()
    if (this is IllegalStateException && rawMessage.contains("Supabase", ignoreCase = true) &&
        rawMessage.contains("delete", ignoreCase = true)
    ) {
        return AccountAuthError.DeleteFailed
    }
    val text = rawMessage.lowercase()
    if (
        text.contains("invalid login credentials") ||
        text.contains("invalid email or password") ||
        text.contains("email not confirmed")
    ) {
        return AccountAuthError.InvalidCredentials
    }
    if (
        text.contains("network") ||
        text.contains("unable to resolve host") ||
        text.contains("timeout")
    ) {
        return AccountAuthError.Network
    }
    if (text.contains("delete") && (text.contains("failed") || text.contains("not allowed"))) {
        return AccountAuthError.DeleteFailed
    }
    return AccountAuthError.Message(message?.ifBlank { null } ?: "Sign-in failed.")
}
