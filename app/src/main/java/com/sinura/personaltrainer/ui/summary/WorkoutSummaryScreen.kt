package com.sinura.personaltrainer.ui.summary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.ui.findActivity
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.SessionHighlight
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.PersonalRecordCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.SummaryCopy
import com.sinura.personaltrainer.domain.SummaryHeadline
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.OutlinedMarks
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.StatTile
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.GoldContainer
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.instrumentTween
import com.sinura.personaltrainer.ui.theme.recordEnter
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * What the workout amounted to, shown once, immediately after finishing.
 *
 * Finishing used to pop silently back to Home. The app knew a session had just set two
 * personal bests and said nothing about it — spending the one moment it has the lifter's full
 * attention on a screen transition.
 *
 * It then spent that moment on a titled page of interchangeable cards whose headline numbers
 * were set *smaller* than the stepper numerals from the workout it was summarising. There is
 * no top bar here at all: the session's one number leads, the two supporting ones flank it,
 * and records are the only thing on the screen allowed to look like an event.
 */
@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: WorkoutSummaryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current

    // The first moment this workout exists in a form a backup would contain, and one of the
    // few places an Activity is reachable — Drive authorization needs one. Fires once per
    // summary entry; the ViewModel is idempotent across a process-death rebuild.
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(Unit) { viewModel.maybeAutoBackup(activity) }

    // Back is Done. The workout behind this screen is finished and gone from the stack, so
    // there is nowhere else back could sensibly lead.
    BackHandler(enabled = !state.isLoading) { onDone() }

    WorkoutSummaryContent(
        state = state,
        unit = unit,
        onRetry = viewModel::retry,
        onOpenSession = onOpenSession,
        onDone = onDone,
    )
}

