package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.QuietButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * The current exercise: picture, equipment, name, where its sets stand, and the
 * set type about to be logged. Session totals stay out of here.
 *
 * Two controls, each where it can be seen (design audit D09). "Lift n of N ⌄" opens the
 * switcher: the whole identity used to be that button with nothing on it saying so, and
 * the only visible control was a menu. The picture opens the lift's own screen (Details),
 * with a corner mark saying it can be opened; Details used to sit inside the switcher's
 * tap area, where only its own handler kept a tap on it from switching lifts.
 */
@Composable
internal fun ExerciseHeader(
    lift: SessionExercise,
    number: Int,
    total: Int,
    setContext: String,
    draftWarmup: Boolean,
    onWarmup: (Boolean) -> Unit,
    onOpenSwitcher: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focused: Boolean = false,
) {
    if (focused) {
        FocusExerciseIdentity(lift, number, total, setContext, onOpenSwitcher, onDetails, modifier, enabled)
        return
    }
    val meaning = LoadClass.of(lift.exercise.loadType).weightMeaning
    val equipment = CurrentLiftCopy.secondaryLine(lift.exercise.equipment.label, meaning)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkoutTestTags.CURRENT_LIFT),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            // A compact matching still gives the full name room beside it at every text size.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkoutTestTags.liftCard(lift.exercise.id))
                    // One reading group, so the picture's place in it below is local to the row.
                    .semantics { isTraversalGroup = true },
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalAlignment = Alignment.Top,
            ) {
                DetailsStill(
                    lift = lift,
                    enabled = enabled,
                    onDetails = onDetails,
                )
                Column(
                    modifier = Modifier.weight(1f).padding(top = Metrics.space1),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    // One stop for what the lift is: its equipment and name, read from the
                    // words themselves. The set context and the switch follow as their own
                    // stops, and nothing is read twice.
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        Kicker(text = equipment, color = TextTertiary, asHeading = false)
                        Text(
                            text = lift.exercise.name,
                            style = InstrumentType.heroTitle,
                            color = TextPrimary,
                        )
                    }
                    SetContextBesideSwitch(setContext = setContext) {
                        QuietButton(
                            text = CurrentLiftCopy.switchLabel(number, total),
                            onClick = onOpenSwitcher,
                            trailing = TemperIcons.ChevronDown,
                            enabled = enabled,
                            modifier = Modifier.testTag(WorkoutTestTags.LIFT_SWITCH),
                            onClickLabel = CurrentLiftCopy.SWITCH,
                        )
                    }
                }
            }
            SetTypeToggle(
                enabled = enabled,
                warmup = draftWarmup,
                onWarmup = onWarmup,
            )
        }
    }
}

/** A labelled switch target beside, rather than around, the independent Details image. */
@Composable
private fun FocusExerciseIdentity(
    lift: SessionExercise,
    number: Int,
    total: Int,
    setContext: String,
    onOpenSwitcher: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    Box(modifier.fillMaxWidth().testTag(WorkoutTestTags.CURRENT_LIFT)) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .testTag(WorkoutTestTags.liftCard(lift.exercise.id)).semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailsStill(lift, enabled, onDetails)
        Column(
            modifier = Modifier.weight(1f).heightIn(min = Metrics.workoutIdentityImage),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(lift.exercise.name, style = InstrumentType.workoutTitle, color = TextPrimary)
            val switch: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.heightIn(min = Metrics.touchMin)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable(enabled = enabled, role = Role.Button,
                            onClickLabel = CurrentLiftCopy.SWITCH, onClick = onOpenSwitcher)
                        .testTag(WorkoutTestTags.LIFT_SWITCH)
                        .padding(horizontal = Metrics.space2, vertical = Metrics.space1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    Text(CurrentLiftCopy.switchLabel(number, total), style = InstrumentType.caption, color = TextSecondary)
                    Icon(TemperIcons.ChevronDown, contentDescription = null, tint = TextSecondary,
                        modifier = Modifier.size(Metrics.chevron))
                }
            }
            if (LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)) {
                Text(setContext, modifier = Modifier.testTag(WorkoutTestTags.SET_CONTEXT),
                    style = InstrumentType.caption, color = TextSecondary)
                switch()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(setContext, modifier = Modifier.weight(1f).testTag(WorkoutTestTags.SET_CONTEXT),
                        style = InstrumentType.caption, color = TextSecondary)
                    switch()
                }
            }
        }
    }
    }
}

