package com.sinura.personaltrainer.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.DangerGymButton
import com.sinura.personaltrainer.ui.components.CountBadge
import com.sinura.personaltrainer.ui.components.EquipmentChip
import com.sinura.personaltrainer.ui.components.SectionHeader
import com.sinura.personaltrainer.ui.components.ScreenSkeleton
import com.sinura.personaltrainer.ui.components.EmptyIllustration
import com.sinura.personaltrainer.ui.components.FieldComplaint
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymDialog
import com.sinura.personaltrainer.ui.components.InstrumentChoiceChip
import com.sinura.personaltrainer.ui.components.InstrumentChoiceGroup
import com.sinura.personaltrainer.ui.components.InstrumentPreset
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.InstrumentSuggestion
import com.sinura.personaltrainer.ui.components.InstrumentSwitch
import com.sinura.personaltrainer.ui.components.InstrumentToggleChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.NumberEntryDialog
import com.sinura.personaltrainer.ui.components.Numeral
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestDurationSheet
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

const val ComponentGalleryTag = "component-state-gallery"
enum class ComponentSection(val title: String) {
    ACTIONS("Actions"), SELECTION("Selection and suggestions"), ENTRY("Numeric entry"),
    CONTENT("Identity and supporting content"), LOADING("Loading and recovery"), OVERLAYS("Sheets and dialogs"),
}
private enum class ActionSample { READY, PRESSED, FOCUSED, SAVING, DISABLED }
private enum class GalleryOverlay { NUMBER, REST, CONFIRM, LONG_CONFIRM }

