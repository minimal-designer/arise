package com.minimaldesigner.arise.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class Milestone(val name: String, val at: Int)

/** Everything the Home, Week and settings views read. Port of arise-v2 `compute()`. */
data class Progress(
    val today: LocalDate,
    val len: Int,
    val dayNum: Int,
    val started: Boolean,
    val cleared: Int,
    val missed: List<LocalDate>,
    val streak: Int,
    /** The longest run of cleared days in this attempt. */
    val best: Int,
    val doneToday: Int,
    val total: Int,
    val progress: Double,
    val milestones: List<Milestone>,
    val hit: List<Milestone>,
    val next: Milestone?,
    val finished: Boolean,
)

data class WeekCol(val date: LocalDate, val inRun: Boolean, val future: Boolean, val n: Int?, val pct: Double?)

data class WeekStats(val monday: LocalDate, val cols: List<WeekCol>, val pct: Double?)

fun daysBetween(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()

/** JavaScript's Math.round: halves round up. */
private fun jsRound(x: Double): Int = floor(x + 0.5).toInt()

fun milestonesFor(len: Int): List<Milestone> =
    Programs.milestones.map { (name, f) -> Milestone(name, max(1, jsRound(f * len))) }

fun doneCount(run: Run, days: Days, date: LocalDate): Int {
    val d = days[date] ?: return 0
    return run.tasks.count { d.isDone(it.id) }
}

fun isDayCleared(run: Run, days: Days, date: LocalDate): Boolean {
    val d = days[date] ?: return false
    return run.tasks.isNotEmpty() && run.tasks.all { d.isDone(it.id) }
}

fun compute(run: Run, days: Days, today: LocalDate): Progress {
    val len = run.lengthDays
    val last = daysBetween(run.startDate, today)
    var cleared = 0
    var streak = 0
    var best = 0
    val missed = mutableListOf<LocalDate>()
    var i = 0
    while (i <= last && i < len) {
        val k = run.startDate.plusDays(i.toLong())
        if (isDayCleared(run, days, k)) {
            cleared++; streak++; best = max(best, streak)
        } else if (k != today) {
            // Today isn't a miss yet: there's still time to finish it.
            missed += k; streak = 0
        }
        i++
    }
    val ms = milestonesFor(len)
    return Progress(
        today = today,
        len = len,
        dayNum = min(len, max(1, last + 1)),
        started = last >= 0,
        cleared = cleared,
        missed = missed,
        streak = streak,
        best = best,
        doneToday = doneCount(run, days, today),
        total = run.tasks.size,
        progress = min(1.0, cleared.toDouble() / len),
        milestones = ms,
        hit = ms.filter { cleared >= it.at },
        next = ms.firstOrNull { cleared < it.at },
        finished = cleared >= len,
    )
}

/** Monday-first week, `offset` weeks from this one. Port of arise-v2 `weekStats()`. */
fun weekStats(run: Run?, days: Days, today: LocalDate, offset: Int): WeekStats {
    val dow = today.dayOfWeek.value - DayOfWeek.MONDAY.value
    val monday = today.minusDays(dow.toLong()).plusWeeks(offset.toLong())
    val total = run?.tasks?.size ?: 0
    val cols = (0 until 7).map { i ->
        val k = monday.plusDays(i.toLong())
        val inRun = run != null && !k.isBefore(run.startDate) && daysBetween(run.startDate, k) < run.lengthDays
        val future = k.isAfter(today)
        val n = if (inRun && !future) doneCount(run!!, days, k) else null
        WeekCol(k, inRun, future, n, n?.let { it.toDouble() / max(1, total) })
    }
    val counted = cols.mapNotNull { it.pct }
    return WeekStats(monday, cols, if (counted.isEmpty()) null else counted.average())
}

sealed interface Status {
    data class NotStarted(val start: LocalDate) : Status
    data object Complete : Status
    data object MissedDay : Status
    data object OnTrack : Status
    data object AtRisk : Status
}

/** The coloured status next to "Today's tasks". `hour` is the local hour, 0-23. */
fun statusOf(run: Run, p: Progress, hour: Int): Status = when {
    !p.started -> Status.NotStarted(run.startDate)
    p.finished -> Status.Complete
    run.resetOnMiss && p.missed.isNotEmpty() -> Status.MissedDay
    p.doneToday == p.total -> Status.OnTrack
    hour >= 20 -> Status.AtRisk
    else -> Status.OnTrack
}
