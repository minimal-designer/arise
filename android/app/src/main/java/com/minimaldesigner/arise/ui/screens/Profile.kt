package com.minimaldesigner.arise.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaldesigner.arise.core.FREE_PASSES
import com.minimaldesigner.arise.core.PauseState
import com.minimaldesigner.arise.core.Programs
import com.minimaldesigner.arise.core.Progress
import com.minimaldesigner.arise.core.REMINDER_TIMES
import com.minimaldesigner.arise.core.Run
import com.minimaldesigner.arise.data.Reminders
import com.minimaldesigner.arise.data.Settings
import com.minimaldesigner.arise.ui.components.Btn
import com.minimaldesigner.arise.ui.components.BtnKind
import com.minimaldesigner.arise.ui.components.Card
import com.minimaldesigner.arise.ui.components.ErrorLine
import com.minimaldesigner.arise.ui.components.Field
import com.minimaldesigner.arise.ui.components.Glyph
import com.minimaldesigner.arise.ui.components.GlyphIcon
import com.minimaldesigner.arise.ui.components.Labeled
import com.minimaldesigner.arise.ui.components.Segmented
import com.minimaldesigner.arise.ui.components.ToggleSwitch
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.fmtShort
import com.minimaldesigner.arise.ui.fmtTime
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.ThemePref
import com.minimaldesigner.arise.ui.theme.Type
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.minimaldesigner.arise.core.FoodGoals
import com.minimaldesigner.arise.core.WeightSource
import com.minimaldesigner.arise.core.bmi
import com.minimaldesigner.arise.core.bmiBand
import com.minimaldesigner.arise.core.bodyWeights
import com.minimaldesigner.arise.core.startWeight
import com.minimaldesigner.arise.data.HealthAccess
import com.minimaldesigner.arise.data.HealthKind
import com.minimaldesigner.arise.data.HealthState
import com.minimaldesigner.arise.ui.fmtClock
import com.minimaldesigner.arise.data.AppMeta
import com.minimaldesigner.arise.data.PastRun
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.Num
import com.minimaldesigner.arise.ui.theme.NumStyle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.minimaldesigner.arise.core.BookEntry
import com.minimaldesigner.arise.core.TaskKind
import com.minimaldesigner.arise.ui.components.EmptyBox
import com.minimaldesigner.arise.ui.fmtSpan
import com.minimaldesigner.arise.ui.theme.AccentPref
import java.io.File
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.LocalTime

/** A section label above a group of cards. */
@Composable
private fun Sec(text: String) = Lbl(text, modifier = Modifier.padding(start = 4.dp, top = 14.dp))

/** One row of a settings list: label on the left, value or control on the right. */
@Composable
private fun ListRow(label: String, color: Color = LocalArise.current.ink, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, Type.body, color, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Rule() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalArise.current.line2))

@Composable
private fun Val(text: String, color: Color = LocalArise.current.ink3) = Txt(text, Type.mono, color)

private fun pad2(n: Int) = n.toString().padStart(2, '0')

/** The pages of Profile & settings (0.2.1): a hub, and one page per group. */
enum class SettingsPage(val title: String) {
    HUB("Profile & settings"),
    CHALLENGE("Challenge"),
    BODY("Body & health"),
    READING("Reading"),
    TASKS("Tasks"),
    APPEARANCE("Appearance"),
    NOTIFICATIONS("Notifications"),
    DATA("Data"),
    ABOUT("About"),
}

private fun themeLabel(t: ThemePref) = when (t) {
    ThemePref.SYSTEM -> "System"
    ThemePref.LIGHT -> "Light"
    ThemePref.DARK -> "Dark"
}

/** One row of the hub: icon, page name, a short summary, and a chevron. */
@Composable
private fun NavRow(glyph: Glyph, label: String, value: String, onClick: () -> Unit) {
    val c = LocalArise.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlyphIcon(glyph, c.ink2, 20.dp)
        Txt(label, Type.body, modifier = Modifier.weight(1f))
        Txt(value, Type.mono, c.ink3, Modifier.widthIn(max = 150.dp), maxLines = 1)
        GlyphIcon(Glyph.Chev, c.ink3, 14.dp)
    }
}

