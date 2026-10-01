package com.minimaldesigner.arise.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaldesigner.arise.core.FoodDay
import com.minimaldesigner.arise.core.FoodLog
import com.minimaldesigner.arise.core.Meal
import com.minimaldesigner.arise.core.averageKcal
import com.minimaldesigner.arise.data.HealthAccess
import com.minimaldesigner.arise.data.HealthKind
import com.minimaldesigner.arise.data.HealthState
import com.minimaldesigner.arise.ui.components.Btn
import com.minimaldesigner.arise.ui.components.BtnKind
import com.minimaldesigner.arise.ui.components.Card
import com.minimaldesigner.arise.ui.components.CardHeader
import com.minimaldesigner.arise.ui.components.EmptyBox
import com.minimaldesigner.arise.ui.components.Glyph
import com.minimaldesigner.arise.ui.components.GlyphIcon
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.LinkButton
import com.minimaldesigner.arise.ui.components.Num
import com.minimaldesigner.arise.ui.components.PageHeader
import com.minimaldesigner.arise.ui.components.Panel
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.components.fade
import com.minimaldesigner.arise.ui.fmtInt
import com.minimaldesigner.arise.ui.fmtLong
import com.minimaldesigner.arise.ui.fmtShort
import com.minimaldesigner.arise.ui.fmtWeekday
import com.minimaldesigner.arise.ui.theme.GeistMono
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.Radius
import com.minimaldesigner.arise.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** The bar chart shows at most this many days, so bars stay tappable on a long run. */
private const val CHART_DAYS = 30

private fun n(v: Double) = fmtInt(v.roundToInt())

/** `#v-food`: day strip, calories, macros, meals and the calorie chart, from Health Connect (1.2.0). */
@Composable
fun Food(log: FoodLog?, health: HealthState, onRefresh: () -> Unit, onConnect: () -> Unit) {
    val c = LocalArise.current
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val d = log?.let { l -> l.days.firstOrNull { it.date == sel } ?: l.days.lastOrNull() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Food") {
            if (d != null) Txt(fmtLong(d.day), Type.small, c.ink3, Modifier.padding(bottom = 6.dp))
        }
        if (log == null || d == null) {
            NoFood(health, onRefresh, onConnect)
            return@Column
        }
        DateStrip(log.days, d.date) { sel = it }
        KcalCard(log, d)
        Macros(log, d)
        MealsPanel(d)
        KcalChart(log, d) { sel = it }
        SourceLine(health, onRefresh)
    }
}

/** No meals to show: say where food comes from and what to do. */
@Composable
private fun NoFood(health: HealthState, onRefresh: () -> Unit, onConnect: () -> Unit) {
    when {
        health.access != HealthAccess.ON || HealthKind.NUTRITION !in health.granted -> {
            EmptyBox(
                "Food comes from Health Connect. Log meals in an app that writes to it, like MyFitnessPal, " +
                    "Cronometer or Samsung Health, then let ARISE read Food.",
            )
            Btn("Connect Health Connect", onConnect, Modifier.fillMaxWidth(), BtnKind.Accent)
        }
        health.reading -> EmptyBox("Reading Health Connect…")
        else -> {
            EmptyBox(health.error ?: "No meals in Health Connect in the last 30 days. Log one in your food app, then refresh.")
            Btn("Refresh", onRefresh, Modifier.fillMaxWidth(), BtnKind.Ghost)
        }
    }
}

/** `.dates`: one pill per logged day, the selected one in chip colours. */
@Composable
private fun DateStrip(days: List<FoodDay>, selected: String, onPick: (String) -> Unit) {
    val c = LocalArise.current
    val list = rememberLazyListState()
    val idx = days.indexOfFirst { it.date == selected }
    // Keep the selected day in view, roughly centred (v2 scrollIntoView inline:center).
    LaunchedEffect(idx, days.size) { if (idx >= 0) list.animateScrollToItem(max(0, idx - 2)) }
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(days, key = { _, it -> it.date }) { _, day ->
            val on = day.date == selected
            Column(
                Modifier
                    .width(50.dp)
                    .clip(Radius.date)
                    .background(if (on) c.chip else c.card)
                    .clickable(role = Role.Tab) { onPick(day.date) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Txt(fmtWeekday(day.day), Type.sub.copy(fontSize = 11.5.sp), if (on) c.chipInk else c.ink2)
                Txt("${day.day.dayOfMonth}", Type.body.copy(fontSize = 17.sp), if (on) c.chipInk else c.ink, weight = FontWeight.Medium)
            }
        }
    }
}

