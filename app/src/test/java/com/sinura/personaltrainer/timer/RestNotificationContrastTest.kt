package com.sinura.personaltrainer.timer

import android.app.Application
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit RT-1. The running rest card's countdown is the app's own layout, drawn by the system on
 * the system's notification surface: white in the light theme, dark in the dark one. Its text
 * was a fixed near-white, so in the light theme the countdown in the shade and on the lock
 * screen was near-white on white (about 1.1 to 1). The text now takes the system's notification
 * colours, which follow the phone's theme.
 *
 * Each case draws the card's three views (collapsed, expanded, heads-up) as the shade would,
 * in the phone's theme, and measures the countdown and its "Rest" line against that theme's
 * notification surface.
 */
@RunWith(RobolectricTestRunner::class)
class RestNotificationContrastTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "notnight")
    fun theCountdownReadsOnALightShade() {
        assertReadable(surface = Color.WHITE, theme = "light")
    }

    @Test
    @Config(qualifiers = "night")
    fun theCountdownReadsOnADarkShade() {
        assertReadable(surface = DARK_SURFACE, theme = "dark")
    }

    private fun assertReadable(surface: Int, theme: String) {
        val card = RestTimerNotifications.runningNotification(
            context = context,
            state = RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = 90_000L,
                totalSeconds = 90,
                sessionId = "session-1",
                timerId = "timer-1",
            ),
            nowElapsedRealtime = 0L,
            nowWallClockMillis = 1_000L,
        )
        val views = mapOf(
            "collapsed" to card.contentView,
            "expanded" to card.bigContentView,
            "heads-up" to card.headsUpContentView,
        )
        for ((name, remote) in views) {
            val drawn = drawn(checkNotNull(remote) { "the $name view is missing" })
            for (id in listOf(R.id.rest_chrono, R.id.rest_kicker)) {
                val text = drawn.findViewById<TextView>(id)
                val contrast = ColorUtils.calculateContrast(opaqueOn(text.currentTextColor, surface), surface)
                val label = context.resources.getResourceEntryName(id)
                assertTrue(
                    "$label in the $name view on a $theme shade: contrast %.2f, needs %.1f".format(contrast, MIN_CONTRAST),
                    contrast >= MIN_CONTRAST,
                )
            }
        }
    }

    /** The views as the shade inflates them: the app's layout, the phone's theme and resources. */
    private fun drawn(remote: RemoteViews) = remote.apply(context, FrameLayout(context))

    /** A translucent text colour as it lands on [surface]. */
    private fun opaqueOn(color: Int, surface: Int): Int = ColorUtils.compositeColors(color, surface)

    private companion object {
        /** Normal text, WCAG AA. */
        const val MIN_CONTRAST = 4.5

        /** The darkest a dark shade's card is drawn lighter than: Material's dark surface. */
        val DARK_SURFACE = Color.parseColor("#303030")
    }
}
