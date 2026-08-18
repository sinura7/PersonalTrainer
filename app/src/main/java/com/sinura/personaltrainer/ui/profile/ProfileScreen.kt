package com.sinura.personaltrainer.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinura.personaltrainer.TrainerViewModel
import com.sinura.personaltrainer.data.UserProfile

@Composable
fun ProfileScreen(viewModel: TrainerViewModel) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var goal by rememberSaveable { mutableStateOf(profile.goal) }
    var weeklyGoal by rememberSaveable { mutableStateOf(profile.weeklyWorkoutGoal.toString()) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(profile) {
        name = profile.displayName
        goal = profile.goal
        weeklyGoal = profile.weeklyWorkoutGoal.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                saved = false
            },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = goal,
            onValueChange = {
                goal = it
                saved = false
            },
            label = { Text("Training goal") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = weeklyGoal,
            onValueChange = {
                weeklyGoal = it.filter { char -> char.isDigit() }
                saved = false
            },
            label = { Text("Weekly workout target") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (saved) {
            Text("Profile saved.", color = MaterialTheme.colorScheme.primary)
        }
        Button(
            onClick = {
                val weekly = weeklyGoal.toIntOrNull()
                when {
                    name.isBlank() -> error = "Enter a name."
                    goal.isBlank() -> error = "Enter a training goal."
                    weekly == null || weekly < 1 -> error = "Weekly target must be at least 1."
                    else -> {
                        error = null
                        viewModel.updateProfile(
                            UserProfile(
                                displayName = name.trim(),
                                goal = goal.trim(),
                                weeklyWorkoutGoal = weekly,
                            ),
                        )
                        saved = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save profile")
        }
    }
}