/**
 * Profile & settings (0.2.1): the hub (photo, name, one row per page) and the pages it opens.
 * Opened from the avatar on Home.
 */
@Composable
fun ProfileScreen(
    page: SettingsPage,
    run: Run,
    p: Progress,
    settings: Settings,
    meta: AppMeta,
    history: List<PastRun>,
    profileThumb: File?,
    health: HealthState,
    photoCount: Int,
    books: List<BookEntry>,
    meditateApp: String?,
    onPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onPhoto: (Uri) -> Unit,
    onBody: (heightCm: Double?, goalKg: Double?) -> Unit,
    onConnectHealth: () -> Unit,
    onOpenHealth: () -> Unit,
    onRefreshHealth: () -> Unit,
    onEditBook: () -> Unit,
    onAttempts: () -> Unit,
    onTheme: (ThemePref) -> Unit,
    onNumbers: (NumStyle) -> Unit,
    onAccent: (AccentPref) -> Unit,
    onCelebrate: (Boolean) -> Unit,
    onChooseMeditateApp: () -> Unit,
    onReminder: (on: Boolean, at: LocalTime) -> Unit,
    onFoodGoals: (FoodGoals) -> Unit,
    busy: String?,
    onBackup: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
    onImport75: (Uri) -> Unit,
    onEnd: () -> Unit,
    pause: PauseState? = null,
    passesLeft: Int = FREE_PASSES,
    onPause: () -> Unit = {},
    onEndPause: () -> Unit = {},
) {
    val c = LocalArise.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).border(1.dp, c.line, CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.ChevLeft, c.ink, 18.dp)
            }
            Lbl(page.title)
        }
        // Each page slides in from the side it's opened towards.
        AnimatedContent(
            page,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(220, delayMillis = 40)) + slideInHorizontally(tween(260)) { dir * it / 8 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { -dir * it / 12 }) using SizeTransform(clip = false) { _, _ -> snap() }
            },
            label = "settings",
        ) { pg ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (pg) {
                    SettingsPage.HUB -> Hub(run, p, settings, meta, profileThumb, health, meditateApp, pause, onPage, onName, onPhoto)
                    SettingsPage.CHALLENGE -> ChallengePage(run, p, history, photoCount, pause, passesLeft, onAttempts, onPause, onEndPause, onEnd)
                    SettingsPage.BODY -> {
                        BodyCard(run, meta, health, onBody)
                        FoodGoalsCard(meta.foodGoals, health, onFoodGoals)
                        Sec("Health Connect")
                        HealthCard(health, onConnectHealth, onOpenHealth, onRefreshHealth)
                    }
                    SettingsPage.READING -> ReadingPage(meta, books, onEditBook)
                    SettingsPage.TASKS -> TasksPage(run, meditateApp, onChooseMeditateApp)
                    SettingsPage.APPEARANCE -> AppearanceCard(settings, onTheme, onNumbers, onAccent, onCelebrate)
                    SettingsPage.NOTIFICATIONS -> {
                        Sec("Evening reminder")
                        ReminderCard(settings, onReminder)
                    }
                    SettingsPage.DATA -> {
                        Sec("Backup")
                        DataCard(busy, onBackup, onRestore, onImport75)
                    }
                    SettingsPage.ABOUT -> AboutCard()
                }
            }
        }
    }
}

