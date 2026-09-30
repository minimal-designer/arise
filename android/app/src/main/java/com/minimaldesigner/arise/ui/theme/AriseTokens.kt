package com.minimaldesigner.arise.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The CSS custom properties from arise-v2/index.html (0.2 Nothing style), one field per `--token`. */
@Immutable
data class AriseColors(
    val page: Color,
    val card: Color,
    val card2: Color,
    val panel: Color,
    val panelInk: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    /** The fainter rule between rows of a list or grid. */
    val line2: Color,
    /** Dot textures, empty dots and the ring's dotted track. */
    val dot: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val chip: Color,
    val chipInk: Color,
    val nav: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightColors = AriseColors(
    page = Color(0xFFF4F4F2),
    card = Color(0xFFFFFFFF),
    card2 = Color(0xFFF7F7F5),
    panel = Color(0xFFFFFFFF),
    panelInk = Color(0xFF121212),
    ink = Color(0xFF121212),
    ink2 = Color(0xFF4F4F52),
    ink3 = Color(0xFF6E6E73),
    line = Color(0xFFDEDED9),
    line2 = Color(0xFFEBEBE6),
    dot = Color(0xFFD4D4CF),
    accent = Color(0xFFE8561C),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFBE3D8),
    good = Color(0xFF1F8F5A),
    warn = Color(0xFFB36A00),
    bad = Color(0xFFD23C3C),
    chip = Color(0xFF121212),
    chipInk = Color(0xFFFFFFFF),
    nav = Color(0xFFFFFFFF),
    scrim = Color(0x6B121212), // rgba(18,18,18,.42)
    isDark = false,
)

val DarkColors = AriseColors(
    page = Color(0xFF000000),
    card = Color(0xFF0F0F10),
    card2 = Color(0xFF16161A),
    panel = Color(0xFF0B0D12),
    panelInk = Color(0xFFF2F2F2),
    ink = Color(0xFFF2F2F2),
    ink2 = Color(0xFFB4B4B8),
    ink3 = Color(0xFF8E8E93),
    line = Color(0xFF232326),
    line2 = Color(0xFF1A1A1D),
    dot = Color(0xFF2A2A2E),
    accent = Color(0xFFFF6A2B),
    accentInk = Color(0xFF160A04),
    accentSoft = Color(0xFF3A1F13),
    good = Color(0xFF4CC98B),
    warn = Color(0xFFE6A23C),
    bad = Color(0xFFEF6666),
    chip = Color(0xFFF2F2F2),
    chipInk = Color(0xFF000000),
    nav = Color(0xFF0F0F10),
    scrim = Color(0xA8000000), // rgba(0,0,0,.66)
    isDark = true,
)

/** One accent in light and dark: the colour, the ink on it, and its soft tint. */
data class AccentTone(val accent: Color, val ink: Color, val soft: Color)

/**
 * The accent colours (0.2.1, Appearance). Orange is ARISE's own and the default. Yellow is
 * the one bright enough to need dark ink in light mode too.
 */
enum class AccentPref(val label: String, val light: AccentTone, val dark: AccentTone) {
    ORANGE("Orange", AccentTone(Color(0xFFE8561C), Color(0xFFFFFFFF), Color(0xFFFBE3D8)), AccentTone(Color(0xFFFF6A2B), Color(0xFF160A04), Color(0xFF3A1F13))),
    RED("Red", AccentTone(Color(0xFFD71921), Color(0xFFFFFFFF), Color(0xFFF9DADB)), AccentTone(Color(0xFFFF3B3F), Color(0xFF1A0405), Color(0xFF3B1415))),
    PINK("Pink", AccentTone(Color(0xFFD63C7A), Color(0xFFFFFFFF), Color(0xFFF8DCE7)), AccentTone(Color(0xFFFF6FA8), Color(0xFF1F0510), Color(0xFF3D1626))),
    VIOLET("Violet", AccentTone(Color(0xFF6B4DE6), Color(0xFFFFFFFF), Color(0xFFE4DEFB)), AccentTone(Color(0xFF9B87FF), Color(0xFF0D0820), Color(0xFF241C45))),
    BLUE("Blue", AccentTone(Color(0xFF1F5FE0), Color(0xFFFFFFFF), Color(0xFFDCE6FB)), AccentTone(Color(0xFF4D8DFF), Color(0xFF030A1A), Color(0xFF14213D))),
    GREEN("Green", AccentTone(Color(0xFF16875A), Color(0xFFFFFFFF), Color(0xFFD5EEE3)), AccentTone(Color(0xFF3DDC97), Color(0xFF03140C), Color(0xFF11301F))),
    YELLOW("Yellow", AccentTone(Color(0xFFE0A800), Color(0xFF1A1400), Color(0xFFF8EDC6)), AccentTone(Color(0xFFFFD23F), Color(0xFF1A1400), Color(0xFF3A3110))),
    ;

    fun tone(dark: Boolean): AccentTone = if (dark) this.dark else light
}

/** [base] with [accent] swapped in. */
fun withAccent(base: AriseColors, accent: AccentPref): AriseColors =
    accent.tone(base.isDark).let { t -> base.copy(accent = t.accent, accentInk = t.ink, accentSoft = t.soft) }

val LocalArise = staticCompositionLocalOf { LightColors }

/** CSS `color-mix(in srgb, a p%, b)`. */
fun mix(a: Color, p: Float, b: Color): Color = Color(
    red = a.red * p + b.red * (1 - p),
    green = a.green * p + b.green * (1 - p),
    blue = a.blue * p + b.blue * (1 - p),
    alpha = a.alpha * p + b.alpha * (1 - p),
)
