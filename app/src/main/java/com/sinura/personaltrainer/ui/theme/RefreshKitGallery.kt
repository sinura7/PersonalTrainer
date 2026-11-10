package com.sinura.personaltrainer.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymFilter
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.ui.components.ActionState
import com.sinura.personaltrainer.ui.components.ActionTone
import com.sinura.personaltrainer.ui.components.GhostButton
import com.sinura.personaltrainer.ui.components.GymBadge
import com.sinura.personaltrainer.ui.components.GymBadgeSize
import com.sinura.personaltrainer.ui.components.GymBadgeVisual
import com.sinura.personaltrainer.ui.components.GymFilterRow
import com.sinura.personaltrainer.ui.components.GymPlateRow
import com.sinura.personaltrainer.ui.components.GymPlateRowState
import com.sinura.personaltrainer.ui.components.GymTag
import com.sinura.personaltrainer.ui.components.InstrumentSheetGalleryChrome
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryButton
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RoleTag
import com.sinura.personaltrainer.ui.components.RoleTagKind
import com.sinura.personaltrainer.ui.components.SecondaryButton
import com.sinura.personaltrainer.ui.components.SegmentOption
import com.sinura.personaltrainer.ui.components.SegmentedControl
import com.sinura.personaltrainer.ui.components.StatStrip
import com.sinura.personaltrainer.ui.components.StatStripCell
import com.sinura.personaltrainer.ui.components.SupportingLineStyle
import com.sinura.personaltrainer.ui.components.TextAction
import com.sinura.personaltrainer.ui.start.StartTile
import com.sinura.personaltrainer.ui.start.StartTileKind
import com.sinura.personaltrainer.ui.start.StartTileState

private val sampleGyms = listOf(
    Gym("home", "Home Gym", "Home", "HM", 0, false),
    Gym("weston", "Anytime Fitness Weston", "Weston", "WS", 1, false),
    Gym("cotrelle", "Cotrelle Fitness", "Cotrelle", "CT", 2, false),
    Gym("maj", "Major Mac", "Major Mac", "MM", 3, false),
    Gym("work", "Work Gym", "Work", "WK", 4, false),
)

@Preview(name = "Refresh kit 1.0", widthDp = 360, heightDp = 1200)
@Composable
fun RefreshKitGalleryPreview() {
    PersonalTrainerTheme { RefreshKitGallery() }
}

@Preview(name = "Refresh kit 2.0", widthDp = 360, heightDp = 1200, fontScale = 2f)
@Composable
fun RefreshKitGalleryLargeTextPreview() {
    PersonalTrainerTheme { RefreshKitGallery() }
}

@Composable
fun RefreshKitGallery(modifier: Modifier = Modifier) {
    var period by rememberSaveable { mutableStateOf("week") }
    var gymFilter by remember { mutableStateOf<GymFilter>(GymFilter.One("weston")) }
    var selectedGym by rememberSaveable { mutableStateOf<String?>("weston") }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Metrics.gutter),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        Text("Refresh kit", style = InstrumentType.display, color = TextPrimary)
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            Kicker("Buttons")
            PrimaryButton(
                label = "Begin at Weston",
                subLabel = "5 lifts · 15 sets",
                onClick = {},
                state = ActionState.Enabled,
            )
            SecondaryButton(label = "Secondary", onClick = {})
            PrimaryGymButton(
                text = "Disabled",
                supporting = "says why",
                enabled = false,
                onClick = {},
                supportingLineStyle = SupportingLineStyle.RefreshReason,
            )
            GhostButton(label = "Ghost", onClick = {})
            TextAction(label = "Text action", onClick = {}, accent = true, testTag = "refresh-text-action")
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            Kicker("Gym plate row")
            GymPlateRow(
                state = GymPlateRowState(
                    gyms = sampleGyms,
                    visits = mapOf("weston" to GymVisit(12, System.currentTimeMillis() - 86_400_000L)),
                    selectedId = selectedGym,
                    onPick = { selectedGym = it },
                    hintBeforePick = "Machines keep their own numbers at each gym. Free weights are shared.",
                    hintAfterPick = "Anytime Fitness Weston. Machines use WS numbers.",
                ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            Kicker("Badges and tag")
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                GymBadge("WS", GymBadgeSize.Small, GymBadgeVisual.Normal)
                GymBadge("WS", GymBadgeSize.Small, GymBadgeVisual.Selected)
                GymBadge("UN", GymBadgeSize.Tiny, GymBadgeVisual.Unassigned)
            }
            GymTag(gym = sampleGyms[1])
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            RoleTag(RoleTagKind.Today)
            RoleTag(RoleTagKind.Main)
            RoleTag(RoleTagKind.WarmUp)
            RoleTag(RoleTagKind.Cardio)
            RoleTag(RoleTagKind.Pr)
            RoleTag(RoleTagKind.Done)
        }
        SegmentedControl(
            options = listOf(
                SegmentOption("day", "Day"),
                SegmentOption("week", "Week"),
                SegmentOption("month", "Month"),
            ),
            selectedKey = period,
            onSelect = { period = it },
        )
        GymFilterRow(
            gyms = sampleGyms,
            selected = gymFilter,
            onSelect = { gymFilter = it },
        )
        StatStrip(
            cells = listOf(
                StatStripCell("Volume", "1,240", "kg"),
                StatStripCell("Sets", "42"),
                StatStripCell("PRs", "2", valueColor = PrGold),
            ),
        )
        StartTile(
            kind = StartTileKind.SessionRoutine,
            title = "Routine",
            caption = "Pick a gym above",
            state = StartTileState.Waiting,
            onClick = {},
            testTag = "start-tile-routine",
        )
        InstrumentSheetGalleryChrome(
            title = "Start",
            caption = "Pick your gym, then go",
        ) {
            Text("Sheet body", style = InstrumentType.body, color = TextSecondary)
        }
        PinnedDock(
            volt = {
                PrimaryButton(label = "Start Push day", onClick = {}, tone = ActionTone.Act)
            },
            nextStep = androidx.compose.ui.text.AnnotatedString("Bench Press first · 62.5 kg × 8"),
        )
    }
}
