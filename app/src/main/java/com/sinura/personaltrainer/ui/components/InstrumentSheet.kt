package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Scrim
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Typography

object InstrumentSheetTags {
    const val FRAME = "instrument-sheet"
    const val HANDLE = "instrument-sheet-handle"
    const val GALLERY = "instrument-sheet-gallery"
}

/** Sheet chrome for the refresh kit gallery (no modal host). */
@Composable
fun InstrumentSheetGalleryChrome(
    title: String,
    caption: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface3)
            .border(Metrics.hairline, SectionEdge, shape)
            .testTag(InstrumentSheetTags.GALLERY)
            .padding(bottom = Metrics.space3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Metrics.space2),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(Metrics.sheetGrabWidth)
                    .height(Metrics.sheetGrabHeight)
                    .clip(Radius.full)
                    .background(HairlineStrong)
                    .testTag(InstrumentSheetTags.HANDLE),
            )
        }
        Box(Modifier.height(Metrics.heroCardPadding))
        Column(
            modifier = Modifier.padding(horizontal = Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(title, style = Typography.headlineSmall, color = TextPrimary)
            if (caption != null) {
                Text(caption, style = InstrumentType.caption, color = TextSecondary)
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState? = null,
    title: String? = null,
    caption: String? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg)
    val frame: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(Metrics.hairline, SectionEdge, shape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Metrics.space2),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(Metrics.sheetGrabWidth)
                        .height(Metrics.sheetGrabHeight)
                        .clip(Radius.full)
                        .background(HairlineStrong)
                        .testTag(InstrumentSheetTags.HANDLE),
                )
            }
            Box(Modifier.height(Metrics.heroCardPadding))
            if (title != null || onClose != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Metrics.gutter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        if (title != null) {
                            Text(title, style = Typography.headlineSmall, color = TextPrimary)
                        }
                        if (caption != null) {
                            Text(caption, style = InstrumentType.caption, color = TextSecondary)
                        }
                    }
                    if (onClose != null) {
                        GhostButton(label = "Close", onClick = onClose)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
                content = content,
            )
        }
    }
    val sheetModifier = modifier.testTag(InstrumentSheetTags.FRAME)
    if (sheetState != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = sheetModifier,
            containerColor = Surface3,
            shape = shape,
            dragHandle = null,
            scrimColor = Scrim,
            content = frame,
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = sheetModifier,
            containerColor = Surface3,
            shape = shape,
            dragHandle = null,
            scrimColor = Scrim,
            content = frame,
        )
    }
}
