package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.LibraryFilter
import com.sinura.personaltrainer.domain.LiftCart
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoadTypeCopy
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.instrumentAnimateItem
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim

/**
 * Choosing a lift, and the row anatomy the library screen shares with this sheet.
 *
 * [ExerciseRow] and [ExerciseSearchField] live here rather than in a file of their own
 * because the picker and the library are the same job on two surfaces — the audit's finding
 * was precisely that they had drifted into two different-looking lists — and one definition
 * is what keeps them from drifting again.
 */

/** Results and creation are separate steps; writes belong to the calling ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(state: ExercisePickerState, onEvent: (ExercisePickerEvent) -> Unit) {
    val busy by rememberUpdatedState(state.progress.busy)
    val canChange = remember { { value: SheetValue -> value != SheetValue.Hidden || !busy } }
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = canChange,
    )
    ModalBottomSheet(
        onDismissRequest = { if (!state.progress.busy) onEvent(ExercisePickerEvent.Dismissed) },
        sheetState = sheet,
    ) {
        ExercisePickerContent(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.fillMaxHeight(SHEET_HEIGHT_SHARE),
        )
    }
}

/** The same native content is used by the sheet and its constrained-layout render tests. */
@Composable
internal fun ExercisePickerContent(
    state: ExercisePickerState,
    onEvent: (ExercisePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMuscleName by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var createName by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var loadTypeName by rememberSaveable { mutableStateOf(LoadType.EXTERNAL.name) }
    val selectedMuscle = CanonicalMuscle.bodyMapOrder.firstOrNull { it.name == selectedMuscleName }
    val visibleResults = state.visibleFor(selectedMuscle)
    val needle = state.query.trim()
    val canCreate = needle.isNotEmpty() &&
        (state.catalog + state.results).none { it.name.equals(needle, ignoreCase = true) }
    val list = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val problem = state.progress.error ?: state.error
    LaunchedEffect(state.progress.createdGeneration) {
        if (state.progress.createdGeneration > 0) creating = false
    }
    LaunchedEffect(problem, creating) { if (problem != null || creating) list.scrollToItem(0) }
    val choose: (Exercise) -> Unit = { exercise ->
        if (!state.progress.creating && (state.multiSelect || !state.progress.busy)) {
            onEvent(if (state.multiSelect) ExercisePickerEvent.Toggled(exercise) else ExercisePickerEvent.Selected(exercise))
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = Metrics.gutter).padding(bottom = Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            if (creating) {
                TextButton(onClick = { creating = false }, enabled = !state.progress.busy) {
                    Text("Back to lifts", style = InstrumentType.bodyStrong)
                }
                Text("Create custom lift", style = InstrumentType.title, color = TextPrimary)
            } else {
                Text(state.title, style = InstrumentType.title, color = TextPrimary)
                ExerciseSearchField(
                    value = state.query,
                    onValueChange = { onEvent(ExercisePickerEvent.QueryChanged(it)) },
                    placeholder = "Search lifts",
                    modifier = Modifier.testTag("picker-search"),
                )
            }
        }
        HairlineDivider(startIndent = 0.dp)
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).testTag("picker-content"),
            contentPadding = PaddingValues(bottom = Metrics.space4),
        ) {
            if (problem != null) {
                item(key = "error") {
                    GymErrorBanner(
                        message = problem,
                        modifier = Modifier.padding(Metrics.gutter),
                        onRetry = if (state.progress.canRetry && !state.progress.busy) {
                            { onEvent(ExercisePickerEvent.Retry) }
                        } else null,
                        onDismiss = { onEvent(ExercisePickerEvent.ErrorDismissed) },
                    )
                }
            }
            state.progress.existing?.let { existing ->
                item(key = "existing") {
                    ExerciseRow(
                        exercise = existing,
                        nameMaxLines = Int.MAX_VALUE,
                        tag = LoadTypeCopy.rowTag(existing),
                    )
                    TextButton(
                        onClick = { creating = false; onEvent(ExercisePickerEvent.Selected(existing)) },
                        enabled = !state.progress.busy,
                        modifier = Modifier.padding(horizontal = Metrics.gutter),
                    ) { Text("Use existing lift: ${existing.name}", style = InstrumentType.bodyStrong) }
                }
            }
            if (creating) {
                item(key = "create-form") {
                    Column(
                        modifier = Modifier.padding(Metrics.gutter).testTag("picker-create-form"),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                    ) {
                        Text(createName, style = InstrumentType.display, color = TextPrimary)
                        Text(
                            "Saved to your Library, then selected here. Cancelling later keeps the Library lift.",
                            style = InstrumentType.caption, color = TextSecondary,
                        )
                        Kicker("MUSCLE GROUP")
                        if (group.isBlank()) Text("Choose a muscle group to continue.", style = InstrumentType.caption, color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            (MuscleGroups.chips + MuscleGroups.OTHER).forEach { muscle ->
                                InstrumentChip(
                                    label = muscle, selected = muscle.equals(group, ignoreCase = true),
                                    onClick = { group = muscle }, enabled = !state.progress.busy,
                                )
                            }
                        }
                        LoadTypeChipRow(
                            selected = LoadType.fromStorage(loadTypeName),
                            onSelect = { loadTypeName = it.name },
                            enabled = !state.progress.busy,
                        )
                    }
                }
            } else {
                item(key = "filters") {
                    LazyRow(
                        contentPadding = PaddingValues(Metrics.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        item { InstrumentChip("All", selectedMuscle == null, { selectedMuscleName = "" }) }
                        items(CanonicalMuscle.bodyMapOrder, key = { it.name }) { muscle ->
                            InstrumentChip(muscle.displayName, selectedMuscle == muscle, {
                                selectedMuscleName = if (selectedMuscleName == muscle.name) "" else muscle.name
                            })
                        }
                    }
                }
                if (state.multiSelect) {
                    item(key = "selection") {
                        Text(SessionOrderCopy.PICKER_HINT, style = InstrumentType.caption, color = TextSecondary,
                            modifier = Modifier.padding(horizontal = Metrics.gutter).padding(bottom = Metrics.space3))
                        if (state.cart.isNotEmpty()) {
                            LazyRow(contentPadding = PaddingValues(horizontal = Metrics.gutter), horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                                itemsIndexed(state.cart, key = { _, e -> e.id }) { i, exercise ->
                                    InstrumentChip(label = "${i + 1}  ${exercise.name}", selected = true, onClick = { choose(exercise) }, enabled = !state.progress.creating)
                                }
                            }
                        }
                    }
                }
                if (needle.isEmpty()) {
                    val siblings = if (state.showSiblings) LibraryFilter.apply(state.siblings, selectedMuscle) else emptyList()
                    if (siblings.isNotEmpty()) {
                        item { Kicker("SAME MOVEMENT", modifier = Modifier.padding(Metrics.gutter)) }
                        items(siblings, key = { "sibling-${it.id}" }) { exercise ->
                            PickerLiftRow(exercise = exercise, selected = exercise.id in state.selectedIds, onClick = { choose(exercise) },
                                cartNumber = LiftCart.cartNumber(state.selectedOrder, exercise.id),
                                enabled = !state.progress.busy, pending = exercise.id in state.progress.pendingIds)
                        }
                    }
                    state.suggestion?.takeIf { selectedMuscle == null || LibraryFilter.matches(it, selectedMuscle) }?.let { exercise ->
                        item(key = "suggestion") {
                            Kicker("SUGGESTED", modifier = Modifier.padding(Metrics.gutter))
                            PickerLiftRow(exercise = exercise, selected = exercise.id in state.selectedIds, onClick = { choose(exercise) }, subtitle = state.suggestionReason,
                                cartNumber = LiftCart.cartNumber(state.selectedOrder, exercise.id),
                                enabled = !state.progress.busy, pending = exercise.id in state.progress.pendingIds)
                        }
                    }
                }
                if (visibleResults.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            scene = EmptyScene.CATALOG,
                            title = "No matching lifts",
                            body = if (selectedMuscle != null) "Try All or a different name." else "Try a different name, or create your own lift.",
                            modifier = Modifier.padding(Metrics.gutter), compact = true,
                        )
                    }
                }
                itemsIndexed(visibleResults, key = { _, e -> e.id }) { i, exercise ->
                    Column(modifier = instrumentAnimateItem()) {
                        PickerLiftRow(exercise = exercise, selected = exercise.id in state.selectedIds, onClick = { choose(exercise) },
                            cartNumber = LiftCart.cartNumber(state.selectedOrder, exercise.id),
                            enabled = !state.progress.creating && (state.multiSelect || !state.progress.busy),
                            pending = exercise.id in state.progress.pendingIds)
                        if (i < visibleResults.lastIndex) HairlineDivider()
                    }
                }
                if (canCreate) {
                    item(key = "create-entry") {
                        TextButton(
                            onClick = {
                                keyboard?.hide()
                                createName = needle
                                group = MuscleGroups.forNewDraft(selectedMuscle)
                                loadTypeName = LoadType.EXTERNAL.name
                                creating = true
                            },
                            enabled = !state.progress.busy,
                            modifier = Modifier.fillMaxWidth().padding(Metrics.gutter).testTag("picker-create-entry"),
                        ) { Text("Create custom lift: $needle", style = InstrumentType.bodyStrong, color = TextSecondary) }
                    }
                }
            }
        }
        if (creating || state.multiSelect || state.progress.busy) {
            PrimaryGymButton(
                text = when {
                    state.progress.creating -> "Creating lift…"
                    state.progress.busy -> "Saving lift…"
                    creating -> "Create and select"
                    state.cart.isEmpty() -> "Done"
                    state.cart.size == 1 -> "Done · 1 lift"
                    else -> "Done · ${state.cart.size} lifts"
                },
                enabled = !state.progress.busy && (!creating || MuscleGroups.resolved(group) != null),
                onClick = {
                    if (creating) onEvent(ExercisePickerEvent.Created(createName, group, LoadType.fromStorage(loadTypeName)))
                    else onEvent(ExercisePickerEvent.Dismissed)
                },
                modifier = Modifier.padding(horizontal = Metrics.gutter, vertical = Metrics.space3).testTag("picker-action"),
            )
        }
    }
}

