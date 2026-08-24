package com.sinura.personaltrainer.timer

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestTimerStatePersistenceTest {
    @Test
    fun saveIsReadableOnTheNextLine() {
        val persistence = SharedPrefsRestTimerStatePersistence(ApplicationProvider.getApplicationContext())
        persistence.clear()
        val state = PersistedRestTimer(
            endsAtElapsedRealtime = 12_000L,
            totalSeconds = 90,
            sessionId = "session-1",
            bootMarker = 99L,
            endsAtWallClockMillis = 1_700_000_000_000L,
            timerId = "timer-1",
        )

        persistence.save(state)

        assertEquals(state, persistence.load())
        persistence.clear()
        assertNull(persistence.load())
    }
}
