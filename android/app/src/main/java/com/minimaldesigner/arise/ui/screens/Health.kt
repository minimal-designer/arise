package com.minimaldesigner.arise.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minimaldesigner.arise.core.HealthSleep
import com.minimaldesigner.arise.core.fmtSleep
import com.minimaldesigner.arise.core.sleepAverage
import com.minimaldesigner.arise.core.sleepEnding
import com.minimaldesigner.arise.core.sleepWeek
import com.minimaldesigner.arise.core.workoutsOn
import com.minimaldesigner.arise.data.HealthAccess
import com.minimaldesigner.arise.data.HealthKind
import com.minimaldesigner.arise.data.HealthState
import com.minimaldesigner.arise.ui.components.Btn
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.Num
import com.minimaldesigner.arise.ui.components.Panel
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.fmtClock
import com.minimaldesigner.arise.ui.fmtInt
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.Type
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Home's Health panel: last night's sleep and the week's sleep schedule, then today's
 * steps, workouts and latest weight. Asks to connect while nothing is shared.
 */
@Composable
fun HealthPanel(health: HealthState, today: LocalDate, onConnect: () -> Unit) {
    val c = LocalArise.current
    when (health.access) {
        HealthAccess.UNKNOWN, HealthAccess.UNAVAILABLE -> return
        HealthAccess.OFF, HealthAccess.NEEDS_UPDATE -> Panel {
            Txt("Health", Type.h2, c.panelInk)
            Note("Bring in your weight, height, workouts, sleep and steps from Health Connect. ARISE only reads them.")
            Btn(if (health.access == HealthAccess.NEEDS_UPDATE) "Get Health Connect" else "Connect Health Connect", onConnect, small = true)
        }
        HealthAccess.ON -> {
            val d = health.data ?: return
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Txt(if (HealthKind.SLEEP in health.granted) "Sleep" else "Health", Type.h2, c.panelInk)
                    Lbl(if (health.reading) "Reading…" else "Health Connect", size = 10f)
                }
                if (HealthKind.SLEEP in health.granted) {
                    val last = sleepEnding(d.sleeps, today)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (last != null) {
                            Row {
                                Num(fmtSleep(last.asleepMins).replace(" ", ""), 34f, c.panelInk, Modifier.alignByBaseline())
                                Txt(" last night", Type.small, c.ink3, Modifier.alignByBaseline())
                            }
                            Lbl("${fmtClock(last.start.toLocalTime())} → ${fmtClock(last.end.toLocalTime())}", size = 10f)
                        } else {
                            Note("No sleep recorded for last night.")
                        }
                    }
                    val week = sleepWeek(d.sleeps, today)
                    SleepChart(week, today)
                    sleepAverage(week.mapNotNull { it.second })?.let { a ->
                        Note("${a.nights}-night average: bed ${fmtClock(a.bed)}, up ${fmtClock(a.wake)}, ${fmtSleep(a.asleepMins)} asleep.")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (HealthKind.STEPS in health.granted) {
                        MiniStat("Steps today", d.steps[today]?.let(::fmtInt) ?: "–", Modifier.weight(1f))
                    }
                    if (HealthKind.WORKOUTS in health.granted) {
                        val ws = workoutsOn(d.workouts, today)
                        MiniStat("Workouts today", if (ws.isEmpty()) "–" else "${ws.sumOf { it.mins }}", Modifier.weight(1f), if (ws.isEmpty()) "" else " min")
                    }
                    if (HealthKind.WEIGHT in health.granted) {
                        MiniStat("Weight", d.weights.lastOrNull()?.let { String.format(Locale.US, "%.1f", it.kg) } ?: "–", Modifier.weight(1f), if (d.weights.isEmpty()) "" else " kg")
                    }
                }
                health.error?.let { ErrorNote(it) }
            }
        }
    }
}

@Composable
private fun ErrorNote(text: String) = Txt(text, Type.small, LocalArise.current.warn)

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier, unit: String = "") {
    val c = LocalArise.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Lbl(label, size = 10f)
        Row {
            Num(value, 22f, c.panelInk, Modifier.alignByBaseline())
            if (unit.isNotEmpty()) Txt(unit, Type.small, c.ink3, Modifier.alignByBaseline())
        }
    }
}

/** The chart runs from 8 pm the evening before (top) to noon (bottom). */
private const val CHART_FROM_HOUR = 20L
private const val CHART_MINS = 16 * 60f

/** Where [t] sits on the chart for the night that ended on [morning], 0 (top) to 1. */
private fun chartY(t: LocalDateTime, morning: LocalDate): Float {
    val base = morning.minusDays(1).atStartOfDay().plusHours(CHART_FROM_HOUR)
    return (Duration.between(base, t).toMinutes() / CHART_MINS).coerceIn(0f, 1f)
}

/**
 * The sleep schedule: one bar per night from bedtime to wake time, last night in the
 * accent. The dotted lines are midnight and 8 am.
 */
@Composable
private fun SleepChart(week: List<Pair<LocalDate, HealthSleep?>>, today: LocalDate) {
    val c = LocalArise.current
    val tracked = week.count { it.second != null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(120.dp)) {
            Box(Modifier.width(26.dp).fillMaxHeight()) {
                // Hour marks, placed where the dotted lines are.
                listOf(0 to "00", 8 to "08").forEach { (h, label) ->
                    val frac = ((h - CHART_FROM_HOUR + 24) % 24) * 60 / CHART_MINS
                    Box(Modifier.fillMaxHeight(frac).align(Alignment.TopStart)) {
                        Lbl(label, size = 9f, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "Sleep schedule, $tracked of 7 nights tracked" }) {
                val guide = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                listOf(0L, 8L).forEach { h ->
                    val y = ((h - CHART_FROM_HOUR + 24) % 24) * 60 / CHART_MINS * size.height
                    drawLine(c.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = guide)
                }
                val slot = size.width / week.size
                val barW = (slot * 0.42f).coerceAtMost(14.dp.toPx())
                week.forEachIndexed { i, (morning, s) ->
                    val x = slot * i + (slot - barW) / 2
                    if (s == null) {
                        drawRoundRect(c.dot, Offset(x, size.height - 3.dp.toPx()), Size(barW, 3.dp.toPx()), CornerRadius(barW / 2))
                    } else {
                        val top = chartY(s.start, morning) * size.height
                        val bottom = chartY(s.end, morning) * size.height
                        drawRoundRect(
                            if (morning == today) c.accent else c.ink3,
                            Offset(x, top), Size(barW, (bottom - top).coerceAtLeast(barW)), CornerRadius(barW / 2),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 26.dp)) {
            week.forEach { (morning, _) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Lbl(morning.dayOfWeek.name.take(1), if (morning == today) c.accent else c.ink3, size = 9f)
                }
            }
        }
    }
}
