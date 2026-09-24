package com.sinura.personaltrainer.ui.workout

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows

/*
 * The floor's rendered tests describe a state in a line and then look at what the screen
 * does with it. These builders are the shared vocabulary for that: one lift, one session,
 * one saved set, one primary action, one dock; and, for the tests that drive the real
 * screen through its ViewModel, one seeded leg extension, its ViewModel, the screen around
 * it, and the ViewModel waits the contract tests share with ActiveWorkoutViewModelTest.
 * They used to live privately in WorkoutFloorComponentsTest, WorkoutLogBarTest and each
 * T1c-1 render test, which is why every new render copied them by hand; one copy here keeps
 * the fixtures from drifting apart.
 */

/** The floor's tests read in pounds, like the owner's phone. */
internal val FLOOR_UNIT: WeightUnit = WeightUnit.LBS

/** 70 lb, stored as the kilograms the database keeps. */
internal val FLOOR_KG70: Double = WeightConverter.lbsToKg(70.0)

internal fun floorLift(
    targetSets: Int,
    id: String = "leg-ext",
    name: String = "Leg Extension",
    equipment: EquipmentType = EquipmentType.MACHINE,
    loadType: LoadType = LoadType.EXTERNAL,
): SessionExercise = SessionExercise(
    id = "se-$id",
    sessionId = "s1",
    exercise = Exercise(
        id = id,
        name = name,
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
        equipment = equipment,
        loadType = loadType,
    ),
    sortOrder = 0,
    targetSets = targetSets,
    targetReps = 10,
    targetWeightKg = FLOOR_KG70,
    restSeconds = 120,
)

internal fun floorSession(sets: List<SetLog>, targetSets: Int, secondLift: Boolean = false): WorkoutSession = WorkoutSession(
    id = "s1",
    routineId = null,
    routineName = "Lower B",
    date = 1L,
    notes = "",
    durationMinutes = 0,
    startedAt = 1L,
    finishedAt = null,
    exercises = if (secondLift) {
        listOf(floorLift(targetSets), floorLift(targetSets = 3, id = "leg-curl", name = "Leg Curl"))
    } else {
        listOf(floorLift(targetSets))
    },
    sets = sets,
)

internal fun floorSet(number: Int, weightKg: Double, reps: Int, rpe: Int? = null, warmup: Boolean = false): SetLog = SetLog(
    id = "set-$number",
    sessionId = "s1",
    exerciseId = "leg-ext",
    exerciseName = "Leg Extension",
    setNumber = number,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isWarmup = warmup,
    completedAt = number.toLong(),
)

internal fun floorPrimaryAction(
    kind: WorkoutPrimaryKind,
    draft: ActiveExerciseDraft = ActiveExerciseDraft(),
    nextName: String? = null,
    enabled: Boolean = true,
): WorkoutPrimaryAction = WorkoutPrimaryAction(
    identity = WorkoutPrimaryIdentity(
        kind = kind,
        sessionId = "session",
        exerciseId = "exercise",
        editingSetId = null,
        draft = draft,
        sets = emptyList(),
        nextExerciseId = null,
        extraSet = false,
        timedGeneration = 0,
        activation = 0L,
        pendingSave = null,
    ),
    enabled = enabled,
    nextName = nextName,
)

/**
 * The dock as the screen builds it, with the verb and payload the action itself names.
 * Only what a test is about needs saying; everything else is a quiet, idle dock.
 */
internal fun floorDockState(
    action: WorkoutPrimaryAction,
    payload: String? = action.payload(unit = FLOOR_UNIT, loadClass = LoadClass.LOADED),
    spokenPayload: String? = null,
    editing: Boolean = false,
    error: String? = null,
    showAnother: Boolean = true,
    undoMessage: String? = null,
    timer: WorkoutDockTimer = WorkoutDockTimer(show = true, restTotalSeconds = 120),
): WorkoutDockState = WorkoutDockState(
    primaryAction = action,
    verb = action.verb(includeNextName = false),
    payload = payload,
    spokenPayload = spokenPayload,
    editing = editing,
    logging = false,
    canLog = true,
    savePending = false,
    error = error,
    suggestionUnavailable = false,
    showAnother = showAnother,
    undoMessage = undoMessage,
    undoKey = undoMessage,
    undoDwellMs = 60_000L,
    timer = timer,
)

