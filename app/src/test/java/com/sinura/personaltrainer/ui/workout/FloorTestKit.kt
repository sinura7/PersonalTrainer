package com.sinura.personaltrainer.ui.workout

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.time.Duration
import org.robolectric.Shadows

/*
 * The floor's rendered tests describe a state in a line and then look at what the screen
 * does with it. These builders are the shared vocabulary for that: one lift, one session,
 * one saved set, one primary action, one dock. They used to live privately in
 * WorkoutFloorComponentsTest and WorkoutLogBarTest, which is why every new render copied
 * them by hand; one copy here keeps the fixtures from drifting apart.
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
): WorkoutDockEvents = WorkoutDockEvents(
    onPrimary = onPrimary,
    onEditFailedSave = {},
    onCancelEdit = onCancelEdit,
    onDismissError = {},
    onAnotherSet = onAnotherSet,
    onUndo = {},
    onUndoDismissed = onUndoDismissed,
    onSkipRest = {},
    onStartRest = {},
    onSelectRestDuration = {},
    onNudgeRest = {},
    onCustomRest = { true },
    onStartSetClock = {},
    onStopSetClock = {},
    onDismissRestBatteryHint = {},
    onOpenRest = onOpenRest,
    onOpenNotifications = {},
)

/**
 * Composes [content] the way the floor sees it: pounds, the app theme, and the system font
 * scale the test asks for. Font scale is the one input that reshapes the floor: from
 * `LogLoopScale.STACK_WELLS_FROM` the numerals stack and the identity's still shrinks.
 */
internal fun ComposeContentTestRule.showFloor(fontScale: Float = 1f, content: @Composable () -> Unit) {
    this.setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalWeightUnit provides FLOOR_UNIT,
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            PersonalTrainerTheme { content() }
        }
    }
}

/** The labels TalkBack offers in its actions menu for this node, in order. */
internal fun SemanticsNodeInteraction.customActionLabels(): List<String> =
    fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

/**
 * Runs the TalkBack action named [label] on the UI thread, as the actions menu would. A
 * missing action fails with the labels the node does offer, so a reworded action reads as
 * what changed rather than as an empty collection.
 */
internal fun ComposeContentTestRule.runCustomAction(node: SemanticsNodeInteraction, label: String) {
    val offered = node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    val action = offered.firstOrNull { it.label == label }
        ?: throw AssertionError("no TalkBack action \"$label\"; the node offers ${offered.map { it.label }}")
    runOnIdle { action.action() }
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
