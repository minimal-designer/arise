package com.minimaldesigner.arise.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.minimaldesigner.arise.core.Angle
import com.minimaldesigner.arise.core.FoodLog
import com.minimaldesigner.arise.core.PhotoSource
import com.minimaldesigner.arise.core.Run
import com.minimaldesigner.arise.core.PhotoPins
import com.minimaldesigner.arise.core.photoPair
import com.minimaldesigner.arise.core.Attempt
import com.minimaldesigner.arise.core.PhotoWeek
import com.minimaldesigner.arise.core.daysBetween
import com.minimaldesigner.arise.core.photoDay
import com.minimaldesigner.arise.core.photoWeeks
import com.minimaldesigner.arise.core.HealthData
import com.minimaldesigner.arise.core.bodyWeights
import com.minimaldesigner.arise.core.weightDelta
import com.minimaldesigner.arise.data.Photo
import com.minimaldesigner.arise.ui.components.Btn
import com.minimaldesigner.arise.ui.components.BtnKind
import com.minimaldesigner.arise.ui.components.Card
import com.minimaldesigner.arise.ui.components.CardHeader
import com.minimaldesigner.arise.ui.components.EmptyBox
import com.minimaldesigner.arise.ui.components.ErrorLine
import com.minimaldesigner.arise.ui.components.FilterChip
import com.minimaldesigner.arise.ui.components.Labeled
import com.minimaldesigner.arise.ui.components.PageHeader
import com.minimaldesigner.arise.ui.components.Segmented
import com.minimaldesigner.arise.ui.components.SheetActions
import com.minimaldesigner.arise.ui.components.Txt
import com.minimaldesigner.arise.ui.fmtShort
import com.minimaldesigner.arise.ui.fmtSpan
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.Radius
import com.minimaldesigner.arise.ui.theme.Type
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.minimaldesigner.arise.ui.components.Lbl
import com.minimaldesigner.arise.ui.components.Num
import com.minimaldesigner.arise.ui.components.LinkButton
import com.minimaldesigner.arise.ui.components.Glyph
import com.minimaldesigner.arise.ui.components.GlyphIcon
import androidx.compose.foundation.layout.size
import java.io.File
import java.time.LocalDate

