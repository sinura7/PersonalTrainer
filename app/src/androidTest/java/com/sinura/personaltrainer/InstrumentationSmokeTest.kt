package com.sinura.personaltrainer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the instrumented lane itself: runner declared, deps resolve, APK installs
 * on the generated debug application id — not a hardcoded release package.
 */
@RunWith(AndroidJUnit4::class)
class InstrumentationSmokeTest {
    @Test
    fun targetContextIsTheGeneratedApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
    }

    @Test
    fun debugLaneUsesTheDebugSuffix() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "connectedDebugAndroidTest must install com.sinura.personaltrainer.debug, not release",
            context.packageName.endsWith(".debug"),
        )
    }
}
