package com.minimaldesigner.arise.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minimaldesigner.arise.core.TaskKind

/** One stroked or filled path on the 24x24 grid, copied from arise-v2's inline SVG. */
internal data class Stroke(val d: String, val width: Float = 1.8f, val fill: Boolean = false, val cut: Boolean = false)

/** The v2 `ICON` set plus the nav glyphs. Circles and rects are rewritten as paths. */
enum class Glyph(internal val parts: List<Stroke>) {
    Chev(listOf(Stroke("M9 5l7 7-7 7", 2f))),
    ChevLeft(listOf(Stroke("M15 5l-7 7 7 7", 2f))),
    Check(listOf(Stroke("M5 12.5l4.5 4.5L19 7.5", 3f))),
    Workout(listOf(Stroke("M3 12h3l3-7 4 14 3-7h5"))),
    Diet(listOf(Stroke("M12 7c-3-3-8-1-8 4 0 5 4 9 8 9s8-4 8-9c0-5-5-7-8-4zM12 7c0-2 1-4 3-4"))),
    Water(listOf(Stroke("M12 3s-6 7-6 11a6 6 0 0 0 12 0c0-4-6-11-6-11z"))),
    Read(listOf(Stroke("M3 5h6a3 3 0 0 1 3 3v12a2 2 0 0 0-2-2H3zM21 5h-6a3 3 0 0 0-3 3v12a2 2 0 0 1 2-2h7z"))),
    Photo(listOf(Stroke("M6 6h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3zM8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0z"))),
    Walk(listOf(Stroke("M11 4a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM11 21l2-6-3-3 1-5 4 3 3 1M8 13l-2 8"))),
    Sleep(listOf(Stroke("M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"))),
    Mind(listOf(Stroke("M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0zM9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0z"))),
    Other(listOf(Stroke("M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0z"))),
    Flame(listOf(Stroke("M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-3 2-4 2-7 2 1 3 2 3 4"))),
    NavHome(listOf(
        Stroke("M4 10.2 12 4l8 6.2V19a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z", fill = true),
        Stroke("M9 15.5c.8.8 1.8 1.2 3 1.2s2.2-.4 3-1.2", cut = true),
    )),
    NavWeek(listOf(Stroke("M5 20V12M10 20V6M15 20v-9M20 20V9"))),
    Sliders(listOf(Stroke("M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"))),
    // 0.2.1: the settings pages.
    Bell(listOf(Stroke("M6 16v-5a6 6 0 0 1 12 0v5l2 2H4zM10 20.5a2 2 0 0 0 4 0"))),
    Theme(listOf(Stroke("M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0z"), Stroke("M12 4a8 8 0 0 1 0 16z", fill = true))),
    Data(listOf(Stroke("M4 6.5c0-2 16-2 16 0v11c0 2-16 2-16 0zM4 6.5c0 2 16 2 16 0M4 12c0 2 16 2 16 0"))),
    Info(listOf(Stroke("M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0zM12 11v5M12 8v.01"))),
    // 0.2.1-alpha.3: the paused challenge.
    Pause(listOf(Stroke("M9 6v12M15 6v12", 2.4f))),
    NavFood(listOf(Stroke("M7 3v8M4.5 3v5a2.5 2.5 0 0 0 5 0V3M7 11v10M17 3c-2 1.5-3 4-3 7h3v11"))),
    NavPhotos(listOf(Stroke("M6 6h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3zM8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0zM9 6l1.2-2h3.6L15 6"))),
}

fun glyphFor(kind: TaskKind): Glyph = when (kind) {
    TaskKind.WORKOUT -> Glyph.Workout
    TaskKind.DIET -> Glyph.Diet
    TaskKind.WATER -> Glyph.Water
    TaskKind.READ -> Glyph.Read
    TaskKind.PHOTO -> Glyph.Photo
    TaskKind.WALK -> Glyph.Walk
    TaskKind.SLEEP -> Glyph.Sleep
    TaskKind.MIND -> Glyph.Mind
    TaskKind.OTHER -> Glyph.Other
}

/**
 * Draws a glyph in [color]. [cutColor] paints the parts that the SVG draws in the
 * background colour (the smile cut out of the filled home icon).
 */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 20.dp, cutColor: Color = Color.Transparent, modifier: Modifier = Modifier) {
    val vector = remember(glyph, color, cutColor) {
        val b = ImageVector.Builder(glyph.name, 24.dp, 24.dp, 24f, 24f)
        glyph.parts.forEach { p ->
            val paint = SolidColor(if (p.cut) cutColor else color)
            if (p.fill) {
                b.addPath(pathData = addPathNodes(p.d), fill = paint)
            } else {
                b.addPath(
                    pathData = addPathNodes(p.d), fill = null, stroke = paint, strokeLineWidth = p.width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        b.build()
    }
    Image(imageVector = vector, contentDescription = null, modifier = modifier.size(size))
}
