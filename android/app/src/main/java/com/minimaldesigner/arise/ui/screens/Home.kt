package com.minimaldesigner.arise.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.minimaldesigner.arise.core.DayRecord
import com.minimaldesigner.arise.core.Days
import com.minimaldesigner.arise.core.HealthData
import com.minimaldesigner.arise.core.SLEEP_GOAL_MINS
import com.minimaldesigner.arise.core.TICK_FREE
import com.minimaldesigner.arise.core.TICK_PHOTO
import com.minimaldesigner.arise.core.fmtSleep
import com.minimaldesigner.arise.core.mindfulMinutes
import com.minimaldesigner.arise.core.sleepEnding
import com.minimaldesigner.arise.core.suggestWorkout
import com.minimaldesigner.arise.core.Programs
import com.minimaldesigner.arise.core.Progress
import com.minimaldesigner.arise.core.PauseState
import com.minimaldesigner.arise.core.daysBetween
import com.minimaldesigner.arise.core.Reading
import com.minimaldesigner.arise.core.Run
import com.minimaldesigner.arise.core.Status
import com.minimaldesigner.arise.core.TaskDef
import com.minimaldesigner.arise.core.TaskKind
import com.minimaldesigner.arise.core.homeDay
import com.minimaldesigner.arise.core.recentWorkouts
import com.minimaldesigner.arise.core.statusOf
import com.minimaldesigner.arise.core.weekStats
import com.minimaldesigner.arise.core.FoodLog
import com.minimaldesigner.arise.data.HealthState
import com.minimaldesigner.arise.ui.components.Burst
import com.minimaldesigner.arise.ui.components.Card
import com.minimaldesigner.arise.ui.components.LocalConfetti
import com.minimaldesigner.arise.ui.components.tickHaptic
import com.minimaldesigner.arise.ui.components.untickHaptic
import com.minimaldesigner.arise.ui.components.FilterChip
import com.minimaldesigner.arise.ui.components.Glyph
import com.minimaldesigner.arise.ui.components.GlyphIcon
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.LinkButton
import com.minimaldesigner.arise.ui.components.Num
import com.minimaldesigner.arise.ui.components.Panel
import com.minimaldesigner.arise.ui.components.StatusDot
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.components.glyphFor
import com.minimaldesigner.arise.ui.fmtInt
import com.minimaldesigner.arise.ui.fmtLong
import com.minimaldesigner.arise.ui.fmtShort
import com.minimaldesigner.arise.ui.theme.AriseColors
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.Radius
import com.minimaldesigner.arise.ui.theme.Type
import java.io.File
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

enum class TaskFilter(val label: String) { All("All"), Todo("To do"), Done("Done") }

private fun pad2(n: Int) = n.toString().padStart(2, '0')

/** Short task names for "2 left · Water, Read". */
private fun shortName(t: TaskDef) = when (t.kind) {
    TaskKind.WORKOUT -> if (t.outdoor) "Outdoor" else "Workout"
    TaskKind.DIET -> "Diet"
    TaskKind.WATER -> "Water"
    TaskKind.READ -> "Read"
    TaskKind.PHOTO -> "Photo"
    else -> t.label.substringBefore(" · ")
}

