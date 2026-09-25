package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RestFloorContext
import com.sinura.personaltrainer.domain.RestFloorCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "PT/RestTimerVM"

data class RestTimerScreenState(
    val loadState: SessionLoadState = SessionLoadState.LOADING,
    val rest: RestTimerUiState = RestTimerUiState(),
    val floor: RestFloorContext = RestFloorContext(
        exerciseName = null,
        lastSetLine = null,
        sessionTargetLine = null,
    ),
)

/**
 * Floor-page rest. Commands hit the same [com.sinura.personaltrainer.timer.RestTimerGateway]
 * as [ActiveWorkoutViewModel]. There is no second clock, and no second coach: the Next line is
 * the Log's call, shown only where the Log shows it ([NextSetInputs], [shownNextSet]). A save
 * the Log holds hides it here too: a failed one for as long as it waits for Retry, a set being
 * written for a moment.
 *
 * The page redraws each second while a rest runs, and each redraw still reads what the Log last
 * left in the draft cache. The coach is asked again only when that, or anything else the Next
 * line is made from, has changed ([FloorKey]); it was asked twice a second (W2c, audit C-2).
 */
class RestTimerViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val restTimer = container.restTimerController
    private val restTotal = MutableStateFlow(PlannedRest(seconds = RestTimerPreferences.DEFAULT_SECONDS, chosen = false))
    private val lighterWeek = MutableStateFlow(false)

    /** What was read for one lift, its hint and then its last session. Used only for that lift. */
    private val liftReads = MutableStateFlow<LiftReads?>(null)

    /** The lift the page last began reading for; a draw of any other lift reads that one. */
    private val readsFor = MutableStateFlow<String?>(null)

    /** A lift drawn after the page opened whose reads it has not begun. */
    private val liftToRead = MutableStateFlow<String?>(null)
    private val sessionReader = WorkoutSessionReader(container.workoutRepository, sessionId, viewModelScope)
    private val restCommands = RestCommands(container, viewModelScope, sessionId)
    private val hintLoader = ProgressionHintLoader(container, sessionId)

    /** The floor last drawn and what it was drawn from; [uiState]'s one collector reads it. */
    private var lastFloor: Pair<FloorKey, RestFloorContext>? = null

    init {
        viewModelScope.launch {
            runCatchingCancellable {
                val current = sessionReader.observations.first {
                    it.loadState == SessionLoadState.FOUND || it.loadState == SessionLoadState.MISSING
                }.session
                val prefs = container.preferencesRepository.restTimerPreferences.first()
                val exerciseId = resolveExerciseId(current)
                val planned = current?.exercises?.firstOrNull { it.exercise.id == exerciseId }
                val reading = exerciseId?.takeIf { current != null && !current.isFinished }
                val opening = if (current != null && reading != null) {
                    readsFor.value = reading
                    LiftReads(exerciseId = reading, hint = loadHint(current, reading)).also(::publish)
                } else {
                    null
                }
                val loaded = RestFloorInputs(
                    reads = opening,
                    lighterWeek = lighterWeek.value,
                    unit = container.preferencesRepository.weightUnit.first(),
                    coachPrefs = container.preferencesRepository.coachPreferences.first(),
                )
                val question = nextSetInputs(current = current, exerciseId = exerciseId, loaded = loaded)
                // Only the Next line asks the Log's question; the planned length keeps the page's
                // old one ([forPlannedRest]), so it does not move in W2b-4.
                val seeded = RestTimer.secondsToStart(
                    planned?.restSeconds,
                    prefs,
                    prescribedSeconds = question.forPlannedRest()
                        .rec(nowMs = time.nowMillis(), todayEpochDay = todayEpochDay())?.restSeconds,
                )
                // The seed fills in the plan; it never replaces a length someone already picked.
                // This load can finish after a tap here or on the Log, and a plain write put the
                // coach's 2:30 back over the 1:45 just chosen (23 Sept).
                restTotal.update { plan -> if (plan.chosen) plan else PlannedRest(seconds = seeded, chosen = false) }
                // Last session is read after the seed: the length does not use it, and a slow or
                // failed read held the page at the 1:30 default (W2b-4 review).
                if (reading != null) readLastSession(reading)
            }.onFailure { AppLog.w(TAG, "Loading rest floor context failed", it) }
        }
        viewModelScope.launch {
            restCommands.presetEchoes.collect { last ->
                restTotal.value = PlannedRest(seconds = last, chosen = true)
            }
        }
        viewModelScope.launch {
            liftToRead.filterNotNull().collectLatest { exerciseId ->
                runCatchingCancellable { readLift(exerciseId) }
                    .onFailure { AppLog.w(TAG, "Reading the drawn lift's history failed", it) }
            }
        }
    }

    val uiState: StateFlow<RestTimerScreenState> = combine(
        sessionReader.observations,
        restCommands.restState(restTotal) { it.seconds },
        combine(
            liftReads,
            lighterWeek,
            container.preferencesRepository.weightUnit,
            container.preferencesRepository.coachPreferences,
        ) { reads, lighter, unit, coach ->
            RestFloorInputs(reads = reads, lighterWeek = lighter, unit = unit, coachPrefs = coach)
        },
    ) { read, rest, extras ->
        val current = read.session
        val missing = current == null || current.isFinished
        RestTimerScreenState(
            loadState = when {
                read.loadState != SessionLoadState.FOUND -> read.loadState
                missing -> SessionLoadState.MISSING
                else -> SessionLoadState.FOUND
            },
            rest = rest,
            floor = if (missing) {
                RestFloorContext(exerciseName = null, lastSetLine = null, sessionTargetLine = null)
            } else {
                floorFor(current, extras)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RestTimerScreenState(),
    )

    /**
     * Skip for the rest this page drew ([RestTimerUiState.timerId], handed over by the button
     * that showed it), not the one running when the tap is read: a rest that finished as Skip
     * was tapped keeps its "Rest complete", and a newer rest keeps running. A ±15 of the shown
     * rest is the same rest and still ends (ADR-012, W2b-3). The dock's Skip is not this one.
     */
    fun skipRest(shownTimerId: String) {
        restTimer.skipIfShown(shownTimerId)
    }

    fun retrySession() {
        sessionReader.retry()
    }

    fun adjustRest(deltaSeconds: Int) {
        restTimer.adjust(deltaSeconds)
    }

    fun selectRestDuration(seconds: Int) {
        restTotal.value = PlannedRest(seconds = seconds, chosen = true)
        restCommands.rememberPick(seconds)
    }

    fun selectCustomRest(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        selectRestDuration(seconds)
        return true
    }

    fun startSelectedRest() {
        restCommands.startPlanned(restTotal.value.seconds)
    }

    fun acknowledgeRestBatteryHint() {
        restCommands.acknowledgeBatteryHint()
    }

    /**
     * What the page says about the lift, from what the Log last left in the draft cache, read at
     * every redraw. The coach is asked, and the floor rebuilt, only when [FloorKey] changes.
     */
    private fun floorFor(current: WorkoutSession, extras: RestFloorInputs): RestFloorContext {
        val exerciseId = resolveExerciseId(current)
        readIfNew(exerciseId)
        val inputs = nextSetInputs(current = current, exerciseId = exerciseId, loaded = extras)
        val key = FloorKey(
            session = current,
            exerciseId = exerciseId,
            coach = inputs.coachKey(),
            draftIsWarmup = inputs.draft.isWarmup,
            // The held save is read, not observed: a change shows at the page's next redraw,
            // each second while a rest runs.
            saveHeld = container.workoutDraftCache.pendingSave(sessionId) != null,
            unit = extras.unit,
        )
        lastFloor?.let { (drawnFrom, floor) -> if (drawnFrom == key) return floor }
        val rec = key.coach.rec(nowMs = time.nowMillis(), todayEpochDay = todayEpochDay())
        // The Log's line or none: not after the lift's planned sets (unless Another set),
        // not on a warm-up entry, not while a set is open for correction (W2b-4), and not
        // while the Log holds a save, one in progress or a failed one waiting for Retry.
        val shown = shownNextSet(rec, draftIsWarmup = key.draftIsWarmup)?.takeIf { !key.saveHeld }
        val loadClass = exerciseId?.let { current.loadClassOf(it) } ?: LoadClass.LOADED
        return RestFloorCopy.context(
            session = current,
            selectedExerciseId = exerciseId,
            unit = key.unit,
            nextLine = shown?.let { SetMicroRecCopy.line(it, loadClass, key.unit) },
        ).also { lastFloor = key to it }
    }

    private fun resolveExerciseId(current: WorkoutSession?): String? {
        val preferred = container.workoutDraftCache.get(sessionId)?.exerciseId
        return current?.resolveSelectedExerciseId(preferred)
    }

    /**
     * The Log's question about the next set of [exerciseId] ([NextSetInputs]). The page has no Log
     * to ask, so it reads what the Log last left in the draft cache: that lift's entry and its
     * Another set, and the set open for correction. The hint and last session beside them are
     * the ones read for that lift the Log's way ([ProgressionHintLoader]), or none while they
     * are still being read. It used to leave out Another set, the set open for correction and
     * the history (W2b-4), and to take one lift's reads for another (W2b-4 review).
     */
    private fun nextSetInputs(
        current: WorkoutSession?,
        exerciseId: String?,
        loaded: RestFloorInputs,
    ): NextSetInputs {
        val cached = exerciseId?.let { container.workoutDraftCache.getLift(sessionId, it) }
        val reads = loaded.reads?.takeIf { it.exerciseId == exerciseId }
        return NextSetInputs(
            session = current,
            selectedExerciseId = exerciseId,
            draft = cached?.entryDraft() ?: ActiveExerciseDraft(),
            hint = reads?.hint,
            editingSetId = container.workoutDraftCache.editingOriginal(sessionId)?.setId,
            wantAnotherSet = cached?.extraSetRequested ?: false,
            historySets = reads?.lastSessionSets.orEmpty(),
            lighterWeek = loaded.lighterWeek,
            unit = loaded.unit,
            coachPrefs = loaded.coachPrefs,
        )
    }

    /**
     * Reads [exerciseId]'s hint and last session when the page draws a lift other than the one it
     * last read for. The Log can move on while the page opens: its read of a lift not yet visited
     * lands after the page read the one before, and the page then drew the new lift with the old
     * one's suggestion and RPE for the whole visit (W2b-4 review). Nothing is asked before the
     * page's opening read has begun; that read is for the lift drawn first.
     */
    private fun readIfNew(exerciseId: String?) {
        val last = readsFor.value ?: return
        if (exerciseId == null || exerciseId == last) return
        readsFor.value = exerciseId
        liftToRead.value = exerciseId
    }

    private suspend fun readLift(exerciseId: String) {
        val current = sessionReader.observations.value.session ?: return
        if (current.isFinished) return
        publish(LiftReads(exerciseId = exerciseId, hint = loadHint(current, exerciseId)))
        readLastSession(exerciseId)
    }

    private suspend fun readLastSession(exerciseId: String) {
        val sets = hintLoader.lastPerformance(exerciseId)?.sets.orEmpty()
        liftReads.update { reads -> if (reads?.exerciseId == exerciseId) reads.copy(lastSessionSets = sets) else reads }
    }

    /** Keeps [reads] only while they are for the lift last asked for; a later ask wins. */
    private fun publish(reads: LiftReads) {
        if (readsFor.value == reads.exerciseId) liftReads.value = reads
    }

    private suspend fun loadHint(current: WorkoutSession, exerciseId: String): ProgressionHint? {
        val planned = current.exercises.firstOrNull { it.exercise.id == exerciseId }
        val lighter = hintLoader.isLighterWeek(hintLoader.thisWeekStart())
        lighterWeek.value = lighter
        return hintLoader.progression(exerciseId, planned, lighter)
    }
}

/**
 * Everything the page's floor is made from: the session, the lift it shows, the coach's question
 * less the clock ([CoachKey]), the entry's warm-up flag, a save the Log holds, and the unit. Two
 * equal keys draw the same floor.
 */
private data class FloorKey(
    val session: WorkoutSession,
    val exerciseId: String?,
    val coach: CoachKey,
    val draftIsWarmup: Boolean,
    val saveHeld: Boolean,
    val unit: WeightUnit,
)

/**
 * The question this page's planned length is asked with: the Log's, as this page asked it before
 * W2b-4, without Another set, the set open for correction or last session's sets. One input now
 * comes from the lift the page draws rather than the cache's newest entry; the two differ only
 * in the moment after a swap, before the Log writes the new lift's entry, when the newest entry
 * could belong to a lift no longer in the workout. The length is
 * not W2b-4's to move (owner decision, 24 September 2026). After Another set the dock still
 * plans the finished lift's 2:30; seeding the extra set's 2:00 here alone would set the two
 * screens apart. Last session's sets change only a first set's RPE, which no rest length reads.
 */
private fun NextSetInputs.forPlannedRest(): NextSetInputs =
    copy(editingSetId = null, wantAnotherSet = false, historySets = emptyList())

/**
 * The length this page will start. [chosen] once someone picked it — here, or on the Log
 * through the shared last preset — and from then on the loader's seed leaves it alone.
 */
private data class PlannedRest(val seconds: Int, val chosen: Boolean)

/** What the rest page read for its coach call, beside the session, the Log's entry and the clock. */
private data class RestFloorInputs(
    val reads: LiftReads?,
    val lighterWeek: Boolean,
    val unit: WeightUnit,
    val coachPrefs: CoachPreferences,
)

/** What the page read for [exerciseId]: its hint, then its last session's sets. */
private data class LiftReads(
    val exerciseId: String,
    val hint: ProgressionHint?,
    val lastSessionSets: List<ExerciseSetRecord> = emptyList(),
)
