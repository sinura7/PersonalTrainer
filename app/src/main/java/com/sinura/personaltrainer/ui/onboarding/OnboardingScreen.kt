package com.sinura.personaltrainer.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.BlueprintRoutine
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.OnboardingPreviewCopy
import com.sinura.personaltrainer.domain.PlanBlueprint
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.shortLabel
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import java.time.DayOfWeek

/**
 * The guided setup: seven questions, then the actual week.
 *
 * The screen this app was missing. Everything else assumed a lifter who already had routines
 * and a pinned week; a new install had neither, no way to get them but a blank routine editor,
 * and nothing anywhere saying that was the order. This is the path in.
 *
 * Three rules it holds to, all of them the owner's brief rather than convention:
 *
 * - **One question per screen.** Seven short decisions read as progress; one form with seven fields
 *   reads as work.
 * - **Every question changes the plan.** Height and body type were both proposed and both cut —
 *   nothing in a strength app consumes a height, and somatotype does not predict how anyone
 *   responds to training. A question whose answer changes nothing is a screen the lifter pays
 *   for and gets nothing back.
 * - **The split is never asked.** It is derived from three answers that determine it. Asking a
 *   new lifter to choose between push/pull/legs and upper/lower is asking them to compare two
 *   things they have no basis to compare.
 *
 * Nothing is written until "Use this plan". Backing out leaves the app exactly as it was.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onBuildMyOwn: (answers: OnboardingAnswers?, weightUnit: WeightUnit?) -> Unit,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) {
        if (finished) onFinished()
    }

    // Its own Scaffold, because setup is composed OUTSIDE the app's nav Scaffold — it owns the
    // whole screen, with no tabs and no live bar. Without one nothing handles the status-bar
    // inset and the first thing a new install shows is a header under the clock.
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            OnboardingHeader(
                state = state,
                onBack = { if (!viewModel.back()) onFinished() },
            )
            state.error?.let { message ->
                GymErrorBanner(
                    message,
                    onRetry = if (state.preview == null) viewModel::retryCatalog else null,
                )
            }

            when (state.step) {
                OnboardingStep.FORK -> ForkStep(
                    onGuided = viewModel::beginGuided,
                    onOwn = { onBuildMyOwn(null, null) },
                )
                OnboardingStep.EXPERIENCE -> ExperienceStep(
                    selected = state.answers.trainingAge,
                    onSelect = viewModel::setExperience,
                )
                OnboardingStep.DAYS_PER_WEEK -> DaysPerWeekStep(
                    selected = state.answers.daysPerWeek,
                    onSelect = viewModel::setDaysPerWeek,
                    onNext = viewModel::next,
                )
                OnboardingStep.WHICH_DAYS -> WhichDaysStep(
                    answers = state.answers,
                    onToggle = viewModel::toggleDay,
                    onNext = viewModel::next,
                )
                OnboardingStep.PLACE -> PlaceStep(
                    selected = state.answers.resolvedPlaces(),
                    onToggle = viewModel::togglePlace,
                    onNext = viewModel::next,
                )
                OnboardingStep.GOAL -> ChoiceStep(
                    title = "What are you training for?",
                    blurb = "This changes the lifts in the week, and what the coach mentions first.",
                    options = TrainingGoal.entries.map { goal ->
                        Choice(goal.displayName, goal.blurb, goal == state.answers.goal) {
                            viewModel.setGoal(goal)
                        }
                    },
                )
                OnboardingStep.EMPHASIS -> ChoiceStep(
                    title = "Where do you want the work?",
                    blurb = "This changes the week you see. Rest days stay rest days.",
                    options = TrainingEmphasis.entries.map { emphasis ->
                        Choice(emphasis.displayName, emphasis.blurb, emphasis == state.answers.emphasis) {
                            viewModel.setEmphasis(emphasis)
                        }
                    },
                )
                OnboardingStep.BODYWEIGHT -> BodyweightStep(
                    answers = state.answers,
                    unit = state.weightUnit,
                    onSet = viewModel::setBodyweight,
                    onUnitChange = viewModel::setWeightUnit,
                    onNext = viewModel::next,
                )
                OnboardingStep.PREVIEW -> PreviewStep(
                    state = state,
                    onApply = viewModel::applyPlan,
                    onOwn = { onBuildMyOwn(state.answers, state.weightUnit) },
                )
            }
        }
    }
}

@Composable
private fun OnboardingHeader(state: OnboardingUiState, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
            )
        }
        // A count, not a bar. Six is a number small enough to say out loud, and "2 of 6" tells
        // the lifter how much is left far more precisely than a partly-filled line.
        if (state.step.isQuestion) {
            Kicker("Question ${state.questionNumber} of ${state.questionCount}")
        }
    }
}

/** One tappable answer. Selecting it also advances — a single choice needs no second tap. */
private data class Choice(
    val label: String,
    val blurb: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ChoiceStep(title: String, blurb: String, options: List<Choice>) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = Metrics.space8),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        item { QuestionTitle(title, blurb) }
        item {
            GroupedList {
                options.forEachIndexed { index, option ->
                    if (index > 0) HairlineDivider()
                    InstrumentRow(
                        title = option.label,
                        subtitle = option.blurb,
                        onClick = option.onClick,
                    ) {
                        if (option.selected) {
                            Text("Selected", style = InstrumentType.caption, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperienceStep(selected: TrainingAge, onSelect: (TrainingAge) -> Unit) {
    val view = LocalView.current
    LazyColumn(
        contentPadding = PaddingValues(bottom = Metrics.space8),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        item {
            QuestionTitle(
                "How much lifting have you done?",
                "This sets how much work a session carries, and how fast the weight climbs.",
            )
        }
        items(TrainingAge.entries.toList(), key = { it.name }) { age ->
            val on = age == selected
            val shape = RoundedCornerShape(Radius.md)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (on) VoltDim else Surface2)
                    .border(
                        if (on) Metrics.emphasisBorder else Metrics.hairline,
                        if (on) Volt else Hairline,
                        shape,
                    )
                    .clickable {
                        Haptics.tick(view)
                        onSelect(age)
                    }
                    .padding(Metrics.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    Text(age.displayName, style = InstrumentType.title, color = TextPrimary)
                    Text(age.blurb, style = InstrumentType.body, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        age.liftsPerSession.toString(),
                        style = InstrumentType.numeralLg,
                        color = if (on) Volt else TextPrimary,
                    )
                    Kicker("LIFTS")
                }
            }
        }
    }
}

@Composable
private fun PlaceStep(
    selected: Set<TrainingPlace>,
    onToggle: (TrainingPlace) -> Unit,
    onNext: () -> Unit,
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap)) {
        QuestionTitle(
            "Where will you train?",
            TrainingPlace.STEP_BLURB,
        )
        TrainingPlace.entries.forEach { place ->
            val on = place in selected
            val shape = RoundedCornerShape(Radius.md)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (on) VoltDim else Surface2)
                    .border(
                        if (on) Metrics.emphasisBorder else Metrics.hairline,
                        if (on) Volt else Hairline,
                        shape,
                    )
                    .clickable {
                        Haptics.tick(view)
                        onToggle(place)
                    }
                    .padding(Metrics.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    Text(place.displayName, style = InstrumentType.title, color = TextPrimary)
                    Text(place.blurb, style = InstrumentType.body, color = TextSecondary)
                }
                if (on) {
                    Text("On", style = InstrumentType.caption, color = Volt)
                }
            }
        }
        Text(
            TrainingPlace.mixCaption(selected),
            style = InstrumentType.caption,
            color = TextTertiary,
        )
        PrimaryGymButton(text = "Continue", onClick = onNext)
    }
}

