package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * G3: one back header. Title, optional back, optional trailing.
 *
 * Screens used to hand-roll this Row eight different ways (padding, Pit fill,
 * kicker vs title vs display). One definition is what keeps them from drifting
 * again when landscape and Library's floating button land later.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backTag: String? = null,
    backDescription: String = "Back",
    backIcon: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack,
    titleStyle: TextStyle = InstrumentType.title,
    titleMaxLines: Int = 1,
    kickerTitle: Boolean = false,
    subtitle: String? = null,
    paintBackground: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        start = Metrics.space2,
        end = Metrics.space2,
        bottom = Metrics.space2,
    ),
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (paintBackground) Modifier.background(Pit) else Modifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = if (backTag != null) Modifier.testTag(backTag) else Modifier,
            ) {
                Icon(backIcon, contentDescription = backDescription, tint = TextSecondary)
            }
        }
        leading?.invoke(this)
        Column(modifier = Modifier.weight(1f)) {
            if (title.isNotEmpty()) {
                if (kickerTitle) {
                    Kicker(title)
                } else {
                    Text(
                        title,
                        style = titleStyle,
                        color = TextPrimary,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}