/**
 * Where the sets stand, with the switch beside it while the words still fit there whole:
 * two lines at most and no word broken. Otherwise the switch drops under the words. Large
 * text squeezed "Working set 3 of 3" into "Worki / ng s…" beside the pill, and which set
 * comes next is the one line on this header a lifter must never lose.
 */
@Composable
private fun SetContextBesideSwitch(
    setContext: String,
    switch: @Composable () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    Layout(
        content = {
            Text(
                text = setContext,
                modifier = Modifier.testTag(WorkoutTestTags.SET_CONTEXT),
                style = InstrumentType.bodyStrong,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            switch()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val pill = measurables[1].measure(Constraints(maxWidth = width))
        val beside = width - pill.width - Metrics.space2.roundToPx()
        val fitsBeside = beside > 0 && measurer.measure(
            text = setContext,
            style = InstrumentType.bodyStrong,
            maxLines = 2,
            constraints = Constraints(maxWidth = beside),
        ).let { words -> !words.hasVisualOverflow && words.multiParagraph.intrinsics.minIntrinsicWidth <= beside }
        if (fitsBeside) {
            val words = measurables[0].measure(Constraints(minWidth = beside, maxWidth = beside))
            val height = maxOf(words.height, pill.height)
            layout(width, height) {
                words.placeRelative(0, (height - words.height) / 2)
                pill.placeRelative(width - pill.width, (height - pill.height) / 2)
            }
        } else {
            val words = measurables[0].measure(Constraints(maxWidth = width))
            val top = words.height + Metrics.space1.roundToPx()
            layout(width, top + pill.height) {
                words.placeRelative(0, 0)
                pill.placeRelative(0, top)
            }
        }
    }
}

/**
 * The lift's picture as the way to its own screen. The corner mark is what tells a thumb
 * the picture opens; the spoken name says where it goes.
 */
@Composable
private fun DetailsStill(
    lift: SessionExercise,
    enabled: Boolean,
    onDetails: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.xs))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = CurrentLiftCopy.OPEN_DETAILS, onClick = onDetails)
            .testTag(WorkoutTestTags.DETAILS)
            .clearAndSetSemantics {
                contentDescription = CurrentLiftCopy.DETAILS_SPOKEN
                // Drawn first, read last: TalkBack says what the lift is, where its sets
                // stand and the switch before the way out to the lift's own screen.
                traversalIndex = 1f
            },
    ) {
        ExerciseThumb(
            exercise = lift.exercise,
            size = Metrics.workoutIdentityImage,
            showBadge = false,
            artPadding = Metrics.space1,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Metrics.space1)
                .size(Metrics.equipmentGlyph)
                .clip(Radius.full)
                .background(Surface2)
                .border(Metrics.hairline, Hairline, Radius.full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TemperIcons.Chevron,
                contentDescription = null,
                tint = if (enabled) TextSecondary else TextDisabled,
                modifier = Modifier.size(Metrics.chevron),
            )
        }
    }
}

/**
 * Working | Warm-up as one two-way radio. Selected is a Volt outline on a dim Volt
 * tint, never a filled block, so the one filled act on the floor stays Log set.
 */
@Composable
internal fun SetTypeToggle(
    enabled: Boolean,
    warmup: Boolean,
    onWarmup: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dense = FloorCompactChrome.setTypeToggleUsesCompactChips()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .testTag(WorkoutTestTags.SET_TYPE),
        horizontalArrangement = Arrangement.spacedBy(if (dense) Metrics.space1 else Metrics.space2),
    ) {
        InstrumentChip(
            label = "Working",
            selected = !warmup,
            onClick = { onWarmup(false) },
            modifier = Modifier.weight(1f).testTag(WorkoutTestTags.WORKING_CHIP),
            role = Role.RadioButton,
            spoken = WORKING_SPOKEN,
            enabled = enabled,
            compact = dense,
            labelStyle = if (dense) InstrumentType.caption else InstrumentType.bodyStrong,
        )
        InstrumentChip(
            label = "Warm-up",
            selected = warmup,
            onClick = { onWarmup(true) },
            modifier = Modifier.weight(1f).testTag(WorkoutTestTags.WARMUP_CHIP),
            role = Role.RadioButton,
            spoken = WARMUP_SPOKEN,
            enabled = enabled,
            compact = dense,
            labelStyle = if (dense) InstrumentType.caption else InstrumentType.bodyStrong,
        )
    }
}

// The chips' Selected state already says "selected" or "not selected"; saying it in the
// words as well was the double announcement.
private const val WORKING_SPOKEN = "Working set"
private const val WARMUP_SPOKEN = "Warm-up set"
