package com.minimaldesigner.arise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaldesigner.arise.core.ProgramId
import com.minimaldesigner.arise.core.Programs
import com.minimaldesigner.arise.core.StartChoice
import com.minimaldesigner.arise.core.TaskDef
import com.minimaldesigner.arise.core.customTask
import com.minimaldesigner.arise.ui.components.Btn
import com.minimaldesigner.arise.ui.components.BtnKind
import com.minimaldesigner.arise.ui.components.Card
import com.minimaldesigner.arise.ui.components.CardHeader
import com.minimaldesigner.arise.ui.components.ErrorLine
import com.minimaldesigner.arise.ui.components.Field
import com.minimaldesigner.arise.ui.components.Glyph
import com.minimaldesigner.arise.ui.components.GlyphIcon
import com.minimaldesigner.arise.ui.components.glyphFor
import com.minimaldesigner.arise.ui.components.Labeled
import com.minimaldesigner.arise.ui.components.LinkButton
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.Segmented
import com.minimaldesigner.arise.ui.components.ToggleSwitch
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.fmtFull
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.Radius
import com.minimaldesigner.arise.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The Custom challenge's daily tasks: shared by the Custom card and the sheet that picks them. */
class CustomPicks {
    val picked = mutableStateListOf<String>().apply { addAll(Programs.defaultCustom) }
    val extra = mutableStateListOf<TaskDef>()

    /** Presets first, then the user's own, in the order the sheet lists them. */
    val all: List<TaskDef> get() = Programs.presets + extra
    val chosen: List<TaskDef> get() = all.filter { it.id in picked }

    fun toggle(id: String) {
        if (id in picked) picked.remove(id) else picked += id
    }

    fun add(label: String): Boolean {
        val v = label.trim()
        if (v.isEmpty()) return false
        val t = customTask(v, System.currentTimeMillis())
        extra += t; picked += t.id
        return true
    }
}

/** `#v-start`: pick a challenge, name and Day 1. A Custom challenge's tasks are picked in a sheet. */
@Composable
fun Onboarding(
    today: LocalDate,
    picks: CustomPicks,
    onChooseTasks: () -> Unit,
    onStart: (StartChoice, onNoTasks: () -> Unit) -> Unit,
    onSample: () -> Unit,
) {
    val c = LocalArise.current
    var program by rememberSaveable { mutableStateOf(ProgramId.HARD) }
    var len by rememberSaveable { mutableStateOf(75) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(today.toString()) }
    var err by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), dotted = true, padding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 18.dp)) {
            Lbl("ARISE")
            Txt("Start your challenge", Type.h1.copy(fontSize = 32.sp, fontWeight = FontWeight.Light), modifier = Modifier.padding(top = 8.dp))
            Txt(
                "Pick a challenge. Check off each day's tasks, add a weekly photo, and watch the ring fill.",
                Type.sub.copy(fontSize = 14.5.sp, lineHeight = 20.sp), c.ink2, Modifier.padding(top = 6.dp),
            )
        }

        Programs.all.forEach { p ->
            PlanCard(
                name = p.name, level = p.level, blurb = p.blurb, rule = p.rule,
                items = if (p.id == ProgramId.CUSTOM) listOf("12 presets", "Your own tasks", "30 to 100 days")
                else p.tasks.map { it.label.replace(" · 45 min", "") },
                selected = program == p.id,
                onClick = {
                    // Picking Custom opens its task picker straight away.
                    if (p.id == ProgramId.CUSTOM && program != ProgramId.CUSTOM) onChooseTasks()
                    program = p.id; err = ""
                },
            )
        }

        if (program == ProgramId.CUSTOM) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardHeader("Your daily tasks", titleStyle = Type.body.copy(fontSize = 17.sp)) {
                        Txt("${picks.picked.size} a day", Type.small, c.ink3)
                    }
                    val chosen = picks.chosen
                    if (chosen.isEmpty()) {
                        Txt("No tasks yet.", Type.small, c.ink3)
                    } else {
                        Chips {
                            chosen.forEach { t ->
                                Row(
                                    Modifier.clip(Radius.pill).background(c.page).padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    GlyphIcon(glyphFor(t.kind), c.ink3, 14.dp)
                                    Txt(t.label, Type.sub, c.ink2)
                                }
                            }
                        }
                    }
                    Btn(if (chosen.isEmpty()) "Choose tasks" else "Change tasks", onChooseTasks, Modifier.fillMaxWidth(), BtnKind.Ghost)
                    Labeled("Length") {
                        Segmented(Programs.customLengths.map { it to "$it days" }, len) { len = it }
                    }
                    ToggleSwitch(reset, { reset = it }, "Missing a day restarts at Day 1")
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Labeled("Your name", Modifier.weight(1f)) {
                        Field(name, { name = it }, placeholder = "Miranda", maxLength = 24)
                    }
                    Labeled("Day 1", Modifier.weight(1f)) {
                        DateField(LocalDate.parse(start)) { picking = true }
                    }
                }
                Btn(
                    "Start challenge",
                    {
                        err = ""
                        onStart(
                            StartChoice(program, name, LocalDate.parse(start), picks.picked.toList(), picks.extra.toList(), len, reset),
                        ) { err = "Pick at least one daily task." }
                    },
                    Modifier.fillMaxWidth(), BtnKind.Accent,
                )
                if (err.isNotEmpty()) ErrorLine(err)
                LinkButton("Try it with sample data", onSample, c.ink2, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    if (picking) {
        DayPicker(LocalDate.parse(start), onPick = { start = it.toString(); picking = false }, onDismiss = { picking = false })
    }
}

