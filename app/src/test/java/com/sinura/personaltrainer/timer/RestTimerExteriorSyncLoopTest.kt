package com.sinura.personaltrainer.timer

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class)
class RestTimerExteriorSyncLoopTest {
    private lateinit var app: PersonalTrainerApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        RestTimerCompletion.reset()
        app.container.restTimerController.stop(fromService = true)
        ShadowSettings.setCanDrawOverlays(true)
        RestTimerAppForeground.setForegroundForTest(false)
        RestTimerOverlayController.release()
        shadowOf(app.mainLooper).idle()
    }

    @After
    fun tearDown() {
        ShadowSettings.reset()
        RestTimerAppForeground.setForegroundForTest(false)
        RestTimerOverlayController.release()
        app.container.restTimerController.stop(fromService = true)
        shadowOf(app.mainLooper).idle()
    }

    @Test
    fun serviceExteriorSyncDoesNotReRequestItself() {
        val store = app.container.restTimerStore
        store.start(
            totalSeconds = RestTimerMarkers.COOLDOWN_SECONDS,
            sessionId = null,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            sourceTag = RestTimerMarkers.COOLDOWN,
        )
        val snap = store.current()
        RestOverlayDismissStore.dismissForRest(app, snap.timerId)
        val controller = Robolectric.buildService(RestTimerService::class.java)
        controller.create()
        controller.get().onStartCommand(
            Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_EXTERIOR_SYNC),
            0,
            1,
        )
        shadowOf(app.mainLooper).idle()
        assertNull(shadowOf(app).nextStartedService)
        controller.get().onStartCommand(
            Intent(app, RestTimerService::class.java).setAction(RestTimerService.ACTION_EXTERIOR_SYNC),
            0,
            2,
        )
        shadowOf(app.mainLooper).idle()
        assertNull(shadowOf(app).nextStartedService)
        controller.destroy()
    }
}