/** Dock callbacks that do nothing unless a test hands one in. */
internal fun floorDockEvents(
    onPrimary: (WorkoutPrimaryAction) -> Boolean = { false },
    onAnotherSet: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onOpenRest: () -> Unit = {},
    onUndoDismissed: () -> Unit = {},
    onSkipRest: () -> Unit = {},
    onStartRest: () -> Unit = {},
    onSelectRestDuration: (Int) -> Unit = {},
    onNudgeRest: (Int) -> Unit = {},
    onCustomRest: (String) -> Boolean = { true },
    onStartSetClock: () -> Unit = {},
    onStopSetClock: () -> Unit = {},
    onDismissRestBatteryHint: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
): WorkoutDockEvents = WorkoutDockEvents(
    onPrimary = onPrimary,
    onEditFailedSave = {},
    onCancelEdit = onCancelEdit,
    onDismissError = {},
    onAnotherSet = onAnotherSet,
    onUndo = {},
    onUndoDismissed = onUndoDismissed,
    onSkipRest = onSkipRest,
    onStartRest = onStartRest,
    onSelectRestDuration = onSelectRestDuration,
    onNudgeRest = onNudgeRest,
    onCustomRest = onCustomRest,
    onStartSetClock = onStartSetClock,
    onStopSetClock = onStopSetClock,
    onDismissRestBatteryHint = onDismissRestBatteryHint,
    onOpenRest = onOpenRest,
    onOpenNotifications = onOpenNotifications,
)

/**
 * Composes [content] the way the floor sees it: pounds, the app theme, and the system font
 * scale the test asks for. Font scale is the one input that reshapes the floor: from
 * `LogLoopScale.STACK_WELLS_FROM` the numerals stack and the identity's still shrinks.
 */
internal fun ComposeContentTestRule.showFloor(fontScale: Float = 1f, content: @Composable () -> Unit) =
    showFloor(fontScale = mutableFloatStateOf(fontScale), content = content)

/**
 * As [showFloor], with the font scale read from [fontScale] during composition: a test that
 * changes it moves the screen to the new size while it is up, as a user raising the system font
 * with the app open does.
 */
internal fun ComposeContentTestRule.showFloor(fontScale: State<Float>, content: @Composable () -> Unit) {
    this.setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalWeightUnit provides FLOOR_UNIT,
            LocalDensity provides Density(density = base.density, fontScale = fontScale.value),
        ) {
            PersonalTrainerTheme { content() }
        }
    }
}

/** How long a rendered test waits for the screen or its ViewModel to reach a state. */
internal const val FLOOR_WAIT_MS = 20_000L

/** The lift the rendered floor tests open on. */
internal const val FLOOR_LIFT_ID = "leg-extension"

/** The lift after it, when a test needs somewhere to move on to. */
internal const val FLOOR_NEXT_LIFT_ID = "romanian-deadlift"
internal const val FLOOR_NEXT_LIFT_NAME = "Romanian Deadlift"

/** [count] saved sets of 70 lb × 10 at RPE 8. */
internal fun floorSets(count: Int): List<TestSetInput> = (1..count).map { TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 8) }

/** The undo dwell a phone with no accessibility timeout gives: the base, unchanged. */
internal val FLOOR_BASE_DWELL: UndoTimeoutProvider = UndoTimeoutProvider { base -> base.toLong() }

/** The workout screen's ViewModel on [sessionId], built as the app builds it but over [deps]. */
internal fun floorViewModel(
    deps: AppDependencies,
    sessionId: String,
    undoTimeout: UndoTimeoutProvider = FLOOR_BASE_DWELL,
): ActiveWorkoutViewModel = ActiveWorkoutViewModel(
    application = ApplicationProvider.getApplicationContext(),
    savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
    container = deps,
    undoTimeout = undoTimeout,
)

/**
 * A live [routineName] (by default "Lower B") on its leg extension, [targetSets] × 10 at 70 lb
 * with 120 s rest, with [loggedSets] already saved; with [withNextLift], a Romanian deadlift
 * (3 × 8), named [nextLiftName], after it. The session's id.
 */
