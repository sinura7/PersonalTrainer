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
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.RestTimerAlerts
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent as buildExactAlarmSettingsIntent
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
            ) { eligible, attempt ->
                eligible && attempt == ExactAlarmAttempt.BEST_EFFORT
            },
        ) { coach, bodyweight, days, checkIn, offerAlarm ->
            SettingsUiState(
                coach = coach,
                bodyweightKg = bodyweight,
                preferredDays = days,
                bodyweightCheckInWeekday = checkIn,
                offerExactAlarmAccess = offerAlarm,
            )
        },
    ) { first, second ->
        first.copy(
            coach = second.coach,
            bodyweightKg = second.bodyweightKg,
            preferredDays = second.preferredDays,
            bodyweightCheckInWeekday = second.bodyweightCheckInWeekday,
            offerExactAlarmAccess = second.offerExactAlarmAccess,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

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
        }
    }

    fun setReminderQuietHours(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            runCatchingCancellable {
                container.preferencesRepository.setReminderQuietHours(startHour, endHour)
            }.onFailure { AppLog.w(TAG, "Saving reminder quiet hours failed", it) }
        }
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
