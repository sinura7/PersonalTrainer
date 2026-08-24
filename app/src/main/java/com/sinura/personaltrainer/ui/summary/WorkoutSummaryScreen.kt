package com.sinura.personaltrainer.ui.summary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.StatTile
import com.sinura.personaltrainer.ui.theme.GoldContainer
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
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
    val summary = state.summary

    // Back is Done. The workout behind this screen is finished and gone from the stack, so
    // there is nowhere else back could sensibly lead.
    BackHandler(enabled = !state.isLoading) { onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        when {
            state.isLoading -> ScreenLoading()

            state.missing || !summary.hasWork -> {
                EmptyState(
                    title = "Workout saved",
                    body = "It is in your history. Nothing to summarise from this one.",
                    actionLabel = "Done",
                    onAction = onDone,
                    modifier = Modifier.padding(Metrics.gutter),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
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
                        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.cardGap)) {
                            StatTile(
                                label = "Working sets",
                                value = summary.workingSets.toString(),
                                modifier = Modifier.weight(1f),
                                valueColor = TextPrimary,
                            )
                            StatTile(
                                label = "Duration",
                                value = summary.durationMinutes.toString(),
                                modifier = Modifier.weight(1f),
                                unit = "min",
                                valueColor = TextPrimary,
                            )
                        }
                    }

                    if (summary.recordCount > 0) {
                        item(key = "records") { PersonalRecordPanel(summary = summary) }
                    }

                    item(key = "lifts-label") {
                        GymSectionHeader(
                            title = "Lifts",
                            modifier = Modifier.padding(top = Metrics.space2),
                        )
                    }
                    item(key = "lifts") { LiftBreakdown(summary = summary, unit = unit) }

                    if (summary.notes.isNotBlank()) {
                        item(key = "notes") {
                            GymCard {
                                Kicker("Notes")
                                Text(summary.notes, style = InstrumentType.body, color = TextSecondary)
                            }
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
    val target = remember(summary.volumeKg, unit) {
        WeightConverter.volumeAnimationTarget(summary.volumeKg, unit)
    }

    var played by rememberSaveable { mutableStateOf(false) }
    var counting by remember { mutableStateOf(played) }
    LaunchedEffect(Unit) {
        counting = true
        played = true
    }
    val shown by animateIntAsState(
        targetValue = if (counting) target else 0,
        animationSpec = tween(durationMillis = Motion.DRAW, easing = Motion.Standard),
        label = "summary-volume",
    )

    val finalLabel = remember(summary.volumeKg, unit) {
        WeightConverter.formatVolumeLabel(summary.volumeKg, unit)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        Kicker("Workout complete", color = Volt)
        Text(
            summary.title,
            style = InstrumentType.title,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(dateLabel, style = InstrumentType.caption, color = TextTertiary)
        Column(
            modifier = Modifier
                .padding(top = Metrics.space5)
                // One node for the whole readout, holding the settled value: a screen reader
                // must never be handed a number that is still counting.
                .semantics(mergeDescendants = true) {
                    contentDescription = "Total volume $finalLabel"
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
                Text(
                    unit.suffix,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = Metrics.space2),
                    style = InstrumentType.unit,
                    color = TextSecondary,
                )
            }
            Kicker("Total volume", color = TextTertiary)
        }
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
private fun PersonalRecordPanel(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    val lines = remember(summary) {
        summary.highlights
            .filter { it.records.isNotEmpty() }
            .map { it.exerciseName to it.records.joinToString(" · ") { kind -> kind.celebrationLabel } }
    }
    var revealed by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(lines.size) {
        while (revealed < lines.size) {
            delay(RECORD_STAGGER_MS)
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
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = PrGold)
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
        lines.forEachIndexed { index, (name, detail) ->
            AnimatedVisibility(
                visible = index < revealed,
                enter = scaleIn(initialScale = 0.92f, animationSpec = Motion.celebrate()) +
                    fadeIn(tween(Motion.FAST)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                    Text(name, style = InstrumentType.bodyStrong, color = TextPrimary)
                    Text(detail, style = InstrumentType.caption, color = PrGold)
                }
            }
        }
    }
}

/**
 * Every lift of the session, as one instrument panel.
 *
 * One card per lift made six lifts look like six unrelated objects and set their numbers as
 * prose — "3 sets · 1,240 kg" in body text, where nothing lines up between rows. Here the two
 * numbers a lifter compares between lifts sit in fixed columns.
 */
@Composable
private fun LiftBreakdown(summary: WorkoutSummary, unit: WeightUnit) {
    GroupedList {
        summary.highlights.forEachIndexed { index, highlight ->
            if (index > 0) HairlineDivider()
            InstrumentRow(
                title = highlight.exerciseName,
                subtitle = highlight.topSet?.let { top ->
                    // Through SetCopy, not raw tonnage: the highlight already knows how this
                    // lift is measured, and a set of push-ups read "Top set 0 kg × 20" here
                    // long after every other surface had stopped saying that.
                    "Top set " + SetCopy.setLine(top.weightKg, top.reps, highlight.loadClass, unit)
                },
                leading = { RecordMark(record = highlight.records.isNotEmpty()) },
            ) {
                MetricCluster(value = highlight.workingSets.toString(), label = "sets")
                val column = SetCopy.workColumn(highlight.work, unit)
                MetricCluster(value = column.value, label = column.label)
            }
        }
    }
}

/**
 * Which rows in the breakdown broke something.
 *
 * Transparent rather than absent when there is no record, so the lift names stay in one
 * column down the list instead of stepping in and out by the width of the dot.
 */
@Composable
private fun RecordMark(record: Boolean) {
    Box(
        modifier = Modifier
            .size(Metrics.space2)
            .background(if (record) PrGold else Color.Transparent)
            .then(
                if (record) {
                    Modifier.semantics { contentDescription = "Personal record" }
                } else {
                    Modifier
                },
            ),
    )
}

/** Pinned, so leaving the reward screen never requires scrolling past the reward. */
@Composable
private fun SummaryActions(onDone: () -> Unit, onOpenSession: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        PrimaryGymButton(text = "Done", onClick = onDone)
        SecondaryGymButton(text = "See full session", onClick = onOpenSession)
    }
}

/**
 * The longer wording, for the one screen that is a celebration rather than a readout.
 *
 * Named `celebrationLabel` and not `label`, which is what it used to be called: the enum has a
 * member `label` of its own ("Heaviest", "Most reps", "Est. 1RM"), members always beat
 * extensions, and so the call site below silently bound to the member and this whole block was
 * dead code. A warning, never an error — which is exactly why it survived.
 */
private val PersonalRecordKind.celebrationLabel: String
    get() = when (this) {
        PersonalRecordKind.WEIGHT -> "Heaviest ever"
        PersonalRecordKind.REPS_AT_WEIGHT -> "Most reps at that weight"
        PersonalRecordKind.ESTIMATED_ONE_REP_MAX -> "Best estimated 1RM"
        PersonalRecordKind.REPS -> "Most reps ever"
    }

private const val RECORD_STAGGER_MS = 140L
private const val RECORD_BORDER_ALPHA = 0.35f