@Composable
private fun Hub(
    run: Run,
    p: Progress,
    settings: Settings,
    meta: AppMeta,
    profileThumb: File?,
    health: HealthState,
    meditateApp: String?,
    pause: PauseState?,
    onPage: (SettingsPage) -> Unit,
    onName: (String) -> Unit,
    onPhoto: (Uri) -> Unit,
) {
    val c = LocalArise.current
    var name by rememberSaveable(run.name) { mutableStateOf(run.name) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(onPhoto) }

    // Photo, name, program
    Card(Modifier.fillMaxWidth(), dotted = true, padding = PaddingValues(horizontal = 16.dp, vertical = 22.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                Avatar(run.name, profileThumb, 96) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(c.chip)
                        .clickable(role = Role.Button) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.Photo, c.chipInk, 15.dp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, Modifier.widthIn(max = 200.dp), placeholder = "Your name", maxLength = 24, onDone = { onName(name) })
                if (name.trim() != run.name && name.isNotBlank()) Btn("Save", { onName(name) }, small = true)
            }
            Lbl("${Programs.of(run.program).name} · attempt ${run.attempt} · since ${fmtShort(run.startDate)}")
        }
    }

    Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        val weight = bodyWeights(health.data).first.lastOrNull()
        val r = meta.reading
        NavRow(Glyph.Flame, "Challenge", if (pause != null) "Paused · back ${fmtShort(pause.pause.untilDate)}" else if (p.started) "Day ${pad2(p.dayNum)} / ${p.len}" else "Starts ${fmtShort(run.startDate)}") { onPage(SettingsPage.CHALLENGE) }
        Rule()
        NavRow(Glyph.Walk, "Body & health", weight?.let { "${fmtKg(it.kg)} kg" } ?: if (health.on) "Connected" else "Health Connect") { onPage(SettingsPage.BODY) }
        Rule()
        NavRow(Glyph.Read, "Reading", r.title.ifEmpty { "No book yet" }) { onPage(SettingsPage.READING) }
        Rule()
        NavRow(Glyph.Mind, "Tasks", meditateApp ?: "Meditation app") { onPage(SettingsPage.TASKS) }
        Rule()
        NavRow(Glyph.Theme, "Appearance", "${themeLabel(settings.theme)} · ${settings.accent.label}") { onPage(SettingsPage.APPEARANCE) }
        Rule()
        NavRow(Glyph.Bell, "Notifications", if (settings.reminderOn) fmtTime(settings.reminderAt) else "Off") { onPage(SettingsPage.NOTIFICATIONS) }
        Rule()
        NavRow(Glyph.Data, "Data", "Backup") { onPage(SettingsPage.DATA) }
        Rule()
        NavRow(Glyph.Info, "About", "") { onPage(SettingsPage.ABOUT) }
    }
}

@Composable
private fun ChallengePage(
    run: Run, p: Progress, history: List<PastRun>, photoCount: Int, pause: PauseState?, passesLeft: Int,
    onAttempts: () -> Unit, onPause: () -> Unit, onEndPause: () -> Unit, onEnd: () -> Unit,
) {
    val c = LocalArise.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("Day", if (p.started) pad2(p.dayNum) else "00", " / ${p.len}", Modifier.weight(1f))
        Stat("Cleared", pad2(p.cleared), "", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("Best streak", pad2(p.best), "", Modifier.weight(1f))
        Stat("Attempts", pad2(run.attempt), "", Modifier.weight(1f))
    }
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        ListRow("Program") { Val(Programs.of(run.program).name) }
        Rule()
        ListRow("Started") { Val(fmtShort(run.startDate)) }
        Rule()
        ListRow("Past attempts", onClick = onAttempts) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Val(if (history.isEmpty()) "None yet" else "${history.size}")
                GlyphIcon(Glyph.Chev, c.ink3, 14.dp)
            }
        }
        if (run.resetOnMiss) {
            Rule()
            ListRow("Free passes") { Val("$passesLeft / $FREE_PASSES left", if (passesLeft == 0) c.bad else c.ink3) }
        }
    }
    // A break: pausing ends in a restart at Day 1, so it's only offered once the challenge is under way.
    if (pause != null || (p.started && !p.finished)) {
        Sec("Break")
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
            when (pause) {
                is PauseState.Active -> {
                    ListRow("Paused: ${pause.pause.reason}") { Val("back ${fmtShort(pause.pause.untilDate)}") }
                    Rule()
                    ListRow("End the pause early", c.accent, onClick = onEndPause) { Val("Restart at Day 1") }
                }
                is PauseState.Over -> ListRow("Break over") { Val("Restart on Home") }
                null -> ListRow("Pause challenge", onClick = onPause) { Val("Restarts after") }
            }
        }
    }
    Sec("End")
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        ListRow("End challenge", c.bad, onClick = onEnd) { Val(if (photoCount > 0) "Photos stay" else "") }
    }
}