/** The Custom challenge's task picker: every preset and the user's own, plus a field to add one. */
@Composable
fun CustomTasksSheetBody(picks: CustomPicks, onDone: () -> Unit) {
    val c = LocalArise.current
    var adding by rememberSaveable { mutableStateOf("") }
    fun add() {
        if (picks.add(adding)) adding = ""
    }
    val n = picks.picked.size

    Txt("Daily tasks", Type.sheetTitle)
    Txt("Pick what you'll do every day of the challenge.", Type.body.copy(fontSize = 14.5.sp), c.ink2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        picks.all.forEach { t -> PickRow(t, t.id in picks.picked) { picks.toggle(t.id) } }
    }
    Labeled("Add your own") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(
                adding, { adding = it }, Modifier.weight(1f), "e.g. 20 push-ups before breakfast",
                KeyboardOptions(imeAction = ImeAction.Done), onDone = ::add,
            )
            Btn("Add", ::add, small = true)
        }
    }
    Btn(
        when (n) { 0 -> "Pick at least one"; 1 -> "Done · 1 task a day"; else -> "Done · $n tasks a day" },
        onDone, Modifier.fillMaxWidth(), BtnKind.Accent, enabled = n > 0,
    )
}

/** One task in the picker: icon, name, and a tick when it's in. */
@Composable
private fun PickRow(t: TaskDef, selected: Boolean, onClick: () -> Unit) {
    val c = LocalArise.current
    Row(
        Modifier.fillMaxWidth().clip(Radius.row).background(if (selected) c.card2 else c.card)
            .border(1.dp, if (selected) c.accent else c.line, Radius.row)
            .clickable(role = Role.Checkbox, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphIcon(glyphFor(t.kind), if (selected) c.accent else c.ink3, 20.dp)
        Txt(t.label, Type.body.copy(fontSize = 15.sp), if (selected) c.ink else c.ink2, Modifier.weight(1f))
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (selected) c.accent else Color.Transparent)
                .border(1.5.dp, if (selected) c.accent else c.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) GlyphIcon(Glyph.Check, c.accentInk, 12.dp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(name: String, level: String, blurb: String, rule: String, items: List<String>, selected: Boolean, onClick: () -> Unit) {
    val c = LocalArise.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(c.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, Radius.card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Txt(name, Type.h2.copy(fontSize = 22.sp))
            Txt(level, Type.sub, c.ink3)
        }
        Txt(blurb, Type.body.copy(fontSize = 14.sp), c.ink2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach {
                Box(Modifier.clip(Radius.pill).background(c.page).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Txt(it, Type.sub, c.ink2)
                }
            }
        }
        Txt(rule, Type.sub, c.accent)
    }
}

/** A read-only field that opens the date picker. */
@Composable
fun DateField(date: LocalDate, onClick: () -> Unit) {
    val c = LocalArise.current
    Box(
        Modifier.fillMaxWidth().clip(Radius.field).background(c.card2).border(1.dp, c.line, Radius.field)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) { Txt(fmtFull(date)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Txt("OK", color = LocalArise.current.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Txt("Cancel", color = LocalArise.current.ink2) } },
    ) { DatePicker(state) }
}