internal suspend fun seedLegExtension(
    deps: FakeAppDependencies,
    loggedSets: List<TestSetInput>,
    withNextLift: Boolean = false,
    targetSets: Int = 3,
    routineName: String = "Lower B",
    nextLiftName: String = FLOOR_NEXT_LIFT_NAME,
): String {
    val seeded = seedTestWorkout(
        deps = deps,
        exerciseId = FLOOR_LIFT_ID,
        exerciseName = "Leg Extension",
        routineName = routineName,
        targetSets = targetSets,
        targetReps = 10,
        targetWeightKg = FLOOR_KG70,
        restSeconds = 120,
        loggedSets = loggedSets,
    )
    if (withNextLift) {
        val next = insertTestExercise(deps = deps, id = FLOOR_NEXT_LIFT_ID, name = nextLiftName, muscleGroup = "Hamstrings")
        deps.workoutRepository.addExerciseToSession(seeded.session.id, next, targetSets = 3, targetReps = 8, targetWeightKg = 40.0, restSeconds = 90)
    }
    return seeded.session.id
}

/** [seedLegExtension], then its ViewModel, kept in [viewModels] for the test's tear-down to clear. */
internal fun openLegExtension(
    deps: FakeAppDependencies,
    viewModels: MutableList<ActiveWorkoutViewModel>,
    loggedSets: List<TestSetInput>,
    withNextLift: Boolean = false,
    undoTimeout: UndoTimeoutProvider = FLOOR_BASE_DWELL,
    targetSets: Int = 3,
    routineName: String = "Lower B",
    nextLiftName: String = FLOOR_NEXT_LIFT_NAME,
): ActiveWorkoutViewModel {
    val sessionId = runBlocking {
        seedLegExtension(
            deps = deps,
            loggedSets = loggedSets,
            withNextLift = withNextLift,
            targetSets = targetSets,
            routineName = routineName,
            nextLiftName = nextLiftName,
        )
    }
    return floorViewModel(deps = deps, sessionId = sessionId, undoTimeout = undoTimeout).also(viewModels::add)
}

/**
 * A lift is on the floor, its prefill has answered and the entry takes taps: what a rendered test
 * waits for before it acts. The prefill reads the database on its own thread and writes the draft
 * when it lands, over anything tapped before then, so an unlocked entry alone is not enough.
 */
internal val FLOOR_LIFT_READY: (ActiveWorkoutUiState) -> Boolean = { state ->
    state.session?.exercises?.isNotEmpty() == true &&
        state.liftReadiness.allowsCommit() &&
        !state.entryLocked
}

/**
 * Composes the real workout screen for [vm] in a [width] × [height] box, with [view] as the
 * screen's view when a test writes down what the screen asks the hand to feel, then waits for
 * the session to load and for [ready].
 */
internal fun ComposeContentTestRule.showWorkoutScreen(
    vm: ActiveWorkoutViewModel,
    width: Dp = 360.dp,
    height: Dp = 800.dp,
    view: View? = null,
    ready: (ActiveWorkoutUiState) -> Boolean = FLOOR_LIFT_READY,
) {
    val screen: @Composable () -> Unit = {
        Box(modifier = Modifier.width(width).height(height)) {
            ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = vm, restNotificationsEnabledOverride = true)
        }
    }
    showFloor {
        if (view == null) {
            screen()
        } else {
            CompositionLocalProvider(LocalView provides view) { screen() }
        }
    }
    waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.loadState == SessionLoadState.FOUND }
    waitUntil(timeoutMillis = FLOOR_WAIT_MS) { ready(vm.uiState.value) }
    waitForIdle()
}

/**
 * A [FeltView] on this rule's activity, hung off its window and stamping each haptic with the
 * screen clock's time, for a test to hand [showWorkoutScreen] as the screen's view.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.attachedFeltView(): FeltView {
    val felt = FeltView(context = activity, clock = { mainClock.currentTime })
    runOnUiThread { felt.attachTo(activity) }
    return felt
}

/**
 * A bare timeout here reports only "Timed out waiting for 30000 ms", which is the one
 * thing already known. The state the wait never reached is what says whether the action
 * under test did nothing, did the wrong thing, or did the right thing into a value that
 * was overwritten before this collector saw it. Reading it in the catch costs nothing on
 * the happy path and cannot perturb the race that got us here — it has already lost.
 */
internal suspend fun ActiveWorkoutViewModel.awaitState(
    predicate: (ActiveWorkoutUiState) -> Boolean,
): ActiveWorkoutUiState = try {
    withTimeout(TestWaits.FLOW_MS) { uiState.first(predicate) }
} catch (timedOut: TimeoutCancellationException) {
    throw AssertionError("awaitState gave up; last uiState was ${uiState.value}", timedOut)
}

