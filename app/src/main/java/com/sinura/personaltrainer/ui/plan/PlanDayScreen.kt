package com.sinura.personaltrainer.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SlotRuleImport
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.sessionLiftNames
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextSecondary
import java.time.LocalDate

/**
 * One weekday as a schedule page. Add and delete blocks here. Start lives
 * on Home (ADR-015).
 */
@Composable
fun PlanDayScreen(
    epochDay: Long,
    startInAdd: Boolean,
    onBack: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    viewModel: PlanDayViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateToEditor by viewModel.navigateToEditor.collectAsStateWithLifecycle()
    val today = LocalTodayEpochDay.current
    val isPast = epochDay < today
    val weekday = Weekday.fromEpochDay(epochDay)
    val names = remember(state.routines) { state.routines.associate { it.id to it.name } }
    val occurrences = remember(epochDay, state.occurrences, state.rules, names) {
        DailyAgenda.forDay(epochDay, state.occurrences, state.rules, names)
    }
    val pinned = remember(weekday, state.rules) {
        state.rules.any { it.weekday == weekday && SlotRuleImport.isImportedSlotRule(it.id) }
    }
    var picking by rememberSaveable(epochDay) {
        mutableStateOf(if (startInAdd && !isPast) DayPicker.KIND else DayPicker.NONE)
    }
    var pendingRemoveRuleId by rememberSaveable(epochDay) { mutableStateOf<String?>(null) }
    var pendingRemoveTitle by rememberSaveable(epochDay) { mutableStateOf("") }
    var seenCompletedAdd by rememberSaveable(epochDay) { mutableStateOf(state.completedAdd) }
    val scroll = rememberScrollState()
    val actionsEnabled = !state.isSaving && !state.canRetryWrite

    BackHandler(enabled = state.isSaving) { /* Finish the accepted write before leaving. */ }
    LaunchedEffect(state.completedAdd) {
        if (state.completedAdd > seenCompletedAdd) picking = DayPicker.NONE
        seenCompletedAdd = state.completedAdd
    }
    LaunchedEffect(state.error) {
        if (state.error != null) scroll.scrollTo(0)
    }

    LaunchedEffect(navigateToEditor) {
        val id = navigateToEditor ?: return@LaunchedEffect
        onOpenRoutine(id)
        viewModel.onEditorNavigationHandled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        PlanDayHeader(
            title = PlanDayCopy.weekdayTitle(weekday),
            dateCaption = DateCopy.weekdayFullDate(LocalDate.ofEpochDay(epochDay)),
            onBack = onBack,
            backEnabled = !state.isSaving,
            weeklyScope = if (isPast) null else PlanDayCopy.addScope(weekday),
        )
        when {
            state.isLoading -> ScreenLoading()
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scroll)
                        .padding(horizontal = Metrics.gutter)
                        .padding(bottom = Metrics.space4),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    state.error?.let { error ->
                        if (state.canRetryWrite) {
                            Column(
                                modifier = Modifier.testTag(PlanDayTags.WRITE_ERROR)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                            ) {
                                Text(error, style = InstrumentType.bodyStrong, color = Danger)
                                Text(PlanDayCopy.RETRY_SCOPE, style = InstrumentType.caption, color = TextSecondary)
                            }
                        } else {
                            GymErrorBanner(error, onDismiss = viewModel::dismissError)
                        }
                    }
                    if (isPast) {
                        Text(
                            PlanDayCopy.PAST,
                            style = InstrumentType.caption,
                            color = TextSecondary,
                        )
                    }
                    if (occurrences.isEmpty() && picking == DayPicker.NONE) {
                        EmptyState(
                            scene = EmptyScene.PLAN,
                            title = PlanDayCopy.EMPTY,
                            body = PlanDayCopy.EMPTY_BODY,
                            compact = true,
                        )
                    } else if (occurrences.isNotEmpty()) {
                        SessionBlocks(
                            occurrences = occurrences,
                            routines = state.routines,
                            isPast = isPast,
                            enabled = actionsEnabled,
                            onOpenRoutine = onOpenRoutine,
                            onRemove = { ruleId, title ->
                                pendingRemoveRuleId = ruleId
                                pendingRemoveTitle = title
                            },
                            onMove = { occurrenceId, delta ->
                                viewModel.moveDayBlock(occurrences, occurrenceId, delta)
                            },
                        )
                    }
                    if (!isPast && picking != DayPicker.NONE) {
                        DayAddPicker(
                            picking = picking,
                            weekday = weekday,
                            hasStrength = pinned,
                            occurrences = occurrences,
                            routines = state.routines,
                            askKeep = false,
                            enabled = actionsEnabled,
                            onPickKind = { picking = it },
                            onCancel = { picking = DayPicker.NONE },
                            onAddWorkout = { routineId, _ ->
                                if (pinned) {
                                    viewModel.addLaterSession(epochDay, routineId)
                                } else {
                                    viewModel.pinRoutine(epochDay, routineId)
                                }
                            },
                            onNewWorkout = { _ ->
                                if (pinned) {
                                    viewModel.composeLaterSession(epochDay)
                                } else {
                                    viewModel.buildDay(epochDay)
                                }
                            },
                            onAddCardio = { type, _ ->
                                viewModel.addCardio(epochDay, type)
                            },
                            onAddAux = { packId, _ ->
                                viewModel.addAuxiliary(epochDay, packId)
                            },
                            suggestedKit = state.suggestedExtraEquipment,
                        )
                    }
                }
                if (state.isSaving) {
                    Text(
                        PlanDayCopy.UPDATING,
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = Metrics.gutter, vertical = Metrics.space3)
                            .testTag(PlanDayTags.SAVING)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else if (state.canRetryWrite) {
                    PrimaryGymButton(
                        text = PlanDayCopy.RETRY_UPDATE,
                        onClick = viewModel::retryWrite,
                        modifier = Modifier.padding(horizontal = Metrics.gutter)
                            .padding(bottom = Metrics.space5).testTag(PlanDayTags.RETRY),
                    )
                } else if (!isPast && picking == DayPicker.NONE) {
                    PrimaryGymButton(
                        text = PlanDayCopy.ADD_SESSION,
                        onClick = { picking = DayPicker.KIND },
                        modifier = Modifier
                            .padding(horizontal = Metrics.gutter)
                            .padding(bottom = Metrics.space5)
                            .testTag(PlanDayTags.ADD)
                            .semantics { contentDescription = PlanDayCopy.ADD_SESSION },
                    )
                }
            }
        }
    }

    pendingRemoveRuleId?.let { ruleId ->
        val rule = state.rules.firstOrNull { it.id == ruleId }
        ConfirmActionDialog(
            title = PlanDayCopy.removeTitle(pendingRemoveTitle),
            body = PlanDayCopy.removeBody(
                day = rule?.weekday ?: weekday,
                recurring = rule?.enabled == true,
            ),
            confirmLabel = PlanDayCopy.REMOVE,
            destructive = true,
            confirmEnabled = actionsEnabled,
            onConfirm = {
                viewModel.deleteSession(epochDay, ruleId)
                pendingRemoveRuleId = null
            },
            onDismiss = { pendingRemoveRuleId = null },
        )
    }
}

