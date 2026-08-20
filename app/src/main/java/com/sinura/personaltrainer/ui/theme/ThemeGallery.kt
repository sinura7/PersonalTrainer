package com.sinura.personaltrainer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Every Material colour role, drawn.
 *
 * This exists because the defect it guards against is invisible in code review. The theme
 * used to map about eighteen of these roles and let the rest fall through to Material's
 * baseline, which is derived from Google's purple-tinted neutrals — so the navigation bar,
 * every dialog, every sheet, the rest timer's progress track and every default card
 * rendered lavender-grey, and nothing in any screen file mentioned a colour at all.
 *
 * Open the preview after touching the scheme. A baseline leak shows up here as an
 * unmistakable violet chip in a wall of near-blacks and one volt green.
 */
@Preview(name = "Colour roles", widthDp = 420, heightDp = 1500)
@Composable
private fun ColourRolesPreview() {
    PersonalTrainerTheme {
        val scheme = MaterialTheme.colorScheme
        Gallery("Material roles") {
            Swatch("primary", scheme.primary)
            Swatch("onPrimary", scheme.onPrimary)
            Swatch("primaryContainer", scheme.primaryContainer)
            Swatch("onPrimaryContainer", scheme.onPrimaryContainer)
            Swatch("inversePrimary", scheme.inversePrimary)
            Swatch("secondary", scheme.secondary)
            Swatch("onSecondary", scheme.onSecondary)
            Swatch("secondaryContainer", scheme.secondaryContainer)
            Swatch("onSecondaryContainer", scheme.onSecondaryContainer)
            Swatch("tertiary", scheme.tertiary)
            Swatch("onTertiary", scheme.onTertiary)
            Swatch("tertiaryContainer", scheme.tertiaryContainer)
            Swatch("onTertiaryContainer", scheme.onTertiaryContainer)
            Swatch("background", scheme.background)
            Swatch("onBackground", scheme.onBackground)
            Swatch("surface", scheme.surface)
            Swatch("onSurface", scheme.onSurface)
            Swatch("surfaceVariant", scheme.surfaceVariant)
            Swatch("onSurfaceVariant", scheme.onSurfaceVariant)
            Swatch("surfaceTint", scheme.surfaceTint)
            Swatch("inverseSurface", scheme.inverseSurface)
            Swatch("inverseOnSurface", scheme.inverseOnSurface)
            Swatch("error", scheme.error)
            Swatch("onError", scheme.onError)
            Swatch("errorContainer", scheme.errorContainer)
            Swatch("onErrorContainer", scheme.onErrorContainer)
            Swatch("outline", scheme.outline)
            Swatch("outlineVariant", scheme.outlineVariant)
            Swatch("scrim", scheme.scrim)
            Swatch("surfaceBright", scheme.surfaceBright)
            Swatch("surfaceDim", scheme.surfaceDim)
            Swatch("surfaceContainerLowest", scheme.surfaceContainerLowest)
            Swatch("surfaceContainerLow", scheme.surfaceContainerLow)
            Swatch("surfaceContainer", scheme.surfaceContainer)
            Swatch("surfaceContainerHigh", scheme.surfaceContainerHigh)
            Swatch("surfaceContainerHighest", scheme.surfaceContainerHighest)
        }
    }
}

/** The semantic tokens screens actually reach for, plus the one intensity ramp. */
@Preview(name = "Instrument tokens", widthDp = 420, heightDp = 900)
@Composable
private fun InstrumentTokensPreview() {
    PersonalTrainerTheme {
        Gallery("Instrument tokens") {
            Swatch("Pit", Pit)
            Swatch("Surface1", Surface1)
            Swatch("Surface2", Surface2)
            Swatch("Surface3", Surface3)
            Swatch("SurfacePressed", SurfacePressed)
            Swatch("Volt", Volt)
            Swatch("VoltContainer", VoltContainer)
            Swatch("PrGold", PrGold)
            Swatch("GoldContainer", GoldContainer)
            Swatch("Warn", Warn)
            Swatch("Danger", Danger)
            Swatch("RestCyan", RestCyan)
            Swatch("TextPrimary", TextPrimary)
            Swatch("TextSecondary", TextSecondary)
            Swatch("TextTertiary", TextTertiary)

            Text(
                "heat ramp",
                style = InstrumentType.kicker,
                color = TextSecondary,
                modifier = Modifier.padding(top = Metrics.space4),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                // Stepped rather than continuous so each stop can be checked for a
                // monotonic rise in lightness, which is what makes the ramp readable
                // without colour vision.
                for (step in 0..10) {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 40.dp)
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(heatColor(step / 10f)),
                    )
                }
            }
        }
    }
}

/** The type ramp, at the sizes it actually ships at. */
@Preview(name = "Type ramp", widthDp = 420, heightDp = 900)
@Composable
private fun TypeRampPreview() {
    PersonalTrainerTheme {
        Gallery("Type") {
            Specimen("numeralHero", "142.5", InstrumentType.numeralHero)
            Specimen("numeralXl", "12,480", InstrumentType.numeralXl)
            Specimen("numeralLg", "1:34", InstrumentType.numeralLg)
            Specimen("numeralMd", "100 × 5", InstrumentType.numeralMd)
            Specimen("numeralSm", "8,420", InstrumentType.numeralSm)
            Specimen("display", "Workout complete", InstrumentType.display)
            Specimen("title", "Barbell bench press", InstrumentType.title)
            Specimen("body", "Hit target. Add 2.5 kg.", InstrumentType.body)
            Specimen("caption", "Mon · 18 Aug", InstrumentType.caption)
            Specimen("kicker", "LAST 7 DAYS", InstrumentType.kicker)
            // The whole point of the bundled faces: these two must be the same width.
            Specimen("tabular check", "1111111111", InstrumentType.numeralMd)
            Specimen("tabular check", "0000000000", InstrumentType.numeralMd)
        }
    }
}

@Composable
private fun Gallery(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .background(Pit)
            .verticalScroll(rememberScrollState())
            .padding(Metrics.gutter),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Text(title, style = InstrumentType.display, color = TextPrimary)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(color)
                .border(Metrics.hairline, HairlineStrong, RoundedCornerShape(Radius.xs)),
        )
        Text(name, style = InstrumentType.body, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(color.hex(), style = InstrumentType.numeralSm, color = TextSecondary)
    }
}

@Composable
private fun Specimen(name: String, sample: String, style: TextStyle) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Text(
            name,
            style = InstrumentType.caption,
            color = TextTertiary,
            modifier = Modifier.width(96.dp),
        )
        Text(sample, style = style, color = TextPrimary)
    }
}

private fun Color.hex(): String {
    val argb = (alpha * 255).toInt().shl(24) or
        (red * 255).toInt().shl(16) or
        (green * 255).toInt().shl(8) or
        (blue * 255).toInt()
    return "#" + argb.toUInt().toString(16).padStart(8, '0').uppercase()
}
