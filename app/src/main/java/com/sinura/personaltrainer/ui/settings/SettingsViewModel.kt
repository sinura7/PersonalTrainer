package com.sinura.personaltrainer.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.backup.BackupEnvelope
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.RoutineGenerator
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.data.repository.ApplyPlanResult
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.reminder.WorkoutAlarmScheduler
import com.sinura.personaltrainer.timer.RestTimerAlerts
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent as buildExactAlarmSettingsIntent
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.util.toLocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/SettingsVM"

/**
 * Everything the Settings preference sections read, in one snapshot.
 *
 * These were ten separate `StateFlow`s, and `SettingsScreen` opened all ten with ten separate
 * `collectAsStateWithLifecycle` calls — ten subscriptions, ten recomposition triggers, and no
 * single value a test could assert the screen against. One state means the screen has one
 * subscription and the sections take plain values.
 */
data class SettingsUiState(
    val weightUnit: WeightUnit = WeightUnit.LBS,
    val clockFormat: ClockFormat = ClockFormat.TWELVE,
    val schedule: SchedulePreferences = SchedulePreferences.DEFAULT,
    val reminders: ReminderPreferences = ReminderPreferences.DEFAULT,
    val restTimer: RestTimerPreferences = RestTimerPreferences.DEFAULT,
    val coach: CoachPreferences = CoachPreferences.DEFAULT,
    val bodyweightKg: Double? = null,
    val preferredDays: Set<Weekday> = emptySet(),
    val bodyweightCheckInWeekday: Weekday? = null,
    val trainingAge: TrainingAge = TrainingAge.NEW,
    val trainingPlace: TrainingPlace? = null,
    /**
     * Honest inexact copy + Settings tap. True only after rest is used or configured, and only
     * while the policy would take the best-effort path. Never says the fallback is reliable.
     */
    val offerExactAlarmAccess: Boolean = false,
)

/**
 * The Settings preferences.
 *
 * Backup, restore, Drive and safety copies live in [BackupCoordinator], reached through
 * [backup]. They were in here — around forty of this class's sixty-two public functions —
 * and they have nothing to do with the weight unit or the rest-timer default beyond sharing
 * a tab.
 */
