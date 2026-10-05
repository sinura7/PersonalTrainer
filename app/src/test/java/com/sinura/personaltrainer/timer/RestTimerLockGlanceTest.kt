package com.sinura.personaltrainer.timer

import android.app.Application
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RestTimerLockGlanceTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun unlockedInteractiveScreenDoesNotAutoPresent() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        assertFalse(RestTimerLockGlance.shouldAutoPresentRunning(context))
    }

    @Test
    fun keyguardLockedAutoPresents() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        assertTrue(RestTimerLockGlance.shouldAutoPresentRunning(context))
    }

    @Test
    fun screenOffAutoPresents() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        assertTrue(RestTimerLockGlance.shouldAutoPresentRunning(context))
    }
}
