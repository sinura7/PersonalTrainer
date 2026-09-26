package com.sinura.personaltrainer.update

import android.content.Intent
import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** How Android's answer to an install session is read ([DebugInstallAnswer.from]). */
@RunWith(RobolectricTestRunner::class)
class DebugInstallAnswerReadTest {
    @Test
    fun anIntentWithNoStatusIsNotAnAnswer() {
        assertNull(DebugInstallAnswer.from(Intent()))
        assertNull(DebugInstallAnswer.from(null))
    }

    @Test
    fun aConfirmCarriesAndroidsSheet() {
        val sheet = Intent("android.content.pm.action.CONFIRM_INSTALL")
        val answer = DebugInstallAnswer.from(status(PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, sheet))
        assertTrue(answer is DebugInstallAnswer.Confirm && answer.sheet.filterEquals(sheet))
    }

    @Test
    fun aConfirmWithNoSheetIsAFailure() {
        val answer = DebugInstallAnswer.from(status(PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertTrue(answer is DebugInstallAnswer.Failed)
    }

    @Test
    fun successCancelAndRefusalAreTold() {
        assertEquals(DebugInstallAnswer.Installed, DebugInstallAnswer.from(status(PackageInstaller.STATUS_SUCCESS)))
        assertEquals(DebugInstallAnswer.Cancelled, DebugInstallAnswer.from(status(PackageInstaller.STATUS_FAILURE_ABORTED)))
        assertEquals(
            DebugInstallAnswer.Failed(PackageInstaller.STATUS_FAILURE_STORAGE, "no room"),
            DebugInstallAnswer.from(
                status(PackageInstaller.STATUS_FAILURE_STORAGE).putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "no room"),
            ),
        )
    }

    private fun status(value: Int) = Intent().putExtra(PackageInstaller.EXTRA_STATUS, value)
}
