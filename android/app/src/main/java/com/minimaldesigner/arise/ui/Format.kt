package com.minimaldesigner.arise.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Locale.US month names: Java's en-GB abbreviates September to "Sept".
private val short = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val long = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.US)
private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val range = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val full = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/** "26 Sep" */
fun fmtShort(d: LocalDate): String = d.format(short)

/** "Saturday 26 September" */
fun fmtLong(d: LocalDate): String = d.format(long)

/** "Sat" */
fun fmtWeekday(d: LocalDate): String = d.format(weekday)

/** "Sep 21" */
fun fmtRange(d: LocalDate): String = d.format(range)

/** "15–21 Sep", or "29 Sep – 5 Oct" across a month. */
fun fmtSpan(from: LocalDate, to: LocalDate): String = when {
    from == to -> fmtShort(from)
    from.month == to.month -> "${from.dayOfMonth}–${fmtShort(to)}"
    else -> "${fmtShort(from)} – ${fmtShort(to)}"
}

/** "26 Sep 2026" */
fun fmtFull(d: LocalDate): String = d.format(full)

private val time = DateTimeFormatter.ofPattern("h a", Locale.US)

/** "8 pm" */
fun fmtTime(t: java.time.LocalTime): String = t.format(time).lowercase(Locale.US)

private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** "23:40" */
fun fmtClock(t: java.time.LocalTime): String = t.format(clock)

/** 2,300 */
fun fmtInt(n: Number): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
