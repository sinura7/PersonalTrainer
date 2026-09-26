package com.sinura.personaltrainer.timer

import android.app.Application
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Each case draws the views the card sets (collapsed, expanded, and heads-up, which is the
 * expanded one) as the shade would, with the phone's day or night setting, and measures every
 * line of text against that setting's card at its least favourable: the greyest light card, a
 * light dark one. The layout must not paint a background of its own or fade, since the text is
 * chosen for the system's card.
 */
@RunWith(RobolectricTestRunner::class)
class RestNotificationContrastTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "notnight")
    fun theCountdownReadsOnALightShade() {
        assertReadable(surface = LIGHT_CARD, theme = "light")
    }

    @Test
    @Config(qualifiers = "night")
    fun theCountdownReadsOnADarkShade() {
        assertReadable(surface = DARK_CARD, theme = "dark")
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
            val all = everyView(drawn)
            for (view in all) {
                val label = if (view.id == View.NO_ID) view.javaClass.simpleName else context.resources.getResourceEntryName(view.id)
                assertNull("$label in the $name view paints its own background", view.background)
                assertEquals("$label in the $name view is faded", 1f, view.alpha)
            }
            val lines = all.filterIsInstance<TextView>()
            assertTrue("the $name view has its countdown and its \"Rest\" line", lines.map { it.id }.containsAll(listOf(R.id.rest_chrono, R.id.rest_kicker)))
            for (text in lines) {
                val contrast = ColorUtils.calculateContrast(opaqueOn(text.currentTextColor, surface), surface)
                val label = context.resources.getResourceEntryName(text.id)
                assertTrue(
                    "$label in the $name view on a $theme shade: contrast %.2f, needs %.1f".format(contrast, MIN_CONTRAST),
                    contrast >= MIN_CONTRAST,
                )
            }
        }
    }

    /** The views as the shade inflates them: the app's layout and resources, day or night. */
    private fun drawn(remote: RemoteViews) = remote.apply(context, FrameLayout(context))

    private fun everyView(root: View): List<View> =
        listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { everyView(root.getChildAt(it)) } else emptyList()

    /** A translucent text colour as it lands on [surface]. */
    private fun opaqueOn(color: Int, surface: Int): Int = ColorUtils.compositeColors(color, surface)

    private companion object {
        /** Normal text, WCAG AA. */
        const val MIN_CONTRAST = 4.5

        /** A light card at its greyest (Android 12 and later tint it; before, it is white). */
        val LIGHT_CARD = Color.parseColor("#E8E7EF")

        /** A dark card on the light side of the dark themes'. */
        val DARK_CARD = Color.parseColor("#303030")
    }
}
