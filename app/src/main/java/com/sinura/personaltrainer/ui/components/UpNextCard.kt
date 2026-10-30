package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags

@Composable
fun UpNextCard(
    kicker: String,
    target: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String? = null,
    loading: Boolean = false,
    showEdit: Boolean = true,
    emphasis: PanelEmphasis = PanelEmphasis.None,
) {
    Panel(modifier = modifier.testTag(WorkoutTestTags.UP_NEXT_CARD), emphasis = emphasis) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Text(
                    kicker,
                    style = InstrumentType.kicker,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loading) {
                    Text("…", style = InstrumentType.numeralLg, color = TextSecondary)
                } else {
                    Text(
                        target,
                        style = InstrumentType.numeralLg,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                reason?.let {
                    Text(
                        it,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showEdit) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.REST_EDIT_UP_NEXT),
                ) {
                    Text(
                        text = stringResource(R.string.rest_edit),
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}
