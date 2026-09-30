package com.minimaldesigner.arise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaldesigner.arise.core.Days
import com.minimaldesigner.arise.core.FREE_PASSES
import com.minimaldesigner.arise.core.PAUSE_LENGTHS
import com.minimaldesigner.arise.core.PAUSE_MAX_DAYS
import com.minimaldesigner.arise.core.PAUSE_REASONS
import com.minimaldesigner.arise.core.Pause
import com.minimaldesigner.arise.data.Reminders
import com.minimaldesigner.arise.ui.fmtWeekday
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import com.minimaldesigner.arise.core.HealthWorkout
import com.minimaldesigner.arise.core.Programs
import com.minimaldesigner.arise.core.Progress
import com.minimaldesigner.arise.core.Run
import com.minimaldesigner.arise.core.TaskDef
import com.minimaldesigner.arise.core.Workout
import com.minimaldesigner.arise.core.WorkoutCheck
import com.minimaldesigner.arise.core.checkWorkout
import com.minimaldesigner.arise.core.daysBetween
import com.minimaldesigner.arise.core.defaultWorkoutType
import com.minimaldesigner.arise.core.leftOn
import com.minimaldesigner.arise.ui.components.Btn
import com.minimaldesigner.arise.ui.components.BtnKind
import com.minimaldesigner.arise.ui.components.ErrorLine
import com.minimaldesigner.arise.ui.components.Field
import com.minimaldesigner.arise.ui.components.Labeled
import com.minimaldesigner.arise.ui.components.PickChip
import com.minimaldesigner.arise.ui.components.Segmented
import com.minimaldesigner.arise.ui.components.SheetActions
import com.minimaldesigner.arise.ui.components.ToggleSwitch
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.fmtClock
import com.minimaldesigner.arise.ui.fmtShort
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.Radius
import com.minimaldesigner.arise.ui.theme.ThemePref
import com.minimaldesigner.arise.ui.theme.Type
import androidx.compose.foundation.border
import com.minimaldesigner.arise.core.Reading
import com.minimaldesigner.arise.data.PastRun
import com.minimaldesigner.arise.ui.components.Glyph
import com.minimaldesigner.arise.ui.components.GlyphIcon
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.LinkButton
import com.minimaldesigner.arise.ui.components.EmptyBox
import kotlin.math.roundToInt
import java.time.LocalDate

/**
 * `#wk-form`: type, minutes, outdoors. Today's Health Connect sessions show as picks that
 * fill the form; [suggested] fills it when the sheet opens.
 */
