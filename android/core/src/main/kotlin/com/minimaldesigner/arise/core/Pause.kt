package com.minimaldesigner.arise.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import java.time.LocalDate
import kotlin.math.max

// 0.2.1: a named break, and the free passes on the missed-day sheet.

/**
 * A break the user named. [from] and [until] are ISO dates: [until] is the day they're
 * back, so the pause runs while today is before it. After a break the challenge restarts
 * at Day 1. Kept in the `meta` table under [MetaKey.PAUSE].
 */
@Serializable
data class Pause(val reason: String, val from: String, val until: String) {
    val fromDate: LocalDate get() = LocalDate.parse(from)
    val untilDate: LocalDate get() = LocalDate.parse(until)

    /** The name for a sentence: "Enjoy this holiday", but "Enjoy this Bali trip" as typed. */
    val phrase: String get() = if (reason in PAUSE_REASONS) reason.lowercase() else reason

    /** Whether [date] falls inside the break. */
    fun covers(date: LocalDate): Boolean = !date.isBefore(fromDate) && date.isBefore(untilDate)
}

/** The stored pause, or null when there's none or it can't be read. */
fun decodePause(text: String?): Pause? =
    MetaJson.decode(Pause.serializer().nullable, text, null)?.takeIf { p -> runCatching { p.fromDate; p.untilDate }.isSuccess }

fun encodePause(p: Pause): String = MetaJson.encode(Pause.serializer(), p)

sealed interface PauseState {
    val pause: Pause

    /** On the break: [daysLeft] until the day back (1 on the last day). */
    data class Active(override val pause: Pause, val daysLeft: Int) : PauseState

    /** The break is over: the challenge has to restart at Day 1. */
    data class Over(override val pause: Pause) : PauseState
}

fun pauseState(p: Pause?, today: LocalDate): PauseState? = when {
    p == null -> null
    today.isBefore(p.untilDate) -> PauseState.Active(p, daysBetween(today, p.untilDate))
    else -> PauseState.Over(p)
}

/** Names the pause sheet offers; tapping one fills the name field. */
val PAUSE_REASONS = listOf("Holiday", "Trip", "Sick days", "Family time", "Festival")

/** Lengths the pause sheet offers, in days. */
val PAUSE_LENGTHS = listOf(3, 7, 14)

const val PAUSE_MAX_DAYS = 60

sealed interface PauseCheck {
    data object NoName : PauseCheck
    data object BadLength : PauseCheck
    data class Ok(val pause: Pause) : PauseCheck
}

/** The pause sheet's validation: a name, and 1 to [PAUSE_MAX_DAYS] days starting today. */
fun checkPause(reason: String, days: Int, today: LocalDate): PauseCheck {
    val name = reason.trim().take(40)
    if (name.isEmpty()) return PauseCheck.NoName
    if (days !in 1..PAUSE_MAX_DAYS) return PauseCheck.BadLength
    return PauseCheck.Ok(Pause(name, today.toString(), today.plusDays(days.toLong()).toString()))
}

/** "I did finish it" can mark a missed day done this many times per attempt. */
const val FREE_PASSES = 3

/** The doneAt that "I did finish it" writes (see [backfill]). */
const val BACKFILLED = "backfilled"

/** Days in this attempt that "I did finish it" filled in. */
fun passesUsed(run: Run, days: Days, today: LocalDate): Int =
    days.count { (date, d) -> !date.isBefore(run.startDate) && !date.isAfter(today) && d.done.values.any { it == BACKFILLED } }

fun passesLeft(run: Run, days: Days, today: LocalDate): Int = max(0, FREE_PASSES - passesUsed(run, days, today))
