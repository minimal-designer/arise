package com.minimaldesigner.arise.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.LocalNumStyle
import com.minimaldesigner.arise.ui.theme.Radius
import com.minimaldesigner.arise.ui.theme.Type
import androidx.compose.material3.Text as M3Text

/** Plain text in the ARISE type scale and ink colour. */
@Composable
fun Txt(
    text: String,
    style: TextStyle = Type.body,
    color: Color = LocalArise.current.ink,
    modifier: Modifier = Modifier,
    weight: FontWeight? = null,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    M3Text(
        text = text, style = style, color = color, modifier = modifier, fontWeight = weight,
        textAlign = align, maxLines = maxLines,
    )
}

/** `.lbl`: small mono uppercase label. */
@Composable
fun Lbl(text: String, color: Color = LocalArise.current.ink3, modifier: Modifier = Modifier, size: Float = 11f) {
    Txt(text.uppercase(), Type.label.copy(fontSize = size.sp), color, modifier, maxLines = 2)
}

/** `.n`: a number in dot-matrix or mono, per the Appearance setting. */
@Composable
fun Num(text: String, size: Float, color: Color = LocalArise.current.ink, modifier: Modifier = Modifier) {
    Txt(text, Type.num(LocalNumStyle.current, size.sp), color, modifier, maxLines = 1)
}

/** `.dotbg`: a dot grid, drawn under the content. */
fun Modifier.dots(color: Color, step: Dp = 9.dp, radius: Dp = 1.dp): Modifier = drawBehind {
    val s = step.toPx()
    val r = radius.toPx()
    var y = s / 2
    while (y < size.height) {
        var x = s / 2
        while (x < size.width) { drawCircle(color, r, Offset(x, y)); x += s }
        y += s
    }
}

/** `.card`: 22dp, card colour, a 1dp rule, 16dp padding. Clickable when [onClick] is set; [dotted] adds the dot grid. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    dotted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalArise.current
    Column(
        modifier = modifier
            .clip(Radius.card)
            .background(c.card)
            .then(if (dotted) Modifier.dots(c.dot) else Modifier)
            .border(1.dp, c.line, Radius.card)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** `.panel`: the 26dp section with panel ink and a rule. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalArise.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.panel)
            .background(c.panel)
            .border(1.dp, c.line, Radius.panel)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** `.card-h`: title on the left, chevron (or [trailing]) on the right. */
@Composable
fun CardHeader(title: String, modifier: Modifier = Modifier, titleStyle: TextStyle = Type.body, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalArise.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Txt(title, titleStyle)
        if (trailing != null) trailing() else GlyphIcon(Glyph.Chev, c.ink, 16.dp)
    }
}

/** `.chip`: a thin outline, filled with --chip when pressed. */
@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, inkOn: Color = LocalArise.current.panelInk) {
    val c = LocalArise.current
    val bg by animateColorAsState(if (selected) c.chip else Color.Transparent, label = "chip")
    Box(
        Modifier
            .clip(Radius.pill)
            .background(bg)
            .border(1.dp, if (selected) c.chip else c.line, Radius.pill)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Txt(label, Type.body.copy(fontSize = 13.5.sp), if (selected) c.chipInk else inkOn) }
}

/** `.pchip`: the picker chip in forms and sheets. */
@Composable
fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalArise.current
    Box(
        Modifier
            .clip(Radius.pill)
            .background(if (selected) c.chip else c.card2)
            .border(1.dp, if (selected) c.chip else c.line, Radius.pill)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Txt(label, Type.body.copy(fontSize = 13.5.sp), if (selected) c.chipInk else c.ink2) }
}

enum class BtnKind { Chip, Accent, Ghost, Danger }

