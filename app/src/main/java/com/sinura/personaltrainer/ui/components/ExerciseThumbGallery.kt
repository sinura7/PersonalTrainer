package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.SeedExercise
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
private data class ThumbSample(
    val equipment: EquipmentType,
    val primary: CanonicalMuscle,
    val movementKey: String,
)

private val SPREAD = listOf(
    ThumbSample(EquipmentType.BARBELL, CanonicalMuscle.CHEST, "bench-press"),
    ThumbSample(EquipmentType.BARBELL, CanonicalMuscle.QUADRICEPS, "squat"),
    ThumbSample(EquipmentType.DUMBBELL, CanonicalMuscle.SHOULDERS, "lateral-raise"),
    ThumbSample(EquipmentType.MACHINE, CanonicalMuscle.BACK, "pulldown"),
    ThumbSample(EquipmentType.CABLE, CanonicalMuscle.TRICEPS, "triceps-extension"),
    ThumbSample(EquipmentType.SMITH, CanonicalMuscle.GLUTES, "squat"),
    ThumbSample(EquipmentType.KETTLEBELL, CanonicalMuscle.HAMSTRINGS, "kettlebell-swing"),
    ThumbSample(EquipmentType.BAND, CanonicalMuscle.CALVES, "calf-raise"),
    ThumbSample(EquipmentType.BODYWEIGHT, CanonicalMuscle.CORE, "plank"),
    ThumbSample(EquipmentType.OTHER, CanonicalMuscle.BICEPS, "curl"),
)

/** One catalog row per family pose, so Studio can judge the whole language at once. */
private val FAMILY_KEYS = listOf(
    "squat", "deadlift", "lunge", "bench-press", "overhead-press",
    "chest-fly", "pulldown", "row", "curl", "triceps-extension",
    "hip-thrust", "plank", "leg-press", "carry",
)

/**
 * A sample lift. Carries one primary credit, one secondary, and a real family key so
 * the preview draws the posed silhouette rather than the standing fallback.
 */
private fun sample(equipment: EquipmentType, primary: CanonicalMuscle, movementKey: String): Exercise {
    val secondary = CanonicalMuscle.entries
        .first { it != primary && it != CanonicalMuscle.OTHER && thumbViewFor(it) == thumbViewFor(primary) }
    return Exercise(
        id = "preview-${equipment.name}-${primary.name}",
        name = "${equipment.label} ${primary.displayName}",
        muscleGroup = primary.catalogLabel,
        notes = "",
        isCustom = false,
        equipment = equipment,
        movementKey = movementKey,
        muscles = listOf(
            MuscleCredit(MuscleNormalizer.keyOf(primary), 1.0),
            MuscleCredit(MuscleNormalizer.keyOf(secondary), 0.5),
        ),
    )
}

private fun fromSeed(seed: SeedExercise): Exercise = Exercise(
    id = seed.id,
    name = seed.name,
    muscleGroup = seed.muscleGroup,
    notes = "",
    isCustom = false,
    equipment = seed.equipment,
    loadType = seed.loadType,
    movementKey = seed.movementKey,
    imageKey = seed.imageKey,
    muscles = seed.credits,
)

@Preview(name = "Tab marks", widthDp = 520, heightDp = 180)
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
                    "Settings" to TemperIcons.Settings,
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

@Preview(name = "Pose families — header size", widthDp = 420, heightDp = 720)
@Composable
private fun PoseFamiliesPreview() {
    val catalog = DefaultExercises.catalog()
    val rows = FAMILY_KEYS.mapNotNull { key -> catalog.firstOrNull { it.movementKey == key } }
    PersonalTrainerTheme {
        GalleryFrame("Family poses — 56dp, one catalog lift each") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                items(rows, key = { it.id }) { seed ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        ExerciseThumb(exercise = fromSeed(seed), size = ThumbSize.header)
                        Text(seed.movementKey ?: seed.name, style = InstrumentType.caption, color = TextTertiary)
                    }
                }
            }
        }
    }
}

@Preview(name = "Body figure — 180dp", widthDp = 420, heightDp = 280)
@Composable
private fun BodyFigurePreview() {
    PersonalTrainerTheme {
        GalleryFrame("Standing figure — locked unlit / heat stills") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(demoHeatArtwork(view = BodyView.FRONT)),
                        contentDescription = null,
                        modifier = Modifier.size(BODY_PREVIEW_HEIGHT),
                        contentScale = ContentScale.Fit,
                    )
                    Text("Front", style = InstrumentType.caption, color = TextTertiary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(demoHeatArtwork(view = BodyView.BACK)),
                        contentDescription = null,
                        modifier = Modifier.size(BODY_PREVIEW_HEIGHT),
                        contentScale = ContentScale.Fit,
                    )
                    Text("Back", style = InstrumentType.caption, color = TextTertiary)
                }
            }
        }
    }
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
                items(SPREAD, key = { "${it.equipment.name}-${it.primary.name}" }) { cell ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        ExerciseThumb(
                            exercise = sample(
                                equipment = cell.equipment,
                                primary = cell.primary,
                                movementKey = cell.movementKey,
                            ),
                            size = size,
                        )
                        Text(
                            "${cell.equipment.label} · ${cell.primary.displayName}",
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

private val BODY_PREVIEW_HEIGHT = 180.dp
