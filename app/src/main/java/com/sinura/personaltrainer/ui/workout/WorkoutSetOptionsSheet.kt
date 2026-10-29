package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.Panel
import com.sinura.personaltrainer.ui.components.PrimaryButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/** Secondary recording controls share one scrollable sheet; Done remains reachable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSetOptionsSheet(
    lift: SessionExercise,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface3,
    ) {
        CompositionLocalProvider(LocalDensity provides density, LocalLayoutDirection provides direction) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space3).testTag(WorkoutTestTags.SET_OPTIONS_SHEET)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Set options", Modifier.weight(1f), style = InstrumentType.title, color = TextPrimary)
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.SET_OPTIONS_DONE)) {
                        Text("Done", style = InstrumentType.bodyStrong, color = TextPrimary)
                    }
                }
                Column(Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()).testTag(WorkoutTestTags.SET_OPTIONS_CONTENT),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                        verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(exercise = lift.exercise, size = Metrics.workoutIdentityImage, showBadge = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                            Text(lift.exercise.name, style = InstrumentType.workoutTitle, color = TextPrimary)
                            Text(lift.exercise.equipment.label, style = InstrumentType.caption, color = TextSecondary)
                        }
                    }
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatNextSheet(
    addSetSubLabel: String?,
    swapSubLabel: String?,
    skipSubLabel: String,
    primaryLabel: String,
    showFinish: Boolean,
    rowsEnabled: Boolean,
    onAddSet: () -> Unit,
    onSwap: () -> Unit,
    onSkip: () -> Unit,
    onEditSaved: () -> Unit,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface2,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space6)
                .testTag(WorkoutTestTags.WHAT_NEXT_SHEET),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Text(
                stringResource(R.string.next_title),
                style = InstrumentType.heroTitle,
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.next_planned_done),
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            WhatNextRow(
                title = stringResource(R.string.next_add_set),
                subtitle = addSetSubLabel,
                enabled = rowsEnabled,
                onClick = onAddSet,
                tag = WorkoutTestTags.WHAT_NEXT_ADD_SET,
            )
            WhatNextRow(
                title = stringResource(R.string.next_swap),
                subtitle = swapSubLabel,
                enabled = rowsEnabled,
                onClick = onSwap,
                tag = WorkoutTestTags.WHAT_NEXT_SWAP,
            )
            WhatNextRow(
                title = stringResource(R.string.next_skip),
                subtitle = skipSubLabel,
                enabled = rowsEnabled,
                onClick = onSkip,
                tag = WorkoutTestTags.WHAT_NEXT_SKIP,
            )
            WhatNextRow(
                title = stringResource(R.string.next_edit),
                subtitle = null,
                enabled = rowsEnabled,
                onClick = onEditSaved,
                tag = WorkoutTestTags.WHAT_NEXT_EDIT,
            )
            PrimaryButton(
                label = primaryLabel,
                onClick = onPrimary,
                modifier = Modifier.testTag(
                    if (showFinish) WorkoutTestTags.DOCK_FINISH else WorkoutTestTags.NEXT,
                ),
            )
        }
    }
}

@Composable
private fun WhatNextRow(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Panel(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        onClick = if (enabled) onClick else null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(title, style = InstrumentType.title, color = TextPrimary)
            subtitle?.let {
                Text(it, style = InstrumentType.caption, color = TextSecondary, maxLines = 2)
            }
        }
    }
}
