package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.AddToRoutineCopy
import com.sinura.personaltrainer.domain.AddToRoutineDestination
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadTypeCopy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Where a lift lands, shown as the row it will become and the floor it
 * is going onto.
 *
 * L-05: Library and the lift page used to offer a list of routine names.
 * Names are not a preview. The landing card is the still, the name, and
 * the Work / Rest [AddDefaults] will write; each destination is the same
 * three stills a start-sheet routine card uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToRoutineSheet(
    exercise: Exercise,
    destinations: List<AddToRoutineDestination>,
    emptyBody: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateRoutine: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface3) {
        AddToRoutineBody(
            exercise = exercise,
            destinations = destinations,
            emptyBody = emptyBody,
            onSelect = onSelect,
            onCreateRoutine = onCreateRoutine,
        )
    }
}

@Composable
internal fun AddToRoutineBody(
    exercise: Exercise,
    destinations: List<AddToRoutineDestination>,
    emptyBody: String,
    onSelect: (String) -> Unit,
    onCreateRoutine: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Metrics.gutter)
            .padding(bottom = Metrics.space7)
            .testTag(AddToRoutineTags.SHEET),
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        Text(
            AddToRoutineCopy.title(exercise.name),
            style = InstrumentType.title,
            color = TextPrimary,
        )
        LandingCard(exercise)
        if (destinations.isEmpty()) {
            Text(emptyBody, style = InstrumentType.body, color = TextSecondary)
            if (onCreateRoutine != null) {
                PrimaryGymButton(
                    text = AddToRoutineCopy.CREATE,
                    onClick = onCreateRoutine,
                    modifier = Modifier.testTag(AddToRoutineTags.CREATE),
                )
            }
        } else {
            destinations.forEach { destination ->
                DestinationCard(
                    destination = destination,
                    onSelect = { onSelect(destination.id) },
                )
            }
        }
    }
}

@Composable
private fun LandingCard(exercise: Exercise) {
    val defaults = AddToRoutineCopy.landing(exercise)
    val spoken = AddToRoutineCopy.spokenLanding(exercise)
    GymCard(
        modifier = Modifier
            .testTag(AddToRoutineTags.LANDING)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            ExerciseThumb(exercise = exercise, size = ThumbSize.header)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    LoadTypeCopy.rowTag(exercise),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            MetricCluster(
                value = AddToRoutineCopy.workValue(defaults),
                label = SessionOrderCopy.WORK,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = AddToRoutineCopy.restClock(defaults),
                label = SessionOrderCopy.REST,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
        }
        Text(
            AddToRoutineCopy.EDITABLE,
            style = InstrumentType.caption,
            color = TextTertiary,
        )
    }
}

@Composable
private fun DestinationCard(
    destination: AddToRoutineDestination,
    onSelect: () -> Unit,
) {
    val stills = AddToRoutineCopy.stills(destination.lifts)
    val mix = AddToRoutineCopy.mix(destination.lifts)
    val spoken = AddToRoutineCopy.spokenDestination(destination)
    GymCard(
        modifier = Modifier
            .testTag(AddToRoutineTags.destination(destination.id))
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        onClick = if (destination.alreadyHolds) null else onSelect,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Text(
                destination.name,
                modifier = Modifier.weight(1f),
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                AddToRoutineCopy.destinationSubtitle(destination),
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
            )
        }
        if (stills.isEmpty()) {
            Text(
                AddToRoutineCopy.NO_LIFTS,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                stills.forEach { lift ->
                    ExerciseThumb(exercise = lift)
                }
            }
            mix?.let { kit ->
                Text(
                    kit,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

object AddToRoutineTags {
    const val SHEET = "add-to-routine-sheet"
    const val LANDING = "add-to-routine-landing"
    const val CREATE = "add-to-routine-create"

    fun destination(id: String): String = "add-to-routine-destination-$id"
}