@Composable
fun WorkoutSheetBody(task: TaskDef, sessions: List<HealthWorkout>, suggested: HealthWorkout?, onCancel: () -> Unit, onDone: (Workout) -> Unit) {
    var type by remember(task) { mutableStateOf(suggested?.type ?: defaultWorkoutType(task)) }
    var mins by remember(task) { mutableStateOf((suggested?.mins?.coerceIn(5, 300) ?: 45).toString()) }
    var outdoor by remember(task) { mutableStateOf(suggested?.outdoor ?: task.outdoor) }
    var from by remember(task) { mutableStateOf(suggested?.id) }
    var err by remember(task) { mutableStateOf("") }

    Txt(task.label, Type.sheetTitle)
    if (sessions.isNotEmpty()) {
        Labeled("From Health Connect today") {
            Chips {
                sessions.forEach { w ->
                    PickChip("${w.title ?: w.type} · ${w.mins} min · ${fmtClock(w.start.toLocalTime())}", w.id == from) {
                        from = w.id; type = w.type; mins = w.mins.coerceIn(5, 300).toString(); outdoor = w.outdoor; err = ""
                    }
                }
            }
        }
    }
    Labeled("Type") {
        Chips { Programs.workoutTypes.forEach { t -> PickChip(t, t == type) { type = t; from = null } } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Labeled("Minutes", Modifier.weight(1f)) {
            Field(mins, { v -> mins = v.filter(Char::isDigit); from = null }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 3)
        }
        Labeled("Where", Modifier.weight(1f)) {
            Column(Modifier.padding(top = 10.dp)) { ToggleSwitch(outdoor, { outdoor = it }, "Outdoors") }
        }
    }
    if (err.isNotEmpty()) ErrorLine(err)
    SheetActions(
        { m -> Btn("Cancel", onCancel, m, BtnKind.Ghost) },
        { m ->
            Btn("Done", {
                when (val r = checkWorkout(task, type, mins.toIntOrNull() ?: 0, outdoor)) {
                    WorkoutCheck.NeedsOutdoors -> err = "This one needs to be outdoors."
                    WorkoutCheck.TooShort -> err = "The task is 45 minutes. Log it when you've done the full session."
                    is WorkoutCheck.Ok -> onDone(r.workout)
                }
            }, m, BtnKind.Accent)
        },
    )
}

/**
 * `#miss-scrim`: modal until one of the choices is made. "I did finish it" spends one of the
 * attempt's [FREE_PASSES]; with none left, restarting is the only way on.
 */
@Composable
fun MissedSheetBody(run: Run, days: Days, missed: LocalDate, keep: String, passesLeft: Int, onFix: () -> Unit, onRestart: () -> Unit) {
    val c = LocalArise.current
    val n = daysBetween(run.startDate, missed) + 1
    val left = leftOn(run, days, missed).joinToString(", ") { it.label.lowercase() }
    Txt("Day $n was missed", Type.sheetTitle)
    Txt(
        "On ${fmtShort(missed)} these weren't checked off: $left. ${Programs.of(run.program).name} starts over after a miss. " +
            if (passesLeft > 0) "If you did finish them and just forgot to tick them, fix it with a free pass instead."
            else "You've used all $FREE_PASSES free passes this attempt, so a restart is the only option.",
        Type.body.copy(fontSize = 14.5.sp), c.ink2,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Lbl("Free passes", size = 10f)
            Lbl("$passesLeft of $FREE_PASSES left this attempt", if (passesLeft > 0) c.accent else c.bad, size = 10f)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(FREE_PASSES) { i ->
                Box(Modifier.weight(1f).height(6.dp).clip(Radius.pill).background(if (i < passesLeft) c.accent else c.line))
            }
        }
    }
    KeepNote(keep)
    if (passesLeft > 0) {
        SheetActions(
            { m -> Btn(if (passesLeft == 1) "Use my last pass" else "I did finish it", onFix, m, BtnKind.Ghost) },
            { m -> Btn("Restart from Day 1", onRestart, m, BtnKind.Accent) },
        )
    } else {
        Btn("Restart from Day 1", onRestart, Modifier.fillMaxWidth(), BtnKind.Accent)
    }
}

/**
 * Pause the challenge: name the break and choose how long. ARISE stays quiet until it's
 * over, then says welcome back, and the challenge restarts at Day 1. With [current] (the
 * welcome-back sheet's "Pause longer") it moves the end of that break instead.
 */