@Composable
fun Home(
    run: Run,
    days: Days,
    p: Progress,
    hour: Int,
    profileThumb: File?,
    food: FoodLog?,
    reading: Reading,
    health: HealthState,
    onTask: (TaskDef) -> Unit,
    onOpenWeek: () -> Unit,
    onOpenFood: () -> Unit,
    onOpenProfile: () -> Unit,
    onConnectHealth: () -> Unit,
    /** A break in progress: the paused card stands in for the Today card and the tasks. */
    pause: PauseState.Active? = null,
    onEndPause: () -> Unit = {},
    /** Health Connect shares food: the calories card shows (else a best-streak card takes its place). */
    foodOn: Boolean = true,
) {
    val c = LocalArise.current
    var filter by rememberSaveable { mutableStateOf(TaskFilter.All) }
    val programName = Programs.of(run.program).name

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // .hello
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt("Hi, ${run.name}", Type.h1.copy(fontWeight = FontWeight.Light))
                Lbl(
                    if (pause != null) "$programName · paused · back ${fmtShort(pause.pause.untilDate)}"
                    else if (p.started) "$programName · Day ${pad2(p.dayNum)} / ${p.len}" + (if (run.attempt > 1) " · attempt ${run.attempt}" else "")
                    else "$programName starts ${fmtShort(run.startDate)}",
                )
            }
            Avatar(run.name, profileThumb, 52, onOpenProfile)
        }

        if (pause != null) {
            PausedCard(run, pause, onEndPause)
        } else {
            // Where each task's check circle and the Today card are on screen, for the confetti.
            val checks = remember { mutableMapOf<String, Offset>() }
            val todayCard = remember { arrayOfNulls<Rect>(1) }
            HomeGrid(run, days, p, hour, food, foodOn, onOpenWeek, onOpenFood, onOpenProfile) { todayCard[0] = it }

            // Today's tasks
            val today = days[p.today]
            Celebrate(run, today, p, checks, todayCard)
            val status = statusOf(run, p, hour)
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Txt("Today's tasks", Type.h2, c.panelInk)
                    val (label, color) = statusLabel(status, p, c)
                    StatusDot(label.uppercase(), color)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskFilter.entries.forEach { f -> FilterChip(f.label, filter == f, { filter = f }) }
                }
                val list = run.tasks.filter { t ->
                    val done = today?.isDone(t.id) == true
                    when (filter) { TaskFilter.All -> true; TaskFilter.Done -> done; TaskFilter.Todo -> !done }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (list.isEmpty()) {
                        Note(if (filter == TaskFilter.Done) "Nothing checked off yet." else "Everything's done for today.")
                    }
                    list.forEach { t ->
                        val done = today?.isDone(t.id) == true
                        val wk = today?.workouts?.firstOrNull { it.taskId == t.id }
                        val read = today?.read
                        val at = today?.done?.get(t.id)
                        val sub = (if (!done && p.started) healthHint(t, health.data, today, p.today) else null) ?: when {
                            at == TICK_PHOTO -> "From today's photo"
                            at == TICK_FREE -> "Free on Day 1 · counts from tonight"
                            done && wk != null -> "${wk.type} · ${wk.mins} min" + (if (wk.outdoor) " · outdoors" else "")
                            t.kind == TaskKind.READ && done && read != null -> "${read.title} · p. ${read.page}" + (if (read.total > 0) " of ${read.total}" else "")
                            t.kind == TaskKind.READ && reading.title.isNotEmpty() -> "${reading.title} · p. ${reading.page}" + (if (reading.total > 0) " of ${reading.total}" else "")
                            else -> t.sub
                        }
                        TaskRow(t.label, sub, glyphFor(t.kind), done, enabled = p.started, onCheckAt = { checks[t.id] = it }) { onTask(t) }
                    }
                }
                Note(if (run.resetOnMiss) "$programName: a missed task restarts the challenge at Day 1." else "Miss a day and only your streak resets.")
            }

        }

        HealthPanel(health, p.today, onConnectHealth)

        // Recent workouts
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Txt("Recent workouts", Type.h2, c.panelInk)
                LinkButton("See week", onOpenWeek, c.panelInk)
            }
            val recent = recentWorkouts(days)
            if (recent.isEmpty()) {
                Note("Your workouts show up here once you check one off.")
            } else {
                Column {
                    recent.forEachIndexed { i, r ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                        Column(Modifier.padding(top = if (i == 0) 0.dp else 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val which = if (r.workout.taskId == "w2") "Workout 2" else "Workout 1"
                            Lbl("${if (r.workout.outdoor) "Outdoor" else "Indoor"} · $which")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Txt("${r.workout.mins} min ${r.workout.type.lowercase()}", Type.body.copy(fontSize = 19.sp), c.panelInk)
                                GlyphIcon(Glyph.Workout, c.ink3, 22.dp)
                            }
                            Txt(fmtLong(r.date), Type.small, c.ink3)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home during a break: how long is left, a nudge to stay sensible, and what happens after.
 * The challenge restarts at Day 1 when the break is over.
 */
@Composable
private fun PausedCard(run: Run, pause: PauseState.Active, onEndPause: () -> Unit) {
    val c = LocalArise.current
    val pz = pause.pause
    val total = daysBetween(pz.fromDate, pz.untilDate).coerceAtLeast(1)
    Card(Modifier.fillMaxWidth(), dotted = true, padding = PaddingValues(horizontal = 18.dp, vertical = 22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Lbl("Paused · ${pz.reason}", c.accent)
                GlyphIcon(Glyph.Pause, c.accent, 22.dp)
            }
            Row {
                Num(pad2(pause.daysLeft), 64f, modifier = Modifier.alignByBaseline())
                Txt(if (pause.daysLeft == 1) " day left" else " days left", Type.small, c.ink3, Modifier.alignByBaseline())
            }
            Txt("This challenge is paused", Type.h2)
            Txt(
                "Enjoy this ${pz.phrase}, but be mindful about what you eat, and include some physical activities.",
                Type.body.copy(fontSize = 15.sp), c.ink2,
            )
            Segments((total - pause.daysLeft).toFloat() / total)
            Lbl("Back on ${fmtShort(pz.untilDate)} · then Day 1, attempt ${run.attempt + 1}", size = 10f)
            LinkButton("Back early? End the pause", onEndPause, c.accent)
        }
    }
}

/**
 * The feedback for ticking: a haptic click and a small burst from the task's check, a
 * lighter click for an untick, and when the day clears, five big bursts over about two
 * seconds, each with a click. It watches today's ticks rather than taps, so a tick from a
 * sheet (workout, read, meditate) or from a photo celebrates too. Opening Home, switching
 * tabs or a new day starts from what's already ticked, so nothing replays.
 */
@Composable
private fun Celebrate(run: Run, today: DayRecord?, p: Progress, checks: Map<String, Offset>, todayCard: Array<Rect?>) {
    val view = LocalView.current
    val confetti = LocalConfetti.current
    val doneIds = run.tasks.filter { today?.isDone(it.id) == true }.map { it.id }.toSet()
    val clear = p.total > 0 && p.doneToday == p.total
    var seen by remember(p.today) { mutableStateOf(doneIds) }
    var wasClear by remember(p.today) { mutableStateOf(clear) }
    LaunchedEffect(doneIds) {
        val added = doneIds - seen
        when {
            added.isNotEmpty() -> {
                view.tickHaptic()
                added.forEach { id -> checks[id]?.let { confetti?.burst(it, Burst.SMALL) } }
            }
            (seen - doneIds).isNotEmpty() -> view.untickHaptic()
        }
        seen = doneIds
    }
    LaunchedEffect(clear) {
        if (clear && !wasClear) {
            delay(250)
            repeat(5) {
                val r = todayCard[0]
                val at = if (r != null) Offset(r.left + r.width * (0.15f + Random.nextFloat() * 0.7f), r.top + r.height * (0.2f + Random.nextFloat() * 0.5f))
                else Offset(view.width * (0.2f + Random.nextFloat() * 0.6f), view.height * 0.3f)
                view.tickHaptic()
                confetti?.burst(at, Burst.BIG)
                delay(380)
            }
        }
        wasClear = clear
    }
}

/** What Health Connect says about an open task: a session to log, last night's sleep, today's steps. */
private fun healthHint(t: TaskDef, d: HealthData?, day: DayRecord?, today: LocalDate): String? {
    if (d == null) return null
    return when (t.kind) {
        TaskKind.WORKOUT -> suggestWorkout(t, d.workouts, day, today)?.let { "Health Connect: ${it.mins} min ${it.type.lowercase()} · tap to log" }
        TaskKind.SLEEP -> sleepEnding(d.sleeps, today)?.let {
            "Health Connect: ${fmtSleep(it.asleepMins)} last night · " + if (it.asleepMins >= SLEEP_GOAL_MINS) "tap to tick" else "under 7 h"
        }
        TaskKind.MIND -> mindfulMinutes(d.mindful, today).takeIf { it > 0 }?.let { "Health Connect: $it min today · tap to log" }
        TaskKind.WALK -> d.steps[today]?.let { "Health Connect: ${fmtInt(it)} steps today" }
        // Only water tasks: the Water kind also covers the cold shower.
        TaskKind.WATER -> d.waterL[today]?.takeIf { t.label.contains(" L") }?.let { "Health Connect: ${fmtLitres(it)} L today" }
        else -> null
    }
}

private fun fmtLitres(l: Double) = String.format(java.util.Locale.US, "%.1f", l)

/** The round avatar with a small settings badge: opens Profile & settings. */
@Composable
fun Avatar(name: String, thumb: File?, sizeDp: Int, onClick: () -> Unit) {
    val c = LocalArise.current
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).border(1.dp, c.line, CircleShape).background(c.card)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Profile and settings" }
            .padding(3.dp),
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(c.card2), contentAlignment = Alignment.Center) {
            if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Txt(name.take(1).uppercase().ifEmpty { "A" }, Type.body.copy(fontSize = (sizeDp * 0.37f).sp), c.accent, weight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HomeGrid(
    run: Run, days: Days, p: Progress, hour: Int, food: FoodLog?, foodOn: Boolean,
    onOpenWeek: () -> Unit, onOpenFood: () -> Unit, onOpenProfile: () -> Unit,
    onTodayBounds: (Rect) -> Unit,
) {
    val c = LocalArise.current
    val w = weekStats(run, days, p.today, 0)
    val lw = weekStats(run, days, p.today, -1)
    val wPct = w.pct?.let { (it * 100).roundToInt() }
    val delta = if (wPct != null && lw.pct != null) wPct - (lw.pct!! * 100).roundToInt() else null
    val clear = p.total > 0 && p.doneToday == p.total

    // grid-template-areas: "today day" "today week" "cal week"
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TodayCard(
                run, days, p, hour, clear,
                Modifier.fillMaxWidth().weight(1f).heightIn(min = 262.dp).onGloballyPositioned { onTodayBounds(it.boundsInRoot()) },
                onOpenWeek,
            )
            if (!foodOn) {
                Card(Modifier.fillMaxWidth(), onClick = onOpenProfile) {
                    Lbl("Best streak")
                    Row(Modifier.padding(top = 10.dp)) {
                        Num(pad2(p.best), 34f, modifier = Modifier.alignByBaseline())
                        Txt(if (p.best == 1) " day" else " days", Type.small, c.ink3, Modifier.alignByBaseline())
                    }
                    Lbl(if (run.attempt > 1) "This attempt · #${run.attempt}" else "This attempt", modifier = Modifier.padding(top = 10.dp), size = 10.5f)
                }
            } else Card(Modifier.fillMaxWidth(), onClick = onOpenFood) {
                Lbl("Calories")
                val fd = food?.let { homeDay(it, p.today) }
                Row(Modifier.padding(top = 10.dp)) {
                    Num(fd?.kcal?.let { fmtInt(it.roundToInt()) } ?: "–", 34f, modifier = Modifier.alignByBaseline())
                    if (fd?.kcal != null) Txt(" kcal", Type.small, c.ink3, Modifier.alignByBaseline())
                }
                Lbl(
                    when {
                        fd != null -> (if (fd.day == p.today) "Today" else fmtShort(fd.day)) + (fd.protein?.let { " · ${it.roundToInt()} g protein" } ?: "")
                        food == null -> "Reading Health Connect…"
                        else -> "No food in Health Connect yet"
                    },
                    modifier = Modifier.padding(top = 10.dp), size = 10.5f,
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth(), onClick = onOpenProfile) {
                Lbl("Day")
                Row(Modifier.padding(top = 10.dp)) {
                    Num(if (p.started) pad2(p.dayNum) else "00", 44f, modifier = Modifier.alignByBaseline())
                    Txt(" / ${p.len}", Type.body.copy(fontSize = 16.sp), c.ink3, Modifier.alignByBaseline())
                }
                Txt(
                    when {
                        p.streak > 0 -> "${p.streak}-day streak"
                        p.started -> "Streak starts today"
                        else -> "Not started"
                    },
                    Type.small, c.ink2, Modifier.padding(top = 10.dp),
                )
            }
            Card(Modifier.fillMaxWidth().weight(1f), onClick = onOpenWeek) {
                Lbl("This week")
                Num(wPct?.let { "$it%" } ?: "–", 44f, modifier = Modifier.padding(top = 10.dp))
                if (delta == null) Txt("of tasks done", Type.small, c.ink2, Modifier.padding(top = 10.dp))
                else Txt("${if (delta >= 0) "+" else ""}$delta% from last week", Type.small, c.accent, Modifier.padding(top = 10.dp), weight = FontWeight.Medium)
                Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
                // One column of dots per day; the lit dots are the share of tasks done.
                val cap = p.total.coerceIn(1, 6)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    w.cols.forEach { col ->
                        val lit = col.pct?.let { (it * cap).roundToInt() } ?: 0
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(cap) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (cap - i <= lit) c.accent else c.dot)) }
                        }
                    }
                }
            }
        }
    }
}

