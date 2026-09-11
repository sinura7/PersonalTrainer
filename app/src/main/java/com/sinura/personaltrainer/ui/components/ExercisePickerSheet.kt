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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
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

/**
 * The picker, as a list of lifts rather than a form with a list under it.
 *
 * Three things used to fight for the top of this sheet: an unstyled title, the search box,
 * and a permanently mounted "Muscle group for new exercise" field that mattered only on the
 * rare create path. Underneath them the results were capped at 360dp inside a half-height
 * sheet — about four rows of a catalog of forty. Here the sheet is a fixed nine-tenths of
 * the screen, the search stays pinned to the top, and everything below it is catalog.
 *
 * Creating is not chrome any more: it appears as a single row above the results, only when
 * what has been typed matches nothing. The muscle chips sit on that row — not a permanent
 * field at the top — because a blank group used to become "Other" and never heat a plate.
 *
 * Multi-add writes as it goes. Every tap lands on the routine or the day immediately, and
 * the cart is a numbered view of that session rather than a staging list held by the sheet:
 * the scrim, the back gesture and a mis-swipe cost the typed query and nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    state: ExercisePickerState,
    onEvent: (ExercisePickerEvent) -> Unit,
) {
    val query = state.query
    val results = state.results
    val title = state.title
    val suggestion = state.suggestion
    val suggestionReason = state.suggestionReason
    val siblings = if (state.showSiblings) state.siblings else emptyList()
    val selectedIds = state.selectedIds
    val selectedOrder = state.selectedOrder
    val cart = state.cart
    val multiSelect = state.multiSelect
    val needle = query.trim()
    val canCreate = needle.isNotEmpty() && results.none { it.name.equals(needle, ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = { onEvent(ExercisePickerEvent.Dismissed) },
        // One height, held: the sheet no longer grows and shrinks under the thumb as the
        // result count changes with every keystroke.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // The height goes on the content, never on the sheet's own modifier: the caller's
        // modifier is the first link in a chain that ends in the drag anchors, so fixing a
        // height there positions the sheet by its top edge and lifts it off the bottom of
        // the screen instead of making it tall.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT_SHARE),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = Metrics.gutter)
                    .padding(bottom = Metrics.space3),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                Text(title, style = InstrumentType.title, color = TextPrimary)
                if (multiSelect) {
                    Text(
                        SessionOrderCopy.PICKER_HINT,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                }
                ExerciseSearchField(
                    value = query,
                    onValueChange = { onEvent(ExercisePickerEvent.QueryChanged(it)) },
                    placeholder = "Search, or name a new lift",
                )
            }
            if (multiSelect && cart.isNotEmpty()) {
                val cartState = rememberLazyListState()
                LaunchedEffect(cart.size) {
                    cartState.animateScrollToItem(cart.lastIndex)
                }
                Column(
                    modifier = Modifier.padding(bottom = Metrics.space3),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    Kicker(
                        SessionOrderCopy.CART,
                        modifier = Modifier.padding(horizontal = Metrics.gutter),
                    )
                    LazyRow(
                        state = cartState,
                        contentPadding = PaddingValues(horizontal = Metrics.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        itemsIndexed(cart, key = { _, exercise -> exercise.id }) { index, exercise ->
                            InstrumentChip(
                                label = "${index + 1}  ${exercise.name}",
                                selected = true,
                                onClick = { onEvent(ExercisePickerEvent.Toggled(exercise)) },
                            )
                        }
                    }
                }
            }
            HairlineDivider(startIndent = 0.dp)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Metrics.space7),
            ) {
                if (siblings.isNotEmpty() && needle.isEmpty()) {
                    item(key = "siblings-header") {
                        Kicker(
                            "SAME MOVEMENT",
                            modifier = Modifier.padding(
                                start = Metrics.space4,
                                top = Metrics.space3,
                                bottom = Metrics.space1,
                            ),
                        )
                    }
                    items(siblings, key = { "sibling-${it.id}" }) { sibling ->
                        Column {
                            PickerLiftRow(
                                exercise = sibling,
                                selected = sibling.id in selectedIds,
                                cartNumber = LiftCart.cartNumber(selectedOrder, sibling.id),
                                onClick = {
                                    if (multiSelect) {
                                        onEvent(ExercisePickerEvent.Toggled(sibling))
                                    } else {
                                        onEvent(ExercisePickerEvent.Selected(sibling))
                                    }
                                },
                            )
                            HairlineDivider()
                        }
                    }
                }
                if (suggestion != null && needle.isEmpty()) {
                    item(key = "suggested") {
                        Column {
                            Kicker(
                                "SUGGESTED",
                                modifier = Modifier.padding(
                                    start = Metrics.space4,
                                    top = Metrics.space3,
                                    bottom = Metrics.space1,
                                ),
                            )
                            PickerLiftRow(
                                exercise = suggestion,
                                selected = suggestion.id in selectedIds,
                                cartNumber = LiftCart.cartNumber(selectedOrder, suggestion.id),
                                onClick = {
                                    if (multiSelect) {
                                        onEvent(ExercisePickerEvent.Toggled(suggestion))
                                    } else {
                                        onEvent(ExercisePickerEvent.Selected(suggestion))
                                    }
                                },
                                subtitle = suggestionReason,
                            )
                            HairlineDivider()
                        }
                    }
                }
                if (canCreate) {
                    item(key = "create") {
                        var group by rememberSaveable(needle) { mutableStateOf("") }
                        var loadTypeName by rememberSaveable(needle) {
                            mutableStateOf(LoadType.EXTERNAL.name)
                        }
                        val loadType = LoadType.fromStorage(loadTypeName)
                        Column {
                            CreateExerciseRow(
                                name = needle,
                                muscleGroup = group,
                                loadType = loadType,
                                onMuscle = { group = it },
                                onLoadType = { loadTypeName = it.name },
                                onClick = {
                                    if (MuscleGroups.resolved(group) != null) {
                                        onEvent(
                                            ExercisePickerEvent.Created(needle, group, loadType),
                                        )
                                    }
                                },
                            )
                            HairlineDivider()
                        }
                    }
                }
                if (results.isEmpty() && !canCreate) {
                    item(key = "empty") {
                        EmptyState(
                            title = if (needle.isEmpty()) "Search the library" else "No matches",
                            body = if (needle.isEmpty()) {
                                "Type a lift name to search, or name a new one to create it."
                            } else {
                                "Nothing in the library matches. Create it as a custom lift above."
                            },
                            modifier = Modifier.padding(Metrics.gutter),
                            compact = true,
                        )
                    }
                } else {
                    itemsIndexed(results, key = { _, exercise -> exercise.id }) { index, exercise ->
                        Column(modifier = instrumentAnimateItem()) {
                            PickerLiftRow(
                                exercise = exercise,
                                selected = exercise.id in selectedIds,
                                cartNumber = LiftCart.cartNumber(selectedOrder, exercise.id),
                                onClick = {
                                    if (multiSelect) {
                                        onEvent(ExercisePickerEvent.Toggled(exercise))
                                    } else {
                                        onEvent(ExercisePickerEvent.Selected(exercise))
                                    }
                                },
                            )
                            if (index < results.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
            if (state.mode == ExercisePickerMode.MULTI_ADD) {
                if (!state.error.isNullOrBlank()) {
                    GymErrorBanner(
                        message = state.error,
                        modifier = Modifier.padding(horizontal = Metrics.gutter),
                        onDismiss = { onEvent(ExercisePickerEvent.ErrorDismissed) },
                    )
                }
                // Done, not Add: the lifts went on the routine as they were tapped. This
                // button is the way out of the sheet, and it says what is already saved.
                PrimaryGymButton(
                    text = when (cart.size) {
                        0 -> "Done"
                        1 -> "Done · 1 lift"
                        else -> "Done · ${cart.size} lifts"
                    },
                    onClick = { onEvent(ExercisePickerEvent.Dismissed) },
                    modifier = Modifier.padding(
                        horizontal = Metrics.gutter,
                        vertical = Metrics.space3,
                    ),
                )
            }
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
) {
    val shape = RoundedCornerShape(Radius.xs)
    ExerciseRow(
        exercise = exercise,
        modifier = if (selected) {
            Modifier
                .clip(shape)
                .background(VoltDim)
                .border(Metrics.emphasisBorder, Volt, shape)
        } else {
            Modifier
        },
        onClick = onClick,
        tag = LoadTypeCopy.rowTag(exercise),
        subtitle = subtitle,
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
    val badgeShape = RoundedCornerShape(percent = 50)
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
                maxLines = 1,
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
                if (tag != null) InstrumentTag(tag)
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

/** The one accented row in the sheet, and only when what was typed matches nothing. */
@Composable
private fun CreateExerciseRow(
    name: String,
    muscleGroup: String,
    loadType: LoadType,
    onMuscle: (String) -> Unit,
    onLoadType: (LoadType) -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xs)
    val ready = MuscleGroups.resolved(muscleGroup) != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Metrics.gutter),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            items(MuscleGroups.chips, key = { it }) { group ->
                InstrumentChip(
                    label = group,
                    selected = group.equals(muscleGroup, ignoreCase = true),
                    onClick = { onMuscle(group) },
                )
            }
            item(key = "other") {
                InstrumentChip(
                    label = MuscleGroups.OTHER,
                    selected = MuscleGroups.otherSelected(muscleGroup),
                    onClick = { onMuscle(MuscleGroups.OTHER) },
                )
            }
        }
        LoadTypeChipRow(
            selected = loadType,
            onSelect = onLoadType,
            modifier = Modifier.padding(horizontal = Metrics.gutter),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.rowMin)
                .clickable(enabled = ready, onClick = onClick)
                .padding(horizontal = Metrics.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(ThumbSize.row)
                    .clip(shape)
                    .background(Surface1)
                    .border(Metrics.hairline, if (ready) Volt else Hairline, shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = if (ready) Volt else TextTertiary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Text(
                    "Create \"$name\"",
                    style = InstrumentType.title,
                    color = if (ready) Volt else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (ready) {
                        LoadTypeCopy.createCaption(muscleGroup, loadType)
                    } else {
                        MuscleGroups.MISSING_MESSAGE
                    },
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun InstrumentTag(label: String) {
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier = Modifier
            .border(Metrics.hairline, Hairline, shape)
            .padding(horizontal = Metrics.space2, vertical = Metrics.space1),
    ) {
        Kicker(label, color = TextTertiary)
    }
}

/** Enough of the screen that the catalog is the sheet, with the scrim still legible above it. */
private const val SHEET_HEIGHT_SHARE = 0.9f
