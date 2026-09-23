package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 24dp marks. Tabs and Settings index rows are Allen's 14 Sep 2026 glyphs
 * (house, calendar, stick figure, list+clock, gear, and the nine row
 * leftovers). [androidx.compose.material3.Icon] tints them Volt when a tab
 * is live and [com.sinura.personaltrainer.ui.theme.TextSecondary] when it is
 * not — the source ink is black so a missing tint would vanish on Pit.
 *
 * Row actions ([Edit], [Delete], [Check], [Chevron]) and the debug
 * About rows ([Log], [Foundation]) stay on the plate language: they were
 * not in that drop.
 *
 * Everything here is drawn rather than imported: `tools/check-design-tokens.py`
 * refuses `Icons.Filled.*` and friends outright, and the ceiling in
 * `tools/checker-baselines.toml` may fall but never rise.
 */
object TemperIcons {
    val Home: ImageVector
        get() = home ?: glyph("Home", TemperGlyphPaths.HOME).also { home = it }

    val Body: ImageVector
        get() = body ?: glyph("Body", TemperGlyphPaths.BODY).also { body = it }

    val Plan: ImageVector
        get() = plan ?: glyph("Plan", TemperGlyphPaths.PLAN).also { plan = it }

    val History: ImageVector
        get() = history ?: glyph("History", TemperGlyphPaths.HISTORY).also { history = it }

    val Settings: ImageVector
        get() = settings ?: glyph("Settings", TemperGlyphPaths.SETTINGS).also { settings = it }

    /** Row action: revise a logged set. */
    val Edit: ImageVector
        get() = edit ?: pencilMark().also { edit = it }

    /** Compact overflow action, drawn in the same plate vocabulary. */
    val More: ImageVector by lazy {
        vector("More") {
            plate(10f, 3f, 14f, 3f, 14f, 7f, 10f, 7f)
            plate(10f, 10f, 14f, 10f, 14f, 14f, 10f, 14f)
            plate(10f, 17f, 14f, 17f, 14f, 21f, 10f, 21f)
        }
    }

    /** Row action: remove a logged set. */
    val Delete: ImageVector
        get() = delete ?: binMark().also { delete = it }

    val Display: ImageVector
        get() = display ?: glyph("Display", TemperGlyphPaths.DISPLAY).also { display = it }

    val Reminders: ImageVector
        get() = reminders ?: glyph("Reminders", TemperGlyphPaths.REMINDERS).also { reminders = it }

    val Generator: ImageVector
        get() = generator ?: glyph("Generator", TemperGlyphPaths.GENERATOR).also { generator = it }

    val Rest: ImageVector
        get() = rest ?: glyph("Rest", TemperGlyphPaths.REST).also { rest = it }

    /** Floor weight well — barbell plates. Replaces the "weight" kicker. */
    val FloorWeight: ImageVector
        get() = floorWeight ?: glyph("FloorWeight", TemperGlyphPaths.FLOOR_WEIGHT).also { floorWeight = it }

    /** Floor reps or hold-time well — tally with a crown. */
    val FloorRepsTime: ImageVector
        get() = floorRepsTime ?: glyph("FloorRepsTime", TemperGlyphPaths.FLOOR_REPS_TIME).also { floorRepsTime = it }

    /** Floor RPE track — rising effort bars. */
    val FloorRpe: ImageVector
        get() = floorRpe ?: glyph("FloorRpe", TemperGlyphPaths.FLOOR_RPE).also { floorRpe = it }

    /** Floor rest dock — clock frame. Settings still uses [Rest]. */
    val FloorRest: ImageVector
        get() = floorRest ?: glyph("FloorRest", TemperGlyphPaths.FLOOR_REST).also { floorRest = it }

    val Bodyweight: ImageVector
        get() = bodyweight ?: glyph("Bodyweight", TemperGlyphPaths.BODYWEIGHT).also { bodyweight = it }

