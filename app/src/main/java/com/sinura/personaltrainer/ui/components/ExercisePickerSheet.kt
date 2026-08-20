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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

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
 * what has been typed matches nothing. Its muscle group is left blank deliberately — the
 * repository classifies a blank as "Other", and the library editor is where a lift's
 * classification is actually chosen, with the whole catalog of groups in front of you.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    query: String,
    results: List<Exercise>,
    onQueryChange: (String) -> Unit,
    onSelect: (Exercise) -> Unit,
    onCreate: (name: String, muscleGroup: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val needle = query.trim()
    val canCreate = needle.isNotEmpty() && results.none { it.name.equals(needle, ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                Text("Add exercise", style = InstrumentType.title, color = TextPrimary)
                ExerciseSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search, or name a new lift",
                )
            }
            HairlineDivider(startIndent = 0.dp)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Metrics.space7),
            ) {
                if (canCreate) {
                    item(key = "create") {
                        Column {
                            CreateExerciseRow(name = needle, onClick = { onCreate(needle, "") })
                            HairlineDivider()
                        }
                    }
                }
                if (results.isEmpty()) {
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
                        Column(modifier = Modifier.animateItem()) {
                            ExerciseRow(
                                name = exercise.name,
                                muscleGroup = exercise.muscleGroup,
                                onClick = { onSelect(exercise) },
                            )
                            if (index < results.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * One lift in a list: image slot, name, muscle group, and whatever the surface does to it.
 *
 * [trailing] is a slot so the library can hang its actions here without the picker growing
 * a set of buttons it has no use for.
 */
@Composable
fun ExerciseRow(
    name: String,
    muscleGroup: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tag: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                start = Metrics.gutter,
                // Icon buttons carry their own 48dp of target, so the trailing edge closes up.
                end = if (trailing != null) Metrics.space2 else Metrics.gutter,
            )
            .padding(vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        ExerciseThumb(name)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                name,
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
                    muscleGroup,
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

/**
 * The 40dp slot every exercise row leads with.
 *
 * The catalog has no imagery yet — it is the audit's first requirement — so reserving the
 * slot now means the rows do not have to be laid out twice. Until then it carries the
 * lift's initial, which at least gives the eye something to run down a long list.
 */
@Composable
private fun ExerciseThumb(name: String) {
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier = Modifier
            .size(THUMB_SIZE)
            .clip(shape)
            .background(Surface1)
            .border(Metrics.hairline, Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = InstrumentType.title,
            color = TextTertiary,
        )
    }
}

/** The one accented row in the sheet, and only when what was typed matches nothing. */
@Composable
private fun CreateExerciseRow(name: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.xs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .clickable(onClick = onClick)
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Box(
            modifier = Modifier
                .size(THUMB_SIZE)
                .clip(shape)
                .background(Surface1)
                .border(Metrics.hairline, Volt, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = Volt)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                "Create \"$name\"",
                style = InstrumentType.title,
                color = Volt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Adds it as a custom lift", style = InstrumentType.caption, color = TextSecondary)
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
private val THUMB_SIZE = 40.dp
