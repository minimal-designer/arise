package com.minimaldesigner.arise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaldesigner.arise.core.Days
import com.minimaldesigner.arise.core.Progress
import com.minimaldesigner.arise.core.Run
import com.minimaldesigner.arise.core.isDayCleared
import com.minimaldesigner.arise.core.Cell
import com.minimaldesigner.arise.core.TaskKind
import com.minimaldesigner.arise.core.weekGrid
import com.minimaldesigner.arise.core.Pause
import com.minimaldesigner.arise.core.weekStats
import com.minimaldesigner.arise.ui.components.Card
import com.minimaldesigner.arise.ui.components.CardHeader
import com.minimaldesigner.arise.ui.components.Glyph
import com.minimaldesigner.arise.ui.components.GlyphIcon
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.Num
import com.minimaldesigner.arise.ui.components.PageHeader
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.fmtInt
import com.minimaldesigner.arise.ui.fmtRange
import com.minimaldesigner.arise.ui.fmtWeekday
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.Radius
import com.minimaldesigner.arise.ui.theme.Type
import com.minimaldesigner.arise.ui.theme.mix
import kotlin.math.roundToInt

/** Dashed outline, as CSS `border: 1.5px dashed`. */
private fun Modifier.dashed(color: Color, width: Dp, radius: Dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 2 * w))),
    )
}

/** CSS `box-shadow: 0 0 0 3px card, 0 0 0 4.5px accent` - a ring outside the element. */
private fun Modifier.ring(color: Color, gap: Dp, width: Dp) = drawBehind {
    val g = gap.toPx() + width.toPx() / 2
    drawRoundRect(
        color, topLeft = Offset(-g, -g), size = Size(size.width + 2 * g, size.height + 2 * g),
        cornerRadius = CornerRadius(size.width / 2 + g), style = Stroke(width.toPx()),
    )
}

@Composable
fun Week(run: Run, days: Days, p: Progress, offset: Int, pause: Pause? = null, onOffset: (Int) -> Unit) {
    val c = LocalArise.current
    val w = weekStats(run, days, p.today, offset)
    val g = weekGrid(run, days, p.today, offset, pause)
    val canPrev = !w.monday.minusDays(1).isBefore(run.startDate)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Week") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ArrowButton(Glyph.ChevLeft, canPrev) { onOffset(offset - 1) }
                Lbl("${fmtRange(w.monday)} – ${fmtRange(w.monday.plusDays(6))}", c.ink)
                ArrowButton(Glyph.Chev, offset < 0) { onOffset(offset + 1) }
            }
        }

        // Every task, every day
        Card {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Lbl("Consistency")
                Num(g.pct?.let { "$it%" } ?: "–", 30f)
            }
            GridRowLayout({}, w.cols.map { col -> { Mn(fmtWeekday(col.date).take(1), col.date == p.today) } }, {})
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            g.rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line2))
                GridRowLayout(
                    { Txt(r.task.label.substringBefore(" · "), Type.body.copy(fontSize = 13.5.sp), maxLines = 1) },
                    r.cells.map { cell -> { CellDot(cell) } },
                    { Txt(if (r.count > 0) "${r.done}/${r.count}" else "–", Type.mono.copy(fontSize = 12.sp), if (r.count > 0 && r.done == 0) c.bad else c.ink2) },
                    height = 34.dp,
                )
                r.values?.let { vals ->
                    val unit = if (r.task.kind == TaskKind.READ) "pages" else "min"
                    GridRowLayout(
                        { Lbl("${fmtInt(vals.sum())} $unit", size = 9.5f) },
                        w.cols.mapIndexed { j, col ->
                            {
                                val v = vals[j]
                                Mn(
                                    when {
                                        v > 0 -> "$v"
                                        !col.inRun || col.future -> ""
                                        col.date == p.today -> "·"
                                        else -> "–"
                                    },
                                    col.date == p.today,
                                )
                            }
                        },
                        {},
                        height = 16.dp,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            GridRowLayout({ Lbl("Day") }, w.cols.map { col -> { Mn(col.n?.let { "$it/${run.tasks.size}" } ?: "", col.date == p.today) } }, {}, height = 28.dp)
            Legend(listOf(Dot(c.accent) to "Done", Dot(Color.Transparent, c.bad) to "Missed", Dot(Color.Transparent, c.ink, dashed = true) to "Today"))
        }

        // Done / cleared / missed
        val counted = w.cols.filter { it.pct != null && it.date != p.today }
        val clearedWk = counted.count { it.pct == 1.0 }
        val missWk = counted.count { (it.pct ?: 1.0) < 1.0 }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kv("Tasks", if (g.possible > 0) "${g.done}/${g.possible}" else "–", c.ink, Modifier.weight(1f))
            Kv("Cleared", "$clearedWk", c.ink, Modifier.weight(1f))
            Kv("Missed", "$missWk", if (missWk > 0) c.bad else c.ink, Modifier.weight(1f))
        }

        // All N days
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Lbl("All ${p.len} days")
                Lbl("${p.cleared} cleared")
            }
            val perRow = if (p.len > 75) 20 else 15
            val msAt = p.milestones.map { it.at }.toSet()
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                (0 until p.len).chunked(perRow).forEach { rowDays ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        rowDays.forEach { i ->
                            val k = run.startDate.plusDays(i.toLong())
                            val cell = Modifier.weight(1f).aspectRatio(1f)
                            when {
                                k == p.today -> Box(cell.border(2.dp, c.accent, CircleShape))
                                k.isBefore(p.today) && isDayCleared(run, days, k) -> Box(cell.clip(CircleShape).background(c.accent))
                                k.isBefore(p.today) -> Box(cell.border(1.5.dp, c.bad, CircleShape))
                                (i + 1) in msAt -> Box(cell.dashed(c.ink3, 1.5.dp, 999.dp))
                                else -> Box(cell.clip(CircleShape).background(c.dot))
                            }
                        }
                        repeat(perRow - rowDays.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Legend(listOf(Dot(c.accent) to "Cleared", Dot(Color.Transparent, c.bad) to "Missed", Dot(Color.Transparent, c.ink3, dashed = true) to "Milestone"))
            Milestones(p)
        }
    }
}

