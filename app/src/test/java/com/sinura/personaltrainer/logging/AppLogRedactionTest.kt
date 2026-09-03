package com.sinura.personaltrainer.logging

import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.diagnostics.DiagnosticCanaries
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Temper Debug is the daily install (`BuildConfig.DEBUG` is true). Workout
 * titles and paths still must not reach logcat; redaction is not a release-only
 * switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class)
class AppLogRedactionTest {
    private val previousSink = AppLog.sink
    private val previousRedact = AppLog.redactMessages

    @After
    fun restoreSeam() {
        AppLog.sink = previousSink
        AppLog.redactMessages = previousRedact
    }

    @Test
    fun debugBuildStillRedactsLogMessages() {
        assertTrue(BuildConfig.DEBUG)
        ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        val logged = mutableListOf<String>()
        AppLog.sink = { _, _, message, _ -> logged += message }
        AppLog.w("PT/Test", DiagnosticCanaries.WORKOUT_NAME)
        assertEquals(listOf(AppLog.REDACTED), logged)
        assertFalse(logged.any { DiagnosticCanaries.WORKOUT_NAME in it })
    }
}
