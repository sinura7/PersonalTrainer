package com.sinura.personaltrainer.ui.theme

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.ui.components.GymPlateRow
import com.sinura.personaltrainer.ui.components.GymPlateRowState
import com.sinura.personaltrainer.ui.components.InstrumentSheetTags
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SupportingLineStyle
import com.sinura.personaltrainer.ui.components.primarySupportingInk
import com.sinura.personaltrainer.ui.start.StartTile
import com.sinura.personaltrainer.ui.start.StartTileKind
import com.sinura.personaltrainer.ui.start.StartTileState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h1200dp-xhdpi")
class RefreshKitRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshKitGalleryRenders() {
        compose.setContent { PersonalTrainerTheme { RefreshKitGallery() } }
        compose.onNodeWithText("Refresh kit").assertExists()
        compose.onNodeWithTag("refresh-gym-plate-weston").assertExists()
        compose.onNodeWithTag(InstrumentSheetTags.GALLERY).assertExists()
    }

    @Test
    fun primaryDisabledReasonUsesTertiaryInk() {
        assertEquals(
            TextTertiary,
            primarySupportingInk(enabled = false, contentColor = Pit, style = SupportingLineStyle.RefreshReason),
        )
        compose.setContent {
            PersonalTrainerTheme {
                PrimaryGymButton(
                    text = "Disabled",
                    supporting = "Enter a weight first",
                    enabled = false,
                    onClick = {},
                    supportingLineStyle = SupportingLineStyle.RefreshReason,
                )
            }
        }
        compose.onNodeWithText("Enter a weight first").assertExists()
    }

    @Test
    fun gymPlateRowMeetsTouchFloor() {
        compose.setContent { PersonalTrainerTheme { RefreshKitGallery() } }
        compose.onNodeWithTag("refresh-gym-plate-home").assertHeightIsAtLeast(Metrics.touchMin)
    }

    @Config(qualifiers = "w360dp-h1200dp-xhdpi", fontScale = 2f)
    @Test
    fun refreshKitTouchFloorsAtLargeText() {
        val manyGyms = (1..11).map { i ->
            Gym("g$i", "Gym $i", "G$i", "G$i".take(2).uppercase(), i, false)
        }
        compose.setContent {
            PersonalTrainerTheme {
                ColumnKitTouchHarness(gyms = manyGyms)
            }
        }
        compose.onNodeWithTag("segment-week").assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        compose.onNodeWithTag("refresh-text-action").assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        compose.onNodeWithTag("refresh-gym-filter-all").assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        compose.onNodeWithTag("start-tile-routine").assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        compose.onNodeWithTag("refresh-gym-row-g1").assertHeightIsAtLeast(Metrics.touchMin)
    }
}

@androidx.compose.runtime.Composable
private fun ColumnKitTouchHarness(gyms: List<Gym>) {
    androidx.compose.foundation.layout.Column {
        com.sinura.personaltrainer.ui.components.SegmentedControl(
            options = listOf(
                com.sinura.personaltrainer.ui.components.SegmentOption("day", "Day"),
                com.sinura.personaltrainer.ui.components.SegmentOption("week", "Week"),
            ),
            selectedKey = "week",
            onSelect = {},
        )
        com.sinura.personaltrainer.ui.components.TextAction(
            label = "Change",
            onClick = {},
            testTag = "refresh-text-action",
        )
        com.sinura.personaltrainer.ui.components.GymFilterRow(
            gyms = gyms.take(3),
            selected = com.sinura.personaltrainer.domain.GymFilter.One("weston"),
            onSelect = {},
        )
        StartTile(
            kind = StartTileKind.SessionRoutine,
            title = "Routine",
            caption = "Pick a gym",
            state = StartTileState.Waiting,
            onClick = {},
            testTag = "start-tile-routine",
        )
        GymPlateRow(
            state = GymPlateRowState(
                gyms = gyms,
                visits = mapOf("g1" to GymVisit(1, null)),
                selectedId = "g1",
                onPick = {},
                hintBeforePick = "Hint",
                hintAfterPick = null,
            ),
        )
    }
}