/** `.kcal-card`: the accent card with the day's calories against the goal. */
@Composable
private fun KcalCard(log: FoodLog, d: FoodDay) {
    val c = LocalArise.current
    val goal = log.goals.kcal
    val kcal = d.kcal ?: 0.0
    Column(
        Modifier.fillMaxWidth().clip(Radius.panel).background(c.accent).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlyphIcon(Glyph.Flame, c.accentInk, 18.dp)
                Lbl("Calories", c.accentInk)
            }
            if (goal != null) Lbl("Goal ${n(goal)}", c.accentInk)
        }
        Row {
            Num(d.kcal?.let(::n) ?: "–", 58f, c.accentInk, Modifier.alignByBaseline())
            Txt("kcal", Type.body.copy(fontSize = 18.sp), c.accentInk, Modifier.alignByBaseline().padding(start = 4.dp))
        }
        if (goal != null && goal > 0) {
            Meter((kcal / goal).toFloat(), 8, c.accentInk.fade(0.22f), c.accentInk)
        }
        val meals = "${d.meals.size} ${if (d.meals.size == 1) "meal" else "meals"} logged"
        val vsGoal = when {
            goal == null || d.kcal == null -> null
            goal - kcal >= 0 -> "${n(goal - kcal)} kcal under goal"
            else -> "${n(kcal - goal)} kcal over goal"
        }
        Txt(listOfNotNull(vsGoal, meals).joinToString(" · "), Type.body.copy(fontSize = 14.sp), c.accentInk.fade(0.92f))
    }
}

@Composable
private fun Meter(fraction: Float, height: Int, track: Color, fill: Color) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(Radius.pill).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(Radius.pill).background(fill))
    }
}

