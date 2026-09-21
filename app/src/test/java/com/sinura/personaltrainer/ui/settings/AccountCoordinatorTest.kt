package com.sinura.personaltrainer.ui.settings

import com.sinura.personaltrainer.FakeAccountAuth
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.AccountAuthCopy
import com.sinura.personaltrainer.domain.AccountSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountCoordinatorTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun CoroutineScope.subscribe(coordinator: AccountCoordinator) =
        launch { coordinator.uiState.collect { } }

    @Test
    fun signInSuccessSurfacesEmail() = runTest(dispatcher) {
        val auth = FakeAccountAuth()
        val deps = FakeAppDependencies(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            accountAuth = auth,
            scheduler = dispatcher,
        )
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val coordinator = AccountCoordinator(deps, scope)
        val subscriber = scope.subscribe(coordinator)

        coordinator.signIn("owner@example.com", "correct")
        assertEquals(1, auth.signInCalls)
        assertEquals("owner@example.com", auth.currentSession?.email)
        assertTrue(coordinator.uiState.value.signedIn)
        assertEquals("owner@example.com", coordinator.uiState.value.session?.email)
        assertEquals(1, auth.signInCalls)
        subscriber.cancel()
        scope.cancel()
        deps.close()
    }

    @Test
    fun wrongPasswordShowsInvalidCredentialsCopy() = runTest(dispatcher) {
        val auth = FakeAccountAuth()
        val deps = FakeAppDependencies(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            accountAuth = auth,
            scheduler = dispatcher,
        )
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val coordinator = AccountCoordinator(deps, scope)
        val subscriber = scope.subscribe(coordinator)

        coordinator.signIn("owner@example.com", "wrong")
        assertTrue(coordinator.uiState.value.error!!.contains("Email or password"))
        subscriber.cancel()
        scope.cancel()
        deps.close()
    }

    @Test
    fun notConfiguredBuildShowsCalmMessage() = runTest(dispatcher) {
        val auth = FakeAccountAuth(configured = false)
        val deps = FakeAppDependencies(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            accountAuth = auth,
            scheduler = dispatcher,
        )
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val coordinator = AccountCoordinator(deps, scope)
        val subscriber = scope.subscribe(coordinator)

        assertEquals(false, coordinator.uiState.value.configured)
        coordinator.signIn("a@b.com", "secret")
        assertEquals(AccountAuthCopy.NOT_CONFIGURED, coordinator.uiState.value.error)
        subscriber.cancel()
        scope.cancel()
        deps.close()
    }

    @Test
    fun signOutClearsSession() = runTest(dispatcher) {
        val auth = FakeAccountAuth(initialSession = AccountSession("owner@example.com", userId = "uid-1"))
        val sync = RecordingSyncStatusPort()
        val deps = FakeAppDependencies(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            accountAuth = auth,
            syncStatus = sync,
            scheduler = dispatcher,
        )
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val coordinator = AccountCoordinator(deps, scope)
        val subscriber = scope.subscribe(coordinator)

        coordinator.signOut()
        assertEquals(false, coordinator.uiState.value.signedIn)
        assertNull(coordinator.uiState.value.session)
        assertEquals(1, auth.signOutCalls)
        assertEquals(1, sync.abandonOutboxCalls)
        subscriber.cancel()
        scope.cancel()
        deps.close()
    }

    @Test
    fun deleteAccountClearsSessionAndOutbox() = runTest(dispatcher) {
        val auth = FakeAccountAuth(initialSession = AccountSession("owner@example.com", userId = "uid-1"))
        val sync = RecordingSyncStatusPort()
        val deps = FakeAppDependencies(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            accountAuth = auth,
            syncStatus = sync,
            scheduler = dispatcher,
        )
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val coordinator = AccountCoordinator(deps, scope)
        val subscriber = scope.subscribe(coordinator)

        coordinator.deleteAccount()
        assertEquals(1, auth.deleteAccountCalls)
        assertEquals(false, coordinator.uiState.value.signedIn)
        assertEquals(1, sync.abandonOutboxCalls)
        subscriber.cancel()
        scope.cancel()
        deps.close()
    }

    @Test
    fun deleteAccountFailureSurfacesError() = runTest(dispatcher) {
        val auth = FakeAccountAuth(initialSession = AccountSession("owner@example.com", userId = "uid-1"))
        auth.nextFailure = IllegalStateException("Supabase auth delete failed (403): not allowed")
        val deps = FakeAppDependencies(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            accountAuth = auth,
            scheduler = dispatcher,
        )
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val coordinator = AccountCoordinator(deps, scope)
        val subscriber = scope.subscribe(coordinator)

        coordinator.deleteAccount()
        assertTrue(coordinator.uiState.value.error!!.contains("delete"))
        assertTrue(coordinator.uiState.value.signedIn)
        subscriber.cancel()
        scope.cancel()
        deps.close()
    }
}
