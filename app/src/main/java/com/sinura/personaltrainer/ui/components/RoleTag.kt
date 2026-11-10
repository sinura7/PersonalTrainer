package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.RoleCardioFill
import com.sinura.personaltrainer.ui.theme.RoleCardioInk
import com.sinura.personaltrainer.ui.theme.RoleLiveFill
import com.sinura.personaltrainer.ui.theme.RoleLiveInk
import com.sinura.personaltrainer.ui.theme.RoleMainFill
import com.sinura.personaltrainer.ui.theme.RoleMainInk
import com.sinura.personaltrainer.ui.theme.RoleRecordFill
import com.sinura.personaltrainer.ui.theme.RoleRecordInk
import com.sinura.personaltrainer.ui.theme.RoleWarmupFill
import com.sinura.personaltrainer.ui.theme.RoleWarmupInk
enum class RoleTagKind {
    Main,
    WarmUp,
    Stretch,
    Cardio,
    Pr,
    Done,
    Today,
    Live,
    Rest,
    NewHere,
}

@Composable
fun RoleTag(
    kind: RoleTagKind,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    val label = buildString {
        append(roleTagLabel(kind))
        if (!value.isNullOrBlank()) {
            append(' ')
            append(value)
        }
    }
    val (ink, tagFill, ring) = roleTagColors(kind)
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (ring) Modifier.border(Metrics.hairline, HairlineStrong, shape) else Modifier)
            .background(tagFill)
            .padding(horizontal = Metrics.roleTagPaddingH, vertical = Metrics.hairline),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = InstrumentType.roleTag,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun roleTagLabel(kind: RoleTagKind): String = when (kind) {
    RoleTagKind.Main -> "Main"
    RoleTagKind.WarmUp -> "Warm-up"
    RoleTagKind.Stretch -> "Stretch"
    RoleTagKind.Cardio -> "Cardio"
    RoleTagKind.Pr -> "PR"
    RoleTagKind.Done -> "Done"
    RoleTagKind.Today -> "Today"
    RoleTagKind.Live -> "Live"
    RoleTagKind.Rest -> "Rest"
    RoleTagKind.NewHere -> "New here"
}

private fun roleTagColors(kind: RoleTagKind): Triple<Color, Color, Boolean> = when (kind) {
    RoleTagKind.Main -> Triple(RoleMainInk, RoleMainFill, false)
    RoleTagKind.WarmUp, RoleTagKind.Stretch, RoleTagKind.Rest, RoleTagKind.NewHere ->
        Triple(RoleWarmupInk, RoleWarmupFill, false)
    RoleTagKind.Cardio -> Triple(RoleCardioInk, RoleCardioFill, true)
    RoleTagKind.Pr -> Triple(RoleRecordInk, RoleRecordFill, false)
    RoleTagKind.Done, RoleTagKind.Today, RoleTagKind.Live ->
        Triple(RoleLiveInk, RoleLiveFill, false)
}
