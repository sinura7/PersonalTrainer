package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.AuxiliaryBlocks
import com.sinura.personaltrainer.data.repository.DayBlocks
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.DayBlockOrder
import com.sinura.personaltrainer.domain.ExtraEquipment
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.SlotRuleImport
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/PlanDayVM"

data class PlanDayUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSaving: Boolean = false,
    val canRetryWrite: Boolean = false,
    val completedAdd: Long = 0,
    val routines: List<Routine> = emptyList(),
    val occurrences: List<ScheduleOccurrence> = emptyList(),
    val rules: List<ScheduleRule> = emptyList(),
    val suggestedExtraEquipment: ExtraEquipment = ExtraEquipment.MIXED,
)

private data class PlanDayWriteState(
    val error: String? = null,
    val saving: Boolean = false,
    val canRetry: Boolean = false,
    val completedAdd: Long = 0,
)

private class PendingPlanWrite(
    val failureMessage: String,
    val isAdd: Boolean,
    val retryable: Boolean,
    val onSuccess: () -> Unit,
    val acceptedAt: CapturedCivilTime,
    val block: suspend (CapturedCivilTime) -> Unit,
)

/**
 * One weekday's schedule page. Does not run the Plan tab's insights
 * pipeline — opening Tuesday used to construct a second [PlanViewModel].
 */
class PlanDayViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val writeState = MutableStateFlow(PlanDayWriteState())
    private var pendingWrite: PendingPlanWrite? = null
    private val _navigateToEditor = MutableStateFlow<String?>(null)
    val navigateToEditor: StateFlow<String?> = _navigateToEditor.asStateFlow()

    val uiState: StateFlow<PlanDayUiState> = combine(
        container.plannerRepository.observeOccurrences(),
        container.plannerRepository.observeRules(),
        container.routineRepository.observeAll(),
        writeState,
        container.preferencesRepository.coachPreferences,
    ) { occurrences, rules, routines, write, coach ->
        PlanDayUiState(
            isLoading = false,
            error = write.error,
            isSaving = write.saving,
            canRetryWrite = write.canRetry,
            completedAdd = write.completedAdd,
            routines = routines,
            occurrences = occurrences,
            rules = rules,
            suggestedExtraEquipment = ExtraEquipment.fromPreferences(coach.availableEquipment),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanDayUiState(),
    )

    fun onEditorNavigationHandled() {
        _navigateToEditor.value = null
    }

    fun agendaFor(epochDay: Long): List<AgendaItem> {
        val names = uiState.value.routines.associate { it.id to it.name }
        return DailyAgenda.forDay(epochDay, uiState.value.occurrences, uiState.value.rules, names)
    }

    fun dismissError() {
        if (writeState.value.saving || pendingWrite != null) return
        writeState.value = writeState.value.copy(error = null)
    }

    fun retryWrite() {
        if (!writeState.value.canRetry || writeState.value.saving) return
        runPendingWrite()
    }

    fun pinRoutine(epochDay: Long, routineId: String, hour: Int = SlotRuleImport.DEFAULT_STRENGTH_HOUR) {
        var acceptedSlotId: String? = null
        var acceptedHour: Int? = null
        write("Could not finish adding this workout.", isAdd = true, retryable = true) { acceptedAt ->
            val clamped = acceptedHour ?: hourAtAcceptance(epochDay, hour, acceptedAt).also { acceptedHour = it }
            if (acceptedSlotId == null) {
                acceptedSlotId = container.scheduleRepository.pin(
                    routineId = routineId,
                    focusKind = null,
                    anchorDay = dayOfWeekFor(epochDay),
                ).id
            }
            refreshPlanner(epochDay)
            applyHourToSlot(epochDay, checkNotNull(acceptedSlotId), clamped)
            finishWorkoutDay(epochDay, SlotRuleImport.ruleIdForSlot(checkNotNull(acceptedSlotId)), acceptedAt)
        }
    }

    fun buildDay(epochDay: Long, hour: Int = SlotRuleImport.DEFAULT_STRENGTH_HOUR) {
        var acceptedRoutineId: String? = null
        var acceptedSlotId: String? = null
        var acceptedHour: Int? = null
        write(
            "Could not finish creating this workout.", isAdd = true, retryable = true,
            onSuccess = { _navigateToEditor.value = acceptedRoutineId },
        ) { acceptedAt ->
            val weekday = dayOfWeekFor(epochDay)
            val routineId = acceptedRoutineId ?: run {
                val name = CustomWeekPolicy.routineName(weekday)
                val pinnedIds = container.scheduleRepository.slots().mapNotNull { it.routineId }.toSet()
                val reusable = container.routineRepository.observeAll().first().firstOrNull { routine ->
                    routine.name.equals(name, ignoreCase = true) && routine.id !in pinnedIds
                }
                (reusable?.id ?: container.routineRepository.create(name).id).also { acceptedRoutineId = it }
            }
            val clamped = acceptedHour ?: hourAtAcceptance(epochDay, hour, acceptedAt).also { acceptedHour = it }
            if (acceptedSlotId == null) {
                acceptedSlotId = container.scheduleRepository.pin(
                    routineId = routineId,
                    focusKind = null,
                    anchorDay = weekday,
                ).id
            }
            refreshPlanner(epochDay)
            applyHourToSlot(epochDay, checkNotNull(acceptedSlotId), clamped)
            finishWorkoutDay(epochDay, SlotRuleImport.ruleIdForSlot(checkNotNull(acceptedSlotId)), acceptedAt)
        }
    }

    fun addCardio(epochDay: Long, type: CardioType, hour: Int = SlotRuleImport.DEFAULT_CARDIO_HOUR) {
        write("Could not add cardio. Check this day before trying again.", isAdd = true) {
            DayBlocks.addCardio(
                planner = container.plannerRepository,
                preferences = container.preferencesRepository,
                epochDay = epochDay,
                type = type,
                once = false,
                todayEpochDay = todayEpochDay(),
                nowMinutes = currentMinutesOfDay(),
                preferredHour = hour,
            )
            refreshPlanner(epochDay)
        }
    }

    fun addLaterSession(epochDay: Long, routineId: String, hour: Int? = null) {
        var acceptedRuleId: String? = null
        var acceptedHour: Int? = null
        write("Could not finish adding this workout.", isAdd = true, retryable = true) { acceptedAt ->
            val weekday = dayOfWeekFor(epochDay)
            if (acceptedRuleId == null) {
                val hours = container.plannerRepository.rules()
                    .filter { it.weekday == weekday }
                    .map { it.hour }
                val clamped = acceptedHour ?: hourAtAcceptance(
                    epochDay, (hour ?: SlotRuleImport.nextLaterHour(hours)).coerceIn(0, 23), acceptedAt,
                ).also { acceptedHour = it }
                acceptedRuleId = container.plannerRepository.addTimedRule(
                    weekday = weekday,
                    hour = clamped,
                    minute = 0,
                    modality = ScheduleModality.STRENGTH,
                    routineId = routineId,
                ).id
            }
            refreshPlanner(epochDay)
            finishWorkoutDay(epochDay, checkNotNull(acceptedRuleId), acceptedAt)
        }
    }

    fun addAuxiliary(epochDay: Long, packId: String, once: Boolean = false) {
        val pack = AuxiliaryPacks.byId(packId) ?: return
        write("Could not add this extra. Check this day before trying again.", isAdd = true) {
            AuxiliaryBlocks.add(
                planner = container.plannerRepository,
                routines = container.routineRepository,
                exercises = container.exerciseRepository,
                preferences = container.preferencesRepository,
                epochDay = epochDay,
                packId = pack.id,
                once = once,
                todayEpochDay = todayEpochDay(),
                nowMinutes = currentMinutesOfDay(),
            )
            refreshPlanner(epochDay)
        }
    }

    fun deleteSession(epochDay: Long, ruleId: String) {
        write("Could not finish removing this session.", retryable = true) {
            when {
                SlotRuleImport.isUserTimedRule(ruleId) ->
                    container.plannerRepository.removeTimedRule(ruleId)
                SlotRuleImport.isImportedSlotRule(ruleId) -> {
                    val slotId = ruleId.removePrefix("rule-")
                    container.scheduleRepository.unpin(slotId)
                }
            }
            refreshPlanner(epochDay)
        }
    }

    fun composeLaterSession(epochDay: Long, hour: Int? = null) {
        var acceptedRoutineId: String? = null
        var acceptedRuleId: String? = null
        var acceptedHour: Int? = null
        write(
            "Could not finish creating this workout.", isAdd = true, retryable = true,
            onSuccess = { _navigateToEditor.value = acceptedRoutineId },
        ) { acceptedAt ->
            val weekday = dayOfWeekFor(epochDay)
            val routineId = acceptedRoutineId ?: run {
                val name = CustomWeekPolicy.extraRoutineName(weekday)
                val used = container.plannerRepository.rules().mapNotNull { it.routineId }.toSet()
                val reusable = container.routineRepository.observeAll().first().firstOrNull { routine ->
                    routine.name.equals(name, ignoreCase = true) && routine.id !in used
                }
                (reusable?.id ?: container.routineRepository.create(name).id).also { acceptedRoutineId = it }
            }
            if (acceptedRuleId == null) {
                val hours = container.plannerRepository.rules()
                    .filter { it.weekday == weekday }
                    .map { it.hour }
                val clamped = acceptedHour ?: hourAtAcceptance(
                    epochDay, (hour ?: SlotRuleImport.nextLaterHour(hours)).coerceIn(0, 23), acceptedAt,
                ).also { acceptedHour = it }
                acceptedRuleId = container.plannerRepository.addTimedRule(
                    weekday = weekday,
                    hour = clamped,
                    minute = 0,
                    modality = ScheduleModality.STRENGTH,
                    routineId = routineId,
                ).id
            }
            refreshPlanner(epochDay)
            finishWorkoutDay(epochDay, checkNotNull(acceptedRuleId), acceptedAt)
        }
    }

    fun moveDayBlock(items: List<AgendaItem>, occurrenceId: String, delta: Int) {
        write("Could not finish changing this order.", retryable = true) {
            val from = items.indexOfFirst { it.occurrence.id == occurrenceId }
            val moves = DayBlockOrder.move(items, from, delta)
            if (moves.isEmpty()) return@write
            container.plannerRepository.applyDayOrder(moves)
        }
    }

    private suspend fun applyHourToSlot(epochDay: Long, slotId: String, hour: Int) {
        val rule = checkNotNull(container.plannerRepository.getRule(SlotRuleImport.ruleIdForSlot(slotId)))
        if (rule.hour != hour) {
            container.plannerRepository.setRuleHour(rule.id, hour.coerceIn(0, 23))
            refreshPlanner(epochDay)
        }
    }

    private fun write(
        failureMessage: String,
        isAdd: Boolean = false,
        retryable: Boolean = false,
        onSuccess: () -> Unit = {},
        block: suspend (CapturedCivilTime) -> Unit,
    ) {
        // Admission is synchronous, before launch or Room can suspend. A failed
        // resumable update owns the page until its exact action finishes.
        if (pendingWrite != null || writeState.value.saving) return
        pendingWrite = PendingPlanWrite(
            failureMessage = failureMessage, isAdd = isAdd, retryable = retryable,
            onSuccess = onSuccess, acceptedAt = time.captureNow(), block = block,
        )
        runPendingWrite()
    }

    private fun runPendingWrite() {
        val pending = pendingWrite ?: return
        writeState.value = writeState.value.copy(saving = true, canRetry = false, error = null)
        viewModelScope.launch {
            try {
                runCatchingCancellable { pending.block(pending.acceptedAt) }
                    .onSuccess {
                        pendingWrite = null
                        writeState.value = writeState.value.copy(
                            saving = false, error = null, canRetry = false,
                            completedAdd = writeState.value.completedAdd + if (pending.isAdd) 1 else 0,
                        )
                        pending.onSuccess()
                    }
                    .onFailure { thrown ->
                        AppLog.w(TAG, pending.failureMessage, thrown)
                        if (!pending.retryable) pendingWrite = null
                        writeState.value = writeState.value.copy(
                            saving = false, error = pending.failureMessage, canRetry = pending.retryable,
                        )
                    }
            } finally {
                if (pendingWrite === pending) writeState.value = writeState.value.copy(saving = false)
            }
        }
    }

    private fun dayOfWeekFor(epochDay: Long): Weekday =
        com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(epochDay).dayOfWeek

    private fun currentMinutesOfDay(): Int {
        val now = time.captureNow()
        return time.wallMinutesOfDay(now.instantMillis, now.zoneId)
    }

    private fun hourAtAcceptance(epochDay: Long, preferred: Int, acceptedAt: CapturedCivilTime): Int =
        SlotRuleImport.hourOnDay(
            preferredHour = preferred, epochDay = epochDay, todayEpochDay = acceptedAt.localEpochDay,
            nowMinutes = time.wallMinutesOfDay(acceptedAt.instantMillis, acceptedAt.zoneId),
        )

    private suspend fun finishWorkoutDay(epochDay: Long, ruleId: String, acceptedAt: CapturedCivilTime) {
        if (epochDay >= acceptedAt.localEpochDay) {
            container.plannerRepository.ensureAcceptedRuleDay(ruleId, epochDay, acceptedAt)
        }
    }

    private suspend fun refreshPlanner(epochDay: Long) {
        val prefs = container.preferencesRepository.schedulePreferences.first()
        val today = todayEpochDay()
        container.plannerRepository.publishPinnedWeek(prefs.weekStart, today)
        val selectedWeek = CivilDate.fromEpochDay(epochDay).previousOrSame(prefs.weekStart)
        if (selectedWeek != CivilDate.fromEpochDay(today).previousOrSame(prefs.weekStart)) {
            container.plannerRepository.ensureWeek(selectedWeek)
        }
    }
}
