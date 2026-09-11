package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.text.DateFormat
import java.util.Date
import com.sinura.personaltrainer.domain.DayLabel
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.LiftChipCopy
import com.sinura.personaltrainer.domain.LiftChipMarks
import com.sinura.personaltrainer.domain.ProgressionCopy
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutCopy
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.LiftCard
import com.sinura.personaltrainer.ui.components.SetEntryPanel
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.util.JvmTime

internal data class WorkoutLiftCardState(
    val lift: SessionExercise,
    val number: Int,
    val selected: Boolean,
    val loggedSets: List<SetLog>,
    val latestSetId: String?,
    val editingSetId: String?,
    val lastPerformance: ExerciseSessionSummary?,
    val hint: ProgressionHint?,
    val draftWeightKg: Double,
    val draftReps: Int,
    val draftWarmup: Boolean,
    val draftRpe: Int?,
    val microRec: SetMicroRec?,
    val unit: WeightUnit,
    val canEdit: Boolean,
    val showAddSet: Boolean,
    val restRunning: Boolean = false,
    val restRemainingSeconds: Int = 0,
)

internal data class WorkoutLiftCardEvents(
    val onSelect: () -> Unit,
    val onSwap: () -> Unit,
    val onRemove: () -> Unit,
    val onWeightKgChange: (Double) -> Unit,
    val onRepsAdjust: (Int) -> Unit,
    val onRepsChange: (Int) -> Unit,
    val onApplyLastTime: (Double, Int) -> Unit,
    val onWarmup: (Boolean) -> Unit,
    val onRpe: (Int?) -> Unit,
    val onApplySuggested: () -> Unit,
    val onEditSet: (String) -> Unit,
    val onDeleteSet: (String) -> Unit,
    val onAddSet: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkoutLiftCard(
    card: WorkoutLiftCardState,
    events: WorkoutLiftCardEvents,
) {
    val lift = card.lift
    val number = card.number
    val selected = card.selected
    val loggedSets = card.loggedSets
    val latestSetId = card.latestSetId
    val editingSetId = card.editingSetId
    val lastPerformance = card.lastPerformance
    val hint = card.hint
    val draftWeightKg = card.draftWeightKg
    val draftReps = card.draftReps
    val draftWarmup = card.draftWarmup
    val draftRpe = card.draftRpe
    val microRec = card.microRec
    val unit = card.unit
    val canEdit = card.canEdit
    val showAddSet = card.showAddSet
    val restRunning = card.restRunning
    val restRemainingSeconds = card.restRemainingSeconds
    val onSelect = events.onSelect
    val onSwap = events.onSwap
    val onRemove = events.onRemove
    val onWeightKgChange = events.onWeightKgChange
    val onRepsAdjust = events.onRepsAdjust
    val onRepsChange = events.onRepsChange
    val onApplyLastTime = events.onApplyLastTime
    val onWarmup = events.onWarmup
    val onRpe = events.onRpe
    val onApplySuggested = events.onApplySuggested
    val onEditSet = events.onEditSet
    val onDeleteSet = events.onDeleteSet
    val onAddSet = events.onAddSet
    val workingLogged = loggedSets.count { !it.isWarmup }
    val targetSets = lift.targetSets
    val chipMarks = LiftChipCopy.marks(
        workingLogged = workingLogged,
        targetSets = targetSets,
        restSeconds = lift.restSeconds,
        restRunningOnThisLift = restRunning,
        remainingSeconds = restRemainingSeconds,
    )
    val chipSpoken = LiftChipCopy.spoken(
        name = lift.exercise.name,
        setProgress = chipMarks.setProgress,
        restClock = chipMarks.restClock,
        restLive = chipMarks.restLive,
    )
    val entryRequester = remember { BringIntoViewRequester() }
    var previousSetCount by remember(lift.id) { mutableIntStateOf(-1) }
    // A card that has just become the selected one carries the entry wells with it, so the
    // same requester that keeps a logged set on screen is what moves the loop to the next
    // lift. bringIntoView animates, which is the difference between arriving at the next
    // exercise and being teleported to it; the guard on `wasSelected` keeps first composition
    // and resume from scrolling a session the lifter has not touched yet.
    var wasSelected by remember(lift.id) { mutableStateOf(selected) }
    LaunchedEffect(lift.id, loggedSets.size, selected) {
        val count = loggedSets.size
        val grew = LogLoopBringIntoView.shouldBringIntoView(previousSetCount, count)
        previousSetCount = count
        val becameSelected = selected && !wasSelected
        wasSelected = selected
        if (grew || becameSelected) {
            entryRequester.bringIntoView()
        }
    }
    val headerTrailing = @Composable {
        LiftChipBadges(
            marks = chipMarks,
            exerciseId = lift.exercise.id,
        )
    }
    LiftCard(
        exercise = lift.exercise,
        selected = selected,
        number = number,
        spoken = chipSpoken,
        onClick = onSelect,
        cardTag = WorkoutTestTags.liftCard(lift.exercise.id),
        trailing = headerTrailing,
    ) {
        if (!selected) return@LiftCard
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Metrics.space3,
                    end = Metrics.space3,
                    bottom = Metrics.space3,
                ),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            CurrentLiftHeader(
                    lift = lift,
                    workingLogged = workingLogged,
                    unit = unit,
                    canEdit = canEdit,
                    rec = microRec,
                    onSwap = onSwap,
                    onRemove = onRemove,
                    showName = false,
                    modifier = Modifier.testTag(WorkoutTestTags.CURRENT_LIFT),
                )
                lastPerformance?.let { last ->
                    LastTimeStrip(
                        summary = last,
                        unit = unit,
                        loadClass = LoadClass.of(lift.exercise.loadType),
                        onApplySet = onApplyLastTime,
                    )
                }
                hint?.let { next ->
                    ProgressionStrip(
                        hint = next,
                        unit = unit,
                        onApply = onApplySuggested,
                    )
                }
                SetEntryPanel(
                    weightKg = draftWeightKg,
                    reps = draftReps,
                    onWeightKgChange = onWeightKgChange,
                    onRepsAdjust = onRepsAdjust,
                    onRepsChange = onRepsChange,
                    unit = unit,
                    loadClass = LoadClass.of(lift.exercise.loadType),
                    plated = lift.exercise.equipment == EquipmentType.BARBELL,
                    modifier = Modifier
                        .testTag(LogLoopBringIntoView.ANCHOR_TAG)
                        .bringIntoViewRequester(entryRequester),
                )
                SecondaryLogOptions(
                    warmup = draftWarmup,
                    rpe = draftRpe,
                    onWarmup = onWarmup,
                    onRpe = onRpe,
                )
                if (loggedSets.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        Kicker("Sets")
                        LoggedSetsPanel(
                            sets = loggedSets,
                            latestSetId = latestSetId,
                            editingSetId = editingSetId,
                            loadClassOf = { LoadClass.of(lift.exercise.loadType) },
                            showAddSet = showAddSet,
                            onEdit = onEditSet,
                            onDelete = onDeleteSet,
                            onAddSet = onAddSet,
                        )
                    }
                }
            }
        }
}