    val Backup: ImageVector
        get() = backup ?: glyph("Backup", TemperGlyphPaths.BACKUP).also { backup = it }

    /** Settings "Your plan" — stacked layers, not the Plan tab's calendar. */
    val YourPlan: ImageVector
        get() = yourPlan ?: glyph("YourPlan", TemperGlyphPaths.YOUR_PLAN).also { yourPlan = it }

    val Diagnostics: ImageVector
        get() = diagnostics ?: glyph("Diagnostics", TemperGlyphPaths.DIAGNOSTICS).also { diagnostics = it }

    val About: ImageVector
        get() = about ?: glyph("About", TemperGlyphPaths.ABOUT).also { about = it }

    val Log: ImageVector
        get() = log ?: logMark().also { log = it }

    val Foundation: ImageVector
        get() = foundation ?: cylinderMark().also { foundation = it }

    val Check: ImageVector
        get() = check ?: checkMark().also { check = it }

    val Chevron: ImageVector
        get() = chevron ?: chevronMark().also { chevron = it }

    /** [Chevron] turned to point down: a control that opens a list (the lift switch). */
    val ChevronDown: ImageVector
        get() = chevronDown ?: chevronDownMark().also { chevronDown = it }

    /** [Chevron] mirrored: the back control on pushed routes. */
    val Back: ImageVector
        get() = back ?: backMark().also { back = it }

    /** Idle Time-set mark. Floor rest keeps [FloorRest]; this adds a crown. */
    val Stopwatch: ImageVector
        get() = stopwatch ?: stopwatchMark().also { stopwatch = it }

    private var home: ImageVector? = null
    private var body: ImageVector? = null
    private var plan: ImageVector? = null
    private var history: ImageVector? = null
    private var settings: ImageVector? = null
    private var edit: ImageVector? = null
    private var delete: ImageVector? = null
    private var display: ImageVector? = null
    private var reminders: ImageVector? = null
    private var generator: ImageVector? = null
    private var rest: ImageVector? = null
    private var floorWeight: ImageVector? = null
    private var floorRepsTime: ImageVector? = null
    private var floorRpe: ImageVector? = null
    private var floorRest: ImageVector? = null
    private var bodyweight: ImageVector? = null
    private var backup: ImageVector? = null
    private var yourPlan: ImageVector? = null
    private var diagnostics: ImageVector? = null
    private var about: ImageVector? = null
    private var log: ImageVector? = null
    private var foundation: ImageVector? = null
    private var check: ImageVector? = null
    private var chevron: ImageVector? = null
    private var chevronDown: ImageVector? = null
    private var back: ImageVector? = null
    private var stopwatch: ImageVector? = null
}

/** One black fill so the named-colour ratchet does not count every path. */
private val GlyphInk = SolidColor(Color.Black)

