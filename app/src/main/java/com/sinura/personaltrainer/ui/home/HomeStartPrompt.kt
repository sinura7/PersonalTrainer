package com.sinura.personaltrainer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.estimatedSessionMinutes
import com.sinura.personaltrainer.ui.components.ActionState
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GymChip
import com.sinura.personaltrainer.ui.components.GymPickerList
import com.sinura.personaltrainer.ui.start.GymPickMode
import com.sinura.personaltrainer.ui.start.GymPickSheet
import com.sinura.personaltrainer.ui.workout.WorkoutPreview
import com.sinura.personaltrainer.ui.workout.WorkoutPreviewScreen
import com.sinura.personaltrainer.ui.workout.PreviewWeights

/**
 * One prompt in front of every Home start. Strength routines show the preview (D6) with a required
 * gym pick (D5) before weights load and Begin is enabled.
 */
@Composable
internal fun HomeStartPrompt(
    routine: Routine?,
    unit: WeightUnit,
    loadWeights: suspend (Routine, String) -> PreviewWeights,
    gyms: List<Gym>,
    gymVisits: Map<String?, GymVisit>,
    pickedGymId: String?,
    onPickGym: (String) -> Unit,
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
    var weights by remember(routine.id, pickedGymId) { mutableStateOf<PreviewWeights?>(null) }
    var showGymSheet by remember { mutableStateOf(false) }
    val pickedGym = gyms.firstOrNull { it.id == pickedGymId }
    LaunchedEffect(routine.id, pickedGymId) {
        weights = if (pickedGymId != null) loadWeights(routine, pickedGymId) else null
    }
    val model = WorkoutPreview.build(
        title = routine.name,
        routine = routine,
        minutes = estimatedSessionMinutes(routine),
        lastWeightKg = weights?.lastKg,
        unit = unit,
        suggestedWeightKg = weights?.suggestedKg.orEmpty(),
    )
    val leave = { if (canDismiss()) onDismiss() }
    val edit = {
        if (canDismiss()) {
            onDismiss()
            onEditRoutine(routine.id)
        }
    }
    val gymReady = pickedGymId != null
  WorkoutPreviewScreen(
        model = model,
        unit = unit,
        onBegin = { if (canConfirm() && gymReady) onConfirm() },
        onBack = leave,
        backEnabled = dismissEnabled,
        onReorder = edit,
        onAddLift = edit,
        beginState = when {
            pending -> ActionState.Loading
            !confirmEnabled || !gymReady -> ActionState.Disabled
            else -> ActionState.Enabled
        },
        beginLabel = confirmLabel.takeIf { labelOverridden },
        contextContent = {
            contextContent()
            if (pickedGym == null) {
                GymPickerList(
                    gyms = gyms.filterNot { it.archived },
                    visits = gymVisits,
                    selectedId = null,
                    onPick = onPickGym,
                )
            } else {
                GymChip(gym = pickedGym, onClick = { showGymSheet = true })
            }
        },
        actionKey = actionKey,
        gymShortName = pickedGym?.shortName,
        onChangeGym = { showGymSheet = true },
    )
    GymPickSheet(
        visible = showGymSheet,
        mode = GymPickMode.SELECT_ONLY,
        title = title,
        caption = null,
        gyms = gyms.filterNot { it.archived },
        visits = gymVisits,
        selectedId = pickedGymId,
        pendingGymId = null,
        loading = false,
        error = null,
        onRetry = {},
        onPick = { id ->
            showGymSheet = false
            onPickGym(id)
        },
        onCancel = { showGymSheet = false },
    )
}
