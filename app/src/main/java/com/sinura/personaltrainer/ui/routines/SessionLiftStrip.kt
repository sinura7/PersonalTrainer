package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.TargetEntry
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.FieldComplaint
import com.sinura.personaltrainer.ui.components.fieldError
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.components.imeAction
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

data class SessionLiftItem(
    val id: String,
    val exercise: Exercise,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val targetWeightKg: Double? = null,
)

object SessionLiftTags {
    const val STRIP = "session-lift-strip"
    const val EDITOR = "session-lift-editor"
    const val HINT = "session-lift-hint"
    fun card(id: String) = "session-lift-card-$id"
}

object SessionLiftCopy {
    const val MOVE_EARLIER = "Earlier"
    const val MOVE_LATER = "Later"
}

object CompactLiftCopy {
    const val TARGET_WEIGHT = "Target weight"
    const val REST = "Rest"
}

object CompactLiftTags {
    const val TARGET_WEIGHT = "compact-lift-target-weight"
}

/**
 * The session as a vertical stack of full-width cards.
 *
 * Each lift occupies one horizontal row. The next sits under it. Tap expands
 * that card for sets, reps, rest and load — not a sideways strip you have to
 * hunt through.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionLiftStrip(
    lifts: List<SessionLiftItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onMoveEarlier: (String) -> Unit,
    onMoveLater: (String) -> Unit,
    onRemove: (String) -> Unit,
    onStageTargets: (String, Int?, Int?, Int?, Double?, String?) -> Unit,
    onCommitTargets: (String) -> Unit,
    /**
     * The card folded shut, taking its four boxes and their text with it. Whatever rule
     * those boxes broke is no longer on screen to fix, so it must not go on refusing.
     */
    onForgetTargetRule: (String) -> Unit,
    modifier: Modifier = Modifier,
    canSwap: (String) -> Boolean = { false },
    onSwap: (String) -> Unit = {},
) {
    val selectedIndex = lifts.indexOfFirst { it.id == selectedId }
    val firstRequester = remember { BringIntoViewRequester() }
    val lastRequester = remember { BringIntoViewRequester() }
    var seenCount by remember { mutableIntStateOf(0) }
    var seenFirstId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lifts.firstOrNull()?.id, lifts.size) {
        val firstId = lifts.firstOrNull()?.id
        when (nextStripScroll(seenFirstId, seenCount, firstId, lifts.size)) {
            StripScrollTarget.START -> firstRequester.bringIntoView()
            StripScrollTarget.LAST -> lastRequester.bringIntoView()
            null -> Unit
        }
        seenCount = lifts.size
        seenFirstId = firstId
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SessionLiftTags.STRIP),
        verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
    ) {
        lifts.forEachIndexed { index, item ->
            val requester = when (index) {
                0 -> firstRequester
                lifts.lastIndex -> lastRequester
                else -> null
            }
            SessionLiftCard(
                item = item,
                number = index + 1,
                total = lifts.size,
                selected = item.id == selectedId,
                onClick = { onSelect(item.id) },
                canMoveEarlier = index > 0,
                canMoveLater = index < lifts.lastIndex,
                canSwap = canSwap(item.id),
                onMoveEarlier = { onMoveEarlier(item.id) },
                onMoveLater = { onMoveLater(item.id) },
                onRemove = { onRemove(item.id) },
                onSwap = { onSwap(item.id) },
                onForgetTargetRule = onForgetTargetRule,
                onStageTargets = { sets, reps, rest, kg, invalid ->
                    onStageTargets(item.id, sets, reps, rest, kg, invalid)
                },
                onCommitTargets = { onCommitTargets(item.id) },
                modifier = Modifier.then(
                    if (requester != null) Modifier.bringIntoViewRequester(requester) else Modifier,
                ),
            )
        }
        if (lifts.isNotEmpty() && selectedIndex < 0) {
            Text(
                SessionOrderCopy.TAP_TO_SET,
                modifier = Modifier.testTag(SessionLiftTags.HINT),
                style = InstrumentType.body,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SessionLiftCard(
    item: SessionLiftItem,
    number: Int,
    total: Int,
    selected: Boolean,
    onClick: () -> Unit,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    canSwap: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
    onSwap: () -> Unit,
    onStageTargets: (Int?, Int?, Int?, Double?, String?) -> Unit,
    onCommitTargets: () -> Unit,
    onForgetTargetRule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    val restClock = RestTimer.formatClock(item.restSeconds)
    val loadKg = item.targetWeightKg?.takeIf { it > 0.0 }
    val loadDisplay = loadKg?.let { kg -> WeightConverter.formatLabel(kg, unit) }
    val spoken = SessionOrderCopy.cardSpoken(
        number = number,
        name = item.exercise.name,
        muscleGroup = item.exercise.muscleGroup,
        sets = item.sets,
        reps = item.reps,
        restClock = restClock,
        load = loadDisplay,
    )
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .clip(shape)
            .background(if (selected) VoltDim else Surface2)
            .border(
                if (selected) Metrics.emphasisBorder else Metrics.hairline,
                if (selected) Volt else Hairline,
                shape,
            ),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag(SessionLiftTags.card(item.id))
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    this.selected = selected
                }
                .padding(Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                CartBadge(number = number, selected = selected)
                ExerciseThumb(
                    exercise = item.exercise,
                    size = ThumbSize.header,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.exercise.name,
                        style = InstrumentType.title,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.exercise.muscleGroup.isNotBlank()) {
                        Text(
                            item.exercise.muscleGroup,
                            style = InstrumentType.caption,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                MetricCluster(
                    value = "${item.sets} × ${item.reps}",
                    label = SessionOrderCopy.WORK,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                )
                MetricCluster(
                    value = restClock,
                    label = SessionOrderCopy.REST,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                )
                if (loadKg != null) {
                    MetricCluster(
                        value = WeightConverter.formatDisplayNumber(
                            WeightConverter.toDisplayValue(loadKg, unit),
                        ),
                        label = SessionOrderCopy.LOAD,
                        unit = unit.suffix,
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                }
            }
        }
        if (selected) {
            SessionLiftEditor(
                item = item,
                number = number,
                total = total,
                canMoveEarlier = canMoveEarlier,
                canMoveLater = canMoveLater,
                canSwap = canSwap,
                onMoveEarlier = onMoveEarlier,
                onMoveLater = onMoveLater,
                onRemove = onRemove,
                onSwap = onSwap,
                onStageTargets = onStageTargets,
                onCommitTargets = onCommitTargets,
                onForgetTargetRule = onForgetTargetRule,
            )
        }
    }
}

@Composable
private fun SessionLiftEditor(
    item: SessionLiftItem,
    number: Int,
    total: Int,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    canSwap: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
    onSwap: () -> Unit,
    onStageTargets: (Int?, Int?, Int?, Double?, String?) -> Unit,
    onCommitTargets: () -> Unit,
    onForgetTargetRule: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SessionLiftTags.EDITOR),
    ) {
        Kicker(
            SessionOrderCopy.liftIndex(number, total),
            modifier = Modifier.padding(horizontal = Metrics.space3),
        )
        key("${item.id}:${item.exercise.id}") {
            // Tied to the SAME key as the boxes themselves, so it fires exactly when their
            // text is discarded — the card folded shut, the lift removed, or this slot reused
            // for a different lift after a reorder. Reopening re-reads the STORED numbers, so
            // a rejection staged from text that no longer exists would refuse Save for a box
            // showing its stored value: a dead end with nothing on screen to correct, the same
            // shape 1801821 fixed for a removed card.
            //
            // A configuration change disposes too, but there `rememberSaveable` restores the
            // typed text and CompactTargetFields re-registers the rejection on its next
            // composition, so clearing here is self-correcting for rotation.
            //
            // The id is captured in a local rather than read through the lambda at dispose
            // time: after a reorder this slot's `item` is already the NEW lift, and forgetting
            // that one would clear a complaint the owner can still see.
            val forgettingId = item.id
            DisposableEffect(forgettingId) {
                onDispose { onForgetTargetRule(forgettingId) }
            }
            CompactTargetFields(
                rowKey = "${item.id}:${item.exercise.id}",
                sets = item.sets,
                reps = item.reps,
                restSeconds = item.restSeconds,
                targetWeightKg = item.targetWeightKg,
                onStageTargets = onStageTargets,
                onCommitTargets = onCommitTargets,
                onRemove = onRemove,
                onSwap = if (canSwap) onSwap else null,
            )
        }
        Kicker(
            SessionOrderCopy.ORDER,
            modifier = Modifier.padding(horizontal = Metrics.space3),
        )
        Row(
            modifier = Modifier.padding(
                start = Metrics.space2,
                end = Metrics.space2,
                bottom = Metrics.space2,
            ),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            TextButton(
                onClick = onMoveEarlier,
                enabled = canMoveEarlier,
            ) {
                Text(
                    SessionLiftCopy.MOVE_EARLIER,
                    style = InstrumentType.bodyStrong,
                    color = if (canMoveEarlier) TextSecondary else TextTertiary,
                )
            }
            TextButton(
                onClick = onMoveLater,
                enabled = canMoveLater,
            ) {
                Text(
                    SessionLiftCopy.MOVE_LATER,
                    style = InstrumentType.bodyStrong,
                    color = if (canMoveLater) TextSecondary else TextTertiary,
                )
            }
        }
    }
}

@Composable
internal fun CartBadge(
    number: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val badgeShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .heightIn(min = Metrics.space6)
            .widthIn(min = Metrics.space6)
            .clip(badgeShape)
            .background(if (selected) Volt else Surface1)
            .border(Metrics.hairline, if (selected) Volt else Hairline, badgeShape)
            .padding(horizontal = Metrics.space1),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = InstrumentType.caption,
            color = if (selected) Pit else TextPrimary,
        )
    }
}