private fun ImageVector.Builder.plate(vararg xy: Float) {
    path(fill = GlyphInk) {
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

private fun glyph(name: String, d: String): ImageVector = vector(name) {
    path(
        fill = GlyphInk,
        pathFillType = PathFillType.EvenOdd,
    ) {
        appendSvg(d)
    }
}

/**
 * A pencil on the same diagonal the plate language uses everywhere else: point, shaft,
 * ferrule, each a filled plate that shares an edge with the next so the three read as one
 * object at 24dp. Drawn rather than borrowed — [TemperIcons] exists because
 * `tools/check-design-tokens.py` refuses `Icons.Filled.*` outright.
 */
private fun pencilMark(): ImageVector = vector("Edit") {
    // Sharpened point, apex in the corner the shaft runs to.
    plate(3.0f, 21.0f, 6.4f, 20.2f, 3.8f, 17.6f)
    // Shaft: a band at 45 degrees, sharing the point's base edge.
    plate(3.8f, 17.6f, 15.2f, 6.2f, 17.8f, 8.8f, 6.4f, 20.2f)
    // Ferrule, sharing the shaft's top edge.
    plate(15.2f, 6.2f, 17.4f, 4.0f, 20.0f, 6.6f, 17.8f, 8.8f)
}

/**
 * A bin: handle, lid, tapered body. The taper is what stops it reading as a cup at tab size,
 * and the lid overhangs the body on both sides for the same reason.
 */
private fun binMark(): ImageVector = vector("Delete") {
    // Handle.
    plate(9.6f, 3.0f, 14.4f, 3.0f, 14.4f, 5.4f, 9.6f, 5.4f)
    // Lid, wider than the body it covers.
    plate(4.0f, 5.6f, 20.0f, 5.6f, 20.0f, 7.8f, 4.0f, 7.8f)
    // Body, tapering in so the silhouette is a bin and not a box.
    plate(5.6f, 8.6f, 18.4f, 8.6f, 17.0f, 20.8f, 7.0f, 20.8f)
}

private fun logMark(): ImageVector = vector("Log") {
    plate(4.4f, 4.4f, 19.6f, 4.4f, 19.6f, 7.6f, 4.4f, 7.6f)
    plate(4.4f, 10.2f, 19.6f, 10.2f, 19.6f, 13.4f, 4.4f, 13.4f)
    plate(4.4f, 16.0f, 15.2f, 16.0f, 15.2f, 19.2f, 4.4f, 19.2f)
}

private fun cylinderMark(): ImageVector = vector("Foundation") {
    plate(5.2f, 4.0f, 18.8f, 4.0f, 18.8f, 8.4f, 5.2f, 8.4f)
    plate(5.2f, 9.4f, 18.8f, 9.4f, 18.8f, 15.2f, 5.2f, 15.2f)
    plate(5.2f, 16.2f, 18.8f, 16.2f, 18.8f, 20.6f, 5.2f, 20.6f)
}

private fun checkMark(): ImageVector = vector("Check") {
    plate(4.0f, 12.0f, 6.6f, 10.4f, 10.6f, 16.8f, 8.0f, 18.4f)
    plate(9.2f, 16.4f, 11.8f, 14.8f, 20.4f, 5.2f, 17.8f, 3.6f)
}

private fun chevronMark(): ImageVector = vector("Chevron") {
    plate(8.8f, 4.8f, 11.4f, 4.8f, 17.4f, 12.0f, 11.4f, 19.2f, 8.8f, 19.2f, 14.4f, 12.0f)
}

private fun chevronDownMark(): ImageVector = vector("ChevronDown") {
    plate(19.2f, 8.8f, 19.2f, 11.4f, 12.0f, 17.4f, 4.8f, 11.4f, 4.8f, 8.8f, 12.0f, 14.4f)
}

private fun backMark(): ImageVector = vector("Back") {
    plate(15.2f, 4.8f, 12.6f, 4.8f, 6.6f, 12.0f, 12.6f, 19.2f, 15.2f, 19.2f, 9.6f, 12.0f)
}

/** Rectangular clock in the floor-rest language, plus a crown for Time set. */
private fun stopwatchMark(): ImageVector = vector("Stopwatch") {
    plate(10.0f, 1.6f, 14.0f, 1.6f, 14.0f, 4.4f, 10.0f, 4.4f)
    plate(4.0f, 5.0f, 20.0f, 5.0f, 20.0f, 7.0f, 4.0f, 7.0f)
    plate(4.0f, 7.0f, 6.0f, 7.0f, 6.0f, 18.0f, 4.0f, 18.0f)
    plate(18.0f, 7.0f, 20.0f, 7.0f, 20.0f, 18.0f, 18.0f, 18.0f)
    plate(4.0f, 18.0f, 20.0f, 18.0f, 20.0f, 20.4f, 4.0f, 20.4f)
    plate(11.0f, 8.2f, 13.0f, 8.2f, 13.0f, 13.4f, 16.2f, 13.4f, 16.2f, 15.4f, 11.0f, 15.4f)
}
