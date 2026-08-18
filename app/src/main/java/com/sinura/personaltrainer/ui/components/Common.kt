package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinura.personaltrainer.domain.toKgLabel
import com.sinura.personaltrainer.domain.toKgNumber

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun KgStepper(
    valueKg: Double,
    onAdjust: (Double) -> Unit,
    modifier: Modifier = Modifier,
    stepKg: Double = 2.5,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepperButton(label = "−2.5", onClick = { onAdjust(-stepKg) })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WEIGHT", style = MaterialTheme.typography.labelLarge)
            Text(
                valueKg.toKgNumber(),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 42.sp, fontWeight = FontWeight.Bold),
            )
            Text(valueKg.toKgLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StepperButton(label = "+2.5", onClick = { onAdjust(stepKg) })
    }
}

@Composable
fun RepsStepper(
    value: Int,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepperButton(label = "−1", onClick = { onAdjust(-1) })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("REPS", style = MaterialTheme.typography.labelLarge)
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 42.sp, fontWeight = FontWeight.Bold),
            )
        }
        StepperButton(label = "+1", onClick = { onAdjust(1) })
    }
}

@Composable
fun StepperButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(width = 88.dp, height = 72.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun RestTimerBar(
    remainingSeconds: Int,
    totalSeconds: Int,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (remainingSeconds <= 0) return
    val progress = if (totalSeconds <= 0) 0f else remainingSeconds.toFloat() / totalSeconds.toFloat()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Rest ${remainingSeconds}s", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onSkip) { Text("Skip rest") }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )
    }
}

@Composable
fun PrimaryGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
