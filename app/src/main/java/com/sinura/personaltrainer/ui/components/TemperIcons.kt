package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 24dp tab marks in the Temper plate language.
 *
 * Filled plates, one colour: [androidx.compose.material3.Icon] tints them Volt when the
 * tab is live and tertiary when it is not. Heat3 stays off these — a selected tab is an
 * action, which is volt's job.
 */
object TemperIcons {
    val Home: ImageVector
        get() = home ?: torsoMark("Home").also { home = it }

    val Body: ImageVector
        get() = body ?: bodyMark().also { body = it }

    val Plan: ImageVector
        get() = plan ?: stackMark("Plan", ticks = false).also { plan = it }

    val History: ImageVector
        get() = history ?: stackMark("History", ticks = true).also { history = it }

    val Settings: ImageVector
        get() = settings ?: settingsMark().also { settings = it }

    private var home: ImageVector? = null
    private var body: ImageVector? = null
    private var plan: ImageVector? = null
    private var history: ImageVector? = null
    private var settings: ImageVector? = null
}

private fun ImageVector.Builder.plate(vararg xy: Float) {
    path(fill = SolidColor(Color.Black)) {
        moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) {
            lineTo(xy[i], xy[i + 1])
            i += 2
        }
        close()
    }
}

private fun vector(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(build).build()

/** The launcher torso, reduced to five plates that still read at 24dp. */
private fun torsoMark(name: String): ImageVector = vector(name) {
    plate(9.4f, 2.8f, 14.6f, 2.8f, 14.6f, 5.8f, 9.4f, 5.8f)
    plate(3.0f, 5.6f, 9.2f, 5.4f, 8.6f, 10.6f, 2.4f, 11.4f)
    plate(14.8f, 5.4f, 21.0f, 5.6f, 21.6f, 11.4f, 15.4f, 10.6f)
    plate(8.8f, 5.8f, 12.0f, 6.2f, 12.0f, 11.6f, 7.6f, 11.0f)
    plate(12.0f, 6.2f, 15.2f, 5.8f, 16.4f, 11.0f, 12.0f, 11.6f)
    plate(8.0f, 11.8f, 16.0f, 11.8f, 16.8f, 15.4f, 15.2f, 19.4f, 8.8f, 19.4f, 7.2f, 15.4f)
}

/** Full figure: torso plus a pair of legs, for the Body tab. */
private fun bodyMark(): ImageVector = vector("Body") {
    plate(9.6f, 1.6f, 14.4f, 1.6f, 14.2f, 4.2f, 9.8f, 4.2f)
    plate(3.2f, 4.4f, 9.4f, 4.2f, 8.8f, 8.6f, 2.6f, 9.2f)
    plate(14.6f, 4.2f, 20.8f, 4.4f, 21.4f, 9.2f, 15.2f, 8.6f)
    plate(8.4f, 4.6f, 15.6f, 4.6f, 16.2f, 10.2f, 7.8f, 10.2f)
    plate(7.6f, 10.6f, 11.6f, 10.6f, 11.2f, 17.2f, 8.0f, 18.0f)
    plate(12.4f, 10.6f, 16.4f, 10.6f, 16.0f, 18.0f, 12.8f, 17.2f)
    plate(8.0f, 18.4f, 11.2f, 17.8f, 11.0f, 22.4f, 8.2f, 22.6f)
    plate(12.8f, 17.8f, 16.0f, 18.4f, 15.8f, 22.6f, 13.0f, 22.4f)
}

/** Stacked plates: a plan is a stack; history is the same stack with a spine of ticks. */
private fun stackMark(name: String, ticks: Boolean): ImageVector = vector(name) {
    plate(5.0f, 3.2f, 19.0f, 3.2f, 18.4f, 7.2f, 5.6f, 7.2f)
    plate(5.0f, 8.6f, 19.0f, 8.6f, 18.4f, 12.6f, 5.6f, 12.6f)
    plate(5.0f, 14.0f, 19.0f, 14.0f, 18.4f, 18.0f, 5.6f, 18.0f)
    if (ticks) {
        plate(2.2f, 4.4f, 4.0f, 4.4f, 4.0f, 6.0f, 2.2f, 6.0f)
        plate(2.2f, 9.8f, 4.0f, 9.8f, 4.0f, 11.4f, 2.2f, 11.4f)
        plate(2.2f, 15.2f, 4.0f, 15.2f, 4.0f, 16.8f, 2.2f, 16.8f)
    }
}

/** A cog from plates: Settings is a tab, not a Material gear on another page. */
private fun settingsMark(): ImageVector = vector("Settings") {
    plate(8.8f, 8.8f, 15.2f, 8.8f, 15.2f, 15.2f, 8.8f, 15.2f)
    plate(10.2f, 2.4f, 13.8f, 2.4f, 13.8f, 7.6f, 10.2f, 7.6f)
    plate(10.2f, 16.4f, 13.8f, 16.4f, 13.8f, 21.6f, 10.2f, 21.6f)
    plate(2.4f, 10.2f, 7.6f, 10.2f, 7.6f, 13.8f, 2.4f, 13.8f)
    plate(16.4f, 10.2f, 21.6f, 10.2f, 21.6f, 13.8f, 16.4f, 13.8f)
    plate(16.0f, 4.2f, 19.8f, 7.2f, 16.8f, 9.0f, 14.2f, 6.0f)
    plate(4.2f, 7.2f, 8.0f, 4.2f, 9.8f, 6.0f, 7.2f, 9.0f)
    plate(16.0f, 19.8f, 19.8f, 16.8f, 16.8f, 15.0f, 14.2f, 18.0f)
    plate(4.2f, 16.8f, 8.0f, 19.8f, 9.8f, 18.0f, 7.2f, 15.0f)
}
