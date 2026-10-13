package com.sinura.personaltrainer.ui.history

import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.HistoryPeriodDrafts
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement

/** Real History/VM/file failures and Android dialogs at actual system fonts. */
@RunWith(Parameterized::class)
class HistoryPeriodRecoveryInstrumentedTest(
    private val width: Int, private val height: Int, private val font: Float, private val rtl: Boolean,
) {
    private val compose = createComposeRule()
    private val window = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                check(Build.HARDWARE in setOf("ranchu", "goldfish") && Build.FINGERPRINT.startsWith("Android/sdk_"))
                val size = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
                val density = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
                val previousFont = shell("settings get system font_scale").trim()
                try {
                    shell("wm density 160")
                    shell("wm size ${width}x$height")
                    shell("settings put system font_scale $font")
                    awaitSystemFont(font)
                    base.evaluate()
                } finally {
                    shell("wm size ${size ?: "reset"}")
                    shell("wm density ${density ?: "reset"}")
                    if (previousFont.toFloatOrNull() != null) shell("settings put system font_scale $previousFont")
                    else shell("settings delete system font_scale")
                    awaitSystemFont(previousFont.toFloatOrNull() ?: 1f)
                    assertEquals(size, Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1))
                    assertEquals(density, Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1))
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(window).around(compose)


    @Test fun recoveryActionsKeepTheIntendedPeriodAndResetOnlyThePrivatePreference() {
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        val root = File(app.noBackupFilesDir, "synthetic-p06-period-${UUID.randomUUID()}")
        check(root.mkdir())
        val failRead = AtomicBoolean(false)
        val failWrite = AtomicBoolean(false)
        val holdWrite = AtomicBoolean(false)
        val writeEntered = CountDownLatch(1)
        val writeRelease = CountDownLatch(1)
        val files = object : DraftFileIO by AtomicDraftFiles {
            override fun read(file: File): ByteArray? {
                if (failRead.get()) throw IOException("synthetic private read failure")
                return AtomicDraftFiles.read(file)
            }
            override fun replace(file: File, bytes: ByteArray) {
                if (holdWrite.get()) {
                    writeEntered.countDown()
                    check(writeRelease.await(15, TimeUnit.SECONDS)) { "Synthetic write was not released" }
                }
                if (failWrite.get()) throw IOException("synthetic private replacement failure")
                AtomicDraftFiles.replace(file, bytes)
            }
        }
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, Dispatchers.IO, files))
        val today = LocalDate.now().toEpochDay()
        val original = HistoryPeriodSelection(AnalyticsHorizon.MONTH, LocalDate.now().minusMonths(1).toEpochDay(), false)
        read { drafts.keep(drafts.open(), original) }
        failRead.set(true)
        val owner = ViewModelStore()
        lateinit var model: HistoryViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = HistoryViewModel(app, SavedStateHandle(), app.container, drafts)
            owner.put("synthetic-p06", model)
        }
        try {
            GoldenCapture.mountDevice(compose = compose, fontScale = font) {
                CompositionLocalProvider(
                    LocalTodayEpochDay provides today,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    HistoryScreen(onOpenSession = {}, onOpenExercise = {}, onOpenActiveSession = {}, viewModel = model)
                }
            }
            awaitError(model)
            reach(HistoryPeriodMemoryTags.ERROR)
            assertCompleteText(HistoryPeriodMemoryTags.ERROR)
            proveTarget(HistoryPeriodMemoryTags.RETRY)
            proveTarget(HistoryPeriodMemoryTags.RESET)
            for (tag in listOf(HistoryPeriodMemoryTags.RETRY, HistoryPeriodMemoryTags.RESET)) {
                compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            }
            compose.onNodeWithTag(HistoryPeriodMemoryTags.ERROR, useUnmergedTree = true).assert(
                hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)),
            )
            capture("unavailable-period")
            reach(HistoryPeriodMemoryTags.RETRY).performClick()
            awaitError(model)
            failRead.set(false)
            reach(HistoryPeriodMemoryTags.RETRY).performClick()
            compose.waitUntil(15000) { model.requestedPeriod.value == original && !model.periodMemory.snapshot.value.saving }
            assertEquals(original, read { HistoryPeriodDrafts(DurableDraftStore(root)).open().remembered })
            reach(HistoryPeriodMemoryTags.RESUMED)
            assertCompleteText(HistoryPeriodMemoryTags.RESUMED)
            capture("restored-period")
            holdWrite.set(true)
            failWrite.set(true)
            reach(HistoryTags.YEAR).performClick()
            compose.waitUntil(15000) { writeEntered.count == 0L && model.periodMemory.snapshot.value.saving }
            reach(HistoryPeriodMemoryTags.KEEPING)
            assertCompleteText(HistoryPeriodMemoryTags.KEEPING)
            capture("keeping-period")
            holdWrite.set(false)
            writeRelease.countDown()
            awaitError(model)
            val intended = original.copy(horizon = AnalyticsHorizon.YEAR)
            assertEquals(intended, model.requestedPeriod.value)
            capture("failed-keep")
            failWrite.set(false)
            reach(HistoryPeriodMemoryTags.RETRY).performClick()
            compose.waitUntil(15000) { !model.periodMemory.snapshot.value.saving && model.periodMemory.snapshot.value.error == null }
            assertEquals(intended, read { HistoryPeriodDrafts(DurableDraftStore(root)).open().remembered })
            failRead.set(true)
            compose.runOnUiThread { model.periodMemory.retry() }
            awaitError(model)
            reach(HistoryPeriodMemoryTags.RESET).performClick()
            compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsDisplayed()
            assertEquals("Native dialog font", font,
                compose.onNodeWithTag(ConfirmActionTags.CONFIRM).fetchSemanticsNode().layoutInfo.density.fontScale, .01f)
            proveTarget(ConfirmActionTags.CONFIRM)
            capture("reset-confirmation")
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(intended, model.requestedPeriod.value)
            reach(HistoryPeriodMemoryTags.RESET).performClick()
            failRead.set(false)
            failWrite.set(true)
            compose.onNodeWithTag(ConfirmActionTags.CONFIRM).performClick()
            awaitError(model)
            assertEquals(intended, model.requestedPeriod.value)
            capture("failed-reset")
            failWrite.set(false)
            reach(HistoryPeriodMemoryTags.RETRY).performClick()
            compose.waitUntil(15000) {
                model.periodMemory.snapshot.value.error == null && !model.periodMemory.snapshot.value.saving &&
                    model.requestedPeriod.value == HistoryPeriodSelection(AnalyticsHorizon.MONTH, today, true)
            }
            assertNull(read { HistoryPeriodDrafts(DurableDraftStore(root)).open().remembered })
            reach(HistoryTags.READOUT)
            assertCompleteText(HistoryTags.RANGE_TITLE)
            capture("recovered-current-month")
        } finally {
            holdWrite.set(false)
            writeRelease.countDown()
            failRead.set(false)
            failWrite.set(false)
            compose.runOnUiThread { owner.clear() }
            check(root.canonicalFile.parentFile == app.noBackupFilesDir.canonicalFile)
            check(root.deleteRecursively())
        }
    }

    private fun awaitError(model: HistoryViewModel) = compose.waitUntil(15000) {
        model.periodMemory.snapshot.value.error != null && !model.periodMemory.snapshot.value.saving
    }

    private fun reach(tag: String) = compose.onNodeWithTag(HistoryTags.LIST)
        .performScrollToNode(hasTestTag(tag)).let { compose.onNodeWithTag(tag).assertIsDisplayed() }

    private fun proveTarget(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        val minimum = with(node.layoutInfo.density) { 48.dp.roundToPx() }
        assertTrue("$tag height", node.boundsInRoot.height + 1 >= minimum)
        assertTrue("$tag width", node.boundsInRoot.width + 1 >= minimum)
    }

    private fun assertCompleteText(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        for (layout in layouts) {
            assertFalse(layout.didOverflowHeight)
            for (line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue(layout.getLineLeft(line) >= -1f)
            }
        }
    }

    private fun capture(name: String) = NativeArtifacts.write(
        "history-$name-${width}-${height}-font${(font * 10).toInt()}-${if (rtl) "rtl" else "ltr"}",
        checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()),
    )

    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) {
        withTimeout(15000) { block() }
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { pipe -> FileInputStream(pipe.fileDescriptor).use { String(it.readBytes()) } }

    private fun awaitSystemFont(expected: Float) {
        val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (kotlin.math.abs(resources.configuration.fontScale - expected) > .01f && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        assertEquals("Actual Android font scale", expected, resources.configuration.fontScale, .01f)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}-rtl{3}")
        fun profiles(): List<Array<Any>> = buildList<Array<Any>> {
            for ((width, height) in listOf(360 to 640, 360 to 800, 412 to 840, 800 to 360, 600 to 960)) {
                for (font in listOf(1f, 1.6f, 2f)) add(arrayOf(width, height, font, false))
            }
            add(arrayOf(360, 800, 2f, true))
        }.filter { profile ->
            val selected = InstrumentationRegistry.getArguments().getString("historyProfile")
            selected == null || selected == "${profile[0]}-${profile[1]}-font${((profile[2] as Float) * 10).toInt()}"
        }
    }
}