@Composable
private fun BodyCard(run: Run, meta: AppMeta, health: HealthState, onBody: (heightCm: Double?, goalKg: Double?) -> Unit) {
    val c = LocalArise.current
    var height by rememberSaveable(meta.profile.heightCm) { mutableStateOf(meta.profile.heightCm?.let(::trimNum).orEmpty()) }
    var goal by rememberSaveable(meta.profile.goalKg) { mutableStateOf(meta.profile.goalKg?.let(::trimNum).orEmpty()) }
    Sec("Body")
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        val (weights, source) = bodyWeights(health.data)
        val start = startWeight(weights, run.startDate)
        val last = weights.lastOrNull()
        val hcHeight = health.data?.heightCm
        ListRow("Height") {
            if (hcHeight != null) {
                Val("${trimNum(hcHeight)} cm")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(height, { height = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.width(84.dp), placeholder = "cm", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), maxLength = 5, onDone = { onBody(height.toDoubleOrNull(), goal.toDoubleOrNull()) })
                    Val("cm")
                }
            }
        }
        Rule()
        ListRow("Start weight") { Val(start?.let { "${fmtKg(it.kg)} kg · ${fmtShort(it.date)}" } ?: "–") }
        Rule()
        ListRow("Now") {
            val d = if (start != null && last != null) last.kg - start.kg else null
            Val(
                (last?.let { "${fmtKg(it.kg)} kg" } ?: "–") + (d?.let { " · ${if (it > 0) "+" else "−"}${fmtKg(kotlin.math.abs(it))}" } ?: ""),
                if (d == null) c.ink3 else if (d <= 0) c.good else c.bad,
            )
        }
        Rule()
        val b = bmi(last?.kg, hcHeight ?: meta.profile.heightCm)
        ListRow("BMI") {
            Val(
                when {
                    b != null -> "$b · ${bmiBand(b).label}"
                    last == null -> "–"
                    else -> "Needs your height"
                },
            )
        }
        Rule()
        ListRow("Goal weight") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(goal, { goal = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.width(84.dp), placeholder = "kg", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), maxLength = 5, onDone = { onBody(height.toDoubleOrNull(), goal.toDoubleOrNull()) })
                Val("kg")
            }
        }
        val g = meta.profile.goalKg
        if (g != null && last != null) {
            Rule()
            ListRow("To go") { Val("${fmtKg((last.kg - g).coerceAtLeast(0.0))} kg") }
        }
        val changed = height != (meta.profile.heightCm?.let(::trimNum).orEmpty()) || goal != (meta.profile.goalKg?.let(::trimNum).orEmpty())
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val from = when (source) {
                WeightSource.HEALTH -> "Weight${if (hcHeight != null) " and height" else ""} from Health Connect."
                WeightSource.NONE -> if (health.on) "No weights in Health Connect yet." else "Connect Health Connect below to bring in your weight."
            }
            Txt(from, Type.sub, c.ink3, Modifier.weight(1f))
            if (changed) Btn("Save", { onBody(height.toDoubleOrNull(), goal.toDoubleOrNull()) }, small = true)
        }
    }
}

@Composable
private fun GoalRow(label: String, value: String, unit: String, onValue: (String) -> Unit) {
    ListRow(label) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(value, { onValue(it.filter { ch -> ch.isDigit() || ch == '.' }) }, Modifier.width(84.dp), placeholder = "–", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), maxLength = 6)
            Val(unit)
        }
    }
}

/** Daily food targets (1.2.0): Health Connect has the meals but no goals, so they're set here. */
@Composable
private fun FoodGoalsCard(goals: FoodGoals, health: HealthState, onSave: (FoodGoals) -> Unit) {
    val c = LocalArise.current
    fun str(v: Double?) = v?.let(::trimNum).orEmpty()
    var kcal by rememberSaveable(goals) { mutableStateOf(str(goals.kcal)) }
    var protein by rememberSaveable(goals) { mutableStateOf(str(goals.protein)) }
    var carbs by rememberSaveable(goals) { mutableStateOf(str(goals.carbs)) }
    var fat by rememberSaveable(goals) { mutableStateOf(str(goals.fat)) }
    var water by rememberSaveable(goals) { mutableStateOf(str(goals.waterL)) }
    fun num(s: String) = s.toDoubleOrNull()?.takeIf { it > 0 }
    val next = FoodGoals(num(kcal), num(protein), num(carbs), num(fat), num(water))
    Sec("Food goals")
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        GoalRow("Calories", kcal, "kcal") { kcal = it }
        Rule()
        GoalRow("Protein", protein, "g") { protein = it }
        Rule()
        GoalRow("Carbs", carbs, "g") { carbs = it }
        Rule()
        GoalRow("Fat", fat, "g") { fat = it }
        Rule()
        GoalRow("Water", water, "L") { water = it }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(
                if (HealthKind.NUTRITION in health.granted) "Meals come from Health Connect. The Food tab measures each day against these."
                else "Meals come from Health Connect: let ARISE read Food below, and the Food tab appears.",
                Type.sub, c.ink3, Modifier.weight(1f),
            )
            if (next != goals) Btn("Save", { onSave(next) }, small = true)
        }
    }
}