/** `#v-photos`: before/after (pinnable), angle filter, photos newest first, by the week of their own attempt. */
@Composable
fun Photos(
    run: Run,
    attempts: List<Attempt>,
    photos: List<Photo>,
    food: FoodLog?,
    health: HealthData?,
    pins: PhotoPins,
    onAdd: () -> Unit,
    onOpen: (Photo) -> Unit,
    onPick: (before: Boolean) -> Unit,
) {
    val c = LocalArise.current
    var angle by rememberSaveable { mutableStateOf<Angle?>(null) }
    val byId = remember(photos) { photos.associateBy { it.meta.id } }
    val metas = remember(photos) { photos.map { it.meta } }
    val pair = remember(metas, run, pins) { photoPair(metas, run, pins) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader("Photos") { Btn("Add photo", onAdd, kind = BtnKind.Accent, small = true) }

        Card {
            val a = pair.before?.let { byId[it.id] }
            val b = pair.after?.let { byId[it.id] }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Lbl("Before · After")
                if (a != null && b != null) Lbl("${a.meta.angle.key} · drag")
            }
            if (a != null && b != null) {
                Compare(a, b, photoDayText(attempts, a.meta.date), photoDayText(attempts, b.meta.date))
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickRow(true, a, pair.pinnedBefore) { onPick(true) }
                    PickRow(false, b, pair.pinnedAfter) { onPick(false) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Fact("Days apart", "${daysBetween(a.meta.date, b.meta.date)}", Modifier.weight(1f))
                    // Health Connect weights, else same-scale weigh-ins from the food log (v2 weightOn).
                    val kg = weightDelta(bodyWeights(health, food).first, a.meta.date, b.meta.date)
                    Fact("Weight", kg?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "–", Modifier.weight(1f))
                    Fact("Photos", "${photos.size}", Modifier.weight(1f))
                }
                Txt(
                    "Your before pick stays until you change it, even after a restart. Restarting or ending a challenge never deletes photos.",
                    Type.small, c.ink3, Modifier.padding(top = 12.dp),
                )
            } else {
                EmptyBox(if (photos.isNotEmpty()) "Add a second photo from the same angle to compare." else "Your first photo becomes the “before”.")
                if (a != null) Box(Modifier.padding(top = 12.dp)) { PickRow(true, a, pair.pinnedBefore) { onPick(true) } }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Angle?>(null, Angle.FRONT, Angle.SIDE, Angle.BACK).forEach { x ->
                FilterChip(x?.label ?: "All", angle == x, { angle = x }, inkOn = c.ink)
            }
        }

        val groups = remember(metas, attempts, angle) { photoWeeks(attempts, metas, angle) }
        if (groups.isEmpty()) EmptyBox("No photos yet.")
        groups.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WeekHeader(g)
                g.photos.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            val p = byId.getValue(m.id)
                            val flag = when (m.id) { pair.before?.id -> "BEFORE"; pair.after?.id -> "AFTER"; else -> null }
                            Tile(p, photoDayText(attempts, m.date), Modifier.weight(1f), flag) { onOpen(p) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** `.pickrow`: the before or after photo, whether it's pinned, and Change. */
@Composable
private fun PickRow(before: Boolean, p: Photo, pinned: Boolean, onClick: () -> Unit) {
    val c = LocalArise.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card2).border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(p.thumb, null, Modifier.width(40.dp).aspectRatio(40f / 52f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Lbl("${if (before) "Before" else "After"} · ${if (pinned) "pinned" else if (before) "first of this attempt" else "latest"}", size = 10f)
            Txt("${fmtShort(p.meta.date)} · ${p.meta.angle.key}" + if (!pinned && !before) " · follows new photos" else "", Type.body.copy(fontSize = 14.5.sp), maxLines = 1)
        }
        Txt("Change", Type.small, c.accent)
    }
}

/** A week's heading: "Week 3", "75 Hard · attempt 1 · Week 2" or "No challenge", and its dates. */
@Composable
private fun WeekHeader(g: PhotoWeek) {
    val c = LocalArise.current
    val a = g.attempt
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Lbl(
            when {
                a == null -> "No challenge"
                a.end == null -> "Week ${g.week}"
                else -> "${a.label} · Week ${g.week}"
            },
            c.ink2, Modifier.weight(1f),
        )
        Lbl(fmtSpan(g.from, g.to), size = 10f)
    }
}

/** "Day 9" in the photo's own attempt; "Before" or "Between" for one outside every attempt. */
fun photoDayText(attempts: List<Attempt>, date: LocalDate): String =
    photoDay(attempts, date)?.let { "Day $it" } ?: if (attempts.any { !it.start.isAfter(date) }) "Between" else "Before"

/** The before/after chooser: photos of one angle, grouped like the Photos tab. */
@Composable
fun PickPhotoSheetBody(attempts: List<Attempt>, photos: List<Photo>, before: Boolean, current: String?, onPin: (String?) -> Unit, onCancel: () -> Unit) {
    val c = LocalArise.current
    val metas = remember(photos) { photos.map { it.meta } }
    val byId = remember(photos) { photos.associateBy { it.meta.id } }
    var angle by remember { mutableStateOf(metas.firstOrNull { it.id == current }?.angle ?: Angle.FRONT) }
    var sel by remember { mutableStateOf(current) }
    Txt(if (before) "Choose the before photo" else "Choose the after photo", Type.sheetTitle)
    Txt(
        if (before) "It stays pinned until you change it, even after a restart." else "Pin one, or let it follow your newest photo.",
        Type.body.copy(fontSize = 14.5.sp), c.ink2,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Angle.entries.forEach { a -> FilterChip(a.label, angle == a, { angle = a }, inkOn = c.ink) }
    }
    val groups = remember(metas, attempts, angle) { photoWeeks(attempts, metas, angle) }
    if (groups.isEmpty()) EmptyBox("No ${angle.key} photos yet.")
    groups.forEach { g ->
        WeekHeader(g)
        g.photos.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    val p = byId.getValue(m.id)
                    val on = m.id == sel
                    Box(
                        Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp))
                            .border(if (on) 2.dp else 0.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton) { sel = m.id },
                    ) {
                        AsyncImage(p.thumb, fmtShort(m.date), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (on) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.Check, c.accentInk, 11.dp)
                        }
                        Txt(
                            fmtShort(m.date), Type.sub.copy(fontSize = 11.sp), Color.White,
                            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                                .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 5.dp),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    LinkButton(if (before) "Use the first photo of this attempt" else "Always use the latest photo", { onPin(null) }, c.ink2)
    SheetActions(
        { m -> Btn("Cancel", onCancel, m, BtnKind.Ghost) },
        { m -> Btn(if (before) "Pin as before" else "Pin as after", { sel?.let(onPin) }, m, BtnKind.Accent, enabled = sel != null) },
    )
}

