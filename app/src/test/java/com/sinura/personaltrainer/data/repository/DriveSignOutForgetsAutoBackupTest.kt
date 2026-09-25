package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PRIVACY.md and the backup threat model (§4.3.1) promise that signing out of Drive deletes the
 * sealed backup password and turns automatic backup off. `BackupService.signOut` ends in
 * [PreferencesRepository.clearDriveSession], which used to forget only the account and folder,
 * so the password stayed on the phone and automatic backup carried on after the sign-out.
 */
@RunWith(RobolectricTestRunner::class)
class DriveSignOutForgetsAutoBackupTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun signingOutOfDriveTurnsAutomaticBackupOffAndForgetsItsPassword() = runBlocking {
        val prefs = deps.preferencesRepository
        prefs.setDriveAccountEmail("owner@example.com")
        prefs.setDriveFolderId("folder-1")
        prefs.armAutoBackup(sealedPassphrase = "sealed-blob")
        prefs.setAutoBackupLastSession("session-1")
        prefs.setAutoBackupNeedsSignIn(true)

        prefs.clearDriveSession()

        val auto = prefs.autoBackupSettings()
        assertFalse(auto.enabled)
        assertNull(auto.sealedPassphrase)
        assertNull(auto.lastBackedUpSessionId)
        assertFalse(prefs.autoBackupEnabled.first())
        assertFalse(prefs.autoBackupNeedsSignIn.first())
        assertNull(prefs.driveAccountEmailOnce())
        assertNull(prefs.driveFolderId())
    }

    @Test
    fun signingOutLeavesTheBackupHistoryLinesAlone() = runBlocking {
        val prefs = deps.preferencesRepository
        prefs.setLastBackup("temper-backup.json", atMillis = 1_000L)
        prefs.setLastVerifiedBackup("temper-backup.json", atMillis = 1_000L)
        prefs.armAutoBackup(sealedPassphrase = "sealed-blob")

        prefs.clearDriveSession()

        assertEquals("temper-backup.json", prefs.lastBackupName.first())
        assertEquals(1_000L, prefs.lastBackupAt.first())
        assertEquals("temper-backup.json", prefs.lastVerifiedBackupName.first())
    }

    @Test
    fun aCopyThatFinishesAfterSignOutCannotLeaveASignInNoteBehind() = runBlocking {
        val prefs = deps.preferencesRepository
        prefs.armAutoBackup(sealedPassphrase = "sealed-blob")
        prefs.setAutoBackupNeedsSignIn(true)
        assertTrue(prefs.autoBackupNeedsSignIn.first())

        prefs.clearDriveSession()
        // An after-workout copy that read the settings before the sign-out ends here.
        prefs.setAutoBackupNeedsSignIn(true)

        assertFalse(prefs.autoBackupNeedsSignIn.first())
    }
}