@Composable
fun PauseSheetBody(run: Run, current: Pause?, today: LocalDate, onCancel: () -> Unit, onPause: (reason: String, days: Int) -> String?) {
    val c = LocalArise.current
    val context = LocalContext.current
    var reason by remember { mutableStateOf(current?.reason ?: "") }
    var len by remember { mutableStateOf(PAUSE_LENGTHS.first()) }
    var other by remember { mutableStateOf(false) }
    var otherText by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val days = if (other) otherText.toIntOrNull() ?: 0 else len
    fun submit() {
        onPause(reason, days)?.let { err = it }
    }
    // The break-over notification needs the permission: ask once, then pause either way.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { submit() }

    Txt(if (current != null) "Pause longer" else "Pause the challenge", Type.sheetTitle)
    Txt(
        if (current != null) "Still away? Choose how many more days from today. ARISE stays quiet until then."
        else "Taking a break? Name it and choose how long. ARISE stays quiet until it's over, then reminds you to come back.",
        Type.body.copy(fontSize = 14.5.sp), c.ink2,
    )
    Labeled("What's the break?") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chips { PAUSE_REASONS.forEach { r -> PickChip(r, reason == r) { reason = r; err = "" } } }
            Field(reason, { reason = it; err = "" }, placeholder = "Name this break, e.g. Bali trip", maxLength = 40)
        }
    }
    Labeled(if (current != null) "More days" else "How long") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chips {
                PAUSE_LENGTHS.forEach { d -> PickChip("$d days", !other && len == d) { len = d; other = false; err = "" } }
                PickChip("Other", other) { other = true; err = "" }
            }
            if (other) {
                Field(
                    otherText, { v -> otherText = v.filter(Char::isDigit); err = "" },
                    placeholder = "Days (1 to $PAUSE_MAX_DAYS)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 2,
                )
            }
            if (days in 1..PAUSE_MAX_DAYS) {
                val back = today.plusDays(days.toLong())
                Lbl("Back on ${fmtWeekday(back)} ${fmtShort(back)}", c.accent, size = 10f)
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(Radius.row).background(c.card2).border(1.dp, c.line, Radius.row).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphIcon(Glyph.Flame, c.accent, 22.dp)
        Txt(
            "A break means a fresh start: when it's over, you restart at Day 1 (attempt ${run.attempt + 1}). Your photos stay.",
            Type.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f),
        )
    }
    if (err.isNotEmpty()) ErrorLine(err)
    SheetActions(
        { m -> Btn("Cancel", onCancel, m, BtnKind.Ghost) },
        { m ->
            Btn("Pause", {
                if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context) && reason.isNotBlank() && days in 1..PAUSE_MAX_DAYS) {
                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    submit()
                }
            }, m, BtnKind.Accent)
        },
    )
}

/** The break is over: modal until the restart (or a longer break). */
@Composable
fun BackSheetBody(run: Run, pause: Pause, keep: String, onLonger: () -> Unit, onRestart: () -> Unit) {
    Txt("Welcome back", Type.sheetTitle)
    Txt(
        "Your ${pause.phrase} is over. A break means a fresh start: Day 1 is today, attempt ${run.attempt + 1}. " +
            "Time to get back on track with your plans.",
        Type.body.copy(fontSize = 14.5.sp), LocalArise.current.ink2,
    )
    KeepNote(keep)
    SheetActions(
        { m -> Btn("Pause longer", onLonger, m, BtnKind.Ghost) },
        { m -> Btn("Restart from Day 1", onRestart, m, BtnKind.Accent) },
    )
}

/** `.banner`: accent-soft strip with one action (sample mode's Exit). */
@Composable
fun Banner(text: String, action: String, onAction: () -> Unit) {
    val c = LocalArise.current
    Row(
        Modifier.fillMaxWidth().clip(Radius.date).background(c.accentSoft).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, Type.body.copy(fontSize = 13.5.sp), modifier = Modifier.weight(1f))
        Btn(action, onAction, small = true)
    }
}

/** "All 29 photos stay in ARISE…", shown before a restart or End challenge. */
fun keepLine(photoCount: Int, pinnedBefore: Boolean): String =
    (if (photoCount == 1) "Your photo stays in ARISE" else "All $photoCount photos stay in ARISE") +
        (if (pinnedBefore) ", and your pinned before photo stays pinned." else ".")