/** `.compare`: before underneath, after clipped from the divider rightwards. */
@Composable
private fun Compare(before: Photo, after: Photo, dayA: String, dayB: String) {
    val c = LocalArise.current
    var cut by remember { mutableFloatStateOf(0.5f) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(Radius.row)
            .background(c.panel)
            .pointerInput(Unit) {
                detectTapGestures { cut = (it.x / size.width).coerceIn(0f, 1f) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> cut = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        AsyncImage(before.file, dayA, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        AsyncImage(
            after.file, dayB,
            Modifier.fillMaxSize().drawWithContent {
                clipRect(left = size.width * cut) { this@drawWithContent.drawContent() }
            },
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxHeight().width(2.dp).offset(x = maxWidth * cut - 1.dp).background(Color.White),
        )
        Label(dayA, Modifier.align(Alignment.TopStart))
        Label(dayB, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun Label(text: String, modifier: Modifier) {
    Box(modifier.padding(10.dp).clip(Radius.pill).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Txt(text, Type.sub, Color(0xFF121212))
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Lbl(label, size = 10f)
        Num(value, 22f)
    }
}

/** `.ph`: a 3:4 thumbnail with a gradient caption. */
@Composable
private fun Tile(p: Photo, day: String, modifier: Modifier, flag: String?, onClick: () -> Unit) {
    val c = LocalArise.current
    val ring = when (flag) { "BEFORE" -> c.ink; "AFTER" -> c.accent; else -> Color.Transparent }
    Box(
        modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)).background(c.card2)
            .border(if (flag != null) 2.dp else 0.dp, ring, RoundedCornerShape(14.dp))
            .clickable(role = Role.Image, onClick = onClick),
    ) {
        AsyncImage(p.thumb, "$day ${p.meta.angle.key}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (flag != null) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).clip(Radius.pill).background(if (flag == "AFTER") c.accent else Color(0xFFF2F2F2))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) { Txt(flag, Type.label.copy(fontSize = 9.5.sp), if (flag == "AFTER") c.accentInk else Color.Black) }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 7.dp),
        ) {
            Txt(day, Type.body.copy(fontSize = 13.sp), Color.White, weight = FontWeight.Medium)
            Txt("${fmtShort(p.meta.date)} · ${p.meta.angle.key}", Type.sub.copy(fontSize = 11.5.sp), Color.White)
        }
    }
}

/** `.lb`: the photo large, its caption, Delete (two taps) and Close. */
@Composable
fun PhotoViewerBody(p: Photo, day: String, pinnedBefore: Boolean, onAngle: (Angle) -> Unit, onPinBefore: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val c = LocalArise.current
    var armed by remember(p.meta.id) { mutableStateOf(false) }
    AsyncImage(
        p.file, "$day ${p.meta.angle.key}",
        Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(Radius.row).background(c.page),
        contentScale = ContentScale.Fit,
    )
    Txt("$day · ${fmtShort(p.meta.date)}", Type.body.copy(fontSize = 14.5.sp), c.ink2)
    // Imported photos come in as front; fix the angle here.
    Segmented(Angle.entries.map { it to it.label }, p.meta.angle, onAngle)
    Btn(if (pinnedBefore) "Pinned as before" else "Pin as before", onPinBefore, kind = BtnKind.Ghost, small = true, enabled = !pinnedBefore)
    if (armed) ErrorLine("This deletes the photo from ARISE. It can't be undone.", c.ink2)
    SheetActions(
        { m -> Btn(if (armed) "Tap again to delete" else "Delete", { if (armed) onDelete() else armed = true }, m, BtnKind.Ghost) },
        { m -> Btn("Close", onClose, m) },
    )
}

/** `#up-form`: take or choose a photo, pick angle and date, save. */
@Composable
fun AddPhotoSheetBody(
    today: LocalDate,
    onCancel: () -> Unit,
    onSave: (uri: Uri, date: LocalDate, angle: Angle, source: PhotoSource, onDone: () -> Unit, onError: (String) -> Unit) -> Unit,
) {
    val c = LocalArise.current
    val context = LocalContext.current
    // Saveable: the camera app can push ARISE out of memory, and the photo must still land here after.
    var picked by rememberSaveable { mutableStateOf<Uri?>(null) }
    var source by rememberSaveable { mutableStateOf(PhotoSource.PICKER) }
    var angle by rememberSaveable { mutableStateOf(Angle.FRONT) }
    var date by rememberSaveable { mutableStateOf(today) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var pendingCapture by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) { picked = pendingCapture; source = PhotoSource.CAMERA; err = "" }
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { picked = uri; source = PhotoSource.PICKER; err = "" }
    }

    Txt("Add a photo", Type.sheetTitle)
    Txt("Same spot and light each time makes the before/after honest.", Type.body.copy(fontSize = 14.5.sp), c.ink2)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(20.dp)).background(c.page).padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val p = picked
        if (p != null) {
            AsyncImage(p, "Selected photo", Modifier.heightIn(max = 220.dp).clip(Radius.field), contentScale = ContentScale.Fit)
        } else {
            Txt("Take a photo or choose one", Type.body, c.ink2)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("Take photo", {
            val dir = File(context.cacheDir, "capture").apply { mkdirs() }
            val f = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            pendingCapture = uri
            takePicture.launch(uri)
        }, Modifier.weight(1f), small = true)
        Btn("Choose photo", {
            choose.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, Modifier.weight(1f), BtnKind.Ghost, small = true)
    }
    Labeled("Angle") {
        Segmented(Angle.entries.map { it to it.label }, angle) { angle = it }
    }
    Labeled("Date") { DateField(date) { picking = true } }
    if (err.isNotEmpty()) ErrorLine(err)
    SheetActions(
        { m -> Btn("Cancel", onCancel, m, BtnKind.Ghost) },
        { m ->
            Btn(if (busy) "Saving…" else "Save", {
                val p = picked ?: return@Btn
                busy = true; err = ""
                onSave(p, date, angle, source, { busy = false }, { e -> busy = false; err = e })
            }, m, BtnKind.Accent, enabled = picked != null && !busy)
        },
    )
    if (picking) DayPicker(date, onPick = { date = it; picking = false }, onDismiss = { picking = false })
}