@Composable
private fun ReadingPage(meta: AppMeta, books: List<BookEntry>, onEditBook: () -> Unit) {
    val c = LocalArise.current
    Sec("Now reading")
    Card(Modifier.fillMaxWidth()) {
        val r = meta.reading
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r.title.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Txt(r.title, Type.body.copy(fontSize = 16.sp), modifier = Modifier.weight(1f), maxLines = 2)
                    Val("p. ${r.page}" + if (r.total > 0) " / ${r.total}" else "")
                }
                if (r.total > 0) Segments(r.page.toFloat() / r.total)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Lbl(if (r.total > 0) "${(r.page * 100f / r.total).roundToInt()}% · ${r.total - r.page} pages to go" else "Add the total pages to see progress", size = 10f)
                    Lbl("Books finished ${r.finished}", size = 10f)
                }
            } else {
                Txt("No book yet. Tap Read 10 pages on Home, or add one here.", Type.small, c.ink3)
                Lbl("Books finished ${r.finished}", size = 10f)
            }
            Btn(if (r.title.isNotEmpty()) "Update" else "Add a book", onEditBook, kind = BtnKind.Ghost, small = true)
        }
    }
    Sec("Books")
    if (books.isEmpty()) {
        EmptyBox("Books you log with the Read task show up here.")
    } else {
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
            books.forEachIndexed { i, b ->
                if (i > 0) Rule()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Txt(b.title, Type.body, maxLines = 2)
                        Lbl(bookLine(b), size = 10f)
                    }
                    when {
                        b.finished -> GlyphIcon(Glyph.Check, c.accent, 16.dp)
                        b.total > 0 -> Val("${(b.page.coerceAtMost(b.total) * 100f / b.total).roundToInt()}%", if (b.current) c.accent else c.ink3)
                        else -> Val("p. ${b.page}")
                    }
                }
            }
        }
    }
}

/** "Finished 21 Sep · 320 pages", "Reading since 3 Sep", "3–9 Sep · stopped on p. 80". */
private fun bookLine(b: BookEntry): String = when {
    b.current -> b.from?.let { "Reading since ${fmtShort(it)}" } ?: "Reading now"
    b.finished -> (b.to?.let { "Finished ${fmtShort(it)}" } ?: "Finished") + (if (b.total > 0) " · ${b.total} pages" else "")
    else -> listOfNotNull(b.from?.let { f -> b.to?.let { t -> fmtSpan(f, t) } }, "stopped on p. ${b.page}").joinToString(" · ")
}

@Composable
private fun TasksPage(run: Run, meditateApp: String?, onChooseMeditateApp: () -> Unit) {
    val c = LocalArise.current
    val kinds = run.tasks.map { it.kind }.toSet()
    Sec("Meditate")
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        ListRow("Meditation app", onClick = onChooseMeditateApp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Val(meditateApp ?: "Not set")
                GlyphIcon(Glyph.Chev, c.ink3, 14.dp)
            }
        }
        Txt(
            "The Meditate task opens this app. Sessions it saves to Health Connect (mindfulness) show up in the sheet when you come back." +
                if (TaskKind.MIND !in kinds) " Your challenge doesn't have a Meditate task." else "",
            Type.sub, c.ink3, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
    }
    Sec("Sleep")
    Card(Modifier.fillMaxWidth()) {
        Txt(
            "A day's sleep is the night you wake up from. Day 1 is free when the challenge starts that day, since the night before came first. " +
                "After that, 7 hours in Health Connect shows on the task, and one tap ticks it." +
                if (TaskKind.SLEEP !in kinds) " Your challenge doesn't have a Sleep task." else "",
            Type.small, c.ink2,
        )
    }
    Sec("Photo")
    Card(Modifier.fillMaxWidth()) {
        Txt("Any day with a progress photo counts the Photo task as done, even after a restart. Delete the day's photo to open it again.", Type.small, c.ink2)
    }
}

