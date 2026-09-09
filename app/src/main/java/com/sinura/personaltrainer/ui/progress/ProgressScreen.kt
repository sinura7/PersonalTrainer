package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.BodyHeatCopy
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.domain.RecommendationIntent
import com.sinura.personaltrainer.domain.RecommendationIntents
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.BodyView
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymNoticeBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.workout.StartSheetOpener
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.instrumentAnimateItem
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@Composable
fun ProgressScreen(
    onOpenLibrary: (CanonicalMuscle?) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenStartSheet: () -> Unit = {},
    viewModel: ProgressViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    var bodyView by rememberSaveable { mutableStateOf(BodyView.FRONT) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { CanonicalMuscle.valueOf(it) }.getOrNull() }
    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        // The window governs every number below it, so it belongs to the chrome rather than to
        // the content: it used to be repeated inside all three branches and scrolled away with
        // the map it labels.
        BodyWindowPicker(
            window = state.window,
            onSelectWindow = viewModel::setWindow,
            onOpenStartSheet = onOpenStartSheet,
        )

        when {
            state.isLoading -> {
                ScreenLoading()
            }

            state.error != null -> {
                // A read failed; nothing was written and nothing needs starting. The remedy is
                // to ask again, which is why this branch no longer offers "Start workout".
                EmptyState(
                    title = "Couldn’t load the map",
                    body = "Every set you have logged is still in your history — only the map " +
                        "failed to build.",
                    actionLabel = "Try again",
                    onAction = viewModel::retry,
                    modifier = Modifier.padding(Metrics.gutter),
                )
            }

            else -> {
                val snap = snapshot ?: emptySnapshot(state.window)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Metrics.gutter,
                        end = Metrics.gutter,
                        top = Metrics.space2,
                        bottom = Metrics.space7,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
                ) {
                    state.notice?.let { message ->
                        // A notice, not a failure. The ViewModel deliberately keeps `notice`
                        // apart from `error` so a working body map is never blanked by a
                        // failed side-query; rendering it in the error banner threw that
                        // distinction away and told the user something had broken.
                        item(key = "notice") {
                            GymNoticeBanner(
                                title = "Some insights are missing",
                                body = message,
                                actionLabel = "Try again",
                                onAction = viewModel::retry,
                            )
                        }
                    }
                    item(key = "map") {
                        BodyMapCard(
                            snapshot = snap,
                            view = bodyView,
                            onViewChange = { bodyView = it },
                            selected = selected,
                            onSelect = { selectedName = it.name },
                            facts = BodyHeatCopy.facts(
                                window = state.window,
                                windowSessions = snap.windowSessions,
                                daysSinceLastFinished = snap.daysSinceLastFinished,
                            ),
                        )
                    }
                    if (!snap.hasWindowWorkingSets) {
                        item(key = "window-empty") {
                            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                                Text(
                                    if (snap.hasAnyWorkingSets) {
                                        BodyHeatCopy.EMPTY_WINDOW
                                    } else {
                                        BodyHeatCopy.EMPTY_LOG
                                    },
                                    style = InstrumentType.body,
                                    color = TextSecondary,
                                    modifier = Modifier.testTag(BodyTags.EMPTY),
                                )
                                // Older work exists and a wider window would show it. The
                                // month is the widest window there is, so the tap is the
                                // same whether Day or Week came up empty.
                                if (snap.hasAnyWorkingSets && state.window != HeatWindow.CURRENT_MONTH) {
                                    SecondaryGymButton(
                                        text = BodyHeatCopy.SHOW_MONTH,
                                        onClick = { viewModel.setWindow(HeatWindow.CURRENT_MONTH) },
                                        modifier = Modifier.testTag(BodyTags.SHOW_MONTH),
                                    )
                                }
                            }
                        }
                    }
                    item(key = "muscles-header") {
                        GymSectionHeader("Muscles")
                    }
                    item(key = "muscles") {
                        GroupedList(modifier = Modifier.testTag(BodyTags.MUSCLES)) {
                            muscleRows(snap).forEachIndexed { index, load ->
                                if (index > 0) HairlineDivider()
                                MuscleHeatRow(
                                    load = load,
                                    selected = selected == load.muscle,
                                    onClick = { selectedName = load.muscle.name },
                                    unit = unit,
                                )
                            }
                        }
                    }
                    if (state.recommendations.isNotEmpty()) {
                        item(key = "recommended-header") {
                            GymSectionHeader(
                                "Recommended",
                                modifier = Modifier.padding(top = Metrics.space5),
                            )
                        }
                        items(state.recommendations, key = { it.id }) { rec ->
                            RecommendationCard(
                                recommendation = rec,
                                onClick = {
                                    when (val intent = RecommendationIntents.from(rec)) {
                                        is RecommendationIntent.OpenLibrary ->
                                            onOpenLibrary(intent.muscle)
                                        is RecommendationIntent.OpenExercise ->
                                            onOpenExercise(intent.exerciseId)
                                        RecommendationIntent.StartWorkout -> onOpenStartSheet()
                                        RecommendationIntent.OpenRoutines -> onOpenRoutines()
                                        RecommendationIntent.OpenBodyMap ->
                                            selectedName = rec.actionMuscle?.name
                                        RecommendationIntent.MarkLighterWeek ->
                                            viewModel.markLighterWeek()
                                        null -> Unit
                                    }
                                },
                                modifier = instrumentAnimateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { muscle ->
        val snap = snapshot ?: emptySnapshot(state.window)
        MuscleDetailSheet(
            load = snap.load(muscle),
            unit = unit,
            windowLabel = state.window.label,
            onDismiss = { selectedName = null },
            onOpenExercise = { exerciseId ->
                selectedName = null
                onOpenExercise(exerciseId)
            },
            onFindLifts = {
                selectedName = null
                onOpenLibrary(muscle)
            },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BodyWindowPicker(
    window: HeatWindow,
    onSelectWindow: (HeatWindow) -> Unit,
    onOpenStartSheet: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Metrics.gutter,
                end = Metrics.gutter,
                top = Metrics.space2,
                bottom = Metrics.space2,
            ),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Body",
                modifier = Modifier.weight(1f),
                style = InstrumentType.display,
                color = TextPrimary,
            )
            StartSheetOpener(
                onOpen = onOpenStartSheet,
                modifier = Modifier.testTag(BodyTags.START_SHEET),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            HeatWindow.entries.forEach { entry ->
                InstrumentChip(
                    label = entry.shortLabel,
                    selected = window == entry,
                    onClick = { onSelectWindow(entry) },
                    modifier = Modifier.testTag(BodyTags.window(entry)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MuscleDetailSheet(
    load: MuscleLoadSummary,
    unit: WeightUnit,
    windowLabel: String,
    onDismiss: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onFindLifts: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface3,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A muscle with a dozen contributing lifts overflows a fully expanded sheet,
                // and the overflow is silently unreachable without this.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.sectionGap),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Kicker("$windowLabel · ${load.band.legendLabel}")
                Text(
                    load.muscle.displayName,
                    style = InstrumentType.display,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space6),
            ) {
                val column = SetCopy.workColumn(load.work, unit)
                MetricCluster(
                    value = column.value,
                    label = column.label,
                    modifier = Modifier.weight(1f),
                    valueStyle = InstrumentType.numeralLg,
                    horizontalAlignment = Alignment.Start,
                )
                MetricCluster(
                    value = load.workingSets.toString(),
                    label = "sets",
                    modifier = Modifier.weight(1f),
                    valueStyle = InstrumentType.numeralLg,
                    horizontalAlignment = Alignment.Start,
                )
                MetricCluster(
                    value = load.sessionCount.toString(),
                    label = "sessions",
                    modifier = Modifier.weight(1f),
                    valueStyle = InstrumentType.numeralLg,
                    horizontalAlignment = Alignment.Start,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Kicker("Last trained", modifier = Modifier.weight(1f))
                Text(
                    recencyLabel(load),
                    style = InstrumentType.bodyStrong,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (load.exercises.isEmpty()) {
                Text(
                    "Nothing in this window maps to ${load.muscle.displayName.lowercase()}.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            } else {
                GymSectionHeader("Contributors")
                GroupedList {
                    load.exercises.forEachIndexed { index, exercise ->
                        if (index > 0) HairlineDivider()
                        InstrumentRow(
                            title = exercise.exerciseName,
                            onClick = { onOpenExercise(exercise.exerciseId) },
                        ) {
                            MetricCluster(value = exercise.workingSets.toString(), label = "sets")
                            val column = SetCopy.workColumn(exercise.work, unit)
                            MetricCluster(value = column.value, label = column.label)
                        }
                    }
                }
            }
            SecondaryGymButton(
                text = "Find ${load.muscle.catalogLabel.lowercase()} lifts",
                onClick = onFindLifts,
                modifier = Modifier
                    .testTag(BodyTags.FIND_LIFTS)
                    .semantics {
                        contentDescription =
                            "Find ${load.muscle.catalogLabel.lowercase()} lifts"
                    },
            )
        }
    }
}

/** The body map draws the ten mapped muscles; "Other" only earns a row when it has work in it. */
private fun muscleRows(snapshot: BodyHeatSnapshot): List<MuscleLoadSummary> =
    snapshot.mapLoads + listOfNotNull(
        snapshot.load(CanonicalMuscle.OTHER).takeIf { it.workingSets > 0 },
    )

private fun emptySnapshot(window: HeatWindow): BodyHeatSnapshot = BodyHeatSnapshot(
    window = window,
    windowStartMs = 0L,
    generatedAtMs = 0L,
    loads = emptyList(),
    hasAnyWorkingSets = false,
    hasWindowWorkingSets = false,
)