/** Today: one ring arc per task around a dotted orb; the card turns orange when all are done. */
@Composable
private fun TodayCard(run: Run, days: Days, p: Progress, hour: Int, clear: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalArise.current
    val bg by animateColorAsState(if (clear) c.accent else c.card, label = "today")
    val ink = if (clear) c.accentInk else c.ink
    val done = days[p.today]
    Column(
        modifier.clip(Radius.card).background(bg).border(1.dp, if (clear) c.accent else c.line, Radius.card)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Lbl("Today", if (clear) c.accentInk else c.ink3)
            Num("${p.doneToday}/${p.total}", 18f, ink)
        }
        val arcOn = if (clear) c.accentInk else c.accent
        val track = if (clear) c.accentInk.copy(alpha = 0.35f) else c.dot
        val orbDot = if (clear) c.accentInk else c.accent
        val orbAlpha = 0.35f + 0.65f * p.progress.toFloat()
        Canvas(Modifier.size(150.dp).semantics { contentDescription = "${p.doneToday} of ${p.total} tasks done" }) {
            val s = size.width / 160f
            val r = 66f * s
            val center = Offset(size.width / 2, size.height / 2)
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(2 * r, 2 * r)
            // Dotted track.
            val circ = (2 * PI * r).toFloat()
            drawCircle(track, r, center, style = Stroke(5f * s, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 8.3f * s))))
            // One arc per finished task, in task order, with a small gap between arcs.
            val n = run.tasks.size.coerceAtLeast(1)
            val gapDeg = if (n > 1) 11f * s / circ * 360f else 0f
            val sweep = 360f / n - gapDeg
            run.tasks.forEachIndexed { i, t ->
                if (done?.isDone(t.id) == true) {
                    drawArc(arcOn, -90f + i * 360f / n + gapDeg / 2, sweep, false, topLeft, arcSize, style = Stroke(7f * s, cap = StrokeCap.Round))
                }
            }
            // The dotted orb, brighter as more days are cleared.
            val orbR = r * 0.64f
            val step = 7f * s
            var y = center.y - orbR
            while (y <= center.y + orbR) {
                var x = center.x - orbR
                while (x <= center.x + orbR) {
                    val dx = x - center.x
                    val dy = y - center.y
                    if (dx * dx + dy * dy <= orbR * orbR) drawCircle(orbDot, 1.8f * s, Offset(x, y), alpha = orbAlpha)
                    x += step
                }
                y += step
            }
        }
        val open = run.tasks.filter { done?.isDone(it.id) != true }.map(::shortName)
        Txt(
            when {
                !p.started -> "Starts ${fmtShort(run.startDate)}"
                clear -> "All clear for today"
                hour >= 20 -> "Evening: ${open.size} left"
                else -> "${open.size} left · " + open.take(2).joinToString(", ") + if (open.size > 2) "…" else ""
            },
            Type.small, if (clear) c.accentInk else c.ink2, align = TextAlign.Center, weight = if (clear) FontWeight.Medium else null,
        )
    }
}

