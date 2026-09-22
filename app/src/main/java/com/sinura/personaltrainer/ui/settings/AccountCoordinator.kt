package com.sinura.personaltrainer.ui.settings

import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.data.auth.toAccountAuthError
import com.sinura.personaltrainer.domain.AccountAuthCopy
import com.sinura.personaltrainer.domain.AccountSession
import com.sinura.personaltrainer.domain.AccountSyncGate
import com.sinura.personaltrainer.domain.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AccountBusyKind {
    SIGN_IN,
    SIGN_UP,
    SIGN_OUT,
    DELETE_ACCOUNT,
}

data class AccountUiState(
    val configured: Boolean = false,
    val session: AccountSession? = null,
    val busy: AccountBusyKind? = null,
    val error: String? = null,
    val sync: SyncStatus = SyncStatus(false, 0, null, null),
    /** Whether Settings → Account offers deletion in the app ([AccountSyncGate]). */
    val deleteAvailable: Boolean = AccountSyncGate.IN_APP_DELETE_AVAILABLE,
) {
    val signedIn: Boolean get() = session != null
}

/**
 * Settings → Account. Supabase sign-in only; no sync or Drive changes.
 */
class AccountCoordinator(
    private val container: AppDependencies,
    private val scope: CoroutineScope,
    private val inAppDeleteAvailable: Boolean = AccountSyncGate.IN_APP_DELETE_AVAILABLE,
) {
    private val busy = MutableStateFlow<AccountBusyKind?>(null)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountUiState> = combine(
        container.accountAuth.session,
        container.syncStatus.status,
        busy,
        error,
    ) { session, sync, busyKind, message ->
        AccountUiState(
            configured = container.accountAuth.configured,
            session = session,
            busy = busyKind,
            error = message,
            sync = sync,
            deleteAvailable = inAppDeleteAvailable,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState(
            configured = container.accountAuth.configured,
            deleteAvailable = inAppDeleteAvailable,
        ),
    )

    fun clearError() {
        error.value = null
    }

    fun signIn(email: String, password: String) {
        if (busy.value != null) return
        busy.value = AccountBusyKind.SIGN_IN
        error.value = null
        scope.launch {
            val result = container.accountAuth.signIn(email, password)
            busy.value = null
            result.onSuccess {
                container.syncStatus.bootstrapAfterSignIn()
                container.syncStatus.requestSync()
            }
            result.onFailure { failure ->
                error.value = AccountAuthCopy.errorMessage(
                    failure.toAccountAuthError(container.accountAuth.configured),
                )
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (busy.value != null) return
        busy.value = AccountBusyKind.SIGN_UP
        error.value = null
        scope.launch {
            val result = container.accountAuth.signUp(email, password)
            busy.value = null
            result.onSuccess {
                container.syncStatus.bootstrapAfterSignIn()
                container.syncStatus.requestSync()
            }
            result.onFailure { failure ->
                error.value = AccountAuthCopy.errorMessage(
                    failure.toAccountAuthError(container.accountAuth.configured),
                )
            }
        }
    }

    fun signOut() {
        if (busy.value != null) return
        busy.value = AccountBusyKind.SIGN_OUT
        error.value = null
        scope.launch {
            val result = container.accountAuth.signOut()
            busy.value = null
            result.onSuccess { container.syncStatus.abandonOutboxOnSignOut() }
            result.onFailure { failure ->
                error.value = AccountAuthCopy.errorMessage(
                    failure.toAccountAuthError(container.accountAuth.configured),
                )
            }
        }
    }

    /**
     * A no-op while in-app deletion is off. The screen hides the button then, and this guard
     * keeps a stale dialog or a future caller from reaching a delete that cannot finish.
     */
    fun deleteAccount() {
        if (!inAppDeleteAvailable || busy.value != null) return
        busy.value = AccountBusyKind.DELETE_ACCOUNT
        error.value = null
        scope.launch {
            val result = container.accountAuth.deleteAccount()
            busy.value = null
            result.onSuccess { container.syncStatus.abandonOutboxOnSignOut() }
            result.onFailure { failure ->
                error.value = AccountAuthCopy.errorMessage(
                    failure.toAccountAuthError(container.accountAuth.configured),
                )
            }
        }
    }
}
