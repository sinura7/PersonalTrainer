package com.sinura.personaltrainer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.estimatedSessionMinutes
import com.sinura.personaltrainer.ui.components.ActionState
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.workout.WorkoutPreview
import com.sinura.personaltrainer.ui.workout.WorkoutPreviewScreen

/**
 * One prompt in front of every Home start. A strength routine opens the pre-workout preview
 * (screen 06, D6); anything else keeps the plain confirm. Both call the same [onConfirm], with
 * the same [canConfirm] guard, so Begin is the existing single start path and never a second one.
 */
@Composable
internal fun HomeStartPrompt(
    routine: Routine?,
    unit: WeightUnit,
    loadLastWeights: suspend (Routine) -> Map<String, Double>,
    onEditRoutine: (String) -> Unit,
    title: String,
    body: String,
    confirmLabel: String,
    pending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean,
    dismissEnabled: Boolean,
    canDismiss: () -> Boolean,
    canConfirm: () -> Boolean,
    contextContent: @Composable () -> Unit,
    actionKey: Any?,
    labelOverridden: Boolean,
) {
    if (routine == null) {
        ConfirmActionDialog(
            title = title, body = body, confirmLabel = confirmLabel, onConfirm = onConfirm,
            onDismiss = onDismiss, confirmEnabled = confirmEnabled, dismissEnabled = dismissEnabled,
            canDismiss = canDismiss, canConfirm = canConfirm, contextContent = contextContent,
            actionKey = actionKey,
        )
        return
    }
    var lastWeights by remember(routine.id) { mutableStateOf<Map<String, Double>?>(null) }
    LaunchedEffect(routine.id) { lastWeights = loadLastWeights(routine) }
    val model = WorkoutPreview.build(
        title = routine.name,
        routine = routine,
        minutes = estimatedSessionMinutes(routine),
        lastWeightKg = lastWeights,
        unit = unit,
    )
    val leave = { if (canDismiss()) onDismiss() }
    val edit = {
        if (canDismiss()) {
            onDismiss()
            onEditRoutine(routine.id)
        }
    }
    WorkoutPreviewScreen(
        model = model,
        unit = unit,
        onBegin = { if (canConfirm()) onConfirm() },
        onBack = leave,
        backEnabled = dismissEnabled,
        onReorder = edit,
        onAddLift = edit,
        beginState = when {
            pending -> ActionState.Loading
            !confirmEnabled -> ActionState.Disabled
            else -> ActionState.Enabled
        },
        beginLabel = confirmLabel.takeIf { labelOverridden },
        contextContent = contextContent,
        actionKey = actionKey,
    )
}