/**
 * The moment a delete, remove or undo may be issued and will be acted on.
 *
 * Every entry mutation begins `if (!canChangeEntry()) return`: while a save is
 * outstanding, another mutation is in flight or the session is not FOUND, the tap is
 * dropped without a word, by design — a queued tap must never act on a screen that has
 * moved on. `entryLocked` projects those same flags, and `uiState` is collected for the
 * ViewModel's whole life by `primaryAction`, so it is live, not a snapshot. A test that
 * taps the instant a row or an offer appears is otherwise racing the tail of the
 * operation that produced it: that is how `undoQueueSurvivesProcessDeath` lost trunk
 * run 35239125454 and reproduced here, with the row still stored and nothing left
 * running.
 */
internal suspend fun ActiveWorkoutViewModel.awaitEntryUnlocked(): ActiveWorkoutUiState =
    awaitState { !it.entryLocked }

/** The stored session once [predicate] holds, or an AssertionError that shows the stored row. */
internal suspend fun WorkoutRepository.awaitSession(
    sessionId: String,
    predicate: (WorkoutSession) -> Boolean,
): WorkoutSession = try {
    withTimeout(TestWaits.FLOW_MS) {
        checkNotNull(
            observeSession(sessionId).first { session ->
                session != null && predicate(session)
            },
        )
    }
} catch (timedOut: TimeoutCancellationException) {
    val stored = runCatching { getSession(sessionId) }
    throw AssertionError("awaitSession gave up; stored row was ${stored.getOrNull()}", timedOut)
}

/**
 * Log a set and wait for the whole action, not just for its row; [repository] is where the
 * row lands and [scheduler] the ViewModel's own clock.
 *
 * The session Flow publishes the moment Room commits, which is the middle of
 * [ActiveWorkoutViewModel.logSet]'s coroutine and not its end: the personal-record
 * moment, `wantAnotherSet`, `error`, the draft reset and the double-tap guard are all
 * written after that. Carrying on at the row raced the rest of the action — whatever the
 * test set next could be taken back by the tail, and a second logSet() could be swallowed
 * by a guard still true from the first.
 *
 * That is what wedged ActiveWorkoutViewModelTest intermittently. The wait below is for an
 * outcome only this log can produce, not for flags an earlier snapshot also shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun ActiveWorkoutViewModel.logSetAndSettle(repository: WorkoutRepository, scheduler: TestCoroutineScheduler) {
    val sessionId = checkNotNull(uiState.value.session?.id) { "logSetAndSettle before the session loaded" }
    val storedBefore = repository.getSession(sessionId)?.sets.orEmpty().toSet()
    val saveBefore = uiState.value.save
    logSet()
    // Wait for THIS log, not for a quiet screen. "Not logging, not saving" is also true of
    // the snapshot from before the tap: `uiState` combines Room flows on Room's threads,
    // so for a moment after logSet() it can still show the pre-log flags, and waiting on
    // them returned before the save began. The next logSet() was then refused by the save
    // still holding the entry lock (expiredTopOfferRevealsTheNextOneWithoutARestChange,
    // 23 Sept). So wait for an outcome only this log can produce: the stored rows changed
    // and the screen shows those rows with the entry unlocked, or this save came to rest
    // as FAILED / CONFLICT, which the write-failure tests go on to assert.
    try {
        withTimeout(TestWaits.FLOW_MS) {
            while (true) {
                val state = uiState.value
                val failed = state.save != saveBefore &&
                    (state.save.phase == WorkoutSavePhase.FAILED || state.save.phase == WorkoutSavePhase.CONFLICT)
                if (failed) break
                val stored = repository.getSession(sessionId)?.sets.orEmpty().toSet()
                val landed = stored != storedBefore && state.session?.sets.orEmpty().toSet() == stored
                if (landed && !state.entryLocked) break
                delay(10)
            }
        }
    } catch (timedOut: TimeoutCancellationException) {
        throw AssertionError("logSetAndSettle: the log neither landed nor failed; uiState was ${uiState.value}", timedOut)
    }
    scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong())
    scheduler.runCurrent()
    scheduler.advanceUntilIdle()
}

/** The labels TalkBack offers in its actions menu for this node, in order. */
internal fun SemanticsNodeInteraction.customActionLabels(): List<String> =
    fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