/**
 * Trailing marks on a live lift chip: `2/5` and the rest clock.
 *
 * Idle rest is caption, not a countdown numeral (G-05). Running rest
 * is RestCyan plus the word Rest, so colour is never the only channel
 * (ADR-023).
 */
@Composable
private fun LiftChipBadges(
    marks: LiftChipMarks,
    exerciseId: String,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Text(
            marks.setProgress,
            modifier = Modifier.testTag(WorkoutTestTags.liftSets(exerciseId)),
            style = InstrumentType.numeralSm,
            color = TextPrimary,
            maxLines = 1,
        )
        marks.restClock?.let { clock ->
            Row(
                modifier = Modifier.testTag(WorkoutTestTags.liftRest(exerciseId)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Kicker(
                    text = LiftChipCopy.REST,
                    color = if (marks.restLive) RestCyan else TextSecondary,
                    asHeading = false,
                )
                Text(
                    clock,
                    style = if (marks.restLive) InstrumentType.numeralSm else InstrumentType.caption,
                    color = if (marks.restLive) RestCyan else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun CurrentLiftHeader(
    lift: SessionExercise,
    workingLogged: Int,
    unit: WeightUnit,
    canEdit: Boolean,
    rec: SetMicroRec?,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
) {
    val targetSets = lift.targetSets.coerceAtLeast(1)
    val targetReps = lift.targetReps.coerceAtLeast(1)
    var menuOpen by rememberSaveable(lift.id) { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (showName || canEdit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showName) {
                    Text(
                        lift.exercise.name,
                        modifier = Modifier.weight(1f),
                        style = InstrumentType.display,
                        color = TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (canEdit) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "Lift options",
                                tint = TextSecondary,
                            )
                        }
                        InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text("Swap lift…", style = InstrumentType.bodyStrong, color = TextPrimary)
                                },
                                onClick = {
                                    menuOpen = false
                                    onSwap()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text("Remove lift", style = InstrumentType.bodyStrong, color = Danger)
                                },
                                onClick = {
                                    menuOpen = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            }
        }
        SetDots(completed = workingLogged, target = targetSets)
        val liveRec = rec?.takeIf { it.reasonCode != SetMicroRecCalculator.LIFT_DONE }
        val loadClass = LoadClass.of(lift.exercise.loadType)
        val liveWeightLabel = liveRec?.nextWeightKg
            ?.takeIf { it > 0.0 && loadClass.weightMeaning != com.sinura.personaltrainer.domain.WeightMeaning.NONE }
            ?.toWeightLabel(unit)
        Text(
            WorkoutCopy.setProgress(
                workingLogged = workingLogged,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeightLabel = lift.targetWeightKg?.takeIf { it > 0.0 }?.toWeightLabel(unit),
                liveReps = liveRec?.nextReps,
                liveWeightLabel = liveWeightLabel,
            ),
            style = InstrumentType.caption,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SetDots(completed: Int, target: Int) {
    val total = maxOf(target, completed)
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(Metrics.space2)
                    .clip(CircleShape)
                    // Progress, not an action. Volt on this screen is reserved for the
                    // things that are live or about to be tapped.
                    .background(if (index < completed) TextSecondary else Hairline),
            )
        }
    }
}

/**
 * What this lift looked like last time, in full.
 *
 * Complementary to [ProgressionStrip], not a duplicate of it: the strip states the decision
 * ("top set 100 kg x 5, add 2.5"), this states the evidence — every working set of the last
 * session, so a lifter can see that the top set came after two easy ones or at the end of a
 * grind, which is the difference between adding weight and repeating it.
 */
@Composable
internal fun LastTimeStrip(
    summary: ExerciseSessionSummary,
    unit: WeightUnit,
    loadClass: LoadClass,
    onApplySet: (weightKg: Double, reps: Int) -> Unit,
) {
    val view = LocalView.current
    val relative = remember(summary.performedAtMs) {
        DayLabel.relative(summary.performedAtMs, JvmTime.nowMillis(), JvmTime)
    }
    val absolute = remember(summary.performedAtMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(summary.performedAtMs))
    }
    Column(
        modifier = Modifier.testTag(WorkoutTestTags.LAST_TIME),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Kicker("Last time · ${relative ?: absolute}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            items(summary.sets, key = { it.setId }) { set ->
                val line = SetCopy.setLine(set.weightKg, set.reps, loadClass, unit)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(Surface1)
                        .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.xs))
                        .clickable(role = Role.Button, onClick = {
                            Haptics.tick(view)
                            onApplySet(set.weightKg, set.reps)
                        })
                        .testTag(WorkoutTestTags.lastTimeChip(set.setId))
                        .semantics {
                            contentDescription = "Use last time $line"
                        }
                        .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
                ) {
                    Text(
                        line,
                        style = InstrumentType.numeralSm,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProgressionStrip(
    hint: ProgressionHint,
    unit: WeightUnit,
    onApply: () -> Unit,
) {
    // The sentence lives in the domain so this strip and the coach card cannot disagree about
    // the same lift, and so a bodyweight lift is told to add a rep rather than a kilogram.
    val reason = ProgressionCopy.stripReason(hint, unit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface2)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.md))
            .padding(horizontal = Metrics.space4, vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            // Labelled "Top set" because that is now literally what these numbers
            // are: the heaviest working set of the last session, not the last logged.
            Text(
                "Top set ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}  →  ${hint.suggestedWeightKg.toWeightLabel(unit)}",
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                reason,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onApply,
            modifier = Modifier.heightIn(min = Metrics.touchMin),
        ) {
            Text("Use", style = InstrumentType.bodyStrong, color = Volt)
        }
    }
}
