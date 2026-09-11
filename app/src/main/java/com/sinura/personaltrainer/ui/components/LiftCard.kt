package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim

/**
 * One lift as a card: still, name, muscle, kit chip, optional number.
 *
 * Workout, a finished session, and a program sheet used to each draw this
 * header. One chrome is what keeps a squat on the floor looking like the
 * same squat in History. Library and the picker stay [ExerciseRow] — a
 * list, not a card.
 */
@Composable
fun LiftCard(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    number: Int? = null,
    spoken: String? = null,
    onClick: (() -> Unit)? = null,
    cardTag: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .clip(shape)
            .background(if (selected) VoltDim else Surface2)
            .border(
                if (selected) Metrics.emphasisBorder else Metrics.hairline,
                if (selected) Volt else Hairline,
                shape,
            ),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .then(if (cardTag != null) Modifier.testTag(cardTag) else Modifier)
                .semantics(mergeDescendants = true) {
                    if (spoken != null) contentDescription = spoken
                    this.selected = selected
                }
                .padding(Metrics.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            if (number != null) {
                CountBadge(number = number, selected = selected)
            }
            ExerciseThumb(
                exercise = exercise,
                size = ThumbSize.header,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (exercise.muscleGroup.isNotBlank()) {
                        Text(
                            exercise.muscleGroup,
                            modifier = Modifier.weight(1f, fill = false),
                            style = InstrumentType.caption,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    EquipmentChip(exercise.equipment)
                }
            }
            trailing()
        }
        if (content != null) {
            content()
        }
    }
}
