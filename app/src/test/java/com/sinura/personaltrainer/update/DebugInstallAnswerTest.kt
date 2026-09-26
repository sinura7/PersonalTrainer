package com.sinura.personaltrainer.update

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.testutil.forgetFirstApplication
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Audit RM-1 and RM-6, through the real installer and the app. Temper Debug hands a downloaded
 * build to Android's installer, and Android answers through the status the session was committed
 * with. For an app that is not the phone's installer the first answer is "the owner must
 * confirm", with Android's install sheet to open. That answer went to the main screen, which
 * never read it: the sheet never opened and the phone stayed on the old build. Each downloaded
 * build also stayed in the app's cache.
 *
 * The installer and the answer's target are the app's own. What Android would send is stood in
 * for: Robolectric's installer reports a failed session through the status, which shows where the
 * status goes, and each case sends that same target the answer it tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class DebugInstallAnswerTest {
    private lateinit var app: PersonalTrainerApp
    private val opened = mutableListOf<ActivityController<out Activity>>()

    @Before
    fun setUp() {
        forgetFirstApplication()
        app = ApplicationProvider.getApplicationContext()
        // Nothing but the answer may start a screen: the first-open walk has had its turn.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        app.markColdStartIntroShown()
        runBlocking {
            app.container.preferencesRepository.setOnboardingComplete(true)
            app.container.preferencesRepository.setLaunchPermissionsAsked(true)
        }
    }

    @After
    fun tearDown() {
        opened.forEach { it.pause().stop().destroy() }
        forgetFirstApplication()
    }

    @Test
    fun theOwnerIsAskedToConfirmTheInstall() {
        val confirm = Intent(CONFIRM_INSTALL).setPackage(SYSTEM_INSTALLER)

        answer(PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(Intent.EXTRA_INTENT, confirm) }

        val started = startedActivities()
        assertTrue(
            "Android's install sheet was never opened; started instead: $started",
            started.any { it.filterEquals(confirm) },
        )
    }

    @Test
    fun anInstallAndroidRefusesSaysSo() {
        answer(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
            putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        }

        assertEquals(DebugUpdateInstall.Failed, app.container.debugUpdate.ui.value.install)
    }

    @Test
    fun aCancelledInstallLeavesTheUpdateToTryAgain() {
        answer(PackageInstaller.STATUS_FAILURE_ABORTED)

        assertEquals(DebugUpdateInstall.Idle, app.container.debugUpdate.ui.value.install)
    }

    /** Only one downloaded build is kept: the one being installed. */
    @Test
    fun stagingANewBuildDeletesTheOneBefore() {
        val installer = AndroidDebugApkInstaller(app)
        val older = installer.stagingFile(OLDER).apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val newer = installer.stagingFile(NEWER)

        assertFalse("the older download is still in the cache", older.exists())
        assertEquals(emptyList<String>(), newer.parentFile?.list()?.toList().orEmpty().filter { it != newer.name })
    }

    /**
     * Hands a build to the real installer and sends its status target [status], as Android
     * would. Returns once that target has handled it.
     */
    private fun answer(status: Int, extras: Intent.() -> Unit = {}) {
        val installer = AndroidDebugApkInstaller(app)
        val apk: File = installer.stagingFile(NEWER).apply { writeBytes(byteArrayOf(0x50, 0x4b, 3, 4)) }
        installer.install(apk)
        val packageInstaller = app.packageManager.packageInstaller
        val session = packageInstaller.mySessions.lastOrNull()
        assertNotNull("the build was not handed to Android's installer", session)
        shadowOf(packageInstaller).setSessionFails(session!!.sessionId)
        val statusTarget = checkNotNull(shadowOf(app).nextStartedActivity) { "the session reported to nothing" }
        startedActivities()

        val answer = Intent(statusTarget)
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, session.sessionId)
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .apply(extras)
        val target = Class.forName(checkNotNull(statusTarget.component).className).asSubclass(Activity::class.java)
        opened += Robolectric.buildActivity(target, answer).setup()
    }

    private fun startedActivities(): List<Intent> =
        generateSequence { shadowOf(app).nextStartedActivity }.toList()

    private companion object {
        const val OLDER = 105
        const val NEWER = 106
        const val CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"
        const val SYSTEM_INSTALLER = "com.google.android.packageinstaller"
    }
}
