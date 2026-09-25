package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.repository.prefs.SETTINGS_RESET_NOTE
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A damaged `user_settings` file used to stay damaged: every read fell back to defaults, every
 * write threw, and the front door's Retry could never succeed, because nothing replaced the
 * file (audit DB-2). The app's corruption handler, which the fake graph shares, now starts it
 * again from defaults, and settings save from then on.
 */
@RunWith(RobolectricTestRunner::class)
class UserSettingsCorruptionTest {
    private var deps: FakeAppDependencies? = null

    @After
    fun tearDown() {
        deps?.close()
    }

    @Test
    fun aDamagedSettingsFileStartsAgainFromDefaultsAndSavesFromThenOn() = runBlocking {
        val graph = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext<Context>(),
            prefsFileBeforeOpen = { file ->
                file.parentFile?.mkdirs()
                file.writeBytes(NOT_A_PREFERENCES_FILE)
            },
        ).also { deps = it }
        val prefs = graph.preferencesRepository

        prefs.setWeightUnit(WeightUnit.KG)

        assertEquals(WeightUnit.KG, prefs.weightUnit.first())
        // The front door reads the file directly; it sees a readable file, not "unavailable".
        assertTrue(prefs.onboardingCompleteHealth.first() is DataHealth.Available)
        // And the reset is said, in Settings → Backup, where automatic backup is turned back on.
        assertEquals(SETTINGS_RESET_NOTE, prefs.restoreRecoveryNote.first())
    }

    private companion object {
        /** Not a protocol buffer: the first byte names field 13 with wire type 6, which is invalid. */
        val NOT_A_PREFERENCES_FILE = "not a preferences file".toByteArray()
    }
}
