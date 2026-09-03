package com.sinura.personaltrainer

import com.sinura.personaltrainer.activity.ConfirmActivity
import com.sinura.personaltrainer.activity.DiscardActivity
import com.sinura.personaltrainer.activity.FinishActivity
import com.sinura.personaltrainer.activity.StartLiveActivity
import com.sinura.personaltrainer.data.repository.ActivityRepository
import com.sinura.personaltrainer.data.repository.BackupRepository
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.OnboardingApplier
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.GoalRepository
import com.sinura.personaltrainer.data.repository.PlannerRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.insights.TrainingInsightsPublisher
import com.sinura.personaltrainer.timer.CardioTimerPersistence
import com.sinura.personaltrainer.timer.RestTimerGateway
import com.sinura.personaltrainer.timer.RestTimerStatePersistence
import com.sinura.personaltrainer.timer.RestTimerStore
import com.sinura.personaltrainer.workout.DiscardWorkout
import com.sinura.personaltrainer.workout.FinishWorkout
import com.sinura.personaltrainer.workout.StartLiveCardio
import com.sinura.personaltrainer.workout.StartOccurrence
import com.sinura.personaltrainer.workout.StartTrainingDay
import com.sinura.personaltrainer.workout.WorkoutDraftCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The graph a ViewModel is constructed with.
 *
 * Production wires [AppContainer]. Tests pass a fake — or a real graph built on an in-memory
 * database — instead of reaching through [Application] for a concrete singleton. That is the
 * whole point of this type: ViewModels stay constructible without the process-wide container.
 */
interface AppDependencies {
    /**
     * File and Drive hops. Production is [kotlinx.coroutines.Dispatchers.IO]. Tests pass the
     * test dispatcher so [kotlinx.coroutines.withContext] does not park off the scheduler.
     */
    val ioDispatcher: CoroutineDispatcher

    /**
     * CPU hops ([kotlinx.coroutines.flow.flowOn] and planner/summary work). Production is
     * [kotlinx.coroutines.Dispatchers.Default]. Tests pass the test dispatcher so those
     * emissions stay on the scheduler.
     */
    val computeDispatcher: CoroutineDispatcher

    val dbMaintenance: DbMaintenance
    val exerciseRepository: ExerciseRepository
    val routineRepository: RoutineRepository
    val scheduleRepository: ScheduleRepository
    val plannerRepository: PlannerRepository
    val goalRepository: GoalRepository
    val workoutRepository: WorkoutRepository
    val preferencesRepository: PreferencesRepository
    val onboardingApplier: OnboardingApplier
    val restTimerStatePersistence: RestTimerStatePersistence
    val restTimerStore: RestTimerStore
    val restTimerController: RestTimerGateway
    val workoutDraftCache: WorkoutDraftCache
    val finishWorkout: FinishWorkout
    val discardWorkout: DiscardWorkout
    val trainingInsights: TrainingInsightsPublisher
    val pendingWeekSuggestion: MutableStateFlow<Boolean>
    /** Home arms this; Plan consumes it once and runs [com.sinura.personaltrainer.ui.plan.PlanViewModel.replayStoredAnswers]. */
    val pendingAnswerReplay: MutableStateFlow<Boolean>
    /** Home / onboarding arms this; the custom-week route consumes it once. */
    val pendingCustomWeek: MutableStateFlow<com.sinura.personaltrainer.domain.CustomWeekLaunch?>
    val startTrainingDay: StartTrainingDay
    val startLiveCardio: StartLiveCardio
    val startOccurrence: StartOccurrence
    val backupRepository: BackupRepository
    val activityRepository: ActivityRepository
    val confirmActivity: ConfirmActivity
    val startLiveActivity: StartLiveActivity
    val discardActivity: DiscardActivity
    val finishActivity: FinishActivity
    val cardioTimerPersistence: CardioTimerPersistence
    val pendingOccurrenceId: MutableStateFlow<String?>
}
