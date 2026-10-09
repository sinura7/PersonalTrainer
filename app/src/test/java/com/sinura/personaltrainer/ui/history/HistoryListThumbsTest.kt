package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.ui.components.SessionLogTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Runtime identity and native artwork replace the old source-string row mirror. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class HistoryListThumbsTest : HistoryPeriodTestHost() {
    @Test fun selectedHistoryRowPicturesItsFirstThreeActualLifts() = evidence("history-stills") {
        graph()
        show()
        awaitLoaded()
        val entry = history.uiState.value.monthGroups.flatMap { it.entries }
            .single { it.kind == HistoryKind.WORKOUT && it.id == HistoryPeriodTestHost.CURRENT_ID }
        assertEquals(listOf("period-lift-1", "period-lift-2", "period-lift-3"), entry.stills.map { it.id })
        findTag(HistoryTags.row(HistoryKind.WORKOUT, HistoryPeriodTestHost.CURRENT_ID))
        val strip = reach(compose.onNode(
            hasTestTag(SessionLogTags.STILLS) and hasAnyAncestor(hasTestTag(HistoryTags.row(HistoryKind.WORKOUT, HistoryPeriodTestHost.CURRENT_ID))),
            useUnmergedTree = true,
        ))
        val thumbs = strip.fetchSemanticsNode().children
        assertEquals("three decorative thumbnail roots are actually mounted", 3, thumbs.size)
        val bitmap = drawWindow()
        try {
            thumbs.forEach { thumb ->
                val bounds = thumb.boundsInWindow.intersect(this.windowBounds())
                val colors = mutableSetOf<Int>()
                for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                    for (x in bounds.left.toInt() until bounds.right.toInt()) colors += bitmap.getPixel(x, y)
                }
                assertTrue("actual native thumb has artwork beyond a blank two-color frame", colors.size > 6)
            }
        } finally { bitmap.recycle() }
        capture("three-lift-native-artwork")
        assertTrue(routes.isEmpty())
    }
}
