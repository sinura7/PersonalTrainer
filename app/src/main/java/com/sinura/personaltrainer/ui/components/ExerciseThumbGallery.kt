package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * A preview sheet for judging the drawings, not a screen.
 *
 * There is no route to this and nothing ships it — it exists so the nine glyphs and the muscle
 * spread can be looked at side by side on a real device, at the sizes they actually render, in
 * one place. Reading them one row at a time down a scrolling library is how you convince
 * yourself a glyph is fine when it is not.
 *
 * Open in Android Studio and use the gutter run-on-device action per preview.
 */
private val SPREAD = listOf(
    EquipmentType.BARBELL to CanonicalMuscle.CHEST,
    EquipmentType.BARBELL to CanonicalMuscle.QUADRICEPS,
    EquipmentType.DUMBBELL to CanonicalMuscle.SHOULDERS,
    EquipmentType.MACHINE to CanonicalMuscle.BACK,
    EquipmentType.CABLE to CanonicalMuscle.TRICEPS,
    EquipmentType.SMITH to CanonicalMuscle.GLUTES,
    EquipmentType.KETTLEBELL to CanonicalMuscle.HAMSTRINGS,
    EquipmentType.BAND to CanonicalMuscle.CALVES,
    EquipmentType.BODYWEIGHT to CanonicalMuscle.CORE,
    EquipmentType.OTHER to CanonicalMuscle.BICEPS,
)

/**
 * A sample lift. Carries one primary credit and one secondary, so the preview shows the
 * two-tone case rather than the easy one.
 */
private fun sample(equipment: EquipmentType, primary: CanonicalMuscle): Exercise {
    val secondary = CanonicalMuscle.entries
        .first { it != primary && it != CanonicalMuscle.OTHER && thumbViewFor(it) == thumbViewFor(primary) }
    return Exercise(
        id = "preview-${equipment.name}-${primary.name}",
        name = "${equipment.label} ${primary.displayName}",
        muscleGroup = primary.catalogLabel,
        notes = "",
        isCustom = false,
        equipment = equipment,
        muscles = listOf(
            MuscleCredit(MuscleNormalizer.keyOf(primary), 1.0),
            MuscleCredit(MuscleNormalizer.keyOf(secondary), 0.5),
        ),
    )
}

@Preview(name = "Tab marks", widthDp = 420, heightDp = 180)
@Composable
private fun TabMarksPreview() {
    PersonalTrainerTheme {
        GalleryFrame("Temper tab marks — 24dp, as the bar draws them") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    "Home" to TemperIcons.Home,
                    "Body" to TemperIcons.Body,
                    "Plan" to TemperIcons.Plan,
                    "History" to TemperIcons.History,
                ).forEach { (label, icon) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(ThumbSize.chipGlyph + Metrics.space1),
                        )
                        Text(label, style = InstrumentType.caption, color = TextTertiary)
                    }
                }
            }
            TemperMark(size = 72.dp)
        }
    }
}

@Preview(name = "Equipment glyphs", widthDp = 420, heightDp = 420)
@Composable
private fun EquipmentGlyphsPreview() {
    PersonalTrainerTheme {
        GalleryFrame("Equipment glyphs — 20dp chip, then badge scale") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                items(EquipmentGlyph.entries.toList(), key = { it.name }) { glyph ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EquipmentGlyphIcon(glyph = glyph, size = ThumbSize.chipGlyph)
                            EquipmentGlyphIcon(glyph = glyph, size = BADGE_GLYPH_PREVIEW)
                        }
                        Text(glyph.name, style = InstrumentType.caption, color = TextTertiary)
                    }
                }
            }
        }
    }
}

@Preview(name = "Thumbs — row size", widthDp = 420, heightDp = 520)
@Composable
private fun RowThumbsPreview() {
    ThumbSpread(size = ThumbSize.row, title = "Thumbnails — 40dp, as rows draw them")
}

@Preview(name = "Thumbs — header size", widthDp = 420, heightDp = 620)
@Composable
private fun HeaderThumbsPreview() {
    ThumbSpread(size = ThumbSize.header, title = "Thumbnails — 56dp, as the detail header draws them")
}

@Composable
private fun ThumbSpread(size: Dp, title: String) {
    PersonalTrainerTheme {
        GalleryFrame(title) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                items(SPREAD, key = { "${it.first.name}-${it.second.name}" }) { (equipment, muscle) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        ExerciseThumb(exercise = sample(equipment, muscle), size = size)
                        Text(
                            "${equipment.label} · ${muscle.displayName}",
                            style = InstrumentType.caption,
                            color = TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryFrame(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit)
            .padding(Metrics.gutter),
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        Text(title, style = InstrumentType.bodyStrong, color = TextSecondary)
        content()
    }
}

/** Roughly the badge's glyph size at a 40dp thumb, so the preview shows the real worst case. */
private val BADGE_GLYPH_PREVIEW = Metrics.space3
