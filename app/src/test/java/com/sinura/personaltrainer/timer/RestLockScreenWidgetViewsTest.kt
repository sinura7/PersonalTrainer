package com.sinura.personaltrainer.timer

import android.app.Application
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestLockScreenWidgetViewsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun buildsRemoteViewsForIdleAndRunningSnapshots() {
        assertNotNull(idleViews())
        assertNotNull(
            RestLockScreenWidgetViews.remoteViewsWide(
                context = context,
                state = RestTimerSnapshot(
                    running = true,
                    endsAtElapsedRealtime = 60_000L,
                    totalSeconds = 60,
                    sessionId = "s",
                    timerId = "t",
                ),
            ),
        )
    }

    @Test
    fun updaterWithNoInstalledWidgetsDoesNotThrow() {
        RestLockScreenWidgetUpdater.updateAll(context, RestTimerSnapshot(running = false))
    }

    private fun idleViews(): RemoteViews =
        RestLockScreenWidgetViews.remoteViewsWide(
            context = context,
            state = RestTimerSnapshot(running = false),
        )
}
