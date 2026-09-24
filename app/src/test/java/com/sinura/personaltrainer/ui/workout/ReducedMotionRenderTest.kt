package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.ui.theme.instrumentLinear
import com.sinura.personaltrainer.ui.theme.instrumentTween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reduced motion on the floor, where the lifter would see it: the instrument specs snap when
 * the phone asks for reduced motion and ease otherwise, and the dock's rest card goes through
 * them, so under reduced motion a tick of its ring lands as soon as it is drawn, and so does the
 * ring's change to the last ten seconds' warning colour, where otherwise both ease in.
 *
 * These were lines of Motion.kt and RestTimerCard.kt read as text (`if
 * (LocalReducedMotion.current) snap() else tween(durationMs)`, instrumentLinear's
 * `LocalReducedMotion.current` and `snap()`, `animationSpec = instrumentLinear(Motion.TICK_MS)`,
 * `animationSpec = instrumentTween(Motion.BASE)`). The rest pages' pulse cannot be rendered
 * (the Compose test clock stops every infinite animation); FloorPacketHFinalPassTest bans an
 * unguarded one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class ReducedMotionRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val px: Float get() = compose.density.density

    @Test
    fun theInstrumentSpecsSnapUnderReducedMotionAndEaseOtherwise() {
        val tweens = mutableMapOf<Boolean, FiniteAnimationSpec<Float>>()
        val linears = mutableMapOf<Boolean, FiniteAnimationSpec<Float>>()
        compose.showFloor {
            listOf(true, false).forEach { reduced ->
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    tweens[reduced] = instrumentTween(Motion.BASE)
                    linears[reduced] = instrumentLinear(Motion.TICK_MS)
                }
            }
        }
        compose.waitForIdle()
        assertTrue("reduced motion snaps a tween, was ${tweens[true]}", tweens[true] is SnapSpec)
        assertTrue("reduced motion snaps a linear sweep, was ${linears[true]}", linears[true] is SnapSpec)
        val tween = tweens[false] as TweenSpec<*>
        assertEquals(Motion.BASE, tween.durationMillis)
        val linear = linears[false] as TweenSpec<*>
        assertEquals(Motion.TICK_MS, linear.durationMillis)
        assertEquals(LinearEasing, linear.easing)
    }

    @Test
    fun underReducedMotionTheDockRingLandsOnATickAtOnce() {
        var remaining by mutableIntStateOf(60)
        showRunningCard(remainingNow = { remaining })
        assertEquals("half the rest is left", 0.5f, cyanShare("1:00"), SHARE_SLACK)
        compose.mainClock.autoAdvance = false
        try {
            remaining = 30
            nextFrame()
            assertEquals("a quarter left, as soon as it is drawn", 0.25f, cyanShare("0:30"), SHARE_SLACK)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun underReducedMotionTheDockRingTakesItsWarningColourAtOnce() {
        var remaining by mutableIntStateOf(60)
        showRunningCard(remainingNow = { remaining })
        compose.mainClock.autoAdvance = false
        try {
            remaining = WARNING_SECONDS
            nextFrame()
            val arc = ringSamples(RestTimer.formatClock(WARNING_SECONDS))
            assertTrue("the arc is drawn in the warning colour, was ${arc.count { isNear(it, Warn) }} samples", arc.count { isNear(it, Warn) } >= MIN_ARC_SAMPLES)
            assertEquals("and none of it is still cyan", 0, arc.count { isNear(it, RestCyan) })
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /** The dock's rest card, running out of 2:00, with [remainingNow] left, under reduced motion. */
    private fun showRunningCard(remainingNow: () -> Int) {
        compose.showFloor {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    RestTimerCard(
                        remainingSeconds = remainingNow(),
                        totalSeconds = TOTAL_SECONDS,
                        running = true,
                        completedTimerId = null,
                        afterWarmup = false,
                        offerSetClock = false,
                        onSkip = {},
                        onStart = {},
                        onNudge = {},
                        onEditDuration = {},
                        onStartSetClock = {},
                        onOpenRest = {},
                    )
                }
            }
        }
    }

    /**
     * Hands the test's write to the screen and runs a few frames. With the clock held, the write
     * reaches the composition only once the snapshot is applied; a snapped value then lands on
     * the animation's first frame, while an eased one has barely begun (a few hundredths of the
     * way over the ring's second, a quarter over the colour's 240 ms).
     */
    private fun nextFrame() {
        Snapshot.sendApplyNotifications()
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
    }

    /** How much of the ring, read along its stroke, is drawn in the running rest's cyan. */
    private fun cyanShare(clock: String): Float {
        val samples = ringSamples(clock)
        return samples.count { isNear(it, RestCyan) }.toFloat() / samples.size
    }

    /** The ring's stroke, read along its middle all the way round, beside the clock [clock]. */
    private fun ringSamples(clock: String): List<Int> {
        val tile = compose.onNode(hasClickAction() and hasText(clock) and hasAnyAncestor(hasTestTag(WorkoutTestTags.REST_BAR))).windowBounds()
        val lead = box(tile.left, tile.top, tile.left + RING_ZONE_DP * px, tile.bottom)
        val frame = compose.drawWindow()
        val ring: Rect = checkNotNull(frame.inkBox(lead, Surface2)) { "nothing is drawn where the ring should be" }
        return frame.around(ring.center.x, ring.center.y, ring.width / 2f - 2f * px)
    }

    private companion object {
        const val TOTAL_SECONDS = 120

        /** The change's own frame, the animation's first, and one to draw it. */
        const val FRAMES = 4

        /** Inside the last ten seconds, where the ring turns to the warning colour. */
        const val WARNING_SECONDS = 9

        /** Samples every 3°, so 120 round the ring; the drained share is read to within a tenth. */
        const val SHARE_SLACK = 0.1f

        /** 9 of 120 seconds is some 9 samples of arc; a round cap adds a sample or two. */
        const val MIN_ARC_SAMPLES = 5

        /** The ring (48 dp) and a little of the 12 dp gap after it, never the kicker's first letter. */
        const val RING_ZONE_DP = 54f
    }
}