internal enum class StripScrollTarget { START, LAST }

internal fun nextStripScroll(
    seenFirstId: String?,
    seenCount: Int,
    firstId: String?,
    size: Int,
): StripScrollTarget? = when {
    seenFirstId != null && firstId != seenFirstId && size > 0 -> StripScrollTarget.START
    size > seenCount && size > 0 -> StripScrollTarget.LAST
    else -> null
}

@Composable
internal fun CompactTargetFields(
    rowKey: String,
    sets: Int,
    reps: Int,
    restSeconds: Int,
    targetWeightKg: Double?,
    onStageTargets: (Int?, Int?, Int?, Double?, String?) -> Unit,
    onCommitTargets: () -> Unit,
    onRemove: () -> Unit,
    onSwap: (() -> Unit)?,
) {
    val unit = LocalWeightUnit.current
    val chain = NumericEntry.ROUTINE_EDITOR_CHAIN
    val setsFocus = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    val restFocus = remember { FocusRequester() }
    val weightFocus = remember { FocusRequester() }
    // What each box reads when it shows the STORED target and nothing else. Held as values so
    // the restore hook below can ask the only question that matters: is the text on screen
    // still what the routine holds, or did the owner type something the routine does not?
    val storedWeightText = targetWeightKg?.let { kg ->
        WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(kg, unit))
    }.orEmpty()
    var setsText by rememberSaveable(rowKey) { mutableStateOf(sets.toString()) }
    var repsText by rememberSaveable(rowKey) { mutableStateOf(reps.toString()) }
    var restText by rememberSaveable(rowKey) { mutableStateOf(restSeconds.toString()) }
    var weightText by rememberSaveable(rowKey) { mutableStateOf(storedWeightText) }
    // The four boxes, read as typed. An empty box means "leave this one alone"; a box that
    // cannot be stored as written is a complaint under that box and a rejection staged with the
    // card, so the commit refuses it and Save and Back count it as unsaved (UX06). The text is
    // never rewritten on the way.
    val entry = TargetEntry.read(setsText, repsText, restText, weightText, unit)
    val stage = {
        val read = TargetEntry.read(setsText, repsText, restText, weightText, unit)
        onStageTargets(read.typedSets, read.typedReps, read.typedRest, read.typedWeightKg, read.firstError)
    }
    // The box text is saved state; what was staged from it is not. After the process is
    // reclaimed the card is rebuilt showing whatever was typed and nothing upstream knows, so
    // Save walks past it — refusing a card the owner can see is wrong when the text cannot be
    // read, and worse when it CAN: typing "8" into reps, losing the process before the box
    // loses focus, and pressing Save popped the editor claiming success while the routine
    // still held 5. Both are the same omission and both are re-registered here.
    //
    // The condition is "the text is not what the routine holds", not "the text is broken",
    // because that is exactly what a pending edit is. A card showing its stored numbers stages
    // nothing, so an untouched editor is never marked dirty and Back never asks about changes
    // nobody made — which is the trap a blanket re-stage would fall into.
    val differsFromStored = setsText != sets.toString() ||
        repsText != reps.toString() ||
        restText != restSeconds.toString() ||
        weightText != storedWeightText
    LaunchedEffect(rowKey, entry.hasError, differsFromStored) {
        if (entry.hasError || differsFromStored) stage()
    }
    Column(
        modifier = Modifier.padding(start = Metrics.space3, end = Metrics.space3, bottom = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            MiniNumberField(
                label = "Sets",
                value = setsText,
                modifier = Modifier.weight(1f),
                onFocusLost = onCommitTargets,
                error = entry.setsError,
                ime = chain[0],
                focusRequester = setsFocus,
                onImeNext = { repsFocus.requestFocus() },
            ) {
                setsText = it
                stage()
            }
            MiniNumberField(
                label = "Reps",
                value = repsText,
                modifier = Modifier.weight(1f),
                onFocusLost = onCommitTargets,
                error = entry.repsError,
                ime = chain[1],
                focusRequester = repsFocus,
                onImeNext = { restFocus.requestFocus() },
            ) {
                repsText = it
                stage()
            }
        }
        MiniNumberField(
            label = CompactLiftCopy.REST,
            value = restText,
            modifier = Modifier.fillMaxWidth(),
            onFocusLost = onCommitTargets,
            error = entry.restError,
            suffix = "s",
            ime = chain[2],
            focusRequester = restFocus,
            onImeNext = { weightFocus.requestFocus() },
        ) {
            restText = it
            stage()
        }
        MiniNumberField(
            label = CompactLiftCopy.TARGET_WEIGHT,
            value = weightText,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CompactLiftTags.TARGET_WEIGHT),
            onFocusLost = onCommitTargets,
            error = entry.weightError,
            allowDecimal = true,
            suffix = unit.suffix,
            ime = chain[3],
            focusRequester = weightFocus,
        ) {
            weightText = it
            stage()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            TextButton(onClick = onRemove) {
                Text("Remove", style = InstrumentType.bodyStrong, color = Danger)
            }
            if (onSwap != null) {
                TextButton(onClick = onSwap) {
                    Text("Swap", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MiniNumberField(
    label: String,
    value: String,
    modifier: Modifier,
    onFocusLost: () -> Unit,
    /**
     * The rule this box's text breaks, or null. Shown once the finger has left the box — a
     * half-typed "62." would otherwise flash red on the way to "62.5" — and kept there until
     * the text changes to something the routine can hold.
     */
    error: String? = null,
    allowDecimal: Boolean = false,
    suffix: String? = null,
    ime: NumericEntry.Ime = NumericEntry.Ime.NEXT,
    focusRequester: FocusRequester? = null,
    onImeNext: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    var hadFocus by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val shownError = error?.takeIf { !focused }
    val complaint: (@Composable () -> Unit)? = shownError?.let { message ->
        { FieldComplaint(message) }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = InstrumentType.caption) },
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fieldError(shownError)
            .onFocusChanged { focus ->
                if (hadFocus && !focus.isFocused) onFocusLost()
                hadFocus = focus.isFocused
                focused = focus.isFocused
            },
        singleLine = true,
        isError = shownError != null,
        supportingText = complaint,
        textStyle = InstrumentType.numeralMd,
        suffix = suffix?.let { unit ->
            { Text(unit, style = InstrumentType.unit, color = TextSecondary) }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ime.imeAction(),
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeNext?.invoke() },
            onDone = { onFocusLost() },
        ),
    )
}
