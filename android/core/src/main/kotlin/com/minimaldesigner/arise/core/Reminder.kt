package com.minimaldesigner.arise.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The evening notification's text. */
data class Nudge(val title: String, val text: String)

/** Times Settings offers for the evening reminder (v2 turns "At risk tonight" on at 8 pm). */
val REMINDER_TIMES: List<LocalTime> = listOf(19, 20, 21, 22).map { LocalTime.of(it, 0) }
val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(20, 0)

/**
 * What the evening reminder says on [today], or null when there's nothing to say: no
 * challenge, not started yet, finished, every task already done, the run's days are over,
 * or the challenge is [paused] (or back from a break and waiting for its restart).
 */
fun eveningNudge(run: Run?, days: Days, today: LocalDate, paused: Boolean = false): Nudge? {
    if (run == null || paused) return null
    val p = compute(run, days, today)
    // A run with misses never reaches `finished`, so also stop once its last day is past.
    if (!p.started || p.finished || daysBetween(run.startDate, today) >= run.lengthDays) return null
    val left = leftOn(run, days, today)
    if (left.isEmpty()) return null
    val n = left.size
    return Nudge(
        title = "At risk tonight: $n ${if (n == 1) "task" else "tasks"} left",
        text = left.joinToString(" · ") { it.label },
    )
}

/** The next time the reminder should fire after [now]: today at [at], or tomorrow if that has passed. */
fun nextReminder(now: LocalDateTime, at: LocalTime): LocalDateTime {
    val today = now.toLocalDate().atTime(at)
    return if (today.isAfter(now)) today else today.plusDays(1)
}