/**
 * Runs the TalkBack action named [label] on the UI thread, as the actions menu would. A
 * missing action fails with the labels the node does offer, so a reworded action reads as
 * what changed rather than as an empty collection.
 */
/**
 * Whether one node matching [matcher] is on screen now. For waiting on something still arriving,
 * like a sheet's rows while it slides up; the caller asserts after the wait.
 */
internal fun ComposeContentTestRule.isDisplayed(matcher: SemanticsMatcher): Boolean =
    try {
        onNode(matcher).assertIsDisplayed()
        true
    } catch (_: AssertionError) {
        false
    }

internal fun ComposeContentTestRule.runCustomAction(node: SemanticsNodeInteraction, label: String) {
    val offered = node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    val action = offered.firstOrNull { it.label == label }
        ?: throw AssertionError("no TalkBack action \"$label\"; the node offers ${offered.map { it.label }}")
    runOnIdle { action.action() }
}

/**
 * The exercise pictures drawn at [size] under [under], each wearing its equipment badge. A still
 * is decorative, since the words beside it name the lift, so it clears its own semantics: nothing
 * reads it, and there is no tag to find it by. It is still a node of the unmerged tree, sized,
 * and one that clears what is under it and says nothing itself. A blank square does that too,
 * so a still is also known by its badge: one square child, [badgeSide] across, flush in its
 * bottom-end corner (see [badgesOf]). Counting them needs no production tag.
 */
internal fun ComposeContentTestRule.stillsUnder(under: SemanticsMatcher, size: Dp): List<SemanticsNode> =
    silentSquaresUnder(under, size).filter { still -> badgesOf(still, size).size == 1 }

/**
 * The silent [size] squares under [under], badged or not: what [stillsUnder] narrows to the
 * stills that wear their badge. Only a test about the badge itself wants the bare squares.
 */
internal fun ComposeContentTestRule.silentSquaresUnder(under: SemanticsMatcher, size: Dp): List<SemanticsNode> {
    val side = with(density) { size.roundToPx() }
    val silentSquare: (SemanticsNode) -> Boolean = { node ->
        node.config.isClearingSemantics &&
            node.config.getOrNull(SemanticsProperties.ContentDescription) == null &&
            node.size.width == side && node.size.height == side
    }
    val still = SemanticsMatcher(description = "a silent $size still", matcher = silentSquare)
    return onAllNodes(still and hasAnyAncestor(under), useUnmergedTree = true).fetchSemanticsNodes()
}

/**
 * The equipment badges drawn flush in [still]'s bottom-end corner: square children [badgeSide]
 * across for a [size] still.
 *
 * The badge says nothing either, so it is found only because Compose UI's shape modifiers (its
 * clip and background) publish a Shape semantics node in the unmerged tree, even under the
 * still's cleared semantics. Compose 1.11.4 does. If a later Compose stops publishing it, every
 * still stops being found and the tests that count stills fail loudly, never quietly pass.
 */
internal fun ComposeContentTestRule.badgesOf(still: SemanticsNode, size: Dp): List<SemanticsNode> {
    val side = with(density) { badgeSide(size).roundToPx() }
    val stillBounds = still.boundsInRoot
    return still.children.filter { child ->
        child.size.width == side && child.size.height == side &&
            child.boundsInRoot.right == stillBounds.right && child.boundsInRoot.bottom == stillBounds.bottom
    }
}

/** A [size] still's badge: its share of the still's edge, capped at the equipment glyph's size. */
internal fun badgeSide(size: Dp): Dp = minOf(size * STILL_BADGE_SHARE, Metrics.equipmentGlyph)

/** The badge's share of a still's edge, before the cap. */
internal const val STILL_BADGE_SHARE = 0.45f

/**
 * Waits, bounded, for [condition], and on a timeout says [what] never came and what [now]
 * showed at the end, not the bare "Condition still not satisfied after 20000 ms".
 */
internal fun ComposeContentTestRule.awaitThat(
    what: String,
    now: () -> Any?,
    timeoutMillis: Long = FLOOR_WAIT_MS,
    condition: () -> Boolean,
) {
    try {
        waitUntil(timeoutMillis = timeoutMillis, condition = condition)
    } catch (timedOut: ComposeTimeoutException) {
        throw AssertionError("$what, not within $timeoutMillis ms; at the end: ${now()}", timedOut)
    }
}