@Composable
internal fun WorkoutSummaryContent(
    state: WorkoutSummaryUiState,
    unit: WeightUnit,
    onRetry: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDone: () -> Unit,
) {
    val summary = state.summary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        when {
            state.isLoading -> ScreenLoading()

            // Before the missing branch on purpose: a failed read also has no summary, and it
            // must not be shown as a deleted session — nor, unless the row was read, as a saved
            // one. Retry re-reads; it never writes.
            state.failed -> SummaryUnavailable(
                savedConfirmed = state.savedConfirmed,
                onRetry = onRetry,
                onOpenSession = { onOpenSession(state.sessionId) },
                onDone = onDone,
            )

            state.missing -> EmptyState(
                scene = EmptyScene.GONE,
                title = SummaryCopy.MISSING_TITLE,
                body = SummaryCopy.MISSING_BODY,
                actionLabel = SummaryCopy.DONE,
                onAction = onDone,
                actionTag = SummaryTags.DONE,
                modifier = Modifier.testTag(SummaryTags.CONTENT)
                    .verticalScroll(rememberScrollState()).padding(Metrics.gutter),
            )

            !summary.hasWork -> {
                // The row was read and holds only warm-ups. "Saved" is earned here by the
                // finished row being the evidence, not by this route having been reached; a
                // row that is somehow not finished gets the same facts without that word.
                EmptyState(
                    scene = EmptyScene.LOG,
                    title = if (state.savedConfirmed) SummaryCopy.SAVED_NO_WORK_TITLE else SummaryCopy.NO_WORK_TITLE,
                    body = if (state.savedConfirmed) SummaryCopy.SAVED_NO_WORK_BODY else SummaryCopy.NO_WORK_BODY,
                    actionLabel = SummaryCopy.DONE,
                    onAction = onDone,
                    actionTag = SummaryTags.DONE,
                    modifier = Modifier.testTag(SummaryTags.CONTENT)
                        .verticalScroll(rememberScrollState()).padding(Metrics.gutter),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).testTag(SummaryTags.CONTENT),
                    contentPadding = PaddingValues(
                        start = Metrics.gutter,
                        end = Metrics.gutter,
                        top = Metrics.space4,
                        bottom = Metrics.space6,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    item(key = "hero") { SummaryHero(summary = summary, unit = unit) }

                    item(key = "tiles") {
                        val stack = LogLoopScale.stackTiles(LocalDensity.current.fontScale)
                        // A mixed day leads with kilograms; its bodyweight reps are real work
                        // too and get a tile rather than vanishing into the hero's rounding.
                        val repsTile = summary.bodyweightReps
                            .takeIf { it > 0 && summary.headline is SummaryHeadline.Volume }
                        if (stack) {
                            Column(verticalArrangement = Arrangement.spacedBy(Metrics.cardGap)) {
                                StatTile(
                                    label = SummaryCopy.WORKING_SETS,
                                    value = summary.workingSets.toString(),
                                    modifier = Modifier.fillMaxWidth(),
                                    valueColor = TextPrimary,
                                )
                                if (repsTile != null) {
                                    StatTile(
                                        label = SummaryCopy.BODYWEIGHT_REPS,
                                        value = repsTile.toString(),
                                        modifier = Modifier.fillMaxWidth(),
                                        valueColor = TextPrimary,
                                    )
                                }
                                StatTile(
                                    label = SummaryCopy.DURATION,
                                    value = summary.durationMinutes.toString(),
                                    modifier = Modifier.fillMaxWidth(),
                                    unit = "min",
                                    valueColor = TextPrimary,
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.cardGap)) {
                                StatTile(
                                    label = SummaryCopy.WORKING_SETS,
                                    value = summary.workingSets.toString(),
                                    modifier = Modifier.weight(1f),
                                    valueColor = TextPrimary,
                                )
                                if (repsTile != null) {
                                    StatTile(
                                        label = SummaryCopy.BODYWEIGHT_REPS,
                                        value = repsTile.toString(),
                                        modifier = Modifier.weight(1f),
                                        valueColor = TextPrimary,
                                    )
                                }
                                StatTile(
                                    label = SummaryCopy.DURATION,
                                    value = summary.durationMinutes.toString(),
                                    modifier = Modifier.weight(1f),
                                    unit = "min",
                                    valueColor = TextPrimary,
                                )
                            }
                        }
                    }

                    if (summary.recordCount > 0) {
                        item(key = "records") {
                            PersonalRecordPanel(summary = summary, exercises = state.highlightExercises)
                        }
                    }

                    item(key = "lifts-label") {
                        GymSectionHeader(
                            title = "Lifts",
                            modifier = Modifier.padding(top = Metrics.space2),
                        )
                    }
                    item(key = "lifts") {
                        LiftBreakdown(summary = summary, unit = unit, exercises = state.highlightExercises)
                    }

                    if (summary.notes.isNotBlank()) {
                        item(key = "notes") {
                            GymCard {
                                Kicker("Notes")
                                Text(summary.notes, style = InstrumentType.body, color = TextSecondary)
                            }
                        }
                    }

                    // A caption, not a card: an automatic copy is housekeeping, and the
                    // records above are the only thing on this screen allowed to be an event.
                    state.autoBackup?.let { line ->
                        item(key = "auto-backup") {
                            Text(
                                text = line,
                                style = InstrumentType.caption,
                                color = TextTertiary,
                            )
                        }
                    }
                }

                HairlineDivider(startIndent = 0.dp)
                SummaryActions(
                    onDone = onDone,
                    onOpenSession = { onOpenSession(summary.sessionId) },
                )
            }
        }
    }
}

/**
 * The session as one number.
 *
 * The count-up is deliberately gated on a saveable flag rather than played whenever this
 * composes: a rotation, a theme change or a process-death restore would otherwise replay the
 * celebration, which turns a reward into a glitch. Tabular figures do the rest — the digits
 * settle in place instead of jittering the layout on every frame.
 */
