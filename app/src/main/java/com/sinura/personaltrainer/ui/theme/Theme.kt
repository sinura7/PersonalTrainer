package com.sinura.personaltrainer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Every Material colour role, mapped on purpose.
 *
 * The scheme this replaces set about eighteen of these and let the rest fall through to
 * Material's baseline, which is derived from Google's purple-tinted neutrals. Those unset
 * roles were not obscure ones: `NavigationBar` draws on `surfaceContainer`, `AlertDialog`
 * on `surfaceContainerHigh`, `ModalBottomSheet` on `surfaceContainerLow`, a selected
 * `FilterChip` and a `LinearProgressIndicator` track on `secondaryContainer`, and — largest
 * of all — a filled `Card` on `surfaceContainerHighest`. So the bottom bar, every dialog,
 * every sheet, the rest timer's own progress track and *every default card in the app*
 * rendered in baseline lavender-grey, inside a product that believed it had a green theme.
 * None of it was visible in code review, because no screen file names these roles.
 *
 * The container ladder below is therefore mapped by **which component consumes each role**
 * rather than by nominal ordering. `surfaceContainer` is the darkest of them because the
 * navigation bar sits on it and the navigation bar belongs on the window colour; cards get
 * [Surface2] through `surfaceContainerHighest`. Anything written later that reaches for a
 * container role lands somewhere deliberate.
 *
 * `surfaceTint` is transparent, which switches off Material's tonal-elevation overlay.
 * Left at its default it takes the primary colour, and the one surface in the old app that
 * used `tonalElevation` was the bar behind the log button — so it wore a lime wash nobody
 * asked for.
 */
private val InstrumentColorScheme = darkColorScheme(
    primary = Volt,
    onPrimary = Pit,
    primaryContainer = VoltContainer,
    onPrimaryContainer = Volt,
    inversePrimary = Color(0xFF41610F),

    // Secondary is the accent again rather than a second colour: it is what a selected
    // chip and a progress track resolve to, and those are "live", which is volt's job.
    secondary = Volt,
    onSecondary = Pit,
    secondaryContainer = VoltContainer,
    onSecondaryContainer = Volt,

    // Tertiary is gold, so anything reaching for it lands on the record family.
    tertiary = PrGold,
    onTertiary = Pit,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = PrGold,

    background = Pit,
    onBackground = TextPrimary,
    surface = Surface2,
    onSurface = TextPrimary,
    surfaceVariant = Surface3,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,

    inverseSurface = TextPrimary,
    inverseOnSurface = Pit,

    error = Danger,
    onError = Pit,
    errorContainer = DangerContainer,
    onErrorContainer = Danger,

    outline = OutlineSolid,
    outlineVariant = OutlineSolidVariant,
    scrim = Color.Black,

    surfaceBright = Surface3,
    surfaceDim = Pit,
    surfaceContainerLowest = Pit,
    surfaceContainerLow = Surface3,
    surfaceContainer = Pit,
    surfaceContainerHigh = Surface3,
    surfaceContainerHighest = Surface2,
)

/**
 * Dark only, and on purpose.
 *
 * Strength training happens indoors in mixed-to-dim light, the rest timer holds the screen
 * awake for minutes at a time, and there is one developer — who can tune one theme exactly
 * or two adequately. The old light theme was a warm sand field with forest-green cards,
 * which is the visual language of a plant-care app; the old dark theme was a single-hue
 * green wash where the accent shared its hue with every container, so nothing could stand
 * out as live.
 *
 * The token architecture would admit a light ladder later. That is a project, not a toggle.
 */
@Composable
fun PersonalTrainerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = InstrumentColorScheme,
        typography = Typography,
        shapes = InstrumentShapes,
        content = content,
    )
}
