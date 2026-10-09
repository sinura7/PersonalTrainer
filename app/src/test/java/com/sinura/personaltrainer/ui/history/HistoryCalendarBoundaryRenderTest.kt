package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.HistoryPeriodRange
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Supported saved anchors must remain renderable even when whole-week padding is not. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class HistoryCalendarBoundaryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun minimumSupportedDayWithSundayPaddingStillOffersItsExactDate() = mountBoundary(LocalDate.MIN)

    @Test
    fun maximumSupportedDayWithFollowingPaddingStillOffersItsExactDate() = mountBoundary(LocalDate.MAX)

    private fun mountBoundary(anchor: LocalDate) {
        val selection = HistoryPeriodSelection(AnalyticsHorizon.DAY, anchor.toEpochDay(), false)
        val range = HistoryPeriodRange(selection.anchorEpochDay, selection.anchorEpochDay + 1)
        val month = TrainingCalendarBuilder.buildSummaries(
            CivilYearMonth.from(CivilDate.fromEpochDay(selection.anchorEpochDay)), emptyList(), Weekday.SUNDAY, range,
        )
        val opened = mutableListOf<Long>()
        compose.setContent {
            PersonalTrainerTheme {
                Box(Modifier.width(360.dp)) {
                    TrainingCalendarCard(
                        month = month, weekStart = Weekday.SUNDAY, today = anchor,
                        selectedEpochDay = selection.anchorEpochDay, horizon = selection.horizon,
                        onSelectDay = { opened += it }, unit = WeightUnit.KG,
                    )
                }
            }
        }
        compose.onNodeWithTag(HistoryTags.day(selection.anchorEpochDay)).assertIsDisplayed().performClick()
        assertEquals(listOf(selection.anchorEpochDay), opened)
    }
}
