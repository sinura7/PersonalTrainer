package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.workout.WorkoutDraftPersistence
import com.sinura.personaltrainer.workout.WorkoutDraftRestore
import com.sinura.personaltrainer.workout.WorkoutEntryCheckpoint
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Native recovery surfaces on the same complete workout UI, with explicitly injected outcomes. */
@RunWith(Parameterized::class)
class WorkoutRecoveryLayoutInstrumentedTest(private val width: Int, private val height: Int, private val font: Float, private val scenario: String) {
    @get:Rule val compose = createComposeRule()
    private val fixture = WorkoutEntryFixture()
    private val previous = checkNotNull(fixture.container.workoutDraftCache.persistence)
    @After fun cleanup() { try { fixture.close() } finally { fixture.container.workoutDraftCache.persistence = previous } }

    @Test fun recoveryActionAndEssentialTextRemainReachable() {
        fixture.container.workoutDraftCache.persistence = object : WorkoutDraftPersistence by previous {
            override fun forEntry(): WorkoutDraftPersistence = this
            override suspend fun open(sessionId: String): WorkoutDraftRestore = when (scenario.substringBefore('-')) {
                "unavailable" -> WorkoutDraftRestore.ReadUnavailable
                "conflict" -> WorkoutDraftRestore.OwnerConflict("Injected changed owner")
                else -> when (val result = previous.open(sessionId)) {
                    is WorkoutDraftRestore.ReadyEntry -> result.copy(resumed = scenario.startsWith("resumed"))
                    else -> result
                }
            }
            override suspend fun checkpoint(entry: WorkoutEntryCheckpoint) {
                if (scenario.startsWith("keep")) throw IOException("Injected preservation failure")
                previous.checkpoint(entry)
            }
        }
        fixture.seed()
        GoldenCapture.mountViewport(compose = compose, width = width.dp, height = height.dp, fontScale = font,
            reduceMotion = true, statusBar = 24.dp, navigationBar = 24.dp) {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG,
                LocalLayoutDirection provides if (scenario.endsWith("rtl")) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Scaffold(bottomBar = { Column {} }) { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                    ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = fixture.vm, restNotificationsEnabledOverride = true)
                } }
            }
        }
        val phase = when (scenario.substringBefore('-')) {
            "keep" -> WorkoutPrivatePhase.PRESERVATION_FAILED
            "unavailable" -> WorkoutPrivatePhase.READ_FAILED
            "conflict" -> WorkoutPrivatePhase.CONFLICT
            else -> WorkoutPrivatePhase.READY
        }
        compose.waitUntil(30000) { fixture.vm.privateRecovery.value.phase == phase }
        GoldenCapture.awaitViewport(compose, width, height)
        val root = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode()
        val density = root.layoutInfo.density.density
        val action = if (scenario.startsWith("keep")) compose.onNodeWithTag("workout-keep-entry").assertIsDisplayed().assertIsEnabled()
        else if (scenario.startsWith("resumed")) {
            compose.revealFloorControlAboveTempo(WorkoutTestTags.LOG_READINESS)
            compose.onNodeWithText("Draft resumed. Choose effort to log this set.").assertIsDisplayed()
            compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        } else {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Check again"))
            compose.onNodeWithText("Check again").assertIsDisplayed().assertIsEnabled()
        }
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertTrue("recovery action remains within the viewport", bounds.top >= root.boundsInRoot.top && bounds.bottom <= root.boundsInRoot.bottom + 1)
        assertTrue("target has its applicable minimum", bounds.height / density >= if (scenario.startsWith("keep") || scenario.startsWith("resumed")) 72 else 48)
        if (scenario.startsWith("keep")) {
            compose.revealFloorControlAboveTempo(WorkoutTestTags.LOG_READINESS)
            compose.onNodeWithText("Keep this entry before logging. Your saved sets are unchanged.").assertIsDisplayed()
        }
        val bitmap = compose.onNodeWithTag(GoldenCapture.DefaultTag).captureToImage().asAndroidBitmap()
        NativeArtifacts.write("workout-recovery-$scenario-${width}x$height-font${(font*10).toInt()}", bitmap)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}-{3}") fun profiles(): List<Array<Any>> = buildList {
            for ((w,h) in listOf(360 to 640,360 to 800,412 to 840,600 to 840,640 to 360)) for (f in listOf(1f,1.6f,2f)) add(arrayOf(w,h,f,"keep"))
            for ((w,h,f) in listOf(Triple(360,640,2f),Triple(640,360,2f),Triple(412,840,1f))) for (s in listOf("unavailable","conflict","resumed")) add(arrayOf(w,h,f,s))
            for (s in listOf("keep-rtl","resumed-rtl")) add(arrayOf(360,800,2f,s))
        }
    }
}