@Composable
private fun ArrowButton(glyph: Glyph, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalArise.current
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (enabled) c.ink else c.ink3, 16.dp) }
}

@Composable
private fun Kv(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Lbl(label)
        Num(value, 28f, color, Modifier.padding(top = 6.dp))
    }
}

/** A grid row: the name column, seven day cells, the score column. */
@Composable
private fun GridRowLayout(name: @Composable () -> Unit, cells: List<@Composable () -> Unit>, score: @Composable () -> Unit, height: androidx.compose.ui.unit.Dp = 26.dp) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(92.dp).padding(end = 6.dp)) { name() }
        cells.forEach { cell -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { cell() } }
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterEnd) { score() }
    }
}

/** Small mono text in a grid cell. */
@Composable
private fun Mn(text: String, today: Boolean) {
    val c = LocalArise.current
    Txt(text, Type.mono.copy(fontSize = 10.sp, lineHeight = 12.sp), if (today) c.ink else c.ink3, align = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
}

/** Done: orange dot. Missed: red ring. Today: dashed ring. Off: a faint small dot. */
@Composable
private fun CellDot(cell: Cell) {
    val c = LocalArise.current
    when (cell) {
        Cell.DONE -> Box(Modifier.size(16.dp).clip(CircleShape).background(c.accent))
        Cell.MISS -> Box(Modifier.size(16.dp).border(1.5.dp, c.bad, CircleShape))
        Cell.TODAY -> Box(Modifier.size(16.dp).dashed(c.ink, 1.5.dp, 999.dp))
        Cell.OFF -> Box(Modifier.size(5.dp).clip(CircleShape).background(c.dot))
    }
}

data class Dot(val fill: Color, val stroke: Color? = null, val dashed: Boolean = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<Dot, String>>) {
    val c = LocalArise.current
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (d, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val m = Modifier.size(9.dp)
                when {
                    d.dashed && d.stroke != null -> Box(m.dashed(d.stroke, 1.5.dp, 999.dp))
                    d.stroke != null -> Box(m.border(1.5.dp, d.stroke, CircleShape))
                    else -> Box(m.clip(CircleShape).background(d.fill))
                }
                Txt(label, Type.sub.copy(fontSize = 12.sp), c.ink3)
            }
        }
    }
}

/** `.miles`: a pill per milestone, filled once reached. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Milestones(p: Progress, prefix: String = "") {
    val c = LocalArise.current
    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        p.milestones.forEach { m ->
            val hit = p.cleared >= m.at
            Box(
                Modifier.clip(Radius.pill)
                    .background(if (hit) c.accentSoft else Color.Transparent)
                    .border(1.dp, if (hit) Color.Transparent else c.line, Radius.pill)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) { Txt("${m.name} · $prefix${m.at}", Type.sub, if (hit) c.ink else c.ink3) }
        }
    }
}
