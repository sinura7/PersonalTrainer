package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.Exercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    query: String,
    results: List<Exercise>,
    onQueryChange: (String) -> Unit,
    onSelect: (Exercise) -> Unit,
    onCreate: (name: String, muscleGroup: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var customMuscle by rememberSaveable { mutableStateOf("") }
    val focusSink = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusSink.requestFocus()
        keyboard?.hide()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusSink)
                    .focusable(),
            )
            Text("Add exercise")
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search or create") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            OutlinedTextField(
                value = customMuscle,
                onValueChange = { customMuscle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Muscle group for new exercise") },
                singleLine = true,
            )
            if (query.isNotBlank() && results.none { it.name.equals(query.trim(), ignoreCase = true) }) {
                PrimaryGymButton(
                    text = "Create \"${query.trim()}\"",
                    onClick = { onCreate(query.trim(), customMuscle) },
                )
            }
            if (results.isEmpty()) {
                EmptyState(
                    title = if (query.isBlank()) "Search the library" else "No matches",
                    body = if (query.isBlank()) {
                        "Type a lift name to search, or enter a new name and create it."
                    } else {
                        "No library match. Use Create above to add this as a custom lift."
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(results, key = { it.id }) { exercise ->
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = { Text(exercise.muscleGroup) },
                            modifier = Modifier.clickable { onSelect(exercise) },
                        )
                    }
                }
            }
        }
    }
}
