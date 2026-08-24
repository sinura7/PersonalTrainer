package com.sinura.personaltrainer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device proof that the installed debug package targets API 36.
 * The lane device may be API 29; targetSdkVersion is the app's claim.
 */
@RunWith(AndroidJUnit4::class)
class SdkTargetInstrumentedTest {
    @Test
    fun debugPackageTargetsApi36() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".debug"))
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(36, info.targetSdkVersion)
    }
}
