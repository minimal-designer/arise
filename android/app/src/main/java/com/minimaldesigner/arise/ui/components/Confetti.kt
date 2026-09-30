package com.minimaldesigner.arise.ui.components

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import com.minimaldesigner.arise.ui.theme.LocalArise
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// 0.2.1: the small celebrations. A burst of dots and dashes from a point, drawn on one
// full-screen canvas over the app. No library: a few dozen particles at most.

enum class Burst(val count: Int, val speedDp: ClosedFloatingPointRange<Float>) {
    /** A ticked task. */
    SMALL(14, 220f..430f),
    /** One burst of the day-cleared run. */
    BIG(40, 320f..720f),
}

internal class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val dash: Boolean,
    var angle: Float,
    val spin: Float,
    val life: Float,
    var age: Float = 0f,
)

/** Where bursts go. Get it from [LocalConfetti]; [ConfettiHost] draws them. */
class Confetti {
    private val live = ArrayList<Particle>()
    internal var tick by mutableLongStateOf(0L)
    internal var wake by mutableIntStateOf(0)
    internal var density = 1f
    internal var palette: List<Color> = listOf(Color(0xFFE8561C))

    /** Off when the user turned Celebrations off, or the phone's animations are off. */
    var enabled = true

    /** Throws a burst from [origin], in root (screen) pixels. */
    fun burst(origin: Offset, size: Burst) {
        if (!enabled || origin == Offset.Unspecified) return
        repeat(size.count) {
            // Mostly upwards, fanned out to about 70° either side.
            val a = (-90f + Random.nextFloat() * 140f - 70f) * (Math.PI / 180).toFloat()
            val v = (size.speedDp.start + Random.nextFloat() * (size.speedDp.endInclusive - size.speedDp.start)) * density
            live += Particle(
                x = origin.x, y = origin.y,
                vx = cos(a) * v, vy = sin(a) * v,
                color = palette[Random.nextInt(palette.size)],
                dash = Random.nextFloat() < 0.45f,
                angle = Random.nextFloat() * 6.28f,
                spin = (Random.nextFloat() - 0.5f) * 14f,
                life = 0.7f + Random.nextFloat() * 0.25f,
            )
        }
        wake++
    }

    internal fun step(dt: Float) {
        val g = 1100f * density
        val iter = live.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.age += dt
            if (p.age >= p.life) { iter.remove(); continue }
            p.vy += g * dt
            val drag = 1f - 2.2f * dt
            p.vx *= drag; p.vy *= drag
            p.x += p.vx * dt; p.y += p.vy * dt
            p.angle += p.spin * dt
        }
    }

    internal val alive: Boolean get() = live.isNotEmpty()
    internal fun particles(): List<Particle> = live
}

val LocalConfetti = staticCompositionLocalOf<Confetti?> { null }

/** True unless the phone's animations are switched off (Developer options or accessibility). */
fun animationsOn(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/** The full-screen, touch-through canvas the bursts are drawn on. Runs only while something's flying. */
@Composable
fun ConfettiHost(confetti: Confetti) {
    val c = LocalArise.current
    confetti.density = LocalDensity.current.density
    confetti.palette = listOf(c.accent, c.accent, c.ink, c.ink3, c.accentSoft)
    LaunchedEffect(confetti.wake) {
        var last = withFrameNanos { it }
        while (confetti.alive) {
            val now = withFrameNanos { it }
            confetti.step(((now - last) / 1e9f).coerceAtMost(0.05f))
            last = now
            confetti.tick = now
        }
        confetti.tick = 0L
    }
    Canvas(Modifier.fillMaxSize()) {
        if (confetti.tick == 0L) return@Canvas
        val d = density
        confetti.particles().forEach { p ->
            val fade = 1f - (p.age / p.life).let { it * it }
            if (p.dash) {
                val dx = cos(p.angle) * 3.5f * d
                val dy = sin(p.angle) * 3.5f * d
                drawLine(p.color, Offset(p.x - dx, p.y - dy), Offset(p.x + dx, p.y + dy), 2.4f * d, StrokeCap.Round, alpha = fade)
            } else {
                drawCircle(p.color, 2.3f * d, Offset(p.x, p.y), alpha = fade)
            }
        }
    }
}

/** A task ticked: the firm "confirm" click where the phone has it (Android 11+). */
fun View.tickHaptic() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
}

/** A task unticked: a lighter tick. */
fun View.untickHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}
