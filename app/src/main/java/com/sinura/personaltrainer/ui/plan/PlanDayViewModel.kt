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
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.DayBlockOrder
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
    val routines: List<Routine> = emptyList(),
    val occurrences: List<ScheduleOccurrence> = emptyList(),
    val rules: List<ScheduleRule> = emptyList(),
)

/**
 * One weekday's schedule page. Does not run the Plan tab's insights
 * pipeline — opening Tuesday used to construct a second [PlanViewModel].
 */
class PlanDayViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val actionError = MutableStateFlow<String?>(null)
    private val _navigateToEditor = MutableStateFlow<String?>(null)
    val navigateToEditor: StateFlow<String?> = _navigateToEditor.asStateFlow()

    val uiState: StateFlow<PlanDayUiState> = combine(
        container.plannerRepository.observeOccurrences(),
        container.plannerRepository.observeRules(),
        container.routineRepository.observeAll(),
        actionError,
    ) { occurrences, rules, routines, error ->
        PlanDayUiState(
            isLoading = false,
            error = error,
            routines = routines,
            occurrences = occurrences,
            rules = rules,
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
        actionError.value = null
    }

    fun pinRoutine(epochDay: Long, routineId: String, hour: Int = SlotRuleImport.DEFAULT_STRENGTH_HOUR) {
        write("Could not pin that routine. Try again.") {
            container.scheduleRepository.pin(
                routineId = routineId,
                focusKind = null,
                anchorDay = dayOfWeekFor(epochDay),
            )
            refreshPlanner()
            applyHourToRoutine(epochDay, routineId, hour)
        }
    }

    fun buildDay(epochDay: Long, hour: Int = SlotRuleImport.DEFAULT_STRENGTH_HOUR) {
        write("Could not build that day. Try again.") {
            val weekday = dayOfWeekFor(epochDay)
            val name = CustomWeekPolicy.routineName(weekday)
            val pinnedIds = container.scheduleRepository.slots().mapNotNull { it.routineId }.toSet()
            val reusable = uiState.value.routines.firstOrNull { routine ->
                routine.name.equals(name, ignoreCase = true) && routine.id !in pinnedIds
            }
            val routineId = reusable?.id ?: container.routineRepository.create(name).id
            container.scheduleRepository.pin(
                routineId = routineId,
                focusKind = null,
                anchorDay = weekday,
            )
            refreshPlanner()
            applyHourToRoutine(epochDay, routineId, hour)
            _navigateToEditor.value = routineId
        }
    }

    fun addCardio(epochDay: Long, type: CardioType, hour: Int = SlotRuleImport.DEFAULT_CARDIO_HOUR) {
        write("Could not add cardio. Try again.") {
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
            refreshPlanner()
        }
    }

    fun addLaterSession(epochDay: Long, routineId: String, hour: Int? = null) {
        write("Could not add that session. Try again.") {
            val weekday = dayOfWeekFor(epochDay)
            val hours = container.plannerRepository.rules()
                .filter { it.weekday == weekday }
                .map { it.hour }
            val preferred = (hour ?: SlotRuleImport.nextLaterHour(hours)).coerceIn(0, 23)
            container.plannerRepository.addTimedRule(
                weekday = weekday,
                hour = SlotRuleImport.hourOnDay(
                    preferredHour = preferred,
                    epochDay = epochDay,
                    todayEpochDay = todayEpochDay(),
                    nowMinutes = currentMinutesOfDay(),
                ),
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                routineId = routineId,
            )
            refreshPlanner()
        }
    }

    fun addAuxiliary(epochDay: Long, packId: String, once: Boolean = false) {
        val pack = AuxiliaryPacks.byId(packId) ?: return
        write("Could not add that block. Try again.") {
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
            refreshPlanner()
        }
    }

    fun deleteSession(epochDay: Long, ruleId: String) {
        write("Could not remove that session. Try again.") {
            when {
                SlotRuleImport.isUserTimedRule(ruleId) ->
                    container.plannerRepository.removeTimedRule(ruleId)
                SlotRuleImport.isImportedSlotRule(ruleId) -> {
                    val slotId = ruleId.removePrefix("rule-")
                    container.scheduleRepository.unpin(slotId)
                }
            }
            refreshPlanner()
        }
    }

    fun composeLaterSession(epochDay: Long, hour: Int? = null) {
        write("Could not add that session. Try again.") {
            val weekday = dayOfWeekFor(epochDay)
            val name = CustomWeekPolicy.extraRoutineName(weekday)
            val used = container.plannerRepository.rules().mapNotNull { it.routineId }.toSet()
            val reusable = uiState.value.routines.firstOrNull { routine ->
                routine.name.equals(name, ignoreCase = true) && routine.id !in used
            }
            val routineId = reusable?.id ?: container.routineRepository.create(name).id
            val hours = container.plannerRepository.rules()
                .filter { it.weekday == weekday }
                .map { it.hour }
            val preferred = (hour ?: SlotRuleImport.nextLaterHour(hours)).coerceIn(0, 23)
            container.plannerRepository.addTimedRule(
                weekday = weekday,
                hour = SlotRuleImport.hourOnDay(
                    preferredHour = preferred,
                    epochDay = epochDay,
                    todayEpochDay = todayEpochDay(),
                    nowMinutes = currentMinutesOfDay(),
                ),
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                routineId = routineId,
            )
            refreshPlanner()
            _navigateToEditor.value = routineId
        }
    }

    fun moveDayBlock(items: List<AgendaItem>, occurrenceId: String, delta: Int) {
        write("Could not reorder that session. Try again.") {
            val from = items.indexOfFirst { it.occurrence.id == occurrenceId }
            val moves = DayBlockOrder.move(items, from, delta)
            if (moves.isEmpty()) return@write
            container.plannerRepository.applyDayOrder(moves)
        }
    }

    private suspend fun applyHourToRoutine(epochDay: Long, routineId: String, hour: Int) {
        val weekday = dayOfWeekFor(epochDay)
        val rule = container.plannerRepository.rules()
            .filter { it.weekday == weekday && it.routineId == routineId }
            .maxByOrNull { it.updatedAtMs } ?: return
        if (rule.hour != hour) {
            container.plannerRepository.setRuleHour(rule.id, hour.coerceIn(0, 23))
            refreshPlanner()
        }
    }

    private fun write(failureMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatchingCancellable { block() }
                .onSuccess { actionError.value = null }
                .onFailure { thrown ->
                    AppLog.w(TAG, failureMessage, thrown)
                    actionError.value = failureMessage
                }
        }
    }

    private fun dayOfWeekFor(epochDay: Long): Weekday =
        com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(epochDay).dayOfWeek

    private fun currentMinutesOfDay(): Int {
        val now = time.captureNow()
        return time.wallMinutesOfDay(now.instantMillis, now.zoneId)
    }

    private suspend fun refreshPlanner() {
        val prefs = container.preferencesRepository.schedulePreferences.first()
        container.plannerRepository.publishPinnedWeek(prefs.weekStart, todayEpochDay())
    }
}