@Composable
private fun AppearanceCard(settings: Settings, onTheme: (ThemePref) -> Unit, onNumbers: (NumStyle) -> Unit, onAccent: (AccentPref) -> Unit, onCelebrate: (Boolean) -> Unit) {
    val c = LocalArise.current
    Sec("Theme")
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(listOf(ThemePref.SYSTEM to "System", ThemePref.LIGHT to "Light", ThemePref.DARK to "Dark"), settings.theme, onTheme)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Txt("Numbers", Type.body)
                Segmented(listOf(NumStyle.DOT to "Dot", NumStyle.MONO to "Mono"), settings.numbers, onNumbers)
            }
        }
    }
    Sec("Accent")
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentPref.entries.forEach { a ->
                    val tone = a.tone(c.isDark)
                    val on = a == settings.accent
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(tone.accent)
                            .border(2.dp, if (on) c.ink else Color.Transparent, CircleShape)
                            .clickable(role = Role.RadioButton) { onAccent(a) }
                            .semantics { contentDescription = a.label },
                        contentAlignment = Alignment.Center,
                    ) { if (on) GlyphIcon(Glyph.Check, tone.ink, 14.dp) }
                }
            }
            Lbl(settings.accent.label + if (settings.accent == AccentPref.ORANGE) " · ARISE's own" else "", size = 10f)
        }
    }
    Sec("Celebrations")
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleSwitch(settings.celebrate, onCelebrate, if (settings.celebrate) "Confetti on" else "Confetti off")
            Txt("A small burst when you tick a task, and a few big ones when the day is clear. The haptic clicks stay either way. No confetti when the phone's animations are off.", Type.small, c.ink2)
        }
    }
}

@Composable
private fun AboutCard() {
    val c = LocalArise.current
    val context = LocalContext.current
    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        ListRow("Version") { Val(info.versionName.orEmpty()) }
        Rule()
        ListRow("Build") { Val("${info.longVersionCode}") }
    }
    Txt("Signed with the ARISE key. Separate from your 75 Hard app.", Type.sub, c.ink3, Modifier.padding(start = 4.dp, top = 4.dp))
}

private fun fmtKg(kg: Double): String = String.format(java.util.Locale.US, "%.1f", kg)

/** What ARISE reads from Health Connect, and the buttons to connect, refresh or manage it. */
@Composable
private fun HealthCard(health: HealthState, onConnect: () -> Unit, onOpen: () -> Unit, onRefresh: () -> Unit) {
    val c = LocalArise.current
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (health.access) {
                HealthAccess.UNKNOWN -> Txt("Checking Health Connect…", Type.small, c.ink2)
                HealthAccess.UNAVAILABLE -> Txt("Health Connect isn't available on this phone.", Type.small, c.ink2)
                HealthAccess.NEEDS_UPDATE -> {
                    Txt("Health Connect needs installing or updating from the Play Store first.", Type.small, c.ink2)
                    Btn("Get Health Connect", onConnect, small = true)
                }
                HealthAccess.OFF -> {
                    Txt("ARISE can read your weight, height, workouts, sleep, steps and mindfulness from Health Connect. It never writes to it.", Type.small, c.ink2)
                    Btn("Connect", onConnect, small = true)
                }
                HealthAccess.ON -> {
                    val missing = HealthKind.entries.filter { it in health.supported && it !in health.granted }
                    Txt("Reading ${health.granted.joinToString(", ") { it.label.lowercase() }}.", Type.small, c.ink2)
                    if (missing.isNotEmpty()) {
                        Txt("Not shared: ${missing.joinToString(", ") { it.label.lowercase() }}. Turn them on in Health Connect.", Type.small, c.ink3)
                    }
                    Lbl(
                        when {
                            health.reading -> "Reading…"
                            health.readAt != null -> "Last read ${fmtClock(java.time.Instant.ofEpochMilli(health.readAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime())}"
                            else -> "Not read yet"
                        },
                        size = 10f,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("Refresh", onRefresh, Modifier.weight(1f), BtnKind.Ghost, small = true, enabled = !health.reading)
                        Btn("Manage", onOpen, Modifier.weight(1f), BtnKind.Ghost, small = true)
                    }
                }
            }
            health.error?.let { ErrorLine(it, c.warn) }
        }
    }
}

private fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

@Composable
private fun Stat(label: String, value: String, suffix: String, modifier: Modifier) {
    val c = LocalArise.current
    Card(modifier, padding = PaddingValues(14.dp)) {
        Lbl(label, size = 10f)
        Row(Modifier.padding(top = 6.dp)) {
            Num(value, 30f, modifier = Modifier.alignByBaseline())
            if (suffix.isNotEmpty()) Txt(suffix, Type.body, c.ink3, Modifier.alignByBaseline())
        }
    }
}

/** Ten short bars, lit to [fraction] (`.bar10`). */
@Composable
fun Segments(fraction: Float, track: Color = LocalArise.current.line, fill: Color = LocalArise.current.accent) {
    val lit = (fraction.coerceIn(0f, 1f) * 10).roundToInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(10) { i -> Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < lit) fill else track)) }
    }
}

/** Zips some file managers label as octet-stream, so accept both. */
private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/** Back up, restore (with a confirm step, since it replaces everything) and the 75 Hard photo import. */
@Composable
private fun DataCard(busy: String?, onBackup: (Uri) -> Unit, onRestore: (Uri) -> Unit, onImport75: (Uri) -> Unit) {
    val c = LocalArise.current
    var restoreFrom by remember { mutableStateOf<Uri?>(null) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(onBackup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreFrom = it }
    val import75 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImport75) }
    val idle = busy == null

    Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("A backup is one zip with your challenges, every ticked day and workout, and your photos.", Type.small, c.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Back up", { backup.launch("arise-backup-${LocalDate.now()}.zip") }, Modifier.weight(1f), small = true, enabled = idle)
                Btn("Restore", { restore.launch(ZIP_TYPES) }, Modifier.weight(1f), BtnKind.Ghost, small = true, enabled = idle)
            }
            val pending = restoreFrom
            if (pending != null) {
                ErrorLine("This replaces everything in ARISE with that backup: challenges, days and photos.", c.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Cancel", { restoreFrom = null }, Modifier.weight(1f), BtnKind.Ghost, small = true)
                    Btn("Replace my data", { restoreFrom = null; onRestore(pending) }, Modifier.weight(1f), BtnKind.Accent, small = true, enabled = idle)
                }
            }
            Txt("75 Hard photos", Type.body, modifier = Modifier.padding(top = 4.dp))
            Txt(
                "Pick the 75 Hard app's backup zip. Its progress photos come in as front photos, dated when they were taken. Importing again skips photos already here.",
                Type.small, c.ink2,
            )
            Btn("Import from 75 Hard", { import75.launch(ZIP_TYPES) }, kind = BtnKind.Ghost, small = true, enabled = idle)
            if (busy != null) Txt(busy, Type.small, c.accent)
        }
    }
}

/** The evening reminder: on/off and the time. Turning it on asks for notification permission. */
@Composable
private fun ReminderCard(settings: Settings, onReminder: (Boolean, LocalTime) -> Unit) {
    val c = LocalArise.current
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) onReminder(true, settings.reminderAt)
    }
    val blocked = settings.reminderOn && !Reminders.canNotify(context)

    Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("One notification a day, only if today's tasks aren't all done.", Type.small, c.ink2)
            ToggleSwitch(settings.reminderOn, { on ->
                when {
                    !on -> onReminder(false, settings.reminderAt)
                    Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context) -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> onReminder(true, settings.reminderAt)
                }
            }, if (settings.reminderOn) "On at ${fmtTime(settings.reminderAt)}" else "Off")
            if (settings.reminderOn) {
                Segmented(REMINDER_TIMES.map { it to fmtTime(it) }, settings.reminderAt) { onReminder(true, it) }
            }
            if (denied || blocked) {
                ErrorLine("Notifications are off for ARISE. Allow them in Android settings to get the reminder.", c.warn)
            }
        }
    }
}