/** `.btn`: pill button. */
@Composable
fun Btn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Chip,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val c = LocalArise.current
    val (bg, fg) = when (kind) {
        BtnKind.Chip -> c.chip to c.chipInk
        BtnKind.Accent -> c.accent to c.accentInk
        BtnKind.Ghost -> Color.Transparent to c.ink
        BtnKind.Danger -> c.bad to Color(0xFF1A0505)
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(Radius.pill)
            .background(bg)
            .then(if (kind == BtnKind.Ghost) Modifier.border(1.dp, c.line, Radius.pill) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 22.dp, vertical = if (small) 9.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label, Type.body.copy(fontSize = if (small) 14.sp else 15.sp), fg, weight = FontWeight.Medium) }
}

/** `.link`: underlined text button. */
@Composable
fun LinkButton(label: String, onClick: () -> Unit, color: Color = LocalArise.current.ink, modifier: Modifier = Modifier) {
    Txt(
        label, Type.body.copy(fontSize = 13.5.sp, textDecoration = TextDecoration.Underline), color,
        modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 2.dp),
    )
}

/** `.field input`: 14dp, card-2, line border that turns accent on focus. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    onDone: (() -> Unit)? = null,
    maxLength: Int = 60,
    mask: Boolean = false,
) {
    val c = LocalArise.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        singleLine = true,
        textStyle = Type.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = keyboard,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.field)
                    .background(c.card2)
                    .border(1.dp, if (focused) c.accent else c.line, Radius.field)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Txt(placeholder, Type.body, c.ink3)
                inner()
            }
        },
    )
}

/** `.field` label above a control. */
@Composable
fun Labeled(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, Type.small, LocalArise.current.ink2)
        content()
    }
}

/** `.switch`: 44x26 track, accent when on. */
@Composable
fun ToggleSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val c = LocalArise.current
    val x by animateDpAsState(if (checked) 21.dp else 3.dp, tween(150), label = "thumb")
    Row(
        Modifier.clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp, 26.dp).clip(Radius.pill).background(if (checked) c.accent else c.line)) {
            Box(
                Modifier.offset(x = x, y = 3.dp).size(20.dp)
                    .shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White),
            )
        }
        Txt(label, Type.body.copy(fontSize = 14.sp), c.ink2)
    }
}

/** `.seg`: segmented pill control. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = LocalArise.current
    Row(Modifier.clip(Radius.pill).border(1.dp, c.line, Radius.pill).padding(3.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier
                    .clip(Radius.pill)
                    .background(if (on) c.chip else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(v) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) { Txt(label, Type.body.copy(fontSize = 13.5.sp), if (on) c.chipInk else c.ink2) }
        }
    }
}

/** The small status line: coloured dot + label. */
@Composable
fun StatusDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Txt(label, Type.sub, color, weight = FontWeight.Medium)
    }
}

/** `.err` line. */
@Composable
fun ErrorLine(text: String, color: Color = LocalArise.current.bad) {
    Txt(text, Type.body.copy(fontSize = 13.5.sp), color, Modifier.fillMaxWidth())
}

/** `.empty`: dashed placeholder box. */
@Composable
fun EmptyBox(text: String) {
    val c = LocalArise.current
    Box(
        Modifier.fillMaxWidth().border(BorderStroke(1.5.dp, c.line), Radius.row).padding(horizontal = 12.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, Type.body.copy(fontSize = 14.sp), c.ink3, align = TextAlign.Center) }
}

/** Swallows taps so a click on a sheet doesn't reach the scrim behind it. */
fun Modifier.consumeTaps(): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(), indication = null, onClick = {},
)

/** A 7dp mini bar for the This week card. */
@Composable
fun MiniBar(fraction: Float?, height: Dp) {
    val c = LocalArise.current
    Box(
        Modifier.size(7.dp, height).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(if (fraction != null && fraction > 0f) c.accent else c.line),
    )
}

/** CSS `color-mix(in srgb, x p%, transparent)`: same hue, alpha scaled (premultiplied mixing). */
fun Color.fade(p: Float): Color = copy(alpha = alpha * p)
