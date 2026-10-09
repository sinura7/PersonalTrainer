package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import com.sinura.personaltrainer.domain.HistoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Measures and hits the actual narrow native calendar instead of comparing a constant. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
class TrainingCalendarTouchTest : HistoryPeriodTestHost() {
    @Test fun twoDigitDatesHaveSeparatedRealTargetsAndAlignedWeekdayColumns() = evidence("calendar-two-digits") {
        font = 2f
        graph()
        show()
        awaitLoaded()
        reachTag(HistoryTags.day(day(10)))
        val first = fullBounds(compose.onNodeWithTag(HistoryTags.day(day(10))).fetchSemanticsNode())
        val second = fullBounds(compose.onNodeWithTag(HistoryTags.day(day(11))).fetchSemanticsNode())
        assertFalse("adjacent real targets do not overlap", first.overlaps(second))
        val heading = fullBounds(reachTag(HistoryTags.weekday(5)).fetchSemanticsNode())
        val saturday = fullBounds(compose.onNodeWithTag(HistoryTags.day(day(10))).fetchSemanticsNode())
        assertEquals("one canvas keeps heading/date centers aligned", heading.center.x, saturday.center.x, 1f)
        tapDay(day(10))
        assertScoped(setOf(HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID))
        tapDay(day(11))
        assertScoped(setOf(HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID))
        val selected = reachTag(HistoryTags.day(day(11))).fetchSemanticsNode()
        assertTrue(selected.config[SemanticsProperties.Selected])
        assertTrue(selected.config[SemanticsProperties.ContentDescription].single().contains("selected"))
        assertTrue(routes.isEmpty())
        capture("pointer-days-10-and-11")
    }
}
