package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GymMetrics {
    val screenPadding: Dp = 20.dp
    val sectionGap: Dp = 20.dp
    val listGap: Dp = 12.dp
    val cardPadding: Dp = 16.dp
    val cardRadius: Dp = 20.dp

    val screenContentPadding = PaddingValues(screenPadding)
}

@Composable
fun GymSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun GymCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(GymMetrics.cardRadius)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            shape = shape,
        ) {
            Column(
                modifier = Modifier.padding(GymMetrics.cardPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            shape = shape,
        ) {
            Column(
                modifier = Modifier.padding(GymMetrics.cardPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SessionLogRow(
    title: String,
    dateLabel: String,
    workingSets: Int,
    volumeLabel: String,
    durationMinutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GymCard(onClick = onClick, modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            dateLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            sessionLogMeta(workingSets, volumeLabel, durationMinutes),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

fun sessionLogMeta(
    workingSets: Int,
    volumeLabel: String,
    durationMinutes: Int,
): String {
    val setLabel = if (workingSets == 1) "working set" else "working sets"
    return "$workingSets $setLabel · $volumeLabel · ${durationMinutes} min"
}
