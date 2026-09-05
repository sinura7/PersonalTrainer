package com.sinura.personaltrainer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The Instrument palette.
 *
 * Two rules explain every value here.
 *
 * **Depth is light, not shadow.** On a near-black field a drop shadow is invisible, so
 * hierarchy is carried by a ladder of surfaces roughly 3% apart in luminance plus a
 * one-pixel hairline. Non-zero `shadowElevation` / `tonalElevation` is
 * forbidden — the second also tints with the accent, which is how the
 * old theme smeared lime across the log bar. `= 0.dp` is how
 * `InstrumentMenu` pins Material's default off.
 *
 * **One accent, earned.** [Volt] means live / act / now, and appears once or twice per
 * screen. [PrGold], [Warn], [Danger] and [RestCyan] are verbs, not decoration: a record,
 * an urgency, a destruction, a recovery. The palette this replaced spent green three ways
 * at once — brand, primary action, and "barely trained" on the heat map — so no colour in
 * it meant anything in particular.
 */

// ---------------------------------------------------------------------------
// Surface ladder
// ---------------------------------------------------------------------------

/** Window background, headers, bottom nav. Not pure black: see [TextPrimary]. */
val Pit = Color(0xFF07090B)

/** Grouped list containers, inset panels, chart plot areas. */
val Surface1 = Color(0xFF0E1215)

/** Cards and tiles — the default component surface. */
val Surface2 = Color(0xFF14191D)

/** Sheets, dialogs, menus: the topmost layer. */
val Surface3 = Color(0xFF1B2126)

/** Pressed and selected fills. */
val SurfacePressed = Color(0xFF232A30)

/** Card borders, dividers, ring tracks. White at 8%. */
val Hairline = Color(0x14FFFFFF)

/** Focus rings, the "today" marker, drag handles. White at 14%. */
val HairlineStrong = Color(0x24FFFFFF)

/** Opaque field and control borders. Unfocused outline is ≥ 3:1 on reading surfaces. */
val OutlineSolid = Color(0xFF6A757C)
val OutlineSolidVariant = Color(0xFF1E2429)

/**
 * The Temper plate. Lifted from the launcher mark so every drawn figure, glyph and empty
 * state is the same steel the icon sits in — not a second grey invented per surface.
 */
val Steel = Color(0xFF5B5B5A)

/** Head, neck, feet: present so the figure reads as a body, quieter than the working plates. */
val SteelDim = Color(0xFF3E4042)

// ---------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------

/**
 * Deliberately not `#FFFFFF` on deliberately not `#000000`.
 * Maximum-contrast pure pairs halate badly for astigmatic readers; this pair still
 * measures about 15.5:1 on [Surface2], comfortably past AAA.
 */
val TextPrimary = Color(0xFFF2F5F7)
val TextSecondary = Color(0xFF9BA7AE)

/** Quiet load-bearing copy — units, kickers, captions. About 5:1 on cards. */
val TextTertiary = Color(0xFF7F8B93)

/** Disabled controls only. Must not be used for copy a gym has to read. */
val TextDisabled = Color(0xFF5F6B73)

// ---------------------------------------------------------------------------
// Accent and semantics
// ---------------------------------------------------------------------------

/** The accent. Roughly 16:1 on [Pit]; ink on a volt fill is [Pit], at the same ratio. */
val Volt = Color(0xFFC2FF44)

/** Selected chips, active tracks. Volt at 14%. */
val VoltDim = Color(0x24C2FF44)

/** An opaque volt-tinted container, for surfaces that sit over unknown backgrounds. */
val VoltContainer = Color(0xFF212B13)

/** Records, and only records. Emotionally distinct from the accent so a PR reads as an event.
 * Volt and PrGold collapse for a deutan reader; Warn sits next to PrGold for everyone
 * (ADR-023). The trophy, the gold container, and the words "personal record" are the
 * non-colour channel. Hex values do not move in a polish packet. */
val PrGold = Color(0xFFFFC53D)
val GoldContainer = Color(0xFF2E2410)

/** Rest running out, missed targets, a stale backup.
 * Next to PrGold in hue. Copy, a clock, or a kicker must travel with it (ADR-023). */
val Warn = Color(0xFFFFB020)

/** Destructive actions only. */
val Danger = Color(0xFFFF6B6B)
val DangerContainer = Color(0xFF2A1214)

/** Recovery and rest-day identity; the cool end of the chart gradient. */
val RestCyan = Color(0xFF33D6E8)
val RestCyanDim = Color(0x2433D6E8)

// ---------------------------------------------------------------------------
// Muscle heat
// ---------------------------------------------------------------------------

/**
 * A magma-derived intensity ramp: luminance climbs monotonically from stop to stop, so
 * intensity is legible from lightness alone and hue is redundant encoding. That makes it
 * safe for deuteranopia, protanopia and tritanopia by construction.
 *
 * The ramp it replaces ran green to amber to red — traffic-light semantics that framed a
 * well-trained muscle as an error, and whose "barely trained" green was the same family as
 * the app's own primary action colour.
 */
val HeatEmpty = Color(0xFF262C31)
val Heat1 = Color(0xFF4A2480)
val Heat2 = Color(0xFF9D2F86)
/** Mid-ramp. Neighbours Danger in hue; the legend band name and luminance carry the signal (ADR-023). */
val Heat3 = Color(0xFFE25A50)
val Heat4 = Color(0xFFFCA05F)

private val HeatStops = arrayOf(Heat1, Heat2, Heat3, Heat4)

/** Below this, a muscle counts as untrained and gets [HeatEmpty] rather than the ramp. */
private const val HEAT_EMPTY_THRESHOLD = 0.02f

/**
 * The single intensity-to-colour function for the whole product.
 *
 * Body map, training calendar and the Home balance row all read from this, so "how hard
 * did I train" looks like one concept everywhere. Previously the calendar shaded days with
 * an alpha ramp of the brand green while the body map used a separate hard-coded
 * green-amber-red scale, and the two disagreed about what a hard day looked like.
 */
fun heatColor(fraction: Double): Color = heatColor(fraction.toFloat())

fun heatColor(fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    if (t <= HEAT_EMPTY_THRESHOLD) return HeatEmpty
    val scaled = ((t - HEAT_EMPTY_THRESHOLD) / (1f - HEAT_EMPTY_THRESHOLD)) * (HeatStops.size - 1)
    val index = scaled.toInt().coerceIn(0, HeatStops.size - 2)
    return lerp(HeatStops[index], HeatStops[index + 1], scaled - index)
}

/** Chart gradient: past on the left in [RestCyan], now on the right in [Volt]. */
val ChartPast = RestCyan
val ChartNow = Volt
