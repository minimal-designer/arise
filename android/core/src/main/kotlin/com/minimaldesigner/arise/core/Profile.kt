package com.minimaldesigner.arise.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

// 0.2: the Profile & settings page, the Read sheet and the pinned before/after photos.
// Each is one small JSON value in the app's `meta` table, keyed by [MetaKey].

@Serializable
data class Profile(
    /** File name of the profile photo in the photo store, or null for the initial. */
    val photo: String? = null,
    val heightCm: Double? = null,
    val goalKg: Double? = null,
)

/** The book being read now, how many books were finished, and (0.2.1) which ones. */
@Serializable
data class Reading(
    val title: String = "",
    val page: Int = 0,
    val total: Int = 0,
    val finished: Int = 0,
    val books: List<Book> = emptyList(),
)

/** A finished book (0.2.1). [finishedOn] is an ISO date. */
@Serializable
data class Book(val title: String, val total: Int = 0, val finishedOn: String? = null)

/** When the live challenge was set up or last restarted, as an ISO date (0.2.1; see [freeSleepDay]). */
@Serializable
data class ChallengeInfo(val startedOn: String? = null)

/** Photo ids the user pinned. The before pin outlives restarts and ended challenges. */
@Serializable
data class PhotoPins(val before: String? = null, val after: String? = null)

enum class MetaKey(val key: String) { PROFILE("profile"), READING("reading"), PINS("pins"), CHALLENGE("challenge"), PAUSE("pause") }

object MetaJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun <T> encode(s: KSerializer<T>, v: T): String = json.encodeToString(s, v)
    /** Falls back to [default] for a missing or unreadable value. */
    fun <T> decode(s: KSerializer<T>, text: String?, default: T): T =
        text?.let { runCatching { json.decodeFromString(s, it) }.getOrNull() } ?: default
}

// ---- Read sheet ----

sealed interface ReadCheck {
    data object NoTitle : ReadCheck
    data object NoPage : ReadCheck
    data object PastEnd : ReadCheck
    /** [reading] is what to store next; [finishedBook] when the page reached the last one. */
    data class Ok(val reading: Reading, val log: ReadLog, val finishedBook: Boolean) : ReadCheck
}

/**
 * Checks the Read sheet and works out today's pages: the difference from last time for the
 * same book, or the whole page count for a new one. Reaching the last page finishes the book.
 */
fun checkRead(current: Reading, title: String, page: Int, total: Int, today: LocalDate): ReadCheck {
    val t = title.trim()
    if (t.isEmpty()) return ReadCheck.NoTitle
    if (page < 1) return ReadCheck.NoPage
    if (total > 0 && page > total) return ReadCheck.PastEnd
    val pages = if (t == current.title) (page - current.page).coerceAtLeast(0) else page
    val log = ReadLog(t, page, total, pages)
    return if (total > 0 && page == total) ReadCheck.Ok(finished(current, t, total, today), log, true)
    else ReadCheck.Ok(current.copy(title = t, page = page, total = total), log, false)
}

/** "Finished it? Start a new book". */
fun finishBook(r: Reading, today: LocalDate): Reading = finished(r, r.title, r.total, today)

/** A clean slate for the next book, with [title] added to the finished ones. */
private fun finished(r: Reading, title: String, total: Int, today: LocalDate): Reading = Reading(
    finished = r.finished + 1,
    books = if (title.isBlank()) r.books else r.books + Book(title.trim(), total, today.toString()),
)

// ---- book log ----

/**
 * One book in the reading log. [from] and [to] are the first and last day it was logged
 * (to is the day it was finished, when known); [pages] adds up the pages logged.
 */
data class BookEntry(
    val title: String,
    val total: Int,
    val page: Int,
    val from: LocalDate?,
    val to: LocalDate?,
    val pages: Int,
    val finished: Boolean,
    val current: Boolean,
)

/**
 * Every book: the one being read, the finished ones, and any other title the Read task
 * logged (so books from before 0.2.1 show up too). The current book first, then the most recent.
 */
