package com.sinura.personaltrainer.timer

import android.app.Application
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RestTimerRunningPresentationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun foregroundWhileUnlockedUsesInAppPresentation() {
        RestTimerAppForeground.setForegroundForTest(true)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        assertEquals(
            RestTimerRunningPresentation.FOREGROUND_IN_APP,
            RestTimerRunningPresentation.resolve(context),
        )
    }

    @Test
    fun backgroundWhileUnlockedUsesBackgroundPresentation() {
        RestTimerAppForeground.setForegroundForTest(false)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        assertEquals(
            RestTimerRunningPresentation.BACKGROUND_UNLOCKED,
            RestTimerRunningPresentation.resolve(context),
        )
    }

    @Test
    fun lockedUsesLockPresentationEvenInForeground() {
        RestTimerAppForeground.setForegroundForTest(true)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        assertEquals(
            RestTimerRunningPresentation.LOCKED,
            RestTimerRunningPresentation.resolve(context),
        )
    }
}
