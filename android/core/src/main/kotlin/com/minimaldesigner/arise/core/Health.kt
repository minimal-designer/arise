package com.minimaldesigner.arise.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

// 0.2: what ARISE reads from Health Connect. The app layer (data/Health.kt) turns Health
// Connect records into these, in the phone's time zone; everything here is plain Kotlin.

data class WeightPoint(val date: LocalDate, val kg: Double)

/** One exercise session. [type] is one of [Programs.workoutTypes]; [outdoor] is a guess from the activity. */
data class HealthWorkout(
    val id: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val type: String,
    val outdoor: Boolean,
    val title: String? = null,
) {
    val mins: Int get() = Duration.between(start, end).toMinutes().toInt()
}

/** One sleep session; [asleepMins] leaves out the awake stages when the tracker records them. */
data class HealthSleep(val start: LocalDateTime, val end: LocalDateTime, val asleepMins: Int)

/** One mindfulness session (0.2.1), from a meditation app that writes to Health Connect. */
data class HealthMindful(val start: LocalDateTime, val end: LocalDateTime, val title: String? = null) {
    val mins: Int get() = Duration.between(start, end).toMinutes().toInt()
}

data class HealthData(
    val heightCm: Double? = null,
    /** One per day (the first weigh-in of the day), oldest first. */
    val weights: List<WeightPoint> = emptyList(),
    val workouts: List<HealthWorkout> = emptyList(),
    val sleeps: List<HealthSleep> = emptyList(),
    val steps: Map<LocalDate, Long> = emptyMap(),
    val mindful: List<HealthMindful> = emptyList(),
)

// ---- body ----

/** Body mass index to one decimal, or null without both numbers. */
fun bmi(kg: Double?, heightCm: Double?): Double? {
    if (kg == null || heightCm == null || kg <= 0 || heightCm <= 0) return null
    val m = heightCm / 100
    return Math.round(kg / (m * m) * 10) / 10.0
}

enum class BmiBand(val label: String) { UNDER("Underweight"), HEALTHY("Healthy"), OVER("Overweight"), OBESE("Obese") }

/** The WHO adult bands. */
fun bmiBand(bmi: Double): BmiBand = when {
    bmi < 18.5 -> BmiBand.UNDER
    bmi < 25.0 -> BmiBand.HEALTHY
    bmi < 30.0 -> BmiBand.OVER
    else -> BmiBand.OBESE
}

enum class WeightSource { HEALTH, FOOD, NONE }

/** The food log's weigh-ins from the first weigh-in's scale, oldest first (see [weightOn]). */
fun foodWeights(log: FoodLog?): List<WeightPoint> {
    val ws = log?.weighIns.orEmpty()
    val scale = ws.firstOrNull()?.scale
    return ws.filter { it.scale == scale && it.kg != null }
        .mapNotNull { w -> runCatching { WeightPoint(LocalDate.parse(w.date), w.kg!!) }.getOrNull() }
        .sortedBy { it.date }
}

/** Health Connect weights when there are any, else the food log's. */
fun bodyWeights(health: HealthData?, food: FoodLog?): Pair<List<WeightPoint>, WeightSource> {
    val h = health?.weights.orEmpty()
    if (h.isNotEmpty()) return h to WeightSource.HEALTH
    val f = foodWeights(food)
    return f to (if (f.isEmpty()) WeightSource.NONE else WeightSource.FOOD)
}

/** The latest weight on or before [date]. */
fun weightAt(points: List<WeightPoint>, date: LocalDate): WeightPoint? = points.lastOrNull { !it.date.isAfter(date) }

/** Weight when the challenge started: the last one on or before Day 1, else the first after it. */
fun startWeight(points: List<WeightPoint>, start: LocalDate): WeightPoint? =
    weightAt(points, start) ?: points.firstOrNull()

fun weightDelta(points: List<WeightPoint>, from: LocalDate, to: LocalDate): Double? {
    val a = weightAt(points, from) ?: return null
    val b = weightAt(points, to) ?: return null
    return b.kg - a.kg
}

// ---- workouts ----

fun workoutsOn(list: List<HealthWorkout>, date: LocalDate): List<HealthWorkout> =
    list.filter { it.start.toLocalDate() == date }.sortedBy { it.start }

/** A session counts as logged when a ticked workout that day has its type and minutes. */
private fun logged(w: HealthWorkout, day: DayRecord?): Boolean =
    day?.workouts.orEmpty().any { it.type == w.type && it.mins == w.mins.coerceIn(5, 300) }

/**
 * The session to offer for an open workout task on [date]: not logged yet, outdoor ones
 * first for an outdoor task, then the longest.
 */
fun suggestWorkout(task: TaskDef, list: List<HealthWorkout>, day: DayRecord?, date: LocalDate): HealthWorkout? =
    workoutsOn(list, date)
        .filter { it.mins >= 1 && !logged(it, day) }
        .sortedWith(compareByDescending<HealthWorkout> { task.outdoor && it.outdoor }.thenByDescending { it.mins })
        .firstOrNull()

// ---- sleep ----

/** The main sleep that ended on [date] (the longest one, so naps don't count). */
fun sleepEnding(sleeps: List<HealthSleep>, date: LocalDate): HealthSleep? =
    sleeps.filter { it.end.toLocalDate() == date }.maxByOrNull { it.asleepMins }

/** The last [n] nights, oldest first, keyed by the morning they ended; null where nothing was tracked. */
fun sleepWeek(sleeps: List<HealthSleep>, today: LocalDate, n: Int = 7): List<Pair<LocalDate, HealthSleep?>> =
    (n - 1 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to sleepEnding(sleeps, it) } }

data class SleepAverage(val bed: LocalTime, val wake: LocalTime, val asleepMins: Int, val nights: Int)

/**
 * Average bedtime, wake time and sleep over [nights]. Times are averaged on a clock that
 * starts at [BED_BASE] or [WAKE_BASE], so 23:30 and 00:30 average to midnight, not noon.
 */
fun sleepAverage(nights: List<HealthSleep>): SleepAverage? {
    if (nights.isEmpty()) return null
    fun avgTime(times: List<LocalTime>, base: Int): LocalTime {
        val shifted = times.map { ((it.toSecondOfDay() / 60) - base + 1440) % 1440 }
        val m = (shifted.average().roundToInt() + base) % 1440
        return LocalTime.of(m / 60, m % 60)
    }
    return SleepAverage(
        bed = avgTime(nights.map { it.start.toLocalTime() }, BED_BASE),
        wake = avgTime(nights.map { it.end.toLocalTime() }, WAKE_BASE),
        asleepMins = nights.map { it.asleepMins }.average().roundToInt(),
        nights = nights.size,
    )
}

private const val BED_BASE = 12 * 60
private const val WAKE_BASE = 18 * 60

/** "7 h 05 m". */
fun fmtSleep(mins: Int): String = "${mins / 60} h ${(mins % 60).toString().padStart(2, '0')} m"

// ---- mindfulness ----

/** The mindfulness sessions that started on [date], oldest first. */
fun mindfulOn(list: List<HealthMindful>, date: LocalDate): List<HealthMindful> =
    list.filter { it.start.toLocalDate() == date }.sortedBy { it.start }

/** Minutes of mindfulness on [date], all sessions added up. */
fun mindfulMinutes(list: List<HealthMindful>, date: LocalDate): Int = mindfulOn(list, date).sumOf { it.mins }
