package com.sinura.personaltrainer.ui.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioEntry
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CatalogMeta
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.ComposerCopy
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.domain.DistanceUnit
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.ExercisePickerProgress
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.StrengthEntry
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.RecoverableExercisePickerSheet
import com.sinura.personaltrainer.ui.components.OwnedPickerRecovery
import com.sinura.personaltrainer.ui.components.FieldComplaint
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.fieldError
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.imeAction
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalDistanceUnit
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@Composable
fun ActivityComposerScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ActivityComposerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedId by viewModel.savedId.collectAsStateWithLifecycle()
    val createdExercise by viewModel.createdExercise.collectAsStateWithLifecycle()
    val pickerOpen by viewModel.showPicker.collectAsStateWithLifecycle()
    val pickerQuery by viewModel.query.collectAsStateWithLifecycle()
    val adderFields by viewModel.adderFields.collectAsStateWithLifecycle()
    val privateStatus = viewModel.formRecovery?.status?.collectAsStateWithLifecycle()?.value
    val formEditable = privateStatus?.loading != true && privateStatus?.pending != true && !state.saving
    val unit = LocalWeightUnit.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(savedId) {
        val id = savedId ?: return@LaunchedEffect
        viewModel.onSavedHandled()
        onSaved(id)
    }

    // While a save is in flight its outcome is unknown, so nothing here may discard the draft
    // or pop the route: a popped entry takes the ViewModel — and the write it is waiting on —
    // with it, and "Going back drops this draft" would then be said of work that may already
    // be in Room. Cancel is disabled and Back is swallowed until the write answers; a save
    // takes milliseconds, and the dock's "Saving…" is the whole of the wait.
    val leave: () -> Unit = {
        if (viewModel.canLeave()) {
            if (viewModel.formRecovery != null) {
                viewModel.keepAndLeave(onBack)
            } else if (state.isDirty) {
                confirmLeave = true
            } else {
                viewModel.discardDraft()
                onBack()
            }
        }
    }
    BackHandler(onBack = leave)

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            Column {
                viewModel.formRecovery?.let { recovery ->
                    val progress by recovery.status.collectAsStateWithLifecycle()
                    progress.error?.let { message -> GymErrorBanner(message = message, onRetry = recovery::retry) }
                    if (progress.accepted) Text("Already saved. Return to History to view your session.", style = InstrumentType.caption, color = TextSecondary)
                }
                // Form-level outcomes sit where Save is pressed, not in a banner at the top of
                // a list the thumb has long since scrolled past.
                state.error?.let { message ->
                    GymErrorBanner(
                        message = message,
                        modifier = Modifier.padding(horizontal = Metrics.gutter),
                        onDismiss = viewModel::dismissError,
                    )
                }
                ComposerSaveDock(
                    saving = state.saving,
                    onSave = viewModel::save,
                    onCancel = leave,
                    enabled = privateStatus?.ready != false,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Metrics.gutter),
            contentPadding = PaddingValues(bottom = Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            item {
                Text(composerTitle(state.mode), style = InstrumentType.title, color = TextPrimary)
                Text(
                    ComposerCopy.LOGGED_AT_NOON,
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            }
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth().testTag("composer-title"),
                    label = { Text("Title") },
                    singleLine = true,
                    enabled = formEditable,
                )
            }
            item {
                Kicker("When")
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    TextButton(onClick = { viewModel.shiftDay(-1) }) {
                        Text(ComposerCopy.EARLIER)
                    }
                    Text(
                        DateCopy.weekdayShort(
                            CivilDate.fromEpochDay(state.epochDay).toJavaLocalDate(),
                        ),
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = Metrics.space3),
                    )
                    TextButton(
                        onClick = { viewModel.shiftDay(1) },
                        enabled = state.canShiftLater,
                    ) {
                        Text(
                            ComposerCopy.LATER,
                            color = if (state.canShiftLater) TextPrimary else TextSecondary,
                        )
                    }
                }
            }
            if (state.mode != ComposerMode.CARDIO) {
                item { Kicker("Strength") }
                itemsIndexed(state.strength, key = { index, line -> "${line.exercise.id}-$index" }) { index, line ->
                    InstrumentRow(
                        title = line.exercise.name,
                        subtitle = ComposerCopy.strengthLineSubtitle(line.reps, line.weightKg, unit),
                        trailing = {
                            RemoveLineButton(
                                onClick = { viewModel.removeStrength(index) },
                                tag = ComposerTags.REMOVE_STRENGTH,
                            )
                        },
                    )
                }
                item {
                    StrengthAdder(
                        catalog = state.catalog,
                        onAdd = viewModel::addStrength,
                        onCreate = viewModel::createExercise,
                        created = createdExercise,
                        onCreatedHandled = viewModel::onCreatedExerciseHandled,
                        pickerError = state.createError,
                        onPickerErrorDismissed = viewModel::dismissCreateError,
                        pickerProgress = state.pickerProgress,
                        onPickerRetry = viewModel::retryPicker,
                        canDismissPicker = viewModel::canLeave,
                        recovery = viewModel.pickerRecovery,
                        parentFields = if (viewModel.formRecovery != null) adderFields else null,
                        onParentFields = viewModel::changeAdder,
                        externalPickerOpen = if (viewModel.pickerRecovery != null) pickerOpen else null,
                        externalQuery = if (viewModel.pickerRecovery != null) pickerQuery else null,
                        onExternalPickerVisible = viewModel::setPickerVisible,
                        onExternalSelect = viewModel::selectPickerExercise,
                    )
                }
            }
            if (state.mode != ComposerMode.STRENGTH) {
                item { Kicker("Cardio") }
                itemsIndexed(state.cardio, key = { index, line -> "${line.type}-$index" }) { index, line ->
                    InstrumentRow(
                        title = ComposerCopy.typeChipLabel(line.type),
                        subtitle = ComposerCopy.cardioLineSubtitle(
                            line.minutes,
                            line.distanceKm,
                            line.indoor,
                            LocalDistanceUnit.current,
                        ),
                        trailing = {
                            RemoveLineButton(
                                onClick = { viewModel.removeCardio(index) },
                                tag = ComposerTags.REMOVE_CARDIO,
                            )
                        },
                    )
                }
                item { CardioAdder(onAdd = viewModel::addCardio) }
            }
            if (viewModel.formRecovery != null) item {
                SecondaryGymButton(text = "Discard draft", onClick = { confirmLeave = true }, enabled = viewModel.canLeave())
            }
        }
    }

    if (confirmLeave) {
        ConfirmActionDialog(
            title = ComposerCopy.LEAVE_TITLE,
            body = if (viewModel.formRecovery != null) "Discard unfinished fields on this device. Saved sessions stay in History." else ComposerCopy.LEAVE_BODY,
            confirmLabel = ComposerCopy.LEAVE,
            dismissLabel = ComposerCopy.KEEP_EDITING,
            onConfirm = {
                if (viewModel.canLeave()) {
                    confirmLeave = false
                    if (viewModel.formRecovery != null) viewModel.discardAndLeave(onBack)
                    else { viewModel.discardDraft(); onBack() }
                }
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

/**
 * Pinned Save, same job as strength Log and live-cardio Finish.
 * This route hides the tab bar, so the dock owns the system-nav inset.
 */
@Composable
internal fun ComposerSaveDock(
    saving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean = true,
) {
    PinnedDock(
        volt = {
            PrimaryGymButton(
                text = if (saving) ComposerCopy.SAVING else ComposerCopy.SAVE,
                onClick = onSave,
                modifier = Modifier.testTag(ComposerTags.SAVE),
                enabled = !saving && enabled,
            )
        },
        secondary = {
            // Disabled, not hidden, while saving: the control stays where the thumb expects it
            // and the disabled ink says why it will not fire.
            TextButton(
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.testTag(ComposerTags.CANCEL),
            ) {
                Text(
                    ComposerCopy.CANCEL,
                    style = InstrumentType.bodyStrong,
                    color = if (saving) TextDisabled else TextSecondary,
                )
            }
        },
    )
}

@Composable
internal fun StrengthAdder(
    catalog: List<Exercise>,
    onAdd: (Exercise, Double, Int) -> Unit,
    onCreate: (String, String, LoadType) -> Unit = { _, _, _ -> },
    created: Exercise? = null,
    onCreatedHandled: () -> Unit = {},
    /** A Create-row failure, shown inside the sheet that asked — a banner behind a modal is invisible. */
    pickerError: String? = null,
    onPickerErrorDismissed: () -> Unit = {},
    pickerProgress: ExercisePickerProgress = ExercisePickerProgress(),
    onPickerRetry: () -> Unit = {},
    canDismissPicker: () -> Boolean = { !pickerProgress.busy },
    recovery: OwnedPickerRecovery? = null,
    parentFields: StrengthAdderFields? = null,
    onParentFields: (StrengthAdderFields) -> Unit = {},
    externalPickerOpen: Boolean? = null,
    externalQuery: String? = null,
    onExternalPickerVisible: (Boolean) -> Unit = {},
    onExternalSelect: (Exercise) -> Unit = {},
) {
    val unit = LocalWeightUnit.current
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var pickerQuery by rememberSaveable { mutableStateOf("") }
    var pickedId by rememberSaveable { mutableStateOf(catalog.firstOrNull()?.id.orEmpty()) }
    var weight by rememberSaveable { mutableStateOf("0") }
    var reps by rememberSaveable { mutableStateOf("8") }
    val entryUnit = parentFields?.unit ?: unit
    LaunchedEffect(parentFields) {
        parentFields?.let { pickedId = it.pickedId; weight = it.weight; reps = it.reps }
    }
    // Set by Add set when a box cannot be read as written; cleared by the next keystroke in
    // that box. The text itself is never rewritten — see NumericEntry.
    var weightError by rememberSaveable { mutableStateOf<String?>(null) }
    var repsError by rememberSaveable { mutableStateOf<String?>(null) }
    val view = LocalView.current
    LaunchedEffect(catalog) {
        if (pickedId.isEmpty()) {
            pickedId = catalog.firstOrNull()?.id.orEmpty()
            if (parentFields != null) onParentFields(StrengthAdderFields(pickedId, weight, reps, entryUnit))
        }
    }
    // A created lift is picked like a tapped one; the sheet closes on it.
    LaunchedEffect(created) {
        val lift = created ?: return@LaunchedEffect
        pickedId = lift.id
        pickerQuery = ""
        pickerOpen = false
        onCreatedHandled()
    }
    val exercise = catalog.firstOrNull { it.id == pickedId }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        SecondaryGymButton(
            text = ComposerCopy.CHOOSE_LIFT,
            onClick = { if (externalPickerOpen != null) onExternalPickerVisible(true) else pickerOpen = true },
            modifier = Modifier.testTag(ComposerTags.CHOOSE_LIFT),
        )
        if (catalog.isEmpty()) {
            Text(ComposerCopy.NO_LIFTS, style = InstrumentType.caption, color = TextSecondary)
        } else {
            Text(
                exercise?.name ?: ComposerCopy.CHOOSE_LIFT,
                style = InstrumentType.bodyStrong,
                color = TextPrimary,
            )
            val strengthChain = NumericEntry.COMPOSER_STRENGTH_CHAIN
            val weightFocus = remember { FocusRequester() }
            val repsFocus = remember { FocusRequester() }
            OutlinedTextField(
                value = weight,
                onValueChange = {
                    weight = it
                    weightError = null
                    if (parentFields != null) onParentFields(StrengthAdderFields(pickedId, weight, reps, entryUnit))
                },
                label = { Text(ComposerCopy.weightFieldLabel(entryUnit)) },
                singleLine = true,
                isError = weightError != null,
                supportingText = complaintSlot(weightError),
                textStyle = InstrumentType.numeralMd,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = strengthChain[0].imeAction(),
                ),
                keyboardActions = KeyboardActions(onNext = { repsFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(weightFocus)
                    .fieldError(weightError),
            )
            OutlinedTextField(
                value = reps,
                onValueChange = {
                    reps = it
                    repsError = null
                    if (parentFields != null) onParentFields(StrengthAdderFields(pickedId, weight, reps, entryUnit))
                },
                label = { Text(ComposerCopy.REPS) },
                singleLine = true,
                isError = repsError != null,
                supportingText = complaintSlot(repsError),
                textStyle = InstrumentType.numeralMd,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = strengthChain[1].imeAction(),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(repsFocus)
                    .fieldError(repsError),
            )
            SecondaryGymButton(
                text = ComposerCopy.ADD_SET,
                onClick = {
                    val lift = exercise ?: return@SecondaryGymButton
                    when (val entry = ComposerCopy.strengthEntry(weight, reps, entryUnit)) {
                        is StrengthEntry.ReadySet -> {
                            weightError = null
                            repsError = null
                            onAdd(lift, entry.weightKg, entry.reps)
                        }
                        is StrengthEntry.RefusedSet -> {
                            // The text stays exactly as typed; the complaint goes under the
                            // box that earned it and focus moves there, so the fix is one
                            // keystroke away instead of a banner somewhere above.
                            weightError = entry.weightError
                            repsError = entry.repsError
                            Haptics.reject(view)
                            if (entry.weightError != null) {
                                weightFocus.requestFocus()
                            } else {
                                repsFocus.requestFocus()
                            }
                        }
                    }
                },
                enabled = exercise != null,
                modifier = Modifier.testTag(ComposerTags.ADD_SET),
            )
        }
    }
    if (externalPickerOpen ?: pickerOpen) {
        RecoverableExercisePickerSheet(
            recovery = recovery,
            canDismiss = canDismissPicker,
            state = ExercisePickerState(
                query = externalQuery ?: pickerQuery,
                results = filterCatalog(catalog, externalQuery ?: pickerQuery),
                title = ComposerCopy.PICKER_TITLE,
                mode = ExercisePickerMode.SINGLE_ADD,
                catalog = catalog,
                error = pickerError,
                progress = pickerProgress,
            ),
            onEvent = { event ->
                when (event) {
                    is ExercisePickerEvent.QueryChanged -> pickerQuery = event.query
                    is ExercisePickerEvent.Selected -> {
                        if (!canDismissPicker()) return@RecoverableExercisePickerSheet
                        onPickerErrorDismissed()
                        if (externalPickerOpen != null) { onExternalSelect(event.exercise); return@RecoverableExercisePickerSheet }
                        pickedId = event.exercise.id
                        pickerQuery = ""
                        pickerOpen = false
                    }
                    is ExercisePickerEvent.Created ->
                        onCreate(event.name, event.muscleGroup, event.loadType)
                    // Multi-add taps are written as they land (#201), so SINGLE_ADD never
                    // sees a Toggled; the arm is here only to keep the when exhaustive.
                    is ExercisePickerEvent.Toggled -> Unit
                    ExercisePickerEvent.ErrorDismissed -> onPickerErrorDismissed()
                    ExercisePickerEvent.Retry -> onPickerRetry()
                    ExercisePickerEvent.Dismissed -> {
                        if (!canDismissPicker()) return@RecoverableExercisePickerSheet
                        if (externalPickerOpen != null) { onExternalPickerVisible(false); return@RecoverableExercisePickerSheet }
                        pickerQuery = ""
                        pickerOpen = false
                    }
                }
            },
        )
    }
}

private typealias ExerciseOption = Exercise

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioAdder(
    onAdd: (CardioType, Int, Double?, Boolean) -> Unit,
) {
    val liveDistance = LocalDistanceUnit.current
    var typedDistanceUnit by rememberSaveable { mutableStateOf<String?>(null) }
    val distanceUnit = DistanceUnit.fromStorage(typedDistanceUnit) ?: liveDistance
    var type by rememberSaveable { mutableStateOf(CardioType.RUN) }
    var indoor by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var distance by rememberSaveable { mutableStateOf("") }
    var minutesError by rememberSaveable { mutableStateOf<String?>(null) }
    var distanceError by rememberSaveable { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val cardioChain = NumericEntry.COMPOSER_CARDIO_CHAIN
    val minutesFocus = remember { FocusRequester() }
    val distanceFocus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            CardioType.entries.forEach { option ->
                InstrumentChip(
                    label = ComposerCopy.typeChipLabel(option),
                    selected = option == type,
                    onClick = { type = option },
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            InstrumentChip(
                label = CardioCopy.INDOOR,
                selected = indoor,
                onClick = { indoor = true },
            )
            InstrumentChip(
                label = CardioCopy.OUTDOOR,
                selected = !indoor,
                onClick = { indoor = false },
            )
        }
        OutlinedTextField(
            value = minutes,
            onValueChange = {
                minutes = it
                minutesError = null
            },
            label = { Text(ComposerCopy.MINUTES) },
            singleLine = true,
            isError = minutesError != null,
            supportingText = complaintSlot(minutesError),
            textStyle = InstrumentType.numeralMd,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = cardioChain[0].imeAction(),
            ),
            keyboardActions = KeyboardActions(onNext = { distanceFocus.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(minutesFocus)
                .fieldError(minutesError),
        )
        OutlinedTextField(
            value = distance,
            onValueChange = {
                distance = it
                typedDistanceUnit = if (it.isBlank()) null else distanceUnit.storageKey
                distanceError = null
            },
            label = { Text(CardioCopy.distanceLabel(distanceUnit)) },
            singleLine = true,
            isError = distanceError != null,
            supportingText = complaintSlot(distanceError),
            textStyle = InstrumentType.numeralMd,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = cardioChain[1].imeAction(),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(distanceFocus)
                .fieldError(distanceError),
        )
        SecondaryGymButton(
            text = ComposerCopy.ADD_CARDIO,
            onClick = {
                when (val entry = ComposerCopy.cardioEntry(minutes, distance, distanceUnit)) {
                    is CardioEntry.ReadyCardio -> {
                        minutesError = null
                        distanceError = null
                        onAdd(type, entry.minutes, entry.distanceKm, indoor)
                    }
                    is CardioEntry.RefusedCardio -> {
                        minutesError = entry.minutesError
                        distanceError = entry.distanceError
                        Haptics.reject(view)
                        if (entry.minutesError != null) {
                            minutesFocus.requestFocus()
                        } else {
                            distanceFocus.requestFocus()
                        }
                    }
                }
            },
            modifier = Modifier.testTag(ComposerTags.ADD_CARDIO),
        )
    }
}

/**
 * The complaint under a numeric box, or nothing. A slot rather than an always-present caption,
 * so a clean form has no empty line reserved under every field.
 */
private fun complaintSlot(message: String?): (@Composable () -> Unit)? =
    message?.let { text -> { FieldComplaint(text) } }

@Composable
private fun RemoveLineButton(
    onClick: () -> Unit,
    tag: String,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    ) {
        Text(ComposerCopy.REMOVE, style = InstrumentType.bodyStrong, color = Danger)
    }
}

private fun filterCatalog(catalog: List<Exercise>, query: String): List<Exercise> {
    val needle = query.trim()
    if (needle.isEmpty()) return catalog
    return catalog.filter { lift ->
        lift.name.contains(needle, ignoreCase = true) ||
            CatalogMeta.matchesSearchTerms(needle, lift.id)
    }
}

private fun composerTitle(mode: ComposerMode): String = when (mode) {
    ComposerMode.STRENGTH -> "Log a past workout"
    ComposerMode.CARDIO -> "Log cardio"
    ComposerMode.MIXED -> "Log a mixed session"
}

private fun CivilDate.toJavaLocalDate(): java.time.LocalDate =
    java.time.LocalDate.ofEpochDay(epochDay)

object ComposerTags {
    const val SAVE = "activity-composer-save"
    const val CANCEL = "activity-composer-cancel"
    const val ADD_SET = "activity-composer-add-set"
    const val ADD_CARDIO = "activity-composer-add-cardio"
    const val CHOOSE_LIFT = "activity-composer-choose-lift"
    const val REMOVE_STRENGTH = "activity-composer-remove-strength"
    const val REMOVE_CARDIO = "activity-composer-remove-cardio"
}