fun bookLog(reading: Reading, days: Days): List<BookEntry> {
    fun key(t: String) = t.trim().lowercase()
    val logs = days.entries.mapNotNull { (d, r) -> r.read?.takeIf { it.title.isNotBlank() }?.let { d to it } }.sortedBy { it.first }
    val byTitle = logs.groupBy { key(it.second.title) }
    val done = reading.books.groupBy { key(it.title) }
    val currentKey = key(reading.title).takeIf { it.isNotEmpty() }
    val keys = (byTitle.keys + done.keys + listOfNotNull(currentKey)).distinct()
    return keys.map { k ->
        val ls = byTitle[k].orEmpty()
        val last = ls.lastOrNull()?.second
        val book = done[k]?.lastOrNull()
        val current = k == currentKey
        val total = (if (current) reading.total else 0).takeIf { it > 0 } ?: last?.total?.takeIf { it > 0 } ?: book?.total ?: 0
        val page = if (current) reading.page else last?.page ?: total
        val finishedOn = book?.finishedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        BookEntry(
            title = if (current) reading.title.trim() else last?.title?.trim() ?: book!!.title,
            total = total,
            page = page,
            from = ls.firstOrNull()?.first,
            to = listOfNotNull(finishedOn, ls.lastOrNull()?.first).maxOrNull(),
            pages = ls.sumOf { it.second.pages },
            finished = !current && (book != null || (total > 0 && page >= total)),
            current = current,
        )
    }.sortedWith(compareByDescending<BookEntry> { it.current }.thenByDescending { it.to })
}

// ---- before / after ----

data class PhotoPair(val before: PhotoMeta?, val after: PhotoMeta?, val pinnedBefore: Boolean, val pinnedAfter: Boolean)

/**
 * Before: the pin, else the first photo of this attempt (front if there is one), else the
 * oldest photo. After: the pin, else the newest photo of the same angle as before.
 */
fun photoPair(photos: List<PhotoMeta>, run: Run?, pins: PhotoPins): PhotoPair {
    val all = sortedPhotos(photos)
    val pinB = pins.before?.let { id -> all.firstOrNull { it.id == id } }
    val inRun = all.filter { run == null || !it.date.isBefore(run.startDate) }
    val before = pinB ?: inRun.firstOrNull { it.angle == Angle.FRONT } ?: inRun.firstOrNull() ?: all.firstOrNull()
    val pinA = pins.after?.let { id -> all.firstOrNull { it.id == id && it.id != before?.id } }
    val after = pinA ?: all.lastOrNull { before != null && it.angle == before.angle && it.id != before.id }
    return PhotoPair(before, after, pinB != null, pinA != null)
}

// ---- Week: every task, every day ----

enum class Cell { OFF, DONE, MISS, TODAY }

/** One task's week. [values] are workout minutes or pages read per day, or null for other tasks. */
data class GridRow(val task: TaskDef, val cells: List<Cell>, val done: Int, val count: Int, val values: List<Int>?)

data class WeekGrid(val week: WeekStats, val rows: List<GridRow>, val done: Int, val possible: Int) {
    val pct: Int? get() = if (possible == 0) null else Math.round(done * 100f / possible)
}

/**
 * The task × day grid for the week [offset] weeks from this one. Today counts only once
 * it's done; days outside the run, in the future or on a break don't count.
 */
fun weekGrid(run: Run, days: Days, today: LocalDate, offset: Int, pause: Pause? = null): WeekGrid {
    val w = weekStats(run, days, today, offset)
    val rows = run.tasks.map { t ->
        var done = 0
        var count = 0
        val cells = w.cols.map { c ->
            val ok = days[c.date]?.isDone(t.id) == true
            when {
                !c.inRun || c.future || pause?.covers(c.date) == true -> Cell.OFF
                c.date == today -> { if (ok) { done++; count++ }; if (ok) Cell.DONE else Cell.TODAY }
                else -> { count++; if (ok) { done++; Cell.DONE } else Cell.MISS }
            }
        }
        val values = when (t.kind) {
            TaskKind.WORKOUT -> w.cols.map { c -> days[c.date]?.workouts.orEmpty().filter { it.taskId == t.id }.sumOf { it.mins } }
            TaskKind.READ -> w.cols.map { c -> days[c.date]?.takeIf { it.isDone(t.id) }?.read?.pages ?: 0 }
            else -> null
        }
        GridRow(t, cells, done, count, values)
    }
    return WeekGrid(w, rows, rows.sumOf { it.done }, rows.sumOf { it.count })
}
