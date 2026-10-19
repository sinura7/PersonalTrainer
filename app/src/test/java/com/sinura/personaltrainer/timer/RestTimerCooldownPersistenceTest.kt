package com.sinura.personaltrainer.timer

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestTimerCooldownPersistenceTest {
    @Test
    fun cooldownMarkerSurvivesSaveAndRehydrate() {
        val persistence = SharedPrefsRestTimerStatePersistence(CooldownOwnPreferences(ApplicationProvider.getApplicationContext()))
        persistence.clear()
        val wall = 1_700_000_000_000L
        val elapsed = 20_000L
        val boot = wall - elapsed
        val stored = PersistedRestTimer(
            endsAtElapsedRealtime = 50_000L,
            totalSeconds = RestTimerMarkers.COOLDOWN_SECONDS,
            sessionId = null,
            bootMarker = boot,
            endsAtWallClockMillis = wall + 30_000L,
            timerId = "cd-1",
            sourceTag = RestTimerMarkers.COOLDOWN,
            bootCount = 7L,
        )
        assertTrue(persistence.save(stored))
        val loaded = checkNotNull(persistence.load())
        assertEquals(RestTimerMarkers.COOLDOWN, loaded.sourceTag)

        val outcome = RestTimerRehydrator.rehydrate(
            stored = loaded,
            nowElapsedRealtime = elapsed,
            nowWallClockMillis = wall,
            nowBootCount = 7L,
        )
        assertTrue(outcome is RestTimerRehydration.Running)
        val running = outcome as RestTimerRehydration.Running
        assertEquals(RestTimerMarkers.COOLDOWN, running.sourceTag)
        assertEquals(null, running.sessionId)
    }

    @Test
    fun legacyRowsWithoutMarkerLoadAsNormalRest() {
        val persistence = SharedPrefsRestTimerStatePersistence(CooldownOwnPreferences(ApplicationProvider.getApplicationContext()))
        persistence.clear()
        val legacy = PersistedRestTimer(
            endsAtElapsedRealtime = 90_000L,
            totalSeconds = 90,
            sessionId = "s1",
            bootMarker = 1L,
            endsAtWallClockMillis = 2L,
            timerId = "rest-1",
        )
        val prefs = CooldownOwnPreferences(ApplicationProvider.getApplicationContext())
            .getSharedPreferences("rest_timer_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("ends_at_elapsed", legacy.endsAtElapsedRealtime)
            .putInt("total_seconds", legacy.totalSeconds)
            .putString("session_id", legacy.sessionId)
            .putLong("boot_marker", legacy.bootMarker)
            .putLong("ends_at_wall", legacy.endsAtWallClockMillis)
            .putString("timer_id", legacy.timerId)
            .putLong("boot_count", legacy.bootCount)
            .commit()
        assertEquals("", checkNotNull(persistence.load()).sourceTag)
    }
}

private class CooldownOwnPreferences(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("cooldown-persistence-$name", mode)
}
