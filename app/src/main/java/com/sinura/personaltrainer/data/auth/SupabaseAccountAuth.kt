package com.sinura.personaltrainer.data.auth

import com.sinura.personaltrainer.domain.AccountAuthError
import com.sinura.personaltrainer.domain.AccountAuthPort
import com.sinura.personaltrainer.domain.AccountSession
import com.sinura.personaltrainer.logging.AppLog
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "PT/AccountAuth"

/**
 * Supabase Auth (email + password) for Settings only. Session persistence uses the library's
 * Android storage; no sync calls are made here.
 */
class SupabaseAccountAuth(
    supabaseUrl: String,
    supabaseAnonKey: String,
) : AccountAuthPort {
    override val configured: Boolean = true

    private val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseAnonKey,
    ) {
        install(Auth)
    }

    override val session: Flow<AccountSession?> =
        client.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val email = status.session.user?.email?.trim().orEmpty()
                    if (email.isEmpty()) null else AccountSession(email)
                }
                SessionStatus.LoadingFromStorage -> null
                is SessionStatus.NotAuthenticated -> null
                is SessionStatus.NetworkError -> null
            }
        }

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
    val text = message?.lowercase().orEmpty()
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
    return AccountAuthError.Message(message?.ifBlank { null } ?: "Sign-in failed.")
}
