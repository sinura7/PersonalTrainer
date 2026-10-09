package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.HistoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Approved period behavior replaces the obsolete source-shape/independent-month guard. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class HistoryStrongerTest : HistoryPeriodTestHost() {
    @Test fun previousPeriodMovesTotalsCalendarAndListTogether() = evidence("coherent-previous") {
        graph()
        show()
        awaitLoaded()
        val stored = inventory()
        reachTag(HistoryTags.PREVIOUS).performClick()
        awaitLoaded()
        assertEquals(AnalyticsHorizon.MONTH, history.uiState.value.horizon)
        assertScoped(setOf(HistoryKind.WORKOUT to HistoryPeriodTestHost.OLD_ID))
        assertRange()
        capture("previous-month")
        reachTag(HistoryTags.CURRENT).performClick()
        awaitLoaded()
        assertScoped(setOf(HistoryKind.WORKOUT to HistoryPeriodTestHost.CURRENT_ID,
            HistoryKind.WORKOUT to HistoryPeriodTestHost.START_ID, HistoryKind.ACTIVITY to HistoryPeriodTestHost.CURRENT_ID))
        assertTrue(history.uiState.value.selection.followToday)
        assertEquals(stored, inventory())
        assertTrue(routes.isEmpty())
    }
}