@Composable
private fun PickerLiftRow(
    exercise: Exercise,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    cartNumber: Int? = null,
    enabled: Boolean = true,
    pending: Boolean = false,
) {
    val shape = RoundedCornerShape(Radius.xs)
    ExerciseRow(
        exercise = exercise,
        modifier = (if (selected) {
            Modifier
                .clip(shape)
                .background(VoltDim)
                .border(Metrics.emphasisBorder, Volt, shape)
        } else {
            Modifier
        }).semantics {
            this.selected = selected
            if (pending) stateDescription = "Saving lift"
        },
        onClick = if (enabled) onClick else null,
        nameMaxLines = Int.MAX_VALUE,
        tag = LoadTypeCopy.rowTag(exercise),
        subtitle = if (pending) "Saving…" else subtitle,
        trailing = when {
            cartNumber != null -> {
                {
                    PickerCartBadge(cartNumber)
                }
            }
            selected -> {
                {
                    Text("Selected", style = InstrumentType.caption, color = TextTertiary)
                }
            }
            else -> null
        },
    )
}

@Composable
private fun PickerCartBadge(number: Int) {
    val badgeShape = Radius.full
    Box(
        modifier = Modifier
            .heightIn(min = Metrics.space5)
            .widthIn(min = Metrics.space5)
            .clip(badgeShape)
            .background(Volt)
            .padding(horizontal = Metrics.space1),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = InstrumentType.caption,
            color = Pit,
        )
    }
}

