package com.minimaldesigner.arise.ui.components

import androidx.activity.compose.BackHandler
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaldesigner.arise.core.PillSpan
import com.minimaldesigner.arise.core.navWeights
import com.minimaldesigner.arise.core.pillSpan
import com.minimaldesigner.arise.core.spaceAround
import com.minimaldesigner.arise.ui.theme.LocalArise
import com.minimaldesigner.arise.ui.theme.mix
import com.minimaldesigner.arise.ui.theme.Radius
import com.minimaldesigner.arise.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/** `.scrim` + `.sheet`. [onDismiss] = null makes it modal (the missed-day sheet). */
@Composable
fun BoxScope.Sheet(visible: Boolean, onDismiss: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalArise.current
    BackHandler(enabled = visible && onDismiss != null) { onDismiss?.invoke() }
    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
        Box(
            Modifier.fillMaxSize().background(c.scrim).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { onDismiss?.invoke() },
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(220)) { it / 4 } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(160)) { it / 4 } + fadeOut(tween(160)),
    ) {
        Column(
            Modifier
                // A tall sheet (the custom task picker) stops below the status bar and scrolls.
                .statusBarsPadding()
                .padding(top = 24.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(Radius.sheet)
                .background(c.card)
                .border(1.dp, c.line, Radius.sheet)
                .consumeTaps()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(Radius.pill).background(c.line))
            content()
        }
    }
}

/** Two equal buttons at the foot of a sheet. */
@Composable
fun SheetActions(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

/** `#toast`: dark pill above the nav, 2.4 s. */
@Composable
fun BoxScope.Toasts(messages: Flow<String>) {
    val c = LocalArise.current
    var text by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf(false) }
    var stamp by remember { mutableStateOf(0) }
    LaunchedEffect(messages) {
        messages.collect { m -> text = m; shown = true; stamp++ }
    }
    LaunchedEffect(stamp) {
        if (shown) {
            delay(2400); shown = false
        }
    }
    AnimatedVisibility(
        shown,
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 92.dp, start = 16.dp, end = 16.dp),
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
    ) {
        Box(
            Modifier.clip(Radius.pill).background(c.chip).padding(horizontal = 18.dp, vertical = 11.dp),
        ) { Txt(text.orEmpty(), Type.body.copy(fontSize = 14.sp), c.chipInk) }
    }
}

enum class Tab(val label: String, val glyph: Glyph) {
    Home("Home", Glyph.NavHome), Week("Week", Glyph.NavWeek), Food("Food", Glyph.NavFood), Photos("Photos", Glyph.NavPhotos)
}

/** A nav slide in progress: what was on screen when it started, and the tab it's heading to. */
private class NavSlide(to: Int, n: Int) {
    var to by mutableIntStateOf(to)
    var startWeights: List<Float> = List(n) { if (it == to) 1f else 0f }
    var startPill: PillSpan? = null
    // The last frame laid out, so a tap mid-slide carries on from there.
    var weights: List<Float> = startWeights
    var pill: PillSpan? = null
}

/**
 * `.nav`: floating pill with a rule. One filled pill sits under the current tab, which shows
 * its label. On a switch the pill slides and stretches across to the new tab while the old
 * label folds into its icon and the new one unfolds, all on one spring; the icon pops and the
 * phone ticks. The tabs and the pill are laid out together, so they never drift apart.
 */
@Composable
fun BoxScope.FloatingNav(current: Tab, tabs: List<Tab>, onSelect: (Tab) -> Unit) {
    val c = LocalArise.current
    val view = LocalView.current
    val at = tabs.indexOf(current).coerceAtLeast(0)
    // A new set of tabs (Food switched on or off) starts a fresh slide.
    val slide = remember(tabs) { NavSlide(at, tabs.size) }
    val f = remember { Animatable(1f) }
    LaunchedEffect(current, tabs) {
        if (at != slide.to) {
            slide.startWeights = slide.weights
            slide.startPill = slide.pill
            slide.to = at
            f.snapTo(0f)
            f.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val weights = navWeights(slide.startWeights, slide.to, f.value)
    slide.weights = weights

    Layout(
        content = {
            Box(Modifier.clip(Radius.pill).background(c.chip))
            tabs.forEachIndexed { i, t ->
                val w = weights[i]
                val pop = remember { Animatable(1f) }
                LaunchedEffect(t == current) {
                    if (t == current) {
                        pop.snapTo(0.82f)
                        pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
                    }
                }
                val ink = mix(c.chipInk, w, c.ink2)
                Row(
                    Modifier
                        .clip(Radius.pill)
                        .clickable(role = Role.Tab) {
                            if (t != current) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(t)
                        }
                        // As wide as the icon, plus [w] of the label: the label unfolds rather than popping in.
                        .layout { m, _ ->
                            val p = m.measure(Constraints())
                            val collapsed = NAV_ICON_TAB.roundToPx()
                            val width = (collapsed + (p.width - collapsed) * w).roundToInt().coerceIn(collapsed, maxOf(collapsed, p.width))
                            layout(width, p.height) { p.placeRelative(0, 0) }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(t.glyph, ink, 22.dp, cutColor = mix(c.chip, w, c.nav), modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                    Txt(t.label, Type.body.copy(fontSize = 14.5.sp), ink, Modifier.padding(start = 8.dp).alpha(w), weight = FontWeight.Medium, maxLines = 1)
                }
            }
        },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            .widthIn(max = 448.dp)
            .fillMaxWidth()
            .clip(Radius.pill)
            .background(c.nav)
            .border(1.dp, c.line, Radius.pill)
            .padding(8.dp),
    ) { measurables, constraints ->
        val placed = measurables.drop(1).map { it.measure(Constraints()) }
        val total = constraints.maxWidth
        val h = placed.maxOf { it.height }
        val widths = placed.map { it.width.toFloat() }
        val xs = spaceAround(widths, total.toFloat())
        val span = pillSpan(slide.startPill, xs, widths, slide.to, f.value)
        slide.pill = span
        val pill = measurables[0].measure(Constraints.fixed(span.width.roundToInt().coerceAtLeast(0), h))
        layout(total, h) {
            pill.place(span.x.roundToInt(), 0)
            placed.forEachIndexed { i, p -> p.place(xs[i].roundToInt(), (h - p.height) / 2) }
        }
    }
}

/** A tab with its label folded: 16 + 22 + 16. */
private val NAV_ICON_TAB = 54.dp

/** `.page-h` / `.hello` spacer at the top of every tab. */
@Composable
fun PageHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Txt(title, Type.page.copy(fontWeight = FontWeight.Light))
        trailing?.invoke()
    }
}

/** Space under the content so the floating nav never covers it (v2 pads 128px). */
@Composable
fun NavClearance() = Box(Modifier.height(128.dp))