@Composable
fun KeepNote(text: String) {
    val c = LocalArise.current
    Row(
        Modifier.fillMaxWidth().clip(Radius.row).background(c.card2).border(1.dp, c.line, Radius.row).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphIcon(Glyph.Photo, c.accent, 22.dp)
        Txt(text, Type.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
    }
}

/** End challenge: a real confirmation, saying what stays. */
@Composable
fun EndSheetBody(keep: String, onCancel: () -> Unit, onEnd: () -> Unit) {
    val c = LocalArise.current
    Txt("End this challenge?", Type.sheetTitle)
    Txt("You go back to the start screen. Your days stay in Past attempts.", Type.body.copy(fontSize = 14.5.sp), c.ink2)
    KeepNote(keep)
    SheetActions(
        { m -> Btn("Keep going", onCancel, m, BtnKind.Ghost) },
        { m -> Btn("End challenge", onEnd, m, BtnKind.Danger) },
    )
}

/** Profile → Past attempts. */
@Composable
fun AttemptsSheetBody(history: List<PastRun>, onClose: () -> Unit) {
    val c = LocalArise.current
    Txt("Past attempts", Type.sheetTitle)
    if (history.isEmpty()) EmptyBox("None yet. Ended or restarted challenges show up here.")
    history.forEach { h ->
        Row(
            Modifier.fillMaxWidth().clip(Radius.row).border(1.dp, c.line, Radius.row).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt("${Programs.of(h.program).name} · attempt ${h.attempt}", Type.body)
                Lbl("${fmtShort(h.start)} – ${h.end?.let(::fmtShort) ?: "?"} · ${if (h.paused) "paused" else if (h.restarted) "restarted" else "ended"}", size = 10f)
            }
            Txt("${h.cleared} cleared", Type.mono, c.ink3)
        }
    }
    Btn("Close", onClose, Modifier.fillMaxWidth())
}

/** Read 10 pages: which book, which page, out of how many. Pre-filled from the current book. */
@Composable
fun ReadSheetBody(
    title: String,
    reading: Reading,
    onCancel: () -> Unit,
    onFinishBook: () -> Unit,
    onDone: (title: String, page: Int, total: Int) -> String?,
) {
    val c = LocalArise.current
    var book by remember { mutableStateOf(reading.title) }
    var page by remember { mutableStateOf(if (reading.title.isNotEmpty()) reading.page.toString() else "") }
    var total by remember { mutableStateOf(if (reading.total > 0) reading.total.toString() else "") }
    var err by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(false) }
    val p = page.toIntOrNull() ?: 0
    val t = total.toIntOrNull() ?: 0
    val same = !fresh && reading.title.isNotEmpty() && book.trim() == reading.title
    val today = if (same) p - reading.page else p

    Txt(title, Type.sheetTitle)
    Txt(
        when {
            fresh -> "Nice. What's next?"
            reading.title.isNotEmpty() -> "Last time you stopped on page ${reading.page}."
            else -> "What are you reading? ARISE remembers it for next time."
        },
        Type.body.copy(fontSize = 14.5.sp), c.ink2,
    )
    Labeled("Book") { Field(book, { book = it; err = "" }, placeholder = "Book title", maxLength = 80) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Labeled("On page", Modifier.weight(1f)) {
            Field(page, { v -> page = v.filter(Char::isDigit); err = "" }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 5)
        }
        Labeled("Of", Modifier.weight(1f)) {
            Field(total, { v -> total = v.filter(Char::isDigit); err = "" }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 5)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Segments(if (t > 0) p.toFloat() / t else 0f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Lbl(if (p > 0 && today > 0) "+$today pages today" else if (p > 0 && same) "Same page as last time" else "", c.accent, size = 10f)
            Lbl(if (t > 0) "${(p.coerceAtMost(t) * 100f / t).roundToInt()}% of the book" else "", size = 10f)
        }
    }
    if (reading.title.isNotEmpty() && !fresh) {
        LinkButton("Finished it? Start a new book", {
            onFinishBook(); fresh = true; book = ""; page = ""; total = ""
        }, c.ink2)
    }
    if (err.isNotEmpty()) ErrorLine(err)
    SheetActions(
        { m -> Btn("Cancel", onCancel, m, BtnKind.Ghost) },
        { m -> Btn("Done", { onDone(book, p, t)?.let { err = it } }, m, BtnKind.Accent) },
    )
}
