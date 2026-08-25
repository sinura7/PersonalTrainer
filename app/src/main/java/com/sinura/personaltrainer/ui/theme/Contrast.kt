package com.sinura.personaltrainer.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast for Instrument tokens (P9.2 / FND-024).
 *
 * [TextTertiary] measures about 3.2:1 and is decorative or disabled only.
 * Load-bearing copy uses [TextPrimary] or [TextSecondary].
 */
object ContrastPolicy {
    const val AA_NORMAL = 4.5
    const val AA_LARGE = 3.0

    fun relativeLuminance(color: Color): Double {
        fun channel(component: Float): Double {
            val value = component.toDouble().coerceIn(0.0, 1.0)
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    fun ratio(foreground: Color, background: Color): Double {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        val lighter = max(first, second)
        val darker = min(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun meetsAA(foreground: Color, background: Color, largeText: Boolean = false): Boolean {
        val floor = if (largeText) AA_LARGE else AA_NORMAL
        return ratio(foreground, background) + 1e-6 >= floor
    }

    fun isLoadBearing(color: Color): Boolean = color != TextTertiary
}
