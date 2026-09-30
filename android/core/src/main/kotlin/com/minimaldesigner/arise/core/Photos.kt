package com.minimaldesigner.arise.core

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

enum class Angle(val key: String, val label: String) {
    FRONT("front", "Front"), SIDE("side", "Side"), BACK("back", "Back");

    companion object {
        fun of(key: String): Angle = entries.firstOrNull { it.key == key } ?: FRONT
    }
}

enum class PhotoSource(val key: String) {
    CAMERA("camera"), PICKER("picker"), IMPORT75("import75"), RESTORE("restore");

    companion object {
        fun of(key: String): PhotoSource = entries.firstOrNull { it.key == key } ?: PICKER
    }
}

/** A progress photo's metadata. The image files live in the app, not here. */
data class PhotoMeta(
    val id: String,
    val date: LocalDate,
    val angle: Angle,
    val source: PhotoSource,
    val createdAt: String,
)

/** Challenge day a date falls on (Day 1 = start). arise-v2 `dayOf`. */
fun dayOf(run: Run?, date: LocalDate): Int = if (run == null) 1 else daysBetween(run.startDate, date) + 1

/** Week of the challenge a day falls in; 0 for photos from before Day 1 (e.g. imported from 75 Hard). */
fun weekOf(day: Int): Int = if (day < 1) 0 else max(1, ceil(day / 7.0).toInt())

/** Oldest first, ties broken by when they were added (v2 `photoList`). */
fun sortedPhotos(photos: List<PhotoMeta>): List<PhotoMeta> =
    photos.sortedWith(compareBy<PhotoMeta>({ it.date }, { it.createdAt }))

/** Newest first, ties broken by when they were added. */
fun newestFirst(photos: List<PhotoMeta>): List<PhotoMeta> = sortedPhotos(photos).asReversed()

/**
 * A challenge attempt's dates, for placing photos. [end] is null while it runs; [label]
 * names a past attempt ("75 Hard · attempt 1").
 */
data class Attempt(val start: LocalDate, val end: LocalDate?, val label: String)

/**
 * The attempt [date] falls in. On a restart day the old attempt ends and the new one starts,
 * so the latest start wins.
 */
fun attemptOn(attempts: List<Attempt>, date: LocalDate): Attempt? =
    attempts.filter { a -> !date.isBefore(a.start) && a.end.let { it == null || !date.isAfter(it) } }.maxByOrNull { it.start }

/** Challenge day of [date] within its own attempt, or null outside every attempt. */
fun photoDay(attempts: List<Attempt>, date: LocalDate): Int? =
    attemptOn(attempts, date)?.let { daysBetween(it.start, date) + 1 }

/**
 * One group on the Photos tab: week [week] of [attempt], or (attempt null, week 0) a
 * calendar week, Monday to Sunday, outside every attempt.
 */
data class PhotoWeek(val attempt: Attempt?, val week: Int, val from: LocalDate, val to: LocalDate, val photos: List<PhotoMeta>)

/**
 * Photos of [angle] (null = all), newest first, grouped by the week of the attempt each
 * was taken in. A restart doesn't lump older photos together: they keep their own attempt's weeks.
 */
fun photoWeeks(attempts: List<Attempt>, photos: List<PhotoMeta>, angle: Angle?): List<PhotoWeek> =
    newestFirst(photos)
        .filter { angle == null || it.angle == angle }
        .groupBy { m ->
            val a = attemptOn(attempts, m.date)
            if (a != null) {
                val w = weekOf(daysBetween(a.start, m.date) + 1)
                Triple(a, w, a.start.plusDays((w - 1) * 7L))
            } else {
                Triple(null, 0, m.date.minusDays(m.date.dayOfWeek.value - 1L))
            }
        }
        .map { (k, list) ->
            val (a, w, from) = k
            val end = a?.end
            val to = from.plusDays(6).let { if (end != null && end.isBefore(it)) end else it }
            PhotoWeek(a, w, from, to, list)
        }

/** The before/after pair: first and last photo of [angle]; "all" compares front. */
fun comparePair(photos: List<PhotoMeta>, angle: Angle?): Pair<PhotoMeta, PhotoMeta>? {
    val pool = sortedPhotos(photos).filter { it.angle == (angle ?: Angle.FRONT) }
    return if (pool.size < 2) null else pool.first() to pool.last()
}
