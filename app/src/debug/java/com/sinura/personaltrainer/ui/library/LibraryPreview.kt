package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.AddToRoutineCopy
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.ui.components.AddToRoutineBody
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExerciseSearchField
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun LibraryChromePreview() {
    PersonalTrainerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Pit)
                .padding(Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary,
                    )
                }
                Text("Library", style = InstrumentType.display, color = TextPrimary)
            }
            ExerciseSearchField(
                value = "",
                onValueChange = {},
                placeholder = "Name or muscle",
            )
            EmptyState(
                scene = EmptyScene.CATALOG,
                title = "No lifts match",
                body = "Clear the search or add a custom lift.",
                actionLabel = "Create lift",
                onAction = {},
                compact = true,
            )
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun AddToRoutinePreview() {
    PersonalTrainerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Pit)
                .padding(Metrics.gutter),
        ) {
            AddToRoutineBody(
                exercise = LibraryPreviewFixtures.squat,
                destinations = listOf(
                    AddToRoutineCopy.destination(
                        routine = LibraryPreviewFixtures.upper,
                        alreadyHolds = false,
                    ),
                    AddToRoutineCopy.destination(
                        routine = LibraryPreviewFixtures.push,
                        alreadyHolds = true,
                    ),
                ),
                emptyBody = AddToRoutineCopy.EMPTY_LIBRARY,
                onSelect = {},
                onCreateRoutine = null,
            )
        }
    }
}

internal object LibraryPreviewFixtures {
    val squat = Exercise(
        id = "ex-barbell-back-squat",
        name = "Barbell Back Squat",
        muscleGroup = "Quads",
        notes = "",
        isCustom = false,
        equipment = EquipmentType.BARBELL,
        loadType = LoadType.EXTERNAL,
        imageKey = "ex_barbell_back_squat",
        muscles = listOf(MuscleCredit("quads", 1.0), MuscleCredit("glutes", 0.5)),
    )
    val press = Exercise(
        id = "ex-chest-press-machine",
        name = "Chest Press",
        muscleGroup = "Chest",
        notes = "",
        isCustom = false,
        equipment = EquipmentType.MACHINE,
        loadType = LoadType.STACK,
        imageKey = "ex_chest_press",
    )
    val row = Exercise(
        id = "ex-seated-row",
        name = "Seated Row",
        muscleGroup = "Back",
        notes = "",
        isCustom = false,
        equipment = EquipmentType.CABLE,
        loadType = LoadType.STACK,
        imageKey = "ex_seated_row",
    )
    val upper = Routine(
        id = "upper",
        name = "Upper",
        notes = "",
        createdAt = 0L,
        updatedAt = 0L,
        exercises = listOf(press, row).mapIndexed { index, exercise ->
            RoutineExercise(
                id = "re-$index",
                routineId = "upper",
                exercise = exercise,
                sortOrder = index,
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = null,
                restSeconds = 90,
            )
        },
    )
    val push = Routine(
        id = "push",
        name = "Push",
        notes = "",
        createdAt = 0L,
        updatedAt = 0L,
        exercises = listOf(squat).mapIndexed { index, exercise ->
            RoutineExercise(
                id = "re-push-$index",
                routineId = "push",
                exercise = exercise,
                sortOrder = index,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = null,
                restSeconds = 150,
            )
        },
    )
}
