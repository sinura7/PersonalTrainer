package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Session chrome grows for text: Close, routine title, Finish / Discard.
 * Session telemetry is available through the named Session summary.
 */
@Composable
internal fun WorkoutHeader(
    routineName: String,
    canFinish: Boolean,
    compact: Boolean,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit = {},
    showDiscard: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    val titleLines = if (fontScale >= 2f || compact) 2 else 1

    ScreenHeader(
        title = routineName,
        onBack = onExit,
        backIcon = Icons.Outlined.Close,
        backDescription = "Exit workout",
        paintBackground = true,
        titleMaxLines = titleLines,
        contentPadding = PaddingValues(start = Metrics.space2, end = Metrics.space2),
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .heightIn(min = Metrics.control)
            .padding(bottom = Metrics.space2),
        trailing = {
            if (showDiscard) {
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .widthIn(min = Metrics.headerActMin)
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.DISCARD),
                ) {
                    Text(
                        EndWorkoutCopy.HEADER_DISCARD,
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                    )
                }
            } else {
                TextButton(
                    onClick = onFinish,
                    enabled = canFinish,
                    modifier = Modifier
                        .widthIn(min = Metrics.headerActMin)
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.FINISH)
                        .semantics {
                            if (!canFinish) {
                                disabled()
                                contentDescription = EndWorkoutCopy.LOG_FIRST
                            }
                        },
                ) {
                    Text(
                        EndWorkoutCopy.HEADER_FINISH,
                        style = InstrumentType.bodyStrong,
                        color = if (canFinish) TextPrimary else TextTertiary,
                    )
                }
            }
        },
    )
}