@Composable
fun TaskRow(label: String, sub: String, glyph: Glyph, done: Boolean, enabled: Boolean, onCheckAt: (Offset) -> Unit = {}, onClick: () -> Unit) {
    val c = LocalArise.current
    val ck by animateColorAsState(if (done) c.accent else Color.Transparent, label = "ck")
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clip(Radius.row)
            .background(c.card2)
            .border(1.dp, c.line, Radius.row)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(26.dp).onGloballyPositioned { onCheckAt(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)) },
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(ck), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Check, c.accentInk, 14.dp) }
            } else {
                Canvas(Modifier.size(26.dp)) {
                    drawCircle(c.ink3, size.width / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(
                label, Type.body.copy(fontSize = 15.5.sp, textDecoration = if (done) TextDecoration.LineThrough else null),
                if (done) c.ink3 else c.ink, weight = if (done) null else FontWeight.Medium,
            )
            if (sub.isNotEmpty()) Lbl(sub, size = 10f)
        }
        GlyphIcon(glyph, c.ink3, 20.dp)
    }
}

/** `.note` on a panel. */
@Composable
fun Note(text: String) {
    Txt(text, Type.small, LocalArise.current.ink3)
}

fun statusLabel(s: Status, p: Progress, c: AriseColors) = when (s) {
    is Status.NotStarted -> "Starts ${fmtShort(s.start)}" to c.ink2
    Status.Complete -> "Complete" to c.good
    Status.MissedDay -> "Missed a day" to c.bad
    Status.AtRisk -> "At risk tonight" to c.warn
    Status.OnTrack -> (if (p.total > 0 && p.doneToday == p.total) "Day cleared" else "On track") to c.good
}