/** `.macros`: protein, carbs and fat against their goals. */
@Composable
private fun Macros(log: FoodLog, d: FoodDay) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Macro("Protein", d.protein, log.goals.protein, Modifier.weight(1f).fillMaxHeight())
        Macro("Carbs", d.carbs, log.goals.carbs, Modifier.weight(1f).fillMaxHeight())
        Macro("Fat", d.fat, log.goals.fat, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Macro(label: String, value: Double?, goal: Double?, modifier: Modifier) {
    val c = LocalArise.current
    Card(modifier, padding = PaddingValues(14.dp)) {
        Lbl(label)
        if (value == null) {
            Num("–", 26f, c.ink3, Modifier.padding(top = 4.dp))
            Txt("Not tracked", Type.sub.copy(fontSize = 11.5.sp), c.ink3, Modifier.padding(top = 6.dp))
        } else {
            Row(Modifier.padding(top = 2.dp)) {
                Num(n(value), 26f, modifier = Modifier.alignByBaseline())
                Txt("g", Type.small, c.ink3, Modifier.alignByBaseline().padding(start = 2.dp))
            }
            if (goal != null && goal > 0) {
                Box(Modifier.padding(top = 8.dp)) { Meter((value / goal).toFloat(), 5, c.line, c.accent) }
                Txt("${(value / goal * 100).roundToInt()}% of ${n(goal)} g", Type.sub.copy(fontSize = 11.5.sp), c.ink3, Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** `#meals-panel`: each meal with its numbers, then the day's note. */
@Composable
private fun MealsPanel(d: FoodDay) {
    val c = LocalArise.current
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Txt("Meals", Type.h2, c.panelInk)
            d.protein?.let { Txt("${n(it)} g protein", Type.mono, c.panelInk) }
        }
        if (d.meals.isEmpty()) {
            Txt("No meals logged for this day.", Type.body.copy(fontSize = 14.sp), c.panelInk.fade(0.75f))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { d.meals.forEach { MealRow(it) } }
        }
    }
}

@Composable
private fun MealRow(m: Meal) {
    val c = LocalArise.current
    Column(
        Modifier.fillMaxWidth().clip(Radius.row).background(c.card2).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Lbl(listOfNotNull(m.slot, m.time).joinToString(" · "), size = 10.5f)
        m.what?.let { Txt(it, Type.body) }
        val rest = listOfNotNull(
            m.protein?.let { "${n(it)} g P" },
            m.carbs?.let { "${n(it)} g C" },
            m.fat?.let { "${n(it)} g F" },
        )
        Text(
            buildAnnotatedString {
                val kcal = m.kcal
                if (kcal != null) withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Medium)) { append("${n(kcal)} kcal") }
                if (rest.isNotEmpty()) append((if (kcal != null) " · " else "") + rest.joinToString(" · "))
            },
            style = Type.small, color = c.ink2,
        )
    }
}

/** `#food-chart`: calories per day against the goal; tap a bar to open that day. */
@Composable
private fun KcalChart(log: FoodLog, d: FoodDay, onPick: (String) -> Unit) {
    val c = LocalArise.current
    val days = log.days.takeLast(CHART_DAYS)
    val goal = log.goals.kcal
    val measurer = rememberTextMeasurer()
    val ax = TextStyle(fontFamily = GeistMono, fontSize = 11.sp, color = c.ink3)

    Card {
        CardHeader("Last ${days.size} ${if (days.size == 1) "day" else "days"}", titleStyle = Type.body.copy(fontSize = 17.sp)) {
            averageKcal(days)?.let { Txt("avg ${n(it)} kcal", Type.mono, c.ink3) }
        }
        // v2's 340x150 viewBox, scaled to the card width.
        val vw = 340f; val vh = 150f; val pl = 30f; val pr = 8f; val pt = 10f; val pb = 22f
        val bw = (vw - pl - pr) / days.size
        val max = (listOfNotNull(goal) + days.mapNotNull { it.kcal }).maxOrNull()?.times(1.1)?.coerceAtLeast(1.0) ?: 1.0
        fun y(v: Double) = (pt + (1 - v / max) * (vh - pt - pb)).toFloat()

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(vw / vh)
                .padding(top = 10.dp)
                .semantics { contentDescription = "Calories per day against the goal" }
                .pointerInput(days) {
                    detectTapGestures { o ->
                        val i = ((o.x / (size.width / vw) - pl) / bw).toInt()
                        days.getOrNull(i)?.let { onPick(it.date) }
                    }
                },
        ) {
            val s = size.width / vw
            fun label(text: String, x: Float, yy: Float, end: Boolean) {
                val t = measurer.measure(text, ax)
                drawText(t, topLeft = Offset(if (end) x * s - t.size.width else x * s, yy * s - t.size.height * 0.75f))
            }
            listOf(0.0, 1000.0, 2000.0).filter { it < max }.forEach { v ->
                drawLine(c.line, Offset(pl * s, y(v) * s), Offset((vw - pr) * s, y(v) * s), strokeWidth = 1.dp.toPx())
                label("${(v / 1000).roundToInt()}k", pl - 5, y(v) + 4, end = true)
            }
            days.forEachIndexed { i, x ->
                val k = x.kcal ?: return@forEachIndexed
                val top = y(k)
                drawRoundRect(
                    if (x.date == d.date) c.accent else c.panel,
                    topLeft = Offset((pl + i * bw + bw * 0.2f) * s, top * s),
                    size = Size(bw * 0.6f * s, (vh - pb - top) * s),
                    cornerRadius = CornerRadius(bw * 0.3f * s),
                )
            }
            if (goal != null) {
                drawLine(
                    c.ink3, Offset(pl * s, y(goal) * s), Offset((vw - pr) * s, y(goal) * s),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * s, 4 * s)),
                )
                label("goal ${n(goal)}", vw - pr, y(goal) - 5, end = true)
            }
            label(fmtShort(days.first().day), pl, vh - 5, end = false)
            label(fmtShort(days.last().day), vw - pr, vh - 5, end = true)
        }
    }
}

private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** `#food-src`: where the numbers come from, when Health Connect was last read, and Refresh. */
@Composable
private fun SourceLine(health: HealthState, onRefresh: () -> Unit) {
    val c = LocalArise.current
    val at = health.readAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt("From Health Connect" + (at?.let { " · read ${hm.format(it)}" } ?: ""), Type.sub, c.ink3, align = TextAlign.Center)
        if (health.error != null) Txt(health.error, Type.sub, c.warn, align = TextAlign.Center)
        if (health.reading) Txt("Reading…", Type.sub, c.ink3) else LinkButton("Refresh", onRefresh, c.ink2)
    }
}
