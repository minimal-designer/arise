package com.minimaldesigner.arise.core

// 0.2.1: the floating nav's sliding pill. The app measures the tabs; the maths lives here
// so it can be tested without Android.

/** The pill's left edge and width, in px. */
data class PillSpan(val x: Float, val width: Float)

fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

/**
 * How open each tab's label is (0 = icon only, 1 = icon and label), [f] of the way from
 * [start] to only tab [to] being open. Starting from the weights on screen means a tap in
 * the middle of a slide carries on from where it is, with no jump.
 */
fun navWeights(start: List<Float>, to: Int, f: Float): List<Float> =
    start.mapIndexed { i, w -> lerp(w, if (i == to) 1f else 0f, f) }

/** Left edges of tabs with [widths] spread across [total] like Arrangement.SpaceAround. */
fun spaceAround(widths: List<Float>, total: Float): List<Float> {
    if (widths.isEmpty()) return emptyList()
    val gap = ((total - widths.sum()) / widths.size).coerceAtLeast(0f)
    var x = gap / 2
    return widths.map { w -> x.also { x += w + gap } }
}

/** The pill, [f] of the way from [start] (where it was) to tab [to] as laid out now. */
fun pillSpan(start: PillSpan?, xs: List<Float>, widths: List<Float>, to: Int, f: Float): PillSpan {
    val target = PillSpan(xs[to], widths[to])
    if (start == null) return target
    return PillSpan(lerp(start.x, target.x, f), lerp(start.width, target.width, f))
}
