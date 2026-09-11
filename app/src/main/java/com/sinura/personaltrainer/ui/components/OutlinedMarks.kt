package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The handful of outlined marks that used to pull
 * `material-icons-extended` (megabytes of unused vectors) into the
 * unminified debug APK Obtainium installs.
 *
 * Paths are the Material outlined 24dp shapes (Apache 2.0). Icons that
 * already live in `material-icons-core` stay on `Icons.Outlined`.
 */
object OutlinedMarks {
    val ExpandLess: ImageVector
        get() = expandLess ?: svgMark("ExpandLess", EXPAND_LESS).also { expandLess = it }

    val ExpandMore: ImageVector
        get() = expandMore ?: svgMark("ExpandMore", EXPAND_MORE).also { expandMore = it }

    val ErrorOutline: ImageVector
        get() = errorOutline ?: svgMark("ErrorOutline", ERROR_OUTLINE).also { errorOutline = it }

    val EmojiEvents: ImageVector
        get() = emojiEvents ?: svgMark("EmojiEvents", EMOJI_EVENTS).also { emojiEvents = it }

    val PlaylistAdd: ImageVector
        get() = playlistAdd
            ?: svgMark("PlaylistAdd", PLAYLIST_ADD, autoMirror = true).also { playlistAdd = it }

    val MoreVert: ImageVector
        get() = moreVert ?: svgMark("MoreVert", MORE_VERT).also { moreVert = it }

    private var expandLess: ImageVector? = null
    private var expandMore: ImageVector? = null
    private var errorOutline: ImageVector? = null
    private var emojiEvents: ImageVector? = null
    private var playlistAdd: ImageVector? = null
    private var moreVert: ImageVector? = null
}

private const val EXPAND_LESS =
    "M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z"
private const val EXPAND_MORE =
    "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z"
private const val ERROR_OUTLINE =
    "M11 15h2v2h-2zm0-8h2v6h-2zm.99-5C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z"
private const val EMOJI_EVENTS =
    "M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94.63 1.5 1.98 2.63 3.61 2.96V19H7v2h10v-2h-4v-3.1c1.63-.33 2.98-1.46 3.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm7 6c-1.65 0-3-1.35-3-3V5h6v6c0 1.65-1.35 3-3 3zm7-6c0 1.3-.84 2.4-2 2.82V7h2v1z"
private const val PLAYLIST_ADD =
    "M14 10H2v2h12v-2zm0-4H2v2h12V6zm4 8v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2 16h8v-2H2v2z"
private const val MORE_VERT =
    "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"

private fun svgMark(name: String, d: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            appendSvg(d)
        }
    }.build()

private fun PathBuilder.appendSvg(d: String) {
    for (node in PathParser().parsePathString(d).toNodes()) {
        when (node) {
            is PathNode.MoveTo -> moveTo(node.x, node.y)
            is PathNode.RelativeMoveTo -> moveToRelative(node.dx, node.dy)
            is PathNode.LineTo -> lineTo(node.x, node.y)
            is PathNode.RelativeLineTo -> lineToRelative(node.dx, node.dy)
            is PathNode.HorizontalTo -> horizontalLineTo(node.x)
            is PathNode.RelativeHorizontalTo -> horizontalLineToRelative(node.dx)
            is PathNode.VerticalTo -> verticalLineTo(node.y)
            is PathNode.RelativeVerticalTo -> verticalLineToRelative(node.dy)
            is PathNode.CurveTo ->
                curveTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
            is PathNode.RelativeCurveTo ->
                curveToRelative(node.dx1, node.dy1, node.dx2, node.dy2, node.dx3, node.dy3)
            is PathNode.ReflectiveCurveTo ->
                reflectiveCurveTo(node.x1, node.y1, node.x2, node.y2)
            is PathNode.RelativeReflectiveCurveTo ->
                reflectiveCurveToRelative(node.dx1, node.dy1, node.dx2, node.dy2)
            is PathNode.QuadTo -> quadTo(node.x1, node.y1, node.x2, node.y2)
            is PathNode.RelativeQuadTo ->
                quadToRelative(node.dx1, node.dy1, node.dx2, node.dy2)
            is PathNode.ReflectiveQuadTo -> reflectiveQuadTo(node.x, node.y)
            is PathNode.RelativeReflectiveQuadTo ->
                reflectiveQuadToRelative(node.dx, node.dy)
            is PathNode.ArcTo ->
                arcTo(
                    node.horizontalEllipseRadius,
                    node.verticalEllipseRadius,
                    node.theta,
                    node.isMoreThanHalf,
                    node.isPositiveArc,
                    node.arcStartX,
                    node.arcStartY,
                )
            is PathNode.RelativeArcTo ->
                arcToRelative(
                    node.horizontalEllipseRadius,
                    node.verticalEllipseRadius,
                    node.theta,
                    node.isMoreThanHalf,
                    node.isPositiveArc,
                    node.arcStartDx,
                    node.arcStartDy,
                )
            is PathNode.Close -> close()
        }
    }
}
