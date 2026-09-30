package com.minimaldesigner.arise.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.minimaldesigner.arise.R

val Geist = FontFamily(
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

/** Dot-matrix numbers (0.2). A static ExtraBold instance of the variable Doto font. */
val Doto = FontFamily(Font(R.font.doto_extrabold, FontWeight.ExtraBold))

/** Dot-matrix or mono numbers (Profile & settings → Appearance). */
enum class NumStyle { DOT, MONO }

val LocalNumStyle = staticCompositionLocalOf { NumStyle.DOT }

/** Radii from the v2 stylesheet. */
object Radius {
    val card = RoundedCornerShape(22.dp)
    val panel = RoundedCornerShape(26.dp)
    val row = RoundedCornerShape(18.dp)   // .task, .meal, .compare
    val field = RoundedCornerShape(14.dp) // inputs, avatar
    val date = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** Type scale from the v2 stylesheet (body 15px / 1.45). */
object Type {
    val body = TextStyle(fontFamily = Geist, fontSize = 15.sp, lineHeight = 21.75.sp)
    val sub = TextStyle(fontFamily = Geist, fontSize = 12.5.sp, lineHeight = 16.25.sp)
    val small = TextStyle(fontFamily = Geist, fontSize = 13.sp, lineHeight = 18.sp)
    val h1 = TextStyle(fontFamily = Geist, fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-0.02).em)
    val page = TextStyle(fontFamily = Geist, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em)
    val h2 = TextStyle(fontFamily = Geist, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em)
    val sheetTitle = TextStyle(fontFamily = Geist, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.02).em)
    val big = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 33.sp, letterSpacing = (-0.03).em)
    val huge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Light, fontSize = 46.sp, lineHeight = 46.sp, letterSpacing = (-0.04).em)
    val kv = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Light, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em)
    val mono = TextStyle(fontFamily = GeistMono, fontSize = 13.sp, lineHeight = 18.sp)
    /** `.lbl`: the small mono uppercase label (pass text already uppercased via Lbl). */
    val label = TextStyle(fontFamily = GeistMono, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em)

    /** `.n`: a number in Doto or Geist Mono, per [LocalNumStyle]. */
    fun num(style: NumStyle, size: TextUnit): TextStyle = when (style) {
        NumStyle.DOT -> TextStyle(fontFamily = Doto, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size)
        NumStyle.MONO -> TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Normal, fontSize = size, lineHeight = size)
    }
}

enum class ThemePref { SYSTEM, LIGHT, DARK }

@Composable
fun AriseTheme(dark: Boolean, numbers: NumStyle = NumStyle.DOT, accent: AccentPref = AccentPref.ORANGE, content: @Composable () -> Unit) {
    val c = withAccent(if (dark) DarkColors else LightColors, accent)
    // M3 is only the base; every component reads LocalArise. The scheme is mapped so any
    // stock M3 piece (date picker, text selection) still looks like ARISE.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.accentInk, primaryContainer = c.accentSoft,
            secondary = c.panelInk, background = c.page, onBackground = c.ink, surface = c.card,
            onSurface = c.ink, onSurfaceVariant = c.ink2, surfaceContainerHigh = c.card,
            surfaceContainerHighest = c.card2, outline = c.line, outlineVariant = c.line, error = c.bad,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.accentInk, primaryContainer = c.accentSoft,
            secondary = c.panelInk, background = c.page, onBackground = c.ink, surface = c.card,
            onSurface = c.ink, onSurfaceVariant = c.ink2, surfaceContainerHigh = c.card,
            surfaceContainerHighest = c.card2, outline = c.line, outlineVariant = c.line, error = c.bad,
        )
    }
    CompositionLocalProvider(LocalArise provides c, LocalNumStyle provides numbers) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