/** Native reference components. All interactions affect disposable in-memory samples only. */
@Composable
fun ComponentStateGallery(
    modifier: Modifier = Modifier,
    section: ComponentSection? = null,
) {
    var overlay by rememberSaveable { mutableStateOf<GalleryOverlay?>(null) }
    var reps by rememberSaveable { mutableStateOf(8) }
    var rest by rememberSaveable { mutableStateOf(90) }
    var status by rememberSaveable { mutableStateOf("No sample action yet") }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Pit).testTag(ComponentGalleryTag),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Metrics.gutter),
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Kicker("Temper component standard")
                Text(section?.title ?: "Controls and states", style = InstrumentType.display, color = TextPrimary)
                Text("Native, interactive reference", style = InstrumentType.caption, color = TextSecondary)
            }
        }
        if (section == null || section == ComponentSection.ACTIONS) item {
            var sample by rememberSaveable { mutableStateOf(ActionSample.READY) }
            val interactions = remember(sample) { MutableInteractionSource() }
            val focus = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(sample) {
                withFrameNanos { }
                focusManager.clearFocus()
                when (sample) {
                    ActionSample.PRESSED -> interactions.emit(PressInteraction.Press(Offset.Zero))
                    ActionSample.FOCUSED -> {
                        inputMode.requestInputMode(InputMode.Keyboard)
                        focus.requestFocus()
                    }
                    else -> Unit
                }
            }
            GalleryPanel(title = "Action state") {
                InstrumentChoiceGroup {
                    ActionSample.entries.forEach { option ->
                        InstrumentChoiceChip(
                            label = option.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = sample == option,
                            onClick = { sample = option },
                        )
                    }
                }
                PrimaryGymButton(
                    text = if (sample == ActionSample.SAVING) "Saving…" else "Log set · 135 lbs × 8",
                    onClick = { status = "Sample set saved" },
                    enabled = sample != ActionSample.SAVING && sample != ActionSample.DISABLED,
                    disabledReason = if (sample == ActionSample.SAVING) "Saving the sample set" else "Sample action unavailable",
                    height = Metrics.commit,
                    interactionSource = interactions,
                    modifier = Modifier.focusRequester(focus),
                )
                SecondaryGymButton(text = "View sets", onClick = { status = "Sample set list opened" })
                DangerGymButton(text = "Discard sample", onClick = { overlay = GalleryOverlay.CONFIRM })
                Text(status, style = InstrumentType.caption, color = TextSecondary)
            }
        }
        if (section == null || section == ComponentSection.SELECTION) item {
            var warmup by rememberSaveable { mutableStateOf(false) }
            var reminder by rememberSaveable { mutableStateOf(false) }
            var weight by rememberSaveable { mutableStateOf(20) }
            GalleryPanel(title = "Different jobs, different controls") {
                InstrumentChoiceGroup {
                    InstrumentChoiceChip("Working", !warmup, { warmup = false })
                    InstrumentChoiceChip("Warm-up", warmup, { warmup = true })
                }
                InstrumentToggleChip("Reminder", reminder, { reminder = it })
                InstrumentSuggestion("15 lbs for a warm-up")
                InstrumentPreset(
                    label = "Use 15 lbs",
                    supporting = "40% ramp · applies a draft only",
                    onClick = { weight = 15; warmup = true },
                )
                Text("Draft: $weight lbs · nothing logged", style = InstrumentType.body, color = TextPrimary)
                InstrumentRow(
                    title = "Independent switch",
                    checked = reminder,
                    onCheckedChange = { reminder = it },
                    trailing = { InstrumentSwitch(checked = reminder) },
                )
            }
        }
        if (section == null || section == ComponentSection.ENTRY) item {
            GalleryPanel(title = "Edit an absolute value") {
                Numeral(value = reps.toString(), unit = "reps")
                SecondaryGymButton(text = "Edit reps", onClick = { overlay = GalleryOverlay.NUMBER })
                Text("Confirm applies the typed number. Cancel keeps the draft.", style = InstrumentType.body, color = TextSecondary)
                FieldComplaint("Validation example: enter a whole number from 1 to ${NumericEntry.MAX_REPS}.")
                PrimaryGymButton(text = "Log set · 135 lbs × $reps", onClick = { status = "Sample set saved" }, height = Metrics.commit)
                Text(status, style = InstrumentType.caption, color = TextSecondary)
            }
        }
        if (section == null || section == ComponentSection.CONTENT) item {
            GalleryPanel(title = "Identity before metrics") {
                InstrumentRow(title = "Barbell Back Squat", subtitle = "Barbell · exercise 1 of 7")
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    CountBadge(number = 1, selected = true)
                    EquipmentChip(label = "Barbell")
                }
                Numeral(value = "135", unit = "lbs")
                SectionHeader(title = "Latest saved set")
                InstrumentRow(title = "Latest set · 135 lbs × 8", subtitle = "Working set 2 of 4 · saved", onClick = { status = "Sample set opened" })
                Text("Workout · In progress", style = InstrumentType.bodyStrong, color = TextPrimary)
                Text("No completed sessions in this period", style = InstrumentType.body, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    EmptyIllustration(scene = EmptyScene.entries.first(), size = Metrics.control)
                    Text("Start when you are ready.", style = InstrumentType.body, color = TextSecondary)
                }
            }
        }
        if (section == null || section == ComponentSection.LOADING) item {
            GalleryPanel(title = "Loading and recoverable failure") {
                Text("Loading your sessions…", style = InstrumentType.body, color = TextSecondary)
                ScreenSkeleton(cards = 1)
                FieldComplaint("Sample error: sessions could not be loaded. Saved training is still on this device.")
                SecondaryGymButton(text = "Retry loading", onClick = { status = "Sample load retried" })
                Text(status, style = InstrumentType.caption, color = TextSecondary)
            }
        }
        if (section == null || section == ComponentSection.OVERLAYS) item {
            GalleryPanel(title = "One overlay at a time") {
                SecondaryGymButton(text = "Open rest controls", onClick = { overlay = GalleryOverlay.REST })
                SecondaryGymButton(text = "Open numeric entry", onClick = { overlay = GalleryOverlay.NUMBER })
                SecondaryGymButton(text = "Open confirmation", onClick = { overlay = GalleryOverlay.CONFIRM })
                SecondaryGymButton(text = "Open long-name confirmation", onClick = { overlay = GalleryOverlay.LONG_CONFIRM })
                Text("Back dismisses the current overlay. Samples do not access saved training data.", style = InstrumentType.body, color = TextSecondary)
            }
        }
    }
    when (overlay) {
        GalleryOverlay.NUMBER -> NumberEntryDialog(
            title = "Reps", unitLabel = "reps", initial = reps.toString(), decimal = false,
            helper = "A whole number from 1 to ${NumericEntry.MAX_REPS}.",
            parse = NumericEntry::parseReps, onConfirm = { reps = it }, onDismiss = { overlay = null },
        )
        GalleryOverlay.REST -> RestDurationSheet(
            selectedSeconds = rest,
            onSelect = { rest = it },
            onNudge = { rest = (rest + it).coerceAtLeast(15) },
            onCustomRest = { raw -> RestTimer.parseCustom(raw)?.let { rest = it; true } ?: false },
            onDismiss = { overlay = null },
        )
        GalleryOverlay.CONFIRM -> GymDialog(
            title = "Discard this sample?", body = "Only the gallery's sample status will change.",
            confirmLabel = "Discard sample", destructive = true,
            onConfirm = { status = "Sample discarded"; overlay = null }, onDismiss = { overlay = null },
        )
        GalleryOverlay.LONG_CONFIRM -> GymDialog(
            title = "Discard ${"a saved routine with a long valid name ".repeat(20)}?",
            body = "This is the complete warning. Only sample data would be discarded.",
            confirmLabel = "Discard sample", destructive = true,
            onConfirm = { status = "Sample discarded"; overlay = null }, onDismiss = { overlay = null },
        )
        null -> Unit
    }
}

@Composable
private fun GalleryPanel(title: String, content: @Composable () -> Unit) {
    GymCard {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
            Kicker(title)
            content()
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun ComponentGalleryPreview() {
    PersonalTrainerTheme { ComponentStateGallery(modifier = Modifier.fillMaxWidth()) }
}
