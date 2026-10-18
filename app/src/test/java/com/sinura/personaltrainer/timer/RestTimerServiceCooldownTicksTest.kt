package com.sinura.personaltrainer.timer

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerMarkers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class)
class RestTimerServiceCooldownTicksTest {
    private lateinit var app: PersonalTrainerApp
    private var ticked = false

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        RestTimerCompletion.reset()
        app.container.restTimerController.stop(fromService = true)
        ticked = false
        RestTimerService.tickObserver = { ticked = true }
        runBlockingPrefsTickOn()
    }

    @After
    fun tearDown() {
        RestTimerService.tickObserver = null
        app.container.restTimerController.stop(fromService = true)
    }

    @Test
    fun cooldownDoesNotScheduleLastFiveSecondTicks() {
        val store = app.container.restTimerStore
        store.start(
            totalSeconds = RestTimerMarkers.COOLDOWN_SECONDS,
            sessionId = null,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            sourceTag = RestTimerMarkers.COOLDOWN,
        )
        val controller = Robolectric.buildService(RestTimerService::class.java)
        controller.create()
        controller.get().onStartCommand(
            Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_SYNC),
            0,
            1,
        )
        shadowOf(app.mainLooper).idle()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(86))
        shadowOf(app.mainLooper).idle()
        assertFalse("cool-down must not tick in the last five seconds", ticked)
        controller.destroy()
    }

    private fun runBlockingPrefsTickOn() {
        kotlinx.coroutines.runBlocking {
            app.container.preferencesRepository.setRestTickEnabled(true)
        }
        shadowOf(app.mainLooper).idle()
    }
}