class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
    envelopeIterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
) : AppViewModel(application, container) {
    val backup = BackupCoordinator(
        container = container,
        scope = viewModelScope,
        application = application,
        envelopeIterations = envelopeIterations,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        // The typed `combine` overloads stop at five flows, so this is two groups of five.
        combine(
            container.preferencesRepository.weightUnit,
            container.preferencesRepository.clockFormat,
            container.preferencesRepository.schedulePreferences,
            container.preferencesRepository.reminderPreferences,
            container.preferencesRepository.restTimerPreferences,
        ) { unit, clock, schedule, reminders, rest ->
            SettingsUiState(
                weightUnit = unit,
                clockFormat = clock,
                schedule = schedule,
                reminders = reminders,
                restTimer = rest,
            )
        },
        combine(
            container.preferencesRepository.coachPreferences,
            container.preferencesRepository.bodyweightKg,
            container.preferencesRepository.preferredDays,
            container.preferencesRepository.bodyweightCheckInWeekday,
            combine(
                container.preferencesRepository.restAlarmEligible,
                container.restTimerController.exactAlarmAttempt,
                container.preferencesRepository.trainingAge,
                container.preferencesRepository.trainingPlace,
            ) { eligible, attempt, age, place ->
                Triple(eligible && attempt == ExactAlarmAttempt.BEST_EFFORT, age, place)
            },
        ) { coach, bodyweight, days, checkIn, extra ->
            SettingsUiState(
                coach = coach,
                bodyweightKg = bodyweight,
                preferredDays = days,
                bodyweightCheckInWeekday = checkIn,
                offerExactAlarmAccess = extra.first,
                trainingAge = extra.second,
                trainingPlace = extra.third,
            )
        },
    ) { first, second ->
        first.copy(
            coach = second.coach,
            bodyweightKg = second.bodyweightKg,
            preferredDays = second.preferredDays,
            bodyweightCheckInWeekday = second.bodyweightCheckInWeekday,
            offerExactAlarmAccess = second.offerExactAlarmAccess,
            trainingAge = second.trainingAge,
            trainingPlace = second.trainingPlace,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    val launchPermissionsAsked: StateFlow<Boolean> =
        container.preferencesRepository.launchPermissionsAsked.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val _generateNotice = MutableStateFlow<String?>(null)
    val generateNotice: StateFlow<String?> = _generateNotice.asStateFlow()

    fun markLaunchPermissionsAsked() {
        viewModelScope.launch {
            container.preferencesRepository.setLaunchPermissionsAsked(true)
        }
    }

    fun dismissGenerateNotice() {
        _generateNotice.value = null
    }

    fun refreshAlarmCapability() {
        container.restTimerController.refreshAlarmCapability()
    }

    fun exactAlarmSettingsIntent(): Intent? =
        buildExactAlarmSettingsIntent(getApplication<Application>().packageName)

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            container.preferencesRepository.setWeightUnit(unit)
        }
    }

    fun setClockFormat(format: ClockFormat) {
        viewModelScope.launch {
            container.preferencesRepository.setClockFormat(format)
        }
    }

    fun setTrainingDays(days: Int) {
        viewModelScope.launch {
            container.preferencesRepository.setTrainingDaysPerWeek(days)
        }
    }

    fun setSplitStyle(style: SplitStyle) {
        viewModelScope.launch {
            container.preferencesRepository.setSplitStyle(style)
        }
    }

    fun setWeekStart(day: Weekday) {
        viewModelScope.launch {
            container.preferencesRepository.setWeekStart(day)
        }
    }

    fun setReminderOptOut(optOut: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setReminderOptOut(optOut)
            rebuildWorkoutAlarms()
        }
    }

    fun setReminderQuietHours(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            runCatchingCancellable {
                container.preferencesRepository.setReminderQuietHours(startHour, endHour)
            }.onFailure { AppLog.w(TAG, "Saving reminder quiet hours failed", it) }
        }
    }

    fun setDayAlarm(weekday: Weekday, hour: Int, minute: Int) {
        viewModelScope.launch {
            runCatchingCancellable {
                container.preferencesRepository.setDayAlarm(weekday, hour, minute)
                rebuildWorkoutAlarms()
            }.onFailure { AppLog.w(TAG, "Saving the workout alarm failed", it) }
        }
    }

    fun clearDayAlarm(weekday: Weekday) {
        viewModelScope.launch {
            runCatchingCancellable {
                container.preferencesRepository.clearDayAlarm(weekday)
                rebuildWorkoutAlarms()
            }.onFailure { AppLog.w(TAG, "Clearing the workout alarm failed", it) }
        }
    }

    fun setPreferredDays(days: Set<Weekday>) {
        viewModelScope.launch {
            container.preferencesRepository.setPreferredDays(days)
            if (days.isNotEmpty()) {
                container.preferencesRepository.setTrainingDaysPerWeek(days.size)
            }
        }
    }

    fun togglePreferredDay(day: Weekday) {
        viewModelScope.launch {
            val current = container.preferencesRepository.preferredDays.first()
            val next = if (day in current) current - day else current + day
            container.preferencesRepository.setPreferredDays(next)
            if (next.isNotEmpty()) {
                container.preferencesRepository.setTrainingDaysPerWeek(next.size)
            }
        }
    }

    fun setTrainingAge(age: TrainingAge) {
        viewModelScope.launch { container.preferencesRepository.setTrainingAge(age) }
    }

    fun setTrainingPlace(place: TrainingPlace) {
        viewModelScope.launch {
            container.preferencesRepository.setTrainingPlace(place)
            container.preferencesRepository.setAvailableEquipment(
                place.equipment.map { it.name }.toSet().let { names ->
                    val gymFloor = TrainingPlace.GYM_FLOOR.map { it.name }.toSet()
                    if (names == gymFloor) emptySet() else names
                },
            )
        }
    }

    fun generateWeek() {
        viewModelScope.launch {
            runCatchingCancellable {
                var catalog = container.exerciseRepository.observeAll().first()
                if (catalog.isEmpty()) {
                    container.dbMaintenance.seedCatalog()
                    catalog = container.exerciseRepository.observeAll().first()
                }
                val answers = container.preferencesRepository.storedOnboardingAnswers()
                val schedule = container.preferencesRepository.schedulePreferences.first()
                val blueprint = RoutineGenerator.generate(
                    answers,
                    catalog,
                    schedule.weekStart,
                    schedule.splitStyle,
                )
                when (
                    val result = container.onboardingApplier.apply(
                        answers = answers,
                        blueprint = blueprint,
                        catalog = catalog,
                        weekStart = schedule.weekStart,
                        today = civilToday().toLocalDate(),
                    )
                ) {
                    is ApplyPlanResult.Applied -> {
                        container.plannerRepository.publishPinnedWeek(
                            schedule.weekStart,
                            todayEpochDay(),
                        )
                        _generateNotice.value = "Week generated"
                    }
                    is ApplyPlanResult.Failed -> _generateNotice.value = result.message
                }
            }.onFailure { thrown ->
                AppLog.w(TAG, "Generating a week failed", thrown)
                _generateNotice.value = "Could not generate a week. Try again."
            }
        }
    }

    private suspend fun rebuildWorkoutAlarms() {
        val prefs = container.preferencesRepository.reminderPreferences.first()
        WorkoutAlarmScheduler.rebuild(getApplication(), prefs, time)
    }

    fun setTrainingGoal(goal: TrainingGoal) {
        viewModelScope.launch { container.preferencesRepository.setTrainingGoal(goal) }
    }

    fun setTrainingEmphasis(emphasis: TrainingEmphasis) {
        viewModelScope.launch { container.preferencesRepository.setTrainingEmphasis(emphasis) }
    }

    /**
     * Toggling equipment off tells the coach not to name lifts you cannot do. An empty set is
     * gym-floor (everything except Hyper Pro). Expanding that to an explicit gym set before
     * toggling is what lets someone add the Hyper Pro without wiping the gym kit down to one chip.
     */
    fun toggleEquipment(equipment: EquipmentType) {
        viewModelScope.launch {
            val current = container.preferencesRepository.coachPreferences.first().availableEquipment
            val expanded = if (current.isEmpty()) {
                TrainingPlace.GYM_FLOOR.map { it.name }.toSet()
            } else {
                current
            }
            val next = if (equipment.name in expanded) {
                expanded - equipment.name
            } else {
                expanded + equipment.name
            }
            val gymFloor = TrainingPlace.GYM_FLOOR.map { it.name }.toSet()
            container.preferencesRepository.setAvailableEquipment(
                if (next == gymFloor) emptySet() else next,
            )
        }
    }

    /**
     * Record a weigh-in for today.
     *
     * A weigh-in rather than an overwrite: the block review reads the history to say what
     * bodyweight did across twelve weeks, and it can only do that if each change is kept.
     */
    fun recordBodyweight(kg: Double) {
        viewModelScope.launch {
            runCatchingCancellable {
                container.preferencesRepository.recordBodyweight(kg, todayEpochDay())
            }.onFailure { AppLog.w(TAG, "Recording bodyweight failed", it) }
        }
    }

    fun clearBodyweight() {
        viewModelScope.launch {
            runCatchingCancellable { container.preferencesRepository.setBodyweightKg(null) }
                .onFailure { AppLog.w(TAG, "Clearing bodyweight failed", it) }
        }
    }

    fun setBodyweightCheckInWeekday(day: Weekday?) {
        viewModelScope.launch {
            runCatchingCancellable { container.preferencesRepository.setBodyweightCheckInWeekday(day) }
                .onFailure { AppLog.w(TAG, "Saving bodyweight check-in day failed", it) }
        }
    }

    fun setRestSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setRestSoundEnabled(enabled)
        }
    }

    fun setRestVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setRestVibrationEnabled(enabled)
        }
    }

    /**
     * Play the rest-complete cue now. Same [RestTimerAlerts.preview] path
     * as 0:00, not a second asset.
     */
    fun previewRestCompleteCue(
        play: (Context, RestTimerPreferences) -> Unit = RestTimerAlerts::preview,
    ) {
        play(getApplication(), uiState.value.restTimer)
    }

    fun setRestTickEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setRestTickEnabled(enabled)
        }
    }

    fun setDefaultRestSeconds(seconds: Int) {
        viewModelScope.launch {
            container.preferencesRepository.setDefaultRestSeconds(seconds)
        }
    }

    fun setDefaultRestCustom(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        setDefaultRestSeconds(seconds)
        return true
    }

    override fun onCleared() {
        backup.dispose()
        super.onCleared()
    }
}
