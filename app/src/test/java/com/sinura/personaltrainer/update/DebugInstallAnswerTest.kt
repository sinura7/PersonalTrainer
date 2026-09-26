package com.sinura.personaltrainer.update

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.testutil.forgetFirstApplication
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Each case sends its answer through the very status the session was committed with, as Android
 * does: the status must exist, reach a component the manifest declares, and carry what Android
 * writes into it. Robolectric's installer answers nothing itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class DebugInstallAnswerTest {
    private lateinit var app: PersonalTrainerApp
    private var screen: ActivityController<MainActivity>? = null

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
        screen?.pause()?.stop()?.destroy()
        forgetFirstApplication()
    }

    @Test
    fun theOwnerIsAskedToConfirmTheInstall() {
        openTheApp()
        startedActivities()

        answer(committed(), PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(Intent.EXTRA_INTENT, SHEET) }

        assertTrue("Android's install sheet was not opened", startedActivities().any { it.filterEquals(SHEET) })
    }

    /**
     * Android answers while the owner is in another app (the download took a while): the app may
     * not open a screen then. The sheet opens when the owner comes back, and only once.
     */
    @Test
    fun anAnswerWhileTheAppIsInTheBackgroundOpensTheSheetOnReturn() {
        openTheApp()
        screen!!.pause().stop()
        idle()
        startedActivities()

        answer(committed(), PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(Intent.EXTRA_INTENT, SHEET) }
        assertFalse("a screen opened while the app was away", startedActivities().any { it.filterEquals(SHEET) })

        screen!!.restart().resume()
        idle()
        assertEquals(1, startedActivities().count { it.filterEquals(SHEET) })

        screen!!.pause().resume()
        idle()
        assertFalse("the sheet opened a second time", startedActivities().any { it.filterEquals(SHEET) })
    }

    /** The same when the main screen had closed: the sheet opens when the app is opened again. */
    @Test
    fun anAnswerWithNoScreenOpenOpensTheSheetWhenTheAppOpens() {
        answer(committed(), PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(Intent.EXTRA_INTENT, SHEET) }
        assertFalse("a screen opened while the app was away", startedActivities().any { it.filterEquals(SHEET) })

        openTheApp()
        assertEquals(1, startedActivities().count { it.filterEquals(SHEET) })

        screen!!.pause().resume()
        idle()
        assertFalse("the sheet opened a second time", startedActivities().any { it.filterEquals(SHEET) })
    }

    @Test
    fun anInstallAndroidRefusesSaysSoAndLeavesNoDownload() {
        val session = committed()
        answer(session, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
            putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        }

        assertEquals(DebugUpdateInstall.Failed, app.container.debugUpdate.ui.value.install)
        awaitNoDownloads()
    }

    @Test
    fun aCancelledSheetLeavesTheUpdateToTryAgainAndNoDownload() {
        answer(committed(), PackageInstaller.STATUS_FAILURE_ABORTED)

        assertEquals(DebugUpdateInstall.Idle, app.container.debugUpdate.ui.value.install)
        awaitNoDownloads()
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

    /** No other app can reach where Android answers, so none can hand the app a screen to open. */
    @Test
    fun whereAndroidAnswersIsNotExported() {
        val info = app.packageManager.getReceiverInfo(ComponentName(app, DEBUG_INSTALL_STATUS_RECEIVER), 0)
        assertFalse(info.exported)
    }

    /** Hands a build to the real installer; the status it committed with. */
    private fun committed(): PendingIntent {
        val installer = AndroidDebugApkInstaller(app)
        val apk = installer.stagingFile(NEWER).apply { writeBytes(byteArrayOf(0x50, 0x4b, 3, 4)) }
        installer.install(apk)
        val session = app.packageManager.packageInstaller.mySessions.lastOrNull()
        assertNotNull("the build was not handed to Android's installer", session)
        val status = PendingIntent.getBroadcast(
            app,
            session!!.sessionId,
            Intent().setClassName(app, DEBUG_INSTALL_STATUS_RECEIVER),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE,
        )
        assertNotNull("the session was committed with no status for Android to answer", status)
        idle()
        return status!!
    }

    /** What Android writes into the session's status when it answers. */
    private fun answer(status: PendingIntent, code: Int, extras: Intent.() -> Unit = {}) {
        status.send(app, 0, Intent().putExtra(PackageInstaller.EXTRA_STATUS, code).apply(extras))
        idle()
    }

    private fun openTheApp() {
        screen = Robolectric.buildActivity(MainActivity::class.java, Intent(app, MainActivity::class.java)).setup()
        idle()
    }

    /** The clear runs off the main thread, after any download or hand-over running then. */
    private fun awaitNoDownloads() {
        val staged = File(app.cacheDir, "debug-update")
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (staged.list()?.any { it.endsWith(".apk") } == true && System.currentTimeMillis() < deadline) {
            idle()
            Thread.sleep(10)
        }
        assertNull("a download is still in the cache", staged.list()?.firstOrNull { it.endsWith(".apk") })
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun startedActivities(): List<Intent> =
        generateSequence { shadowOf(app).nextStartedActivity }.toList()

    private companion object {
        const val OLDER = 105
        const val NEWER = 106
        const val WAIT_MS = 10_000L
        val SHEET: Intent = Intent("android.content.pm.action.CONFIRM_INSTALL").setPackage("com.google.android.packageinstaller")
    }
}
