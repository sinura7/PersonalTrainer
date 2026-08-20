package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@Composable
fun ProgressScreen(
    onOpenLibrary: (String?) -> Unit,
    onStartWorkout: () -> Unit,
    onOpenRoutines: () -> Unit,
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
        ProgressHeader(window = state.window, onSelectWindow = viewModel::setWindow)

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
                    onAction = { viewModel.setWindow(state.window) },
                    modifier = Modifier.padding(Metrics.gutter),
                )
            }

            snapshot == null || !snapshot.hasAnyWorkingSets -> {
                EmptyState(
                    title = "See what you trained",
                    body = "Working-set volume lights the map for the window you pick.",
                    actionLabel = "Start workout",
                    onAction = onStartWorkout,
                    modifier = Modifier.padding(Metrics.gutter),
                )
            }

            else -> {
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
                        item(key = "notice") { GymErrorBanner(message) }
                    }
                    if (!snapshot.hasWindowWorkingSets) {
                        item(key = "window-empty") {
                            Text(
                                "You haven’t logged a working set ${state.window.sentenceLabel()}. " +
                                    "Each muscle below still shows how long ago it was last trained.",
                                style = InstrumentType.body,
                                color = TextSecondary,
                            )
                        }
                    }
                    item(key = "map") {
                        BodyMapCard(
                            snapshot = snapshot,
                            view = bodyView,
                            onViewChange = { bodyView = it },
                            selected = selected,
                            onSelect = { selectedName = it.name },
                        )
                    }
                    if (state.recommendations.isNotEmpty()) {
                        item(key = "recommended-header") {
                            GymSectionHeader("Recommended", modifier = Modifier.padding(top = Metrics.space5))
                        }
                        items(state.recommendations, key = { it.id }) { rec ->
                            RecommendationCard(
                                recommendation = rec,
                                onClick = {
                                    dispatchRecommendation(
                                        recommendation = rec,
                                        onOpenLibrary = onOpenLibrary,
                                        onStartWorkout = onStartWorkout,
                                        onOpenRoutines = onOpenRoutines,
                                        onOpenProgress = { selectedName = rec.actionMuscle?.name },
                                    )
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    item(key = "muscles-header") {
                        GymSectionHeader("Muscles", modifier = Modifier.padding(top = Metrics.space5))
                    }
                    item(key = "muscles") {
                        GroupedList {
                            muscleRows(snapshot).forEachIndexed { index, load ->
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
                }
            }
        }
    }

    selected?.let { muscle ->
        snapshot?.let { snap ->
            MuscleDetailSheet(
                load = snap.load(muscle),
                unit = unit,
                windowLabel = state.window.label,
                onDismiss = { selectedName = null },
                onFindLifts = {
                    selectedName = null
                    onOpenLibrary(muscle.catalogLabel)
                },
            )
        }
    }
}

@Composable
private fun ProgressHeader(
    window: HeatWindow,
    onSelectWindow: (HeatWindow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Metrics.gutter,
                end = Metrics.gutter,
                top = Metrics.space2,
                bottom = Metrics.space3,
            ),
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Text("Body", style = InstrumentType.display, color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            HeatWindow.entries.forEach { entry ->
                InstrumentChip(
                    label = entry.pickerLabel,
                    selected = window == entry,
                    onClick = { onSelectWindow(entry) },
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
                Kicker("$windowLabel · ${load.band.legendLabel} load")
                Text(load.muscle.displayName, style = InstrumentType.display, color = TextPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space6)) {
                MetricCluster(
                    value = WeightConverter.formatGroupedNumber(
                        WeightConverter.toDisplayValue(load.volumeKg, unit),
                    ),
                    label = unit.suffix,
                    valueStyle = InstrumentType.numeralLg,
                    horizontalAlignment = Alignment.Start,
                )
                MetricCluster(
                    value = load.workingSets.toString(),
                    label = "sets",
                    valueStyle = InstrumentType.numeralLg,
                    horizontalAlignment = Alignment.Start,
                )
                MetricCluster(
                    value = load.sessionCount.toString(),
                    label = "sessions",
                    valueStyle = InstrumentType.numeralLg,
                    horizontalAlignment = Alignment.Start,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Kicker("Last trained")
                Text(recencyLabel(load), style = InstrumentType.bodyStrong, color = TextPrimary)
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
                        InstrumentRow(title = exercise.exerciseName) {
                            MetricCluster(value = exercise.workingSets.toString(), label = "sets")
                            MetricCluster(
                                value = WeightConverter.formatGroupedNumber(
                                    WeightConverter.toDisplayValue(exercise.volumeKg, unit),
                                ),
                                label = unit.suffix,
                            )
                        }
                    }
                }
            }
            SecondaryGymButton(
                text = "Find ${load.muscle.catalogLabel.lowercase()} lifts",
                onClick = onFindLifts,
            )
        }
    }
}

/** The body map draws the ten mapped muscles; "Other" only earns a row when it has work in it. */
private fun muscleRows(snapshot: BodyHeatSnapshot): List<MuscleLoadSummary> =
    snapshot.mapLoads + listOfNotNull(
        snapshot.load(CanonicalMuscle.OTHER).takeIf { it.workingSets > 0 },
    )

/**
 * A bare "7" beside the word "Week" made the picker read as two different kinds of thing.
 * Both units are spelled now, and the labels are the picker's own — no copy elsewhere has to
 * quote them back at the reader.
 */
private val HeatWindow.pickerLabel: String
    get() = when (this) {
        HeatWindow.LAST_7_DAYS -> "7D"
        HeatWindow.LAST_14_DAYS -> "14D"
        HeatWindow.CURRENT_WEEK -> "THIS WEEK"
    }

/** The window as it reads inside a sentence about training, preposition included. */
private fun HeatWindow.sentenceLabel(): String = when (this) {
    HeatWindow.LAST_7_DAYS, HeatWindow.LAST_14_DAYS -> "in the ${label.lowercase()}"
    HeatWindow.CURRENT_WEEK -> "this week"
}