@Composable
private fun DaysPerWeekStep(selected: Int, onSelect: (Int) -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap)) {
        QuestionTitle(
            "How many days a week can you train?",
            "Be honest about the bad weeks, not hopeful about the good ones — this decides the whole shape of the plan.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(selected.toString(), style = InstrumentType.numeralXl, color = TextPrimary)
            Text(
                if (selected == 1) "day" else "days",
                style = InstrumentType.unit,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = Metrics.space2),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                (SchedulePreferences.MIN_DAYS..4).forEach { days ->
                    InstrumentChip(
                        label = days.toString(),
                        selected = days == selected,
                        onClick = { onSelect(days) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                (5..SchedulePreferences.MAX_DAYS).forEach { days ->
                    InstrumentChip(
                        label = days.toString(),
                        selected = days == selected,
                        onClick = { onSelect(days) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        PrimaryGymButton(text = "Continue", onClick = onNext)
    }
}

@Composable
private fun WhichDaysStep(
    answers: OnboardingAnswers,
    onToggle: (DayOfWeek) -> Unit,
    onNext: () -> Unit,
) {
    val remaining = answers.daysPerWeek - answers.preferredDays.size
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap)) {
        QuestionTitle(
            "Which days suit you?",
            when {
                remaining > 0 -> "Pick $remaining more, or skip and they'll be spaced out for you."
                else -> "That's your week. Tap one again to change it."
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            DayOfWeek.entries.forEach { day ->
                InstrumentChip(
                    label = day.shortLabel().take(2),
                    selected = day in answers.preferredDays,
                    onClick = { onToggle(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        PrimaryGymButton(
            text = if (answers.preferredDays.isEmpty()) "Space them out for me" else "Continue",
            onClick = onNext,
        )
    }
}

@Composable
private fun BodyweightStep(
    answers: OnboardingAnswers,
    unit: WeightUnit,
    onSet: (Double?) -> Unit,
    onUnitChange: (WeightUnit) -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap)) {
        QuestionTitle(
            "Roughly what do you weigh?",
            "Recorded so the end of your block can say what your weight did over twelve weeks. " +
                "Nothing else reads it — bodyweight lifts are counted in reps. Skip it if you'd rather not.",
        )
        BodyweightWheel(
            kg = answers.bodyweightKg,
            unit = unit,
            onKgChange = onSet,
            onUnitChange = onUnitChange,
        )
        PrimaryGymButton(
            text = if (answers.bodyweightKg == null) "Skip this" else "Continue",
            onClick = onNext,
        )
        if (answers.bodyweightKg != null) {
            SecondaryGymButton(
                text = "Skip this",
                onClick = {
                    onSet(null)
                    onNext()
                },
            )
        }
    }
}

@Composable
private fun ForkStep(onGuided: () -> Unit, onOwn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            Text("Let's get you training", style = InstrumentType.display, color = TextPrimary)
            Text(
                "Seven quick questions and you'll have a week of sessions, with the lifts already in them.",
                style = InstrumentType.body,
                color = TextSecondary,
            )
        }
        PrimaryGymButton(text = "Build my plan", onClick = onGuided)
        SecondaryGymButton(text = "I'll build my own", onClick = onOwn)
    }
}

@Composable
private fun PreviewStep(
    state: OnboardingUiState,
    onApply: () -> Unit,
    onOwn: () -> Unit,
) {
    val plan = state.preview
    LazyColumn(
        contentPadding = PaddingValues(bottom = Metrics.space8),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        item {
            QuestionTitle(
                "Here's your block",
                plan?.let {
                    "${OnboardingPreviewCopy.headline(state.answers, it)}. " +
                        "${TrainingBlock.DEFAULT_WEEKS} weeks of ${it.splitStyle.displayName}."
                } ?: (state.error ?: "Building it…"),
            )
        }
        if (plan != null) {
            item { WeekLine(plan) }
            item {
                Text(
                    OnboardingPreviewCopy.FOOTER,
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
            }
            items(plan.routines, key = { it.key }) { routine ->
                RoutineCard(routine)
            }
        }
        if (state.existingProgram) {
            item {
                // Setup is reachable again from Settings, and nothing here deletes: a lifter
                // with weeks of history pointing at their routines must not lose them to a
                // second run.
                Text(
                    "You already have routines. These will be added alongside them, not replace them.",
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                PrimaryGymButton(
                    text = if (state.applying) "Building…" else "Use this plan",
                    onClick = onApply,
                    enabled = plan != null && !state.applying,
                )
                SecondaryGymButton(text = "I'll build my own", onClick = onOwn)
            }
        }
    }
}

@Composable
private fun WeekLine(plan: PlanBlueprint) {
    GroupedList {
        plan.days.forEachIndexed { index, day ->
            if (index > 0) HairlineDivider()
            val (title, subtitle) = OnboardingPreviewCopy.dayLine(day, plan.routineFor(day))
            InstrumentRow(title = title, subtitle = subtitle)
        }
    }
}

@Composable
private fun RoutineCard(routine: BlueprintRoutine) {
    GymCard {
        Kicker(routine.name)
        routine.lifts.forEach { lift ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lift.name,
                    modifier = Modifier.weight(1f),
                    style = InstrumentType.body,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${lift.targets.sets} × ${lift.targets.reps}",
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun QuestionTitle(title: String, blurb: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Text(title, style = InstrumentType.title, color = TextPrimary)
        Text(blurb, style = InstrumentType.body, color = TextSecondary)
    }
}
