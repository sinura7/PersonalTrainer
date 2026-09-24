package com.sinura.personaltrainer.timer

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
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
        // Its own file: the app's start-up recovery clears the shared row on its own thread,
        // and could land between this save and the read that follows it.
        val persistence = SharedPrefsRestTimerStatePersistence(OwnPreferences(ApplicationProvider.getApplicationContext()))
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

/** The same context, with preferences of its own and itself as the application context. */
private class OwnPreferences(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("persistence-test-$name", mode)
}