/**
 * One lift in a list: thumbnail, name, muscle group, and whatever the surface does to it.
 *
 * Takes the [Exercise] rather than a name and a group string, because the thumbnail needs the
 * equipment and the muscle credits too — and every call site already had the object in hand.
 * Passing three fields where one would do is how a row ends up unable to draw its own picture.
 *
 * [trailing] is a slot so the library can hang its actions here without the picker growing
 * a set of buttons it has no use for.
 */
@Composable
fun ExerciseRow(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tag: String? = null,
    /**
     * Overrides the muscle-group subtitle. Exactly one caller uses it — the coach's pinned
     * suggestion, which says WHY it is suggested rather than what it trains, and would be a
     * worse row if it repeated the group the rows below it already show.
     */
    subtitle: String? = null,
    nameMaxLines: Int = 1,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(
                start = Metrics.gutter,
                // Icon buttons carry their own 48dp of target, so the trailing edge closes up.
                end = if (trailing != null) Metrics.space2 else Metrics.gutter,
            )
            .padding(vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        ExerciseThumb(exercise)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                exercise.name,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    subtitle ?: exercise.muscleGroup,
                    modifier = Modifier.weight(1f, fill = false),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tag != null) EquipmentChip(tag)
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * A search bar in the app's own language: one [Surface1] well with a hairline, not a form
 * field with a floating label and an M3 4dp corner.
 *
 * It does not take focus on its own. The sheet opens on the catalog, and a keyboard that
 * covers the thing you came to look at is not a shortcut — which is what the invisible 1dp
 * focus-sink Box that used to sit above this was working around.
 */
@Composable
fun ExerciseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.control)
            .clip(shape)
            .background(Surface1)
            .border(Metrics.hairline, Hairline, shape)
            .padding(start = Metrics.space4, end = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = TextTertiary)
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = InstrumentType.body,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = InstrumentType.body.copy(color = TextPrimary),
                singleLine = true,
                cursorBrush = SolidColor(Volt),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = TextSecondary)
            }
        }
    }
}

/** Enough of the screen that the catalog is the sheet, with the scrim still legible above it. */
private const val SHEET_HEIGHT_SHARE = 0.9f
