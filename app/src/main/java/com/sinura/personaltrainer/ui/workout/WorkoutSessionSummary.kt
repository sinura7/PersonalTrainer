package com.sinura.personaltrainer.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sinura.personaltrainer.domain.SessionTelemetryCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GymDialog
import com.sinura.personaltrainer.util.QuantityFormat
import kotlinx.coroutines.delay

/** Session-wide facts are read together, away from the current exercise's progress. */
@Composable
internal fun WorkoutSessionSummary(
    routineName: String,
    startedAt: Long,
    workingSets: Int,
    warmups: Int,
    work: SetWork,
    unit: WeightUnit,
    onDismiss: () -> Unit,
) {
    var elapsedSeconds by remember(startedAt) {
        mutableIntStateOf(((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0).toInt())
    }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0).toInt()
            delay((60 - elapsedSeconds % 60).coerceAtLeast(1) * 1_000L)
        }
    }
    val facts = buildList {
        add(routineName)
        add("Elapsed: ${SessionTelemetryCopy.elapsedMinutesLabel(elapsedSeconds)}")
        add("Working sets: $workingSets")
        add("Warm-ups: $warmups")
        if (work.volumeKg > 0) add("External volume: ${QuantityFormat.formatVolumeLabel(work.volumeKg, unit)}")
        if (work.bodyweightReps > 0) add("Bodyweight reps: ${work.bodyweightReps}")
    }
    GymDialog(
        title = "Session summary",
        body = facts.joinToString("\n\n"),
        confirmLabel = "Done",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        dismissLabel = null,
    )
}
