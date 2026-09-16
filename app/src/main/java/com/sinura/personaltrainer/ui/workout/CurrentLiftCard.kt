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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SessionTelemetryCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.EquipmentGlyphIcon
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.components.glyphFor
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.util.QuantityFormat
import kotlinx.coroutines.delay

/**
 * Image-led exercise hero. Replaces the neon-outlined selected-lift card
 * and the separate minute telemetry strip.
 *
 * 112 dp still, `ContentScale.Fit`, no crop, no overlay badge. Overflow is
 * its own 48 dp target. The rest of the hero opens the lift switcher.
 */
@Composable
internal fun CurrentLiftCard(
    lift: SessionExercise,
    number: Int,
    total: Int,
    workingLogged: Int,
    canEdit: Boolean,
    onOpenSwitcher: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onNotes: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit = {},
    startedAt: Long? = null,
    workingSets: Int = 0,
    work: SetWork = SetWork.NONE,
    unit: WeightUnit = WeightUnit.KG,
    landscape: Boolean = false,
) = ExerciseHero(
    lift = lift,
    number = number,
    total = total,
    workingLogged = workingLogged,
    canEdit = canEdit,
    onOpenSwitcher = onOpenSwitcher,
    onSwap = onSwap,
    onRemove = onRemove,
    onNotes = onNotes,
    modifier = modifier,
    onSkip = onSkip,
    startedAt = startedAt,
    workingSets = workingSets,
    work = work,
    unit = unit,
    landscape = landscape,
)

@Composable
internal fun ExerciseHero(
    lift: SessionExercise,
    number: Int,
    total: Int,
    workingLogged: Int,
    canEdit: Boolean,
    onOpenSwitcher: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onNotes: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit = {},
    startedAt: Long? = null,
    workingSets: Int = 0,
    work: SetWork = SetWork.NONE,
    unit: WeightUnit = WeightUnit.KG,
    landscape: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    val widthDp = LocalWindowInfo.current.containerDpSize.width.value.roundToInt()
    val thumb = if (landscape) ThumbSize.heroLandscape else ThumbSize.hero
    val meaning = LoadClass.of(lift.exercise.loadType).weightMeaning
    val equipmentLine = CurrentLiftCopy.secondaryLine(lift.exercise.equipment.label, meaning)
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(startedAt) {
        if (startedAt == null) {
            elapsedSeconds = 0
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = ((System.currentTimeMillis() - startedAt) / 1_000L)
                .coerceAtLeast(0L)
                .toInt()
            elapsedSeconds = elapsed
            val intoMinute = elapsed % 60
            delay(((60 - intoMinute).coerceAtLeast(1)) * 1_000L)
        }
    }
    val includeVolume = SessionTelemetryCopy.includeVolume(fontScale, widthDp)
    val volume = if (work.volumeKg > 0.0) {
        QuantityFormat.formatVolumeLabel(work.volumeKg, unit)
    } else {
        null
    }
    val telemetry = SessionTelemetryCopy.line(
        elapsedSeconds = elapsedSeconds,
        workingSets = workingSets,
        volume = volume,
        includeVolume = includeVolume,
    )
    val spoken = CurrentLiftCopy.heroSpoken(
        name = lift.exercise.name,
        number = number,
        total = total,
        workingLogged = workingLogged,
        targetSets = lift.targetSets,
        equipmentLabel = lift.exercise.equipment.label,
        meaning = meaning,
        telemetry = telemetry,
    )
    val shape = RoundedCornerShape(Radius.sm)
    val minHeight = if (fontScale >= 2f) {
        Metrics.touchMin
    } else {
        Metrics.exerciseHeroMin
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(Surface2)
            .border(Metrics.hairline, Hairline, shape)
            .testTag(WorkoutTestTags.CURRENT_LIFT)
            .padding(Metrics.space3),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onOpenSwitcher)
                .testTag(WorkoutTestTags.liftCard(lift.exercise.id))
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    selected = true
                },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            ExerciseThumb(
                exercise = lift.exercise,
                size = thumb,
                showBadge = false,
                artPadding = Metrics.space2,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Kicker(
                    text = CurrentLiftCopy.heroOrdinal(number, total),
                    asHeading = false,
                )
                Text(
                    lift.exercise.name,
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EquipmentGlyphIcon(
                        glyph = glyphFor(lift.exercise.equipment),
                        size = ThumbSize.chipGlyph,
                    )
                    Text(
                        equipmentLine,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    CurrentLiftCopy.heroProgress(workingLogged, lift.targetSets),
                    modifier = Modifier.testTag(WorkoutTestTags.liftSets(lift.exercise.id)),
                    style = InstrumentType.numeralSm,
                    color = TextPrimary,
                    maxLines = 1,
                )
                Text(
                    telemetry,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(WorkoutTestTags.INSTRUMENT_STRIP),
                )
            }
        }
        LiftOverflowMenu(
            liftId = lift.id,
            canEdit = canEdit,
            onSkip = onSkip,
            onSwap = onSwap,
            onRemove = onRemove,
            onNotes = onNotes,
        )
    }
}

/**
 * Packet G: every row stays visible. Skip parks the lift and moves on without deleting;
 * Swap and Remove on a logged lift are disabled with the reason, never silently gone —
 * the lift is part of what happened, and vanishing the control would read as permission.
 */
@Composable
internal fun LiftOverflowMenu(
    liftId: String,
    canEdit: Boolean,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onNotes: () -> Unit,
    onSkip: () -> Unit = {},
) {
    var menuOpen by rememberSaveable(liftId) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier
                .size(Metrics.touchMin)
                .testTag(WorkoutTestTags.LIFT_OPTIONS),
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "Lift options",
                tint = TextSecondary,
            )
        }
        InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        CurrentLiftCopy.SESSION_NOTES,
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                    )
                },
                onClick = {
                    menuOpen = false
                    onNotes()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        CurrentLiftCopy.SKIP,
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                    )
                },
                onClick = {
                    menuOpen = false
                    onSkip()
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            CurrentLiftCopy.SWAP,
                            style = InstrumentType.bodyStrong,
                            color = TextPrimary,
                        )
                        if (!canEdit) {
                            Text(
                                CurrentLiftCopy.EDIT_BLOCKED_REASON,
                                style = InstrumentType.caption,
                                color = TextSecondary,
                            )
                        }
                    }
                },
                enabled = canEdit,
                onClick = {
                    menuOpen = false
                    onSwap()
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            CurrentLiftCopy.REMOVE,
                            style = InstrumentType.bodyStrong,
                            color = Danger,
                        )
                        if (!canEdit) {
                            Text(
                                CurrentLiftCopy.EDIT_BLOCKED_REASON,
                                style = InstrumentType.caption,
                                color = TextSecondary,
                            )
                        }
                    }
                },
                enabled = canEdit,
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}