@Composable
internal fun PlanDayHeader(
    title: String,
    dateCaption: String,
    onBack: () -> Unit,
    weeklyScope: String? = null,
    backEnabled: Boolean = true,
) {
    Column {
        ScreenHeader(
            title = title,
            subtitle = dateCaption,
            onBack = onBack,
            backEnabled = backEnabled,
            backTag = PlanDayTags.BACK,
        )
        weeklyScope?.let { scope ->
            Text(
                text = scope,
                style = InstrumentType.caption,
                color = TextSecondary,
                modifier = Modifier
                    .padding(horizontal = Metrics.gutter)
                    .padding(bottom = Metrics.space2)
                    .testTag(PlanDayTags.SCOPE),
            )
        }
    }
}

@Composable
private fun SessionBlocks(
    occurrences: List<AgendaItem>,
    routines: List<Routine>,
    isPast: Boolean,
    enabled: Boolean,
    onOpenRoutine: (String) -> Unit,
    onRemove: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    GroupedList {
        occurrences.forEachIndexed { index, item ->
            if (index > 0) HairlineDivider()
            val ruleId = item.rule?.id
            val routineId = item.rule?.routineId
            val names = sessionLiftNames(routineId, routines)
            val modality = item.rule?.modality ?: ScheduleModality.STRENGTH
            val canEdit = routineId != null &&
                modality == ScheduleModality.STRENGTH
            Column(
                modifier = Modifier.padding(bottom = if (!isPast && ruleId != null) Metrics.space3 else 0.dp),
            ) {
                InstrumentRow(
                    title = item.title,
                    modifier = Modifier.testTag(PlanDayTags.block(item.occurrence.id)),
                    subtitle = SessionOrderCopy.occurrenceLine(
                        item.occurrence.status,
                        names,
                        modality,
                    ),
                    onClick = if (canEdit) {
                        { onOpenRoutine(routineId) }
                    } else {
                        null
                    },
                    enabled = enabled,
                    trailing = if (!isPast && ruleId != null) {
                        {
                            TextButton(
                                onClick = { onRemove(ruleId, item.title) },
                                enabled = enabled,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .heightIn(min = Metrics.touchMin)
                                    .testTag(PlanDayTags.remove(ruleId)),
                            ) {
                                Text(
                                    PlanDayCopy.REMOVE,
                                    style = InstrumentType.bodyStrong,
                                    color = Danger,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                if (!isPast && occurrences.size > 1) {
                    ReorderRow(
                        title = item.title,
                        occurrenceId = item.occurrence.id,
                        index = index,
                        lastIndex = occurrences.lastIndex,
                        onMove = onMove,
                        enabled = enabled,
                        modifier = Modifier.padding(horizontal = Metrics.space4),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReorderRow(
    title: String,
    occurrenceId: String,
    index: Int,
    lastIndex: Int,
    onMove: (String, Int) -> Unit,
    // Plain Modifier, per lint's ModifierParameter rule: the list-row inset is the
    // caller's, so a block that already has card padding does not pay it twice.
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        if (index > 0) {
            TextButton(
                onClick = { onMove(occurrenceId, -1) },
                enabled = enabled,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .testTag(PlanDayTags.moveUp(occurrenceId))
                    .semantics { contentDescription = PlanDayCopy.moveUp(title) },
            ) {
                Text(
                    PlanDayCopy.UP,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
        }
        if (index < lastIndex) {
            TextButton(
                onClick = { onMove(occurrenceId, 1) },
                enabled = enabled,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .testTag(PlanDayTags.moveDown(occurrenceId))
                    .semantics { contentDescription = PlanDayCopy.moveDown(title) },
            ) {
                Text(
                    PlanDayCopy.DOWN,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
        }
    }
}

object PlanDayTags {
    const val BACK = "plan-day-back"
    const val ADD = "plan-day-add"
    const val SCOPE = "plan-day-scope"
    const val SAVING = "plan-day-saving"
    const val WRITE_ERROR = "plan-day-write-error"
    const val RETRY = "plan-day-retry"

    fun block(occurrenceId: String): String = "plan-day-block-$occurrenceId"

    fun remove(ruleId: String): String = "plan-day-remove-$ruleId"

    fun moveUp(occurrenceId: String): String = "plan-move-up-$occurrenceId"

    fun moveDown(occurrenceId: String): String = "plan-move-down-$occurrenceId"
}
