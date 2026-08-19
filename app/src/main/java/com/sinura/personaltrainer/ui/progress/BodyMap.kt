package com.sinura.personaltrainer.ui.progress

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.MuscleLoadSummary

enum class BodyView(val label: String) {
    FRONT("Front"),
    BACK("Back"),
}

private data class BodyHotspot(
    val muscle: CanonicalMuscle,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Composable
fun BodyMapCard(
    snapshot: BodyHeatSnapshot,
    view: BodyView,
    onViewChange: (BodyView) -> Unit,
    selected: CanonicalMuscle?,
    onSelect: (CanonicalMuscle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyView.entries.forEach { option ->
                FilterChip(
                    selected = view == option,
                    onClick = { onViewChange(option) },
                    label = { Text(option.label) },
                )
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            Canvas(Modifier.fillMaxSize()) {
                val figureLeft = widthPx * 0.28f
                val figureWidth = widthPx * 0.44f
                val outline = if (dark) Color(0xFF2C4A3E) else Color(0xFFC5D5CB)
                drawRoundRect(
                    color = outline.copy(alpha = 0.35f),
                    topLeft = Offset(figureLeft + figureWidth * 0.38f, heightPx * 0.015f),
                    size = Size(figureWidth * 0.24f, heightPx * 0.09f),
                    cornerRadius = CornerRadius(figureWidth * 0.12f),
                )
                drawRoundRect(
                    color = outline.copy(alpha = 0.22f),
                    topLeft = Offset(figureLeft, heightPx * 0.12f),
                    size = Size(figureWidth, heightPx * 0.86f),
                    cornerRadius = CornerRadius(36f),
                )
            }
            val hotspots = if (view == BodyView.FRONT) frontHotspots() else backHotspots()
            val density = LocalDensity.current
            hotspots.forEach { spot ->
                val load = snapshot.load(spot.muscle)
                val fill by animateColorAsState(
                    targetValue = heatFill(load.heat, dark),
                    label = "heat-${spot.muscle}-${spot.left}",
                )
                val selectedBorder = selected == spot.muscle
                val outlineAlpha = if (dark) 0.22f else 0.08f
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (widthPx * spot.left).toDp() },
                            y = with(density) { (heightPx * spot.top).toDp() },
                        )
                        .size(
                            width = with(density) { (widthPx * spot.width).toDp() },
                            height = with(density) { (heightPx * spot.height).toDp() },
                        )
                        .clip(RoundedCornerShape(40))
                        .background(fill)
                        .border(
                            if (selectedBorder) 2.dp else 1.dp,
                            if (selectedBorder) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.White.copy(alpha = outlineAlpha)
                            },
                            RoundedCornerShape(40),
                        )
                        .semantics {
                            contentDescription = "${spot.muscle.displayName}, ${load.band.legendLabel} load"
                            role = Role.Button
                        }
                        .clickable { onSelect(spot.muscle) },
                )
            }
        }
        HeatLegend()
    }
}

@Composable
fun HeatLegend(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Load", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendSwatch("Low", heatFill(0.22f, dark))
            LegendSwatch("Moderate", heatFill(0.5f, dark))
            LegendSwatch("High", heatFill(0.95f, dark))
        }
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MuscleHeatRow(
    load: MuscleLoadSummary,
    selected: Boolean,
    onClick: () -> Unit,
    volumeLabel: String,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val fill by animateColorAsState(heatFill(load.heat, dark), label = "row-${load.muscle}")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(fill),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(load.muscle.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                muscleRowMeta(load, volumeLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(load.band.legendLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun muscleRowMeta(load: MuscleLoadSummary, volumeLabel: String): String {
    val sets = if (load.workingSets == 1) "1 set" else "${load.workingSets} sets"
    return "$sets · $volumeLabel · ${recencyLabel(load)}"
}

fun recencyLabel(load: MuscleLoadSummary): String = when (val days = load.daysSinceLastTrained) {
    null -> "Not trained yet"
    0 -> "Trained today"
    1 -> "1 day ago"
    else -> "$days days ago"
}

fun heatFill(heat: Double, dark: Boolean): Color = heatFill(heat.toFloat(), dark)

fun heatFill(heat: Float, dark: Boolean): Color {
    val empty = if (dark) Color(0xFF2A4A3C) else Color(0xFFD7E3DB)
    val low = if (dark) Color(0xFF3D8A62) else Color(0xFF8FBF9A)
    val mid = if (dark) Color(0xFFF0C14A) else Color(0xFFE0A317)
    val high = if (dark) Color(0xFFFF8A50) else Color(0xFFD64B3A)
    val t = heat.coerceIn(0f, 1f)
    return when {
        t <= 0.02f -> empty
        t < 0.34f -> lerp(low, mid, t / 0.34f)
        else -> lerp(mid, high, ((t - 0.34f) / 0.66f).coerceIn(0f, 1f))
    }
}

private fun frontHotspots(): List<BodyHotspot> = listOf(
    BodyHotspot(CanonicalMuscle.SHOULDERS, 0.20f, 0.125f, 0.60f, 0.08f),
    BodyHotspot(CanonicalMuscle.CHEST, 0.33f, 0.20f, 0.34f, 0.13f),
    BodyHotspot(CanonicalMuscle.BICEPS, 0.10f, 0.22f, 0.18f, 0.16f),
    BodyHotspot(CanonicalMuscle.BICEPS, 0.72f, 0.22f, 0.18f, 0.16f),
    BodyHotspot(CanonicalMuscle.CORE, 0.35f, 0.34f, 0.30f, 0.16f),
    BodyHotspot(CanonicalMuscle.QUADRICEPS, 0.30f, 0.52f, 0.18f, 0.22f),
    BodyHotspot(CanonicalMuscle.QUADRICEPS, 0.52f, 0.52f, 0.18f, 0.22f),
    BodyHotspot(CanonicalMuscle.CALVES, 0.32f, 0.76f, 0.14f, 0.18f),
    BodyHotspot(CanonicalMuscle.CALVES, 0.54f, 0.76f, 0.14f, 0.18f),
)

private fun backHotspots(): List<BodyHotspot> = listOf(
    BodyHotspot(CanonicalMuscle.SHOULDERS, 0.20f, 0.125f, 0.60f, 0.08f),
    BodyHotspot(CanonicalMuscle.BACK, 0.31f, 0.20f, 0.38f, 0.22f),
    BodyHotspot(CanonicalMuscle.TRICEPS, 0.10f, 0.22f, 0.18f, 0.16f),
    BodyHotspot(CanonicalMuscle.TRICEPS, 0.72f, 0.22f, 0.18f, 0.16f),
    BodyHotspot(CanonicalMuscle.GLUTES, 0.33f, 0.43f, 0.34f, 0.11f),
    BodyHotspot(CanonicalMuscle.HAMSTRINGS, 0.30f, 0.55f, 0.18f, 0.18f),
    BodyHotspot(CanonicalMuscle.HAMSTRINGS, 0.52f, 0.55f, 0.18f, 0.18f),
    BodyHotspot(CanonicalMuscle.CALVES, 0.32f, 0.76f, 0.14f, 0.18f),
    BodyHotspot(CanonicalMuscle.CALVES, 0.54f, 0.76f, 0.14f, 0.18f),
)