/** What a tap is announced as doing ("double-tap to …"), or null when the node gives no label. */
internal fun SemanticsNodeInteraction.clickLabel(): String? =
    fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

/** Every content description this node carries once its children are merged in. */
internal fun SemanticsNodeInteraction.spokenDescriptions(): List<String> =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

/** Every piece of visible text this node carries once its children are merged in. */
internal fun SemanticsNodeInteraction.mergedTexts(): List<String> =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

/**
 * The text of this node as Compose laid it out, for its lines and whether it fits. Bounds
 * alone cannot show that, since a text allowed to overflow keeps the box it was measured
 * into and simply draws beyond it.
 */
internal fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
    checkNotNull(action) { "this node is not a laid-out text" }.invoke(layouts)
    return layouts.single()
}

/**
 * Whether the text is laid out whole in the width it got: its natural one-line width is no
 * wider than its box. The layout's own `didOverflowWidth` cannot answer this here. The
 * result semantics hands back is rebuilt at the full width on offer, so every text narrower
 * than its room would read as overflowing.
 */
internal fun TextLayoutResult.fitsItsWidth(): Boolean = multiParagraph.intrinsics.maxIntrinsicWidth <= size.width

/**
 * Opens the keypad of the numeral [on] — with a tap, or with the TalkBack action [byAction]
 * when one is named — checks it with [whileOpen], types [value], presses Set and lets the
 * clock run again.
 *
 * The keypad's field takes focus and blinks its cursor for as long as it is open, and
 * Robolectric's auto-advancing clock chases that animation without end: the first test
 * that typed a number hung for a minute and took the JVM's heap with it. Stepping the
 * clock by hand while the dialog is up is enough to compose it, type and confirm. The
 * dialog's window attaches on the main looper, which the held clock no longer drives, so
 * each step runs one Compose frame and one looper frame together. A test asserts the
 * keypad exists rather than that it is displayed, and Set is pressed through its click
 * action once it says it is enabled, so neither depends on the dialog window's layout
 * pass. The clock is handed back even when a check fails, so one failure stays one
 * failure rather than a held clock for whatever the test does next.
 */
internal fun ComposeContentTestRule.withKeypad(
    on: SemanticsNodeInteraction,
    value: String,
    byAction: String? = null,
    whileOpen: () -> Unit = {},
) {
    mainClock.autoAdvance = false
    try {
        if (byAction == null) on.performClick() else runCustomAction(on, byAction)
        settleKeypad()
        whileOpen()
        onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement(value)
        settleKeypad()
        onNodeWithText("Set").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        settleKeypad()
    } finally {
        mainClock.autoAdvance = true
    }
    waitForIdle()
}

/**
 * Runs [block] with the clock held, then hands it back and lets the screen settle.
 *
 * For dialogs with a text field other than the keypad (the custom rest length): typing
 * focuses the field, and its blinking cursor keeps an auto-advancing clock busy for good, as
 * [withKeypad] explains. Inside, step the screen with [settle] after each tap; a dialog that
 * refuses its value stays up, so the block closes it before the clock is handed back.
 */
internal fun ComposeContentTestRule.holdingTheClock(block: () -> Unit) {
    mainClock.autoAdvance = false
    try {
        block()
        settle()
    } finally {
        mainClock.autoAdvance = true
    }
    waitForIdle()
}

/** A few frames with the clock held: one Compose frame and one main-looper frame each. */
internal fun ComposeContentTestRule.settle() = settleKeypad()

/**
 * Taps with [tap] where no keypad may open, and asserts none did. The clock is held for the
 * tap, as in [withKeypad], so a regression that opens the keypad anyway fails here within a
 * few frames instead of chasing the field's cursor until the heap runs out.
 */
internal fun ComposeContentTestRule.assertTapOpensNoKeypad(tap: () -> Unit) {
    mainClock.autoAdvance = false
    try {
        tap()
        settleKeypad()
        onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
    } finally {
        mainClock.autoAdvance = true
    }
}

private fun ComposeContentTestRule.settleKeypad() {
    repeat(KEYPAD_SETTLE_FRAMES) {
        mainClock.advanceTimeByFrame()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
    }
}

private const val KEYPAD_SETTLE_FRAMES = 20
private const val FRAME_MS = 16L
