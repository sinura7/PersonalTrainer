package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.AuxiliaryPack
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.ExistingLayoutMatcher
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MissedWorkDecision
import com.sinura.personaltrainer.domain.MissedWorkPolicy
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.SlotRuleImport
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.timer.CardioElapsed
import com.sinura.personaltrainer.timer.PersistedCardioTimer
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.domain.InsightFailure
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineGenerator
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.BlockReview
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.BlockReviewBuilder
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeekTwoCopy
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WeeklySchedulePlanner
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.workout.StartDayOutcome
import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PT/PlanVM"

data class PlanUiState(
    val isLoading: Boolean = true,
    val week: WeeklySchedulePlan? = null,
    val routines: List<Routine> = emptyList(),
    val preferences: SchedulePreferences = SchedulePreferences.DEFAULT,
    val inProgress: WorkoutSession? = null,
    val loggedEpochDays: Set<Long> = emptySet(),
    /** A previewed week. Nothing here is stored until it is accepted. */
    val proposals: List<SuggestedTrainingDay> = emptyList(),
    /** The block this week belongs to, or null when the lifter is not in one. */
    val block: TrainingBlock? = null,
    /**
     * What the block came to, present only once it is over.
     *
     * Null while the block is running — not an empty review. Totting up twelve weeks that are
     * still going would invite reading a mid-block number as a result.
     */
    val blockReview: BlockReview? = null,
    /** True when this calendar week is the marked lighter week. */
    val lighterWeek: Boolean = false,
    val error: String? = null,
    val occurrences: List<com.sinura.personaltrainer.domain.ScheduleOccurrence> = emptyList(),
    val rules: List<com.sinura.personaltrainer.domain.ScheduleRule> = emptyList(),
    val missedWorkPrompt: Boolean = false,
    val overdueCount: Int = 0,
)

