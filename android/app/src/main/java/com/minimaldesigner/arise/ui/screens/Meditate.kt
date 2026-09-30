package com.minimaldesigner.arise.ui.screens

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.minimaldesigner.arise.core.HealthMindful
import com.minimaldesigner.arise.core.TaskDef
import com.minimaldesigner.arise.core.labelMinutes
import com.minimaldesigner.arise.ui.components.Btn
import com.minimaldesigner.arise.ui.components.BtnKind
import com.minimaldesigner.arise.ui.components.Card
import com.minimaldesigner.arise.ui.components.ErrorLine
import com.minimaldesigner.arise.ui.components.Field
import com.minimaldesigner.arise.ui.components.Glyph
import com.minimaldesigner.arise.ui.components.GlyphIcon
import com.minimaldesigner.arise.ui.components.Labeled
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.Num
import com.minimaldesigner.arise.ui.components.PickChip
import com.minimaldesigner.arise.ui.components.SheetActions
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.fmtClock
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** An app the phone can open, for the Meditate task's app picker. */
data class LaunchApp(val pkg: String, val label: String, val icon: ImageBitmap?)

private val MINDFUL_WORDS = listOf("medit", "mindful", "calm", "headspace", "insight", "medito", "breath", "zen", "waking up", "balance", "sleep")

/** Every app with a launcher icon, meditation-looking ones first, then A to Z. */
suspend fun launchableApps(context: Context): List<LaunchApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { ri ->
            val icon = runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            LaunchApp(ri.activityInfo.packageName, ri.loadLabel(pm).toString(), icon)
        }
        .sortedWith(compareByDescending<LaunchApp> { a -> MINDFUL_WORDS.any { a.label.lowercase().contains(it) } }.thenBy { it.label.lowercase() })
}

/** An installed app's name, or null once it's gone. */
fun appLabel(context: Context, pkg: String): String? = if (pkg.isEmpty()) null else runCatching {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrNull()

/**
 * Meditate: open the meditation app, then come back and tap Done. Shows what Health Connect
 * logged today, or how long ARISE was left for the app, and has a plain timer for when no app is set.
 */
@Composable
fun MeditateSheetBody(
    task: TaskDef,
    app: String?,
    sessions: List<HealthMindful>,
    awayMins: Int?,
    onOpenApp: () -> Unit,
    onChooseApp: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val c = LocalArise.current
    val view = LocalView.current
    val target = labelMinutes(task) ?: 10
    var endAt by rememberSaveable(task.id) { mutableStateOf<Long?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (endAt != null) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val left = endAt?.let { ((it - now + 999) / 1000).coerceAtLeast(0) } ?: (target * 60L)
    val timeUp = endAt != null && left == 0L
    LaunchedEffect(timeUp) { if (timeUp) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }

    Txt(task.label, Type.sheetTitle)
    Txt(
        if (app != null) "Open $app, meditate, then come back here and tap Done." else "Choose the app you meditate with, or use the timer below.",
        Type.body.copy(fontSize = 14.5.sp), c.ink2,
    )
    if (sessions.isNotEmpty()) {
        Labeled("From Health Connect today") {
            Chips {
                sessions.forEach { s -> PickChip("${s.title ?: "Session"} · ${s.mins} min · ${fmtClock(s.start.toLocalTime())}", true) {} }
            }
        }
    } else if (awayMins != null && awayMins > 0) {
        Txt("You were away $awayMins min.", Type.body, c.accent)
    }
    if (app != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("Open $app", onOpenApp, Modifier.weight(1f), BtnKind.Accent)
            Btn("Change", onChooseApp, kind = BtnKind.Ghost, small = true)
        }
    } else {
        Btn("Choose your meditation app", onChooseApp, Modifier.fillMaxWidth())
    }
    Card(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lbl(if (timeUp) "Time's up" else "Timer", if (timeUp) c.accent else c.ink3, size = 10f)
                Num("${left / 60}:${(left % 60).toString().padStart(2, '0')}", 30f, if (timeUp) c.accent else c.ink)
            }
            Btn(
                when {
                    endAt == null -> "Start"
                    timeUp -> "Reset"
                    else -> "Stop"
                },
                { endAt = if (endAt == null) System.currentTimeMillis() + target * 60_000L else null },
                kind = BtnKind.Ghost, small = true,
            )
        }
    }
    val known = sessions.sumOf { it.mins }.takeIf { it > 0 } ?: awayMins
    if (known != null && known in 1 until target && !timeUp) ErrorLine("That's under $target minutes.", c.warn)
    SheetActions(
        { m -> Btn("Cancel", onCancel, m, BtnKind.Ghost) },
        { m -> Btn("Done", onDone, m, BtnKind.Accent) },
    )
}

/** Choose the app the Meditate task opens. */
@Composable
fun AppPickerSheetBody(current: String, onPick: (pkg: String, label: String) -> Unit, onCancel: () -> Unit) {
    val c = LocalArise.current
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchApp>?>(null) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = launchableApps(context) }

    Txt("Your meditation app", Type.sheetTitle)
    Txt("Meditate opens it. Apps that look like meditation apps are at the top.", Type.body.copy(fontSize = 14.5.sp), c.ink2)
    Field(q, { q = it }, placeholder = "Search apps", maxLength = 40)
    val list = apps
    if (list == null) {
        Txt("Loading your apps…", Type.small, c.ink3)
    } else {
        val shown = list.filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }.take(80)
        if (shown.isEmpty()) Txt("No app called that.", Type.small, c.ink3)
        Column {
            shown.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { onPick(a.pkg, a.label) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val icon = a.icon
                    if (icon != null) Image(icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                    else Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.card2))
                    Txt(a.label, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
                    if (a.pkg == current) GlyphIcon(Glyph.Check, c.accent, 16.dp)
                }
            }
        }
    }
    Btn("Cancel", onCancel, Modifier.fillMaxWidth(), BtnKind.Ghost)
}
