package com.sinura.personaltrainer.ui.activity

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.ComposerCopy
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
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
    val unit = LocalWeightUnit.current
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
                        subtitle = ComposerCopy.strengthLineSubtitle(line.reps, line.weightKg, unit),
                        trailing = {
                            RemoveLineButton(
                                onClick = { viewModel.removeStrength(index) },
                                tag = ComposerTags.REMOVE_STRENGTH,
                            )
                        },
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
                    InstrumentRow(
                        title = ComposerCopy.typeChipLabel(line.type),
                        subtitle = ComposerCopy.cardioLineSubtitle(line.minutes, line.distanceKm),
                        trailing = {
                            RemoveLineButton(
                                onClick = { viewModel.removeCardio(index) },
                                tag = ComposerTags.REMOVE_CARDIO,
                            )
                        },
                    )
                }
                item { CardioAdder(onAdd = viewModel::addCardio) }
            }
            item {
                PrimaryGymButton(
                    text = if (state.saving) ComposerCopy.SAVING else ComposerCopy.SAVE,
                    onClick = viewModel::save,
                    modifier = Modifier.testTag(ComposerTags.SAVE),
                    enabled = !state.saving,
                )
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(ComposerTags.CANCEL),
                ) { Text(ComposerCopy.CANCEL) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrengthAdder(
    catalog: List<ExerciseOption>,
    onAdd: (com.sinura.personaltrainer.domain.Exercise, Double, Int) -> Unit,
) {
    val unit = LocalWeightUnit.current
    var pickedId by rememberSaveable { mutableStateOf(catalog.firstOrNull()?.id.orEmpty()) }
    var weight by rememberSaveable { mutableStateOf("0") }
    var reps by rememberSaveable { mutableStateOf("8") }
    val exercise = catalog.firstOrNull { it.id == pickedId } ?: catalog.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            catalog.take(8).forEach { lift ->
                InstrumentChip(
                    label = lift.name,
                    selected = lift.id == pickedId,
                    onClick = { pickedId = lift.id },
                )
            }
        }
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text(ComposerCopy.weightFieldLabel(unit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = reps,
            onValueChange = { reps = it },
            label = { Text(ComposerCopy.REPS) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryGymButton(
            text = ComposerCopy.ADD_SET,
            onClick = {
                val lift = exercise ?: return@SecondaryGymButton
                onAdd(lift, ComposerCopy.parseWeightToKg(weight, unit), reps.toIntOrNull() ?: 0)
            },
            modifier = Modifier.testTag(ComposerTags.ADD_SET),
        )
    }
}

private typealias ExerciseOption = com.sinura.personaltrainer.domain.Exercise

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioAdder(
    onAdd: (CardioType, Int, Double?, Boolean) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(CardioType.RUN) }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var distance by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            CardioType.entries.forEach { option ->
                InstrumentChip(
                    label = ComposerCopy.typeChipLabel(option),
                    selected = option == type,
                    onClick = { type = option },
                )
            }
        }
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it },
            label = { Text(ComposerCopy.MINUTES) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = distance,
            onValueChange = { distance = it },
            label = { Text(CardioCopy.DISTANCE_LABEL) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryGymButton(
            text = ComposerCopy.ADD_CARDIO,
            onClick = {
                onAdd(type, minutes.toIntOrNull() ?: 0, distance.toDoubleOrNull(), false)
            },
            modifier = Modifier.testTag(ComposerTags.ADD_CARDIO),
        )
    }
}

@Composable
private fun RemoveLineButton(
    onClick: () -> Unit,
    tag: String,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    ) {
        Text(ComposerCopy.REMOVE, style = InstrumentType.bodyStrong, color = Danger)
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
    const val ADD_SET = "activity-composer-add-set"
    const val ADD_CARDIO = "activity-composer-add-cardio"
    const val REMOVE_STRENGTH = "activity-composer-remove-strength"
    const val REMOVE_CARDIO = "activity-composer-remove-cardio"
}
