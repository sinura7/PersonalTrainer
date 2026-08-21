package com.sinura.personaltrainer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the instrumented lane itself: runner declared, deps resolve, APK installs. */
@RunWith(AndroidJUnit4::class)
class InstrumentationSmokeTest {
    @Test
    fun targetContextIsTheApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.sinura.personaltrainer", context.packageName)
    }
}
