package com.sinura.personaltrainer

import com.sinura.personaltrainer.data.repository.BackupRepository
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.OnboardingApplier
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.insights.TrainingInsightsPublisher
import com.sinura.personaltrainer.timer.RestTimerController
import com.sinura.personaltrainer.timer.RestTimerStatePersistence
import com.sinura.personaltrainer.timer.RestTimerStore
import com.sinura.personaltrainer.workout.DiscardWorkout
import com.sinura.personaltrainer.workout.FinishWorkout
import com.sinura.personaltrainer.workout.StartTrainingDay
import com.sinura.personaltrainer.workout.WorkoutDraftCache
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The graph a ViewModel is constructed with.
 *
 * Production wires [AppContainer]. Tests pass a fake — or a real graph built on an in-memory
 * database — instead of reaching through [Application] for a concrete singleton. That is the
 * whole point of this type: ViewModels stay constructible without the process-wide container.
 */
interface AppDependencies {
    val dbMaintenance: DbMaintenance
    val exerciseRepository: ExerciseRepository
    val routineRepository: RoutineRepository
    val scheduleRepository: ScheduleRepository
    val workoutRepository: WorkoutRepository
    val preferencesRepository: PreferencesRepository
    val onboardingApplier: OnboardingApplier
    val restTimerStatePersistence: RestTimerStatePersistence
    val restTimerStore: RestTimerStore
    val restTimerController: RestTimerController
    val workoutDraftCache: WorkoutDraftCache
    val finishWorkout: FinishWorkout
    val discardWorkout: DiscardWorkout
    val trainingInsights: TrainingInsightsPublisher
    val pendingWeekSuggestion: MutableStateFlow<Boolean>
    /** Home arms this; Plan consumes it once and runs [com.sinura.personaltrainer.ui.plan.PlanViewModel.replayStoredAnswers]. */
    val pendingAnswerReplay: MutableStateFlow<Boolean>
    val startTrainingDay: StartTrainingDay
    val backupRepository: BackupRepository
}