@Composable
private fun SummaryHero(summary: WorkoutSummary, unit: WeightUnit) {
    val dateLabel = remember(summary.performedAtMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(summary.performedAtMs))
    }
    // The hero is the measure the session was made of — kilograms, or reps, or sets — so a
    // set of push-ups is never announced as "0 kg". See WorkoutSummary.headline.
    val headline = summary.headline
    val target = remember(headline, unit) {
        when (headline) {
            is SummaryHeadline.Volume -> WeightConverter.volumeAnimationTarget(headline.kg, unit)
            is SummaryHeadline.BodyweightReps -> headline.reps
            is SummaryHeadline.WorkingSets -> headline.count
        }
    }
    val heroUnit: String? = when (headline) {
        is SummaryHeadline.Volume -> unit.suffix
        is SummaryHeadline.BodyweightReps -> null
        is SummaryHeadline.WorkingSets -> null
    }
    val heroLabel = when (headline) {
        is SummaryHeadline.Volume -> SummaryCopy.TOTAL_VOLUME
        is SummaryHeadline.BodyweightReps -> SummaryCopy.BODYWEIGHT_REPS
        is SummaryHeadline.WorkingSets -> SummaryCopy.WORKING_SETS
    }

    var played by rememberSaveable { mutableStateOf(false) }
    var counting by remember { mutableStateOf(played) }
    LaunchedEffect(Unit) {
        counting = true
        played = true
    }
    val shown by animateIntAsState(
        targetValue = if (counting) target else 0,
        animationSpec = instrumentTween(Motion.DRAW),
        label = "summary-volume",
    )

    val finalLabel = remember(headline, unit) {
        when (headline) {
            is SummaryHeadline.Volume -> WeightConverter.formatVolumeLabel(headline.kg, unit)
            is SummaryHeadline.BodyweightReps -> "${headline.reps} bodyweight reps"
            is SummaryHeadline.WorkingSets -> "${headline.count} working sets"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        Kicker(SummaryCopy.COMPLETE)
        Text(
            summary.title,
            style = InstrumentType.title,
            color = TextPrimary,
        )
        Text(dateLabel, style = InstrumentType.caption, color = TextTertiary)
        Column(
            modifier = Modifier
                .padding(top = Metrics.space5)
                // One node for the whole readout, holding the settled value: a screen reader
                // must never be handed a number that is still counting.
                .semantics(mergeDescendants = true) {
                    contentDescription = "$heroLabel $finalLabel"
                },
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    com.sinura.personaltrainer.util.QuantityFormat.formatGroupedNumber(shown.toDouble()),
                    modifier = Modifier.alignByBaseline(),
                    style = InstrumentType.numeralXl,
                    color = TextPrimary,
                    maxLines = 1,
                )
                if (heroUnit != null) {
                    Text(
                        heroUnit,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = Metrics.space2),
                        style = InstrumentType.unit,
                        color = TextSecondary,
                    )
                }
            }
            Kicker(heroLabel, color = TextTertiary)
        }
    }
}

/**
 * The summary could not be built. Two different sentences, by what the read established:
 * with the finished row in hand the workout is saved and only the summary is missing; without
 * it nothing is known, and the screen says so instead of guessing either way. Retry re-reads
 * and never writes; Done goes Home exactly as it does from the receipt.
 */
@Composable
private fun SummaryUnavailable(
    savedConfirmed: Boolean,
    onRetry: () -> Unit,
    onOpenSession: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SummaryTags.CONTENT)
            .verticalScroll(rememberScrollState())
            .padding(Metrics.gutter),
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        EmptyState(
            scene = EmptyScene.RETRY,
            title = if (savedConfirmed) {
                SummaryCopy.SAVED_SUMMARY_UNAVAILABLE_TITLE
            } else {
                SummaryCopy.UNAVAILABLE_TITLE
            },
            body = if (savedConfirmed) {
                SummaryCopy.SAVED_SUMMARY_UNAVAILABLE_BODY
            } else {
                SummaryCopy.UNAVAILABLE_BODY
            },
            actionLabel = DataHealthCopy.RETRY,
            onAction = onRetry,
            actionTag = SummaryTags.RETRY,
        )
        if (savedConfirmed) {
            SecondaryGymButton(
                text = SummaryCopy.OPEN_SESSION,
                onClick = onOpenSession,
                modifier = Modifier.testTag(SummaryTags.OPEN_SESSION),
            )
        }
        SecondaryGymButton(
            text = SummaryCopy.DONE,
            onClick = onDone,
            modifier = Modifier.testTag(SummaryTags.DONE),
        )
    }
}

/**
 * The records, in gold, as their own object.
 *
 * These used to be a `primaryContainer` card — the same anatomy, radius and container colour
 * as every other card on the screen and as the error banner elsewhere in the product, so the
 * app's best news and its failures were drawn identically. Gold belongs to records and to
 * nothing else, and the entrance is staggered because three records landing at once read as
 * one paragraph while three landing in sequence read as three events.
 */
@Composable
private fun PersonalRecordPanel(
    summary: WorkoutSummary,
    exercises: Map<String, Exercise>,
    modifier: Modifier = Modifier,
) {
    val lines = remember(summary) {
        summary.highlights
            .filter { it.records.isNotEmpty() }
    }
    var revealed by rememberSaveable { mutableIntStateOf(0) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(lines.size, reduced) {
        if (reduced) {
            revealed = lines.size
            return@LaunchedEffect
        }
        while (revealed < lines.size) {
            delay(Motion.RECORD_STAGGER_MS)
            revealed += 1
        }
    }

    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GoldContainer, shape)
            .border(Metrics.hairline, PrGold.copy(alpha = RECORD_BORDER_ALPHA), shape)
            .padding(Metrics.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(OutlinedMarks.EmojiEvents, contentDescription = null, tint = PrGold)
            Text(
                if (summary.recordCount == 1) {
                    "1 personal record"
                } else {
                    "${summary.recordCount} personal records"
                },
                style = InstrumentType.title,
                color = PrGold,
            )
        }
        lines.forEachIndexed { index, highlight ->
            AnimatedVisibility(
                visible = index < revealed,
                enter = recordEnter(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                    SummaryLiftIdentity(highlight, exercises, artTag = SummaryTags.recordArt(highlight.exerciseId))
                    Text(
                        highlight.records.joinToString(" · ", transform = PersonalRecordCopy::celebration),
                        style = InstrumentType.caption,
                        color = PrGold,
                    )
                }
            }
        }
    }
}

