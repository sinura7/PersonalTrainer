package com.sinura.personaltrainer.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ActivityComposerScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ActivityComposerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedId by viewModel.savedId.collectAsStateWithLifecycle()
    LaunchedEffect(savedId) {
        val id = savedId ?: return@LaunchedEffect
        viewModel.onSavedHandled()
        onSaved(id)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Metrics.gutter),
            contentPadding = PaddingValues(bottom = Metrics.space8),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            item {
                Text(composerTitle(state.mode), style = InstrumentType.title, color = TextPrimary)
                Text(
                    "Nothing is written until you save. Future dates are refused.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            }
            state.error?.let { message ->
                item { GymErrorBanner(message) }
            }
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true,
                )
            }
            item {
                Kicker("When")
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    TextButton(onClick = { viewModel.shiftDay(-1) }) { Text("Earlier") }
                    Text(
                        DATE_FORMAT.format(CivilDate.fromEpochDay(state.epochDay).toJavaLocalDate()),
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = Metrics.space3),
                    )
                    TextButton(onClick = { viewModel.shiftDay(1) }) { Text("Later") }
                }
            }
            if (state.mode != ComposerMode.CARDIO) {
                item { Kicker("Strength") }
                itemsIndexed(state.strength, key = { index, line -> "${line.exercise.id}-$index" }) { index, line ->
                    InstrumentRow(
                        title = line.exercise.name,
                        subtitle = "${line.reps} reps · ${line.weightKg} kg",
                        onClick = { viewModel.removeStrength(index) },
                    )
                }
                item {
                    StrengthAdder(
                        catalog = state.catalog,
                        onAdd = viewModel::addStrength,
                    )
                }
            }
            if (state.mode != ComposerMode.STRENGTH) {
                item { Kicker("Cardio") }
                itemsIndexed(state.cardio, key = { index, line -> "${line.type}-$index" }) { index, line ->
                    val distance = line.distanceKm?.let { " · $it km" }.orEmpty()
                    InstrumentRow(
                        title = line.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        subtitle = "${line.minutes} min$distance",
                        onClick = { viewModel.removeCardio(index) },
                    )
                }
                item { CardioAdder(onAdd = viewModel::addCardio) }
            }
            item {
                PrimaryGymButton(
                    text = if (state.saving) "Saving…" else "Save",
                    onClick = viewModel::save,
                    modifier = Modifier.testTag(ComposerTags.SAVE),
                    enabled = !state.saving,
                )
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(ComposerTags.CANCEL),
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun StrengthAdder(
    catalog: List<ExerciseOption>,
    onAdd: (com.sinura.personaltrainer.domain.Exercise, Double, Int) -> Unit,
) {
    var pickedId by rememberSaveable { mutableStateOf(catalog.firstOrNull()?.id.orEmpty()) }
    var weight by rememberSaveable { mutableStateOf("0") }
    var reps by rememberSaveable { mutableStateOf("8") }
    val exercise = catalog.firstOrNull { it.id == pickedId } ?: catalog.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        catalog.take(8).forEach { lift ->
            TextButton(onClick = { pickedId = lift.id }) {
                Text(if (lift.id == pickedId) "• ${lift.name}" else lift.name)
            }
        }
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text("Weight kg") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = reps,
            onValueChange = { reps = it },
            label = { Text("Reps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                val lift = exercise ?: return@TextButton
                onAdd(lift, weight.toDoubleOrNull() ?: 0.0, reps.toIntOrNull() ?: 0)
            },
        ) { Text("Add set") }
    }
}

private typealias ExerciseOption = com.sinura.personaltrainer.domain.Exercise

@Composable
private fun CardioAdder(
    onAdd: (CardioType, Int, Double?, Boolean) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(CardioType.RUN) }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var distance by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        CardioType.entries.forEach { option ->
            TextButton(onClick = { type = option }) {
                Text(if (option == type) "• ${option.name}" else option.name)
            }
        }
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it },
            label = { Text("Minutes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = distance,
            onValueChange = { distance = it },
            label = { Text("Distance km (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                onAdd(type, minutes.toIntOrNull() ?: 0, distance.toDoubleOrNull(), false)
            },
        ) { Text("Add cardio") }
    }
}

private fun composerTitle(mode: ComposerMode): String = when (mode) {
    ComposerMode.STRENGTH -> "Log a past workout"
    ComposerMode.CARDIO -> "Log cardio"
    ComposerMode.MIXED -> "Log a mixed session"
}

private fun CivilDate.toJavaLocalDate(): java.time.LocalDate =
    java.time.LocalDate.ofEpochDay(epochDay)

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)

object ComposerTags {
    const val SAVE = "activity-composer-save"
    const val CANCEL = "activity-composer-cancel"
}