/**
 * The Plan tab: the week you decided on, and the routines it is built from.
 *
 * The distinction this whole screen turns on is between the week that is *stored* — pinned
 * slots, in [PlanUiState.week] — and a week that is merely *proposed*, in
 * [PlanUiState.proposals]. Before this, there was only one kind, and it was regenerated behind
 * your back on every emission: what looked like a plan was a suggestion that reshuffled
 * whenever you logged anything. Here a suggestion is visibly a suggestion until you accept it,
 * and accepting it is the only thing in the class that writes a slot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val actionError = MutableStateFlow<String?>(null)
    private val proposals = MutableStateFlow<List<SuggestedTrainingDay>>(emptyList())

    private     val insights: StateFlow<TrainingInsights?> = container.trainingInsights.observeShared()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val completedBlockSessions = container.preferencesRepository.trainingBlock
        .flatMapLatest { block ->
            flow {
                val today = todayEpochDay()
                if (block == null || !block.isCompleteOn(today)) {
                    emit(emptyList())
                    return@flow
                }
                val zone = JvmTime.defaultZoneId()
                emit(
                    container.workoutRepository.sessionsBetween(
                        minDateMs = JvmTime.startOfDayMillis(
                            CivilDate.fromEpochDay(block.startEpochDay),
                            zone,
                        ),
                        maxDateMs = JvmTime.startOfDayMillis(
                            CivilDate.fromEpochDay(block.endExclusiveEpochDay),
                            zone,
                        ) - 1,
                    ),
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val uiState: StateFlow<PlanUiState> = combine(
        insights,
        container.workoutRepository.observeInProgress(),
        combine(
            container.preferencesRepository.schedulePreferences,
            container.preferencesRepository.trainingBlock,
            container.preferencesRepository.weightUnit,
            combine(
                container.preferencesRepository.bodyweightLog,
                container.preferencesRepository.lighterWeekStartEpochDay,
                completedBlockSessions,
            ) { log, lighterStart, blockSessions ->
                Triple(log, lighterStart, blockSessions)
            },
        ) { preferences, block, unit, extras ->
            SettingsAndBlock(
                preferences,
                block,
                unit,
                extras.first,
                extras.second,
                extras.third,
            )
        },
        combine(
            proposals,
            actionError,
            combine(
                container.plannerRepository.observeOccurrences(),
                container.plannerRepository.observeRules(),
                container.plannerRepository.observeDecisions(),
            ) { occurrences, rules, decisions ->
                PlannerSnapshot(occurrences, rules, decisions)
            },
        ) { previewed, error, planner -> Triple(previewed, error, planner) },
    ) { current, inProgress, settings, extras ->
        val previewed = extras.first
        val error = extras.second
        val planner = extras.third
        if (current == null) return@combine PlanUiState()
        val zone = ZoneId.systemDefault()
        val today = todayEpochDay()
        val nowMinutes = currentMinutesOfDay()
        val weekStart = current.weekPlan?.weekStartEpochDay
            ?: CivilDate.fromEpochDay(today).previousOrSame(settings.preferences.weekStart).epochDay
        val weekOcc = planner.occurrences.filter {
            it.localEpochDay in weekStart..(weekStart + 6)
        }
        val overdue = MissedWorkPolicy.overdue(weekOcc, today, nowMinutes)
        val decision = planner.decisions.firstOrNull { it.weekStartEpochDay == weekStart }
        PlanUiState(
            isLoading = false,
            week = current.weekPlan,
            routines = current.routines,
            preferences = settings.preferences,
            inProgress = inProgress,
            loggedEpochDays = current.summaries.map { it.localEpochDay }.toSet(),
            proposals = previewed,
            block = settings.block,
            blockReview = settings.block
                ?.takeIf { it.isCompleteOn(today) }
                ?.let { finished ->
                    BlockReviewBuilder.build(
                        block = finished,
                        sessions = settings.blockSessions,
                        unit = settings.unit,
                        bodyweightLog = settings.bodyweightLog,
                    )
                },
            lighterWeek = LighterWeek.isCurrent(
                settings.lighterWeekStart,
                current.weekPlan?.weekStartEpochDay,
            ),
            error = error ?: when {
                current.failed(InsightFailure.PLAN) ->
                    "Couldn’t read this week’s plan. Your pins are safe — try again."
                else -> null
            },
            occurrences = weekOcc,
            rules = planner.rules,
            missedWorkPrompt = MissedWorkPolicy.promptNeeded(overdue, decision),
            overdueCount = overdue.size,
        )
    }
        // Off the main thread. This transform walks every finished session to build the logged
        // set, and once a block completes it walks every set of it again for the review — with
        // a records check per set, which compares against everything before it. That is not
        // main-thread work, and it was on the main thread before the review made it obvious.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlanUiState(),
        )

    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    private val _blockedByInProgress = MutableStateFlow<BlockedStart?>(null)
    val blockedByInProgress: StateFlow<BlockedStart?> = _blockedByInProgress.asStateFlow()

    private val _navigateToCardio = MutableStateFlow<String?>(null)
    val navigateToCardio: StateFlow<String?> = _navigateToCardio.asStateFlow()

    private val _navigateToComposer = MutableStateFlow<String?>(null)
    val navigateToComposer: StateFlow<String?> = _navigateToComposer.asStateFlow()

    private val _navigateToEditor = MutableStateFlow<String?>(null)
    val navigateToEditor: StateFlow<String?> = _navigateToEditor.asStateFlow()

    fun onCardioNavigationHandled() {
        _navigateToCardio.value = null
    }

    fun onComposerNavigationHandled() {
        _navigateToComposer.value = null
    }

    fun onEditorNavigationHandled() {
        _navigateToEditor.value = null
    }

    init {
        // Home's empty hero can ask for a preview before this screen exists. The flag is
        // consumed exactly once — written back to false BEFORE the work runs, so an Activity
        // recreated in between does not re-arm it — and it stores nothing on its own.
        viewModelScope.launch {
            container.pendingWeekSuggestion.collect { pending ->
                if (!pending) return@collect
                container.pendingWeekSuggestion.value = false
                suggestFills()
            }
        }
        viewModelScope.launch {
            container.pendingAnswerReplay.collect { pending ->
                if (!pending) return@collect
                container.pendingAnswerReplay.value = false
                replayStoredAnswers()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Pinning
    // -----------------------------------------------------------------------

    fun pinRoutine(epochDay: Long, routineId: String) {
        write("Could not pin that routine. Try again.") {
            container.scheduleRepository.pin(
                routineId = routineId,
                focusKind = null,
                anchorDay = dayOfWeekFor(epochDay),
            )
            refreshPlanner()
        }
    }

    fun pinFocus(epochDay: Long, kind: SessionFocusKind) {
        write("Could not pin that focus. Try again.") {
            container.scheduleRepository.pin(
                routineId = null,
                focusKind = kind,
                anchorDay = dayOfWeekFor(epochDay),
            )
            refreshPlanner()
        }
    }

    /** No confirm: unpinning loses nothing that re-pinning cannot put back in two taps. */
    fun unpin(slotId: String) {
        write("Could not unpin that day. Try again.") {
            container.scheduleRepository.unpin(slotId)
            refreshPlanner()
        }
    }

    fun swapRoutine(slotId: String, routineId: String) {
        write("Could not swap that day's routine. Try again.") {
            container.scheduleRepository.swapRoutine(slotId, routineId)
            refreshPlanner()
        }
    }

    /**
     * Manual week-build: name the weekday, pin it, then open the editor
     * so lifts / sets / reps are added on that day. Reuses an unpinned
     * routine already named for the weekday.
     */
    fun buildDay(epochDay: Long) {
        write("Could not build that day. Try again.") {
            val weekday = dayOfWeekFor(epochDay)
            val name = CustomWeekPolicy.routineName(weekday)
            val pinnedIds = uiState.value.week?.days?.mapNotNull { it.routineId }?.toSet().orEmpty()
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
            _navigateToEditor.value = routineId
        }
    }

    // -----------------------------------------------------------------------
    // Suggestions
    // -----------------------------------------------------------------------

    /**
     * Builds a preview and shows it. Deliberately writes nothing — the old "New week" button
     * rewrote the plan on the spot, which is why nobody could tell a plan from a redraw.
     */
    fun suggestFills() {
        viewModelScope.launch {
            val current = insights.value ?: insights.first { it != null } ?: return@launch
            val snapshot = current.snapshot
            if (snapshot == null) {
                actionError.value = "Couldn’t read your recent training, so there is nothing to suggest from."
                return@launch
            }
            val preferences = container.preferencesRepository.schedulePreferences.first()
            val emphasis = container.preferencesRepository.coachPreferences.first().emphasis
            val slots = runCatchingCancellable { container.scheduleRepository.slots() }
                .getOrElse { thrown ->
                    AppLog.w(TAG, "Reading the pinned slots failed", thrown)
                    emptyList()
                }
            val plan = withContext(Dispatchers.Default) {
                WeeklySchedulePlanner.plan(
                    preferences = preferences,
                    snapshot = snapshot,
                    recommendations = current.recommendations,
                    routines = current.routines,
                    recentSessions = current.history,
                    nowMs = System.currentTimeMillis(),
                    pinnedSlots = slots,
                    emphasis = emphasis,
                )
            }
            // Only the days the planner invented. Echoed pins carry a slotId and are already
            // on the strip; re-drawing them as suggestions would offer to pin them twice.
            proposals.value = plan.days.filter { it.slotId == null && !it.isRest }
            actionError.value = null
        }
    }

    fun acceptFills() {
        val previewed = proposals.value
        if (previewed.isEmpty()) return
        proposals.value = emptyList()
        write("Could not save that week. Try again.") {
            container.scheduleRepository.acceptFills(previewed)
            refreshPlanner()
        }
    }

    fun dismissFills() {
        proposals.value = emptyList()
    }

    /**
     * Rebuilds the setup layout on the routines already on the phone.
     *
     * Writes nothing. Matching is name-then-kind; [acceptFills] is still the only pin. A
     * Settings rebuild is the wrong tool here — it would create a second copy of every
     * session they already train.
     */
    fun replayStoredAnswers() {
        viewModelScope.launch {
            val current = insights.value ?: insights.first { it != null } ?: return@launch
            val week = current.weekPlan
            if (week == null) {
                actionError.value = WeekTwoCopy.MATCH_FAILED
                return@launch
            }
            val answers = runCatchingCancellable {
                container.preferencesRepository.storedOnboardingAnswers()
            }.getOrElse { thrown ->
                AppLog.w(TAG, "Reading stored answers failed", thrown)
                actionError.value = WeekTwoCopy.MATCH_FAILED
                return@launch
            }
            val catalog = runCatchingCancellable {
                container.exerciseRepository.observeAll().first()
            }.getOrElse { thrown ->
                AppLog.w(TAG, "Reading the catalog for replay failed", thrown)
                emptyList()
            }
            val matched = withContext(Dispatchers.Default) {
                val blueprint = RoutineGenerator.generate(
                    answers = answers,
                    catalog = catalog,
                    weekStart = week.preferences.weekStart,
                )
                ExistingLayoutMatcher.match(
                    blueprint = blueprint,
                    existing = current.routines,
                    weekStartEpochDay = week.weekStartEpochDay,
                )
            }
            if (matched.isEmpty()) {
                actionError.value = WeekTwoCopy.MATCH_FAILED
                return@launch
            }
            proposals.value = matched
            actionError.value = null
        }
    }

    // -----------------------------------------------------------------------
    // Starting
    // -----------------------------------------------------------------------

    fun startDay(day: SuggestedTrainingDay) {
        viewModelScope.launch {
            PendingOccurrence.bindForPlannedDay(container, day)
            start(day)
        }
    }

    /**
     * Empty session on this calendar day. Does not bind the plan row —
     * finishing it must leave the scheduled occurrence Planned so the
     * evening session can still run.
     */
    fun startFreeWorkout() {
        viewModelScope.launch {
            PendingOccurrence.forget(container)
            when (val outcome = container.workoutRepository.startFreeWorkoutSafely()) {
                is StartSessionOutcome.Started -> {
                    actionError.value = null
                    _navigateToSession.value = outcome.session.id
                }
                is StartSessionOutcome.Blocked ->
                    _blockedByInProgress.value = BlockedStart(
                        day = SuggestedTrainingDay(
                            epochDay = todayEpochDay(),
                            dayOfWeek = Weekday.fromEpochDay(todayEpochDay()),
                            isRest = false,
                            focusKind = SessionFocusKind.FULL_BODY,
                            focusTitle = "Free workout",
                            routineId = null,
                            routineName = "Free workout",
                            reason = "",
                            emphasisMuscles = emptyList(),
                            confidence = ScheduleConfidence.HIGH,
                        ),
                        sessionId = outcome.inProgress.id,
                    )
                is StartSessionOutcome.Unavailable ->
                    actionError.value = outcome.message
            }
        }
    }

    fun addMorningCardio(epochDay: Long) {
        addCardio(epochDay, CardioType.RUN)
    }

    fun addCardio(epochDay: Long, type: CardioType) {
        write("Could not add cardio. Try again.") {
            val weekday = dayOfWeekFor(epochDay)
            val already = container.plannerRepository.rules().any {
                it.weekday == weekday && it.modality == ScheduleModality.CARDIO
            }
            if (already) return@write
            container.plannerRepository.addTimedRule(
                weekday = weekday,
                hour = SlotRuleImport.DEFAULT_CARDIO_HOUR,
                minute = 0,
                modality = ScheduleModality.CARDIO,
                templateId = ScheduleKind.cardio(type),
            )
            refreshPlanner()
        }
    }

    /**
     * Another strength session on this weekday, after the latest existing
     * row. Recurring. Bound to [routineId] so accessory / Hyper Pro work
     * is its own start, not a rewrite of the evening pin.
     */
    fun addLaterSession(epochDay: Long, routineId: String) {
        write("Could not add that session. Try again.") {
            val weekday = dayOfWeekFor(epochDay)
            val hours = container.plannerRepository.rules()
                .filter { it.weekday == weekday }
                .map { it.hour }
            container.plannerRepository.addTimedRule(
                weekday = weekday,
                hour = SlotRuleImport.nextLaterHour(hours),
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                routineId = routineId,
            )
            refreshPlanner()
        }
    }

    fun addAuxiliary(epochDay: Long, packId: String) {
        val pack = AuxiliaryPacks.byId(packId) ?: return
        write("Could not add that block. Try again.") {
            val weekday = dayOfWeekFor(epochDay)
            val tag = ScheduleKind.aux(pack.id)
            val already = container.plannerRepository.rules().any {
                it.weekday == weekday && it.templateId == tag
            }
            if (already) return@write
            val routineId = ensureAuxiliaryRoutine(pack)
            val hours = container.plannerRepository.rules()
                .filter { it.weekday == weekday }
                .map { it.hour }
            container.plannerRepository.addTimedRule(
                weekday = weekday,
                hour = SlotRuleImport.nextLaterHour(hours),
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                routineId = routineId,
                templateId = tag,
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

    private suspend fun ensureAuxiliaryRoutine(pack: AuxiliaryPack): String {
        val existing = container.routineRepository.observeAll().first()
            .firstOrNull { it.name.equals(pack.title, ignoreCase = true) }
        if (existing != null) return existing.id
        val created = container.routineRepository.create(pack.title, pack.caption)
        for (lift in pack.lifts) {
            val exercise = container.exerciseRepository.getById(lift.exerciseId) ?: continue
            container.routineRepository.addExercise(
                routineId = created.id,
                exercise = exercise,
                targetSets = lift.sets,
                targetReps = lift.reps,
                targetWeightKg = null,
                restSeconds = lift.restSeconds,
            )
        }
        return created.id
    }

    /**
     * Mint (or reuse) a weekday-extra routine, attach it as a later
     * session, then open the editor so the lifts exist before Home Start.
     */
    fun composeLaterSession(epochDay: Long) {
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
            container.plannerRepository.addTimedRule(
                weekday = weekday,
                hour = SlotRuleImport.nextLaterHour(hours),
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                routineId = routineId,
            )
            refreshPlanner()
            _navigateToEditor.value = routineId
        }
    }

    fun removeTimedRule(ruleId: String) {
        write("Could not remove that session. Try again.") {
            container.plannerRepository.removeTimedRule(ruleId)
            refreshPlanner()
        }
    }

    fun applyMissedWork(choice: MissedWorkChoice) {
        write("Could not save that decision. Try again.") {
            val weekStartEpoch = uiState.value.week?.weekStartEpochDay
                ?: return@write
            val now = JvmTime.captureNow()
            container.plannerRepository.applyMissedWork(
                choice = choice,
                weekStart = CivilDate.fromEpochDay(weekStartEpoch),
                todayEpochDay = todayEpochDay(),
                nowMinutesOfDay = currentMinutesOfDay(now.instantMillis, now.zoneId),
                deviceZoneId = now.zoneId,
                nowMs = now.instantMillis,
            )
        }
    }

    fun startOccurrence(occurrenceId: String) {
        viewModelScope.launch {
            val occurrence = container.plannerRepository.getOccurrence(occurrenceId) ?: return@launch
            val rule = container.plannerRepository.getRule(occurrence.ruleId)
            when (rule?.modality ?: ScheduleModality.STRENGTH) {
                ScheduleModality.CARDIO -> {
                    PendingOccurrence.forget(container)
                    startCardioOccurrence(occurrence, rule)
                }
                ScheduleModality.MIXED -> {
                    PendingOccurrence.bind(container, occurrence.id)
                    _navigateToComposer.value = "mixed"
                }
                ScheduleModality.STRENGTH -> {
                    PendingOccurrence.bind(container, occurrence.id)
                    val item = AgendaItem(occurrence, rule)
                    start(
                        SuggestedTrainingDay(
                            epochDay = occurrence.localEpochDay,
                            dayOfWeek = com.sinura.personaltrainer.domain.Weekday.fromEpochDay(
                                occurrence.localEpochDay,
                            ),
                            isRest = false,
                            focusKind = rule?.focusKind
                                ?: com.sinura.personaltrainer.domain.SessionFocusKind.FULL_BODY,
                            focusTitle = item.title,
                            routineId = rule?.routineId,
                            routineName = rule?.routineId?.let { id ->
                                uiState.value.routines.firstOrNull { it.id == id }?.name
                            },
                            reason = "Planned.",
                            emphasisMuscles = emptyList(),
                            confidence = com.sinura.personaltrainer.domain.ScheduleConfidence.HIGH,
                            slotId = null,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun startCardioOccurrence(
        occurrence: ScheduleOccurrence,
        rule: com.sinura.personaltrainer.domain.ScheduleRule?,
    ) {
        val type = ScheduleKind.cardioTypeOrRun(rule?.templateId)
        val now = JvmTime.captureNow()
        val block = CardioBlock(
            id = IdFactory.Uuid.newId(),
            sortOrder = 0,
            type = type,
            indoor = false,
            elapsedSeconds = 0L,
            movingSeconds = 0L,
            distanceMeters = null,
            elevationMeters = null,
            heartRateBpm = null,
            energyKj = null,
            rpe = null,
            routeRef = null,
        )
        when (val write = container.startLiveActivity(CardioCopy.name(type), listOf(block), now, occurrence.id)) {
            is ActivityWrite.Accepted -> {
                val nowElapsed = android.os.SystemClock.elapsedRealtime()
                val nowWall = System.currentTimeMillis()
                container.cardioTimerPersistence.save(
                    PersistedCardioTimer(
                        sessionId = write.session.id,
                        startedAtElapsedRealtime = nowElapsed,
                        startedAtWallClockMillis = nowWall,
                        bootMarker = CardioElapsed.bootMarker(nowWall, nowElapsed),
                    ),
                )
                actionError.value = null
                _navigateToCardio.value = write.session.id
            }
            is ActivityWrite.Rejected -> actionError.value = write.reason
        }
    }

    fun agendaFor(epochDay: Long): List<AgendaItem> {
        val names = uiState.value.routines.associate { it.id to it.name }
        return DailyAgenda.forDay(epochDay, uiState.value.occurrences, uiState.value.rules, names)
    }

    private suspend fun start(day: SuggestedTrainingDay) {
        when (val outcome = container.startTrainingDay(day)) {
            is StartDayOutcome.Open -> {
                actionError.value = null
                _navigateToSession.value = outcome.sessionId
            }
            is StartDayOutcome.Blocked ->
                _blockedByInProgress.value = BlockedStart(day, outcome.inProgressSessionId)
            is StartDayOutcome.Failed -> actionError.value = outcome.message
            StartDayOutcome.Ignored -> Unit
        }
    }

    fun resumeBlocked() {
        val blocked = _blockedByInProgress.value ?: return
        _blockedByInProgress.value = null
        _navigateToSession.value = blocked.sessionId
    }

    fun discardBlockedAndStart() {
        val blocked = _blockedByInProgress.value ?: return
        _blockedByInProgress.value = null
        viewModelScope.launch {
            when (val result = container.discardWorkout(blocked.sessionId)) {
                DiscardOutcome.Discarded -> start(blocked.day)
                is DiscardOutcome.Failed -> actionError.value = result.message
            }
        }
    }

    fun dismissBlockedStart() {
        _blockedByInProgress.value = null
    }

    fun onSessionNavigationHandled() {
        _navigateToSession.value = null
    }

    // -----------------------------------------------------------------------
    // Routines and tuning
    // -----------------------------------------------------------------------

    fun deleteRoutine(routineId: String) {
        write("Could not delete that routine. Try again.") {
            container.routineRepository.delete(routineId)
        }
    }

    fun setTrainingDays(days: Int) {
        viewModelScope.launch { container.preferencesRepository.setTrainingDaysPerWeek(days) }
    }

    fun setSplit(style: SplitStyle) {
        viewModelScope.launch { container.preferencesRepository.setSplitStyle(style) }
    }

    fun setWeekStart(day: Weekday) {
        viewModelScope.launch { container.preferencesRepository.setWeekStart(day) }
    }

    fun setLighterWeek(enabled: Boolean) {
        viewModelScope.launch {
            val start = insights.value?.weekPlan?.weekStartEpochDay
                ?: uiState.value.week?.weekStartEpochDay
                ?: return@launch
            container.preferencesRepository.setLighterWeekStartEpochDay(
                if (enabled) start else null,
            )
        }
    }

    fun onErrorShown() {
        actionError.value = null
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

    private fun currentMinutesOfDay(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: String = ZoneId.systemDefault().id,
    ): Int {
        val start = JvmTime.startOfDayMillis(
            CivilDate.fromEpochDay(todayEpochDay(nowMs, JvmTime, zoneId)),
            zoneId,
        )
        return DailyAgenda.minutesOfDay(nowMs, start)
    }

    /**
     * Begin the next twelve weeks from the top of this week.
     *
     * Nothing is deleted and nothing is regenerated: the routines, the pinned week and every
     * logged session stay exactly as they are. A block is a horizon, not a container, so
     * starting a new one moves the marker and leaves the training alone.
     */
    fun startNextBlock() {
        write("Couldn't start a new block. Try again.") {
            val weekStart = container.preferencesRepository.schedulePreferences.first().weekStart
            // beginBlock keeps the block being replaced when it was finished. It always is
            // here — this is only reachable from the completed state — but the rule lives in
            // one place rather than being asserted at each caller.
            container.preferencesRepository.beginBlock(
                next = TrainingBlock.startingIn(
                    today = LocalDate.now().let { d ->
                        com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(d.toEpochDay())
                    },
                    weekStart = weekStart,
                ),
                todayEpochDay = todayEpochDay(),
            )
        }
    }

    /** The day whose Start was refused, held so the screen can ask instead of the app deciding. */
    data class BlockedStart(val day: SuggestedTrainingDay, val sessionId: String)

    private suspend fun refreshPlanner() {
        val prefs = container.preferencesRepository.schedulePreferences.first()
        container.plannerRepository.publishPinnedWeek(prefs.weekStart, todayEpochDay())
    }

    private data class PlannerSnapshot(
        val occurrences: List<ScheduleOccurrence>,
        val rules: List<com.sinura.personaltrainer.domain.ScheduleRule>,
        val decisions: List<MissedWorkDecision>,
    )

    private data class SettingsAndBlock(
        val preferences: SchedulePreferences,
        val block: TrainingBlock?,
        val unit: WeightUnit,
        val bodyweightLog: List<BodyweightEntry>,
        val lighterWeekStart: Long?,
        val blockSessions: List<WorkoutSession>,
    )
}