/**
 * The recorded receipt, with matching exercise identity and complete values.
 * Names and values wrap independently of the artwork and metrics, so larger text does
 * not turn a receipt into an abbreviation or crowd out its saved result.
 */
@Composable
private fun LiftBreakdown(summary: WorkoutSummary, unit: WeightUnit, exercises: Map<String, Exercise>) {
    GroupedList {
        summary.highlights.forEachIndexed { index, highlight ->
            if (index > 0) HairlineDivider()
            Column(
                modifier = Modifier.fillMaxWidth().testTag(SummaryTags.lift(highlight.exerciseId))
                    .padding(Metrics.space3),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                SummaryLiftIdentity(highlight, exercises, artTag = SummaryTags.art(highlight.exerciseId))
                highlight.topSet?.let { top ->
                    // Through SetCopy, not raw tonnage: the highlight already knows how this
                    // lift is measured, and a set of push-ups read "Top set 0 kg × 20" here
                    // long after every other surface had stopped saying that.
                    Text(
                        "Top set " + SetCopy.setLine(top.weightKg, top.reps, highlight.loadClass, unit),
                        style = InstrumentType.body,
                        color = TextSecondary,
                    )
                }
                if (highlight.records.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecordMark(record = true)
                        Text("Personal record", style = InstrumentType.caption, color = PrGold)
                    }
                }
                val column = SetCopy.workColumn(highlight.work, unit)
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    ReceiptMetric(highlight.workingSets.toString(), "sets", Modifier.weight(1f))
                    ReceiptMetric(column.value, column.label, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryLiftIdentity(
    highlight: SessionHighlight,
    exercises: Map<String, Exercise>,
    artTag: String,
) {
    val exercise = exercises[highlight.exerciseId]?.copy(name = highlight.exerciseName) ?: Exercise(
        id = highlight.exerciseId,
        name = highlight.exerciseName,
        muscleGroup = "",
        notes = "",
        isCustom = true,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.testTag(artTag)) { ExerciseThumb(exercise, size = ThumbSize.header) }
        Text(
            highlight.exerciseName,
            modifier = Modifier.weight(1f),
            style = InstrumentType.bodyStrong,
            color = TextPrimary,
        )
    }
}

@Composable
private fun ReceiptMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        Text(value, style = InstrumentType.numeralSm, color = TextPrimary)
        Text(label, style = InstrumentType.kicker, color = TextTertiary)
    }
}

/** Decorative accent beside the explicit, readable Personal record label. */
@Composable
private fun RecordMark(record: Boolean) {
    Box(
        modifier = Modifier
            .size(Metrics.space2)
            .background(if (record) PrGold else Color.Transparent)
    )
}

/** Pinned, so leaving the reward screen never requires scrolling past the reward.
 *  Owns the system-nav inset: this route has no tab bar. */
@Composable
internal fun SummaryActions(onDone: () -> Unit, onOpenSession: () -> Unit) {
    PinnedDock(
        volt = {
            PrimaryGymButton(
                text = SummaryCopy.DONE,
                onClick = onDone,
                modifier = Modifier.testTag(SummaryTags.DONE),
            )
        },
        secondary = {
            SecondaryGymButton(
                text = SummaryCopy.OPEN_SESSION,
                onClick = onOpenSession,
                modifier = Modifier.testTag(SummaryTags.OPEN_SESSION),
            )
        },
    )
}

object SummaryTags {
    const val CONTENT = "summary-content"
    const val DONE = "summary-done"
    const val OPEN_SESSION = "summary-open-session"
    const val RETRY = "summary-retry"
    fun lift(exerciseId: String) = "summary-lift-$exerciseId"
    fun art(exerciseId: String) = "summary-art-$exerciseId"
    fun recordArt(exerciseId: String) = "summary-record-art-$exerciseId"
}


private const val RECORD_BORDER_ALPHA = 0.35f
