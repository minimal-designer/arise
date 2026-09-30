package com.minimaldesigner.arise.core

import java.time.LocalDate

// 0.2.1: ticks ARISE works out from other data instead of storing. They carry a marker in
// place of the ISO time, and the repository strips them before writing a day to Room.

/** The photo task on a day that has a progress photo. */
const val TICK_PHOTO = "auto:photo"

/** Sleep on Day 1, when the night before came before the challenge started. */
const val TICK_FREE = "auto:free"

fun isDerived(doneAt: String?): Boolean = doneAt != null && doneAt.startsWith("auto:")

/** The day as it should be stored: without the derived ticks. */
fun DayRecord.stored(): DayRecord = copy(done = done.filterValues { !isDerived(it) })

/**
 * Adds the derived ticks to [days]: the photo task on every date in [photoDates], and the
 * sleep task on [freeSleepOn]. Anything already ticked is left as it is.
 */
fun withDerivedTicks(run: Run?, days: Days, photoDates: Set<LocalDate>, freeSleepOn: LocalDate?): Days {
    if (run == null) return days
    val photoTasks = run.tasks.filter { it.kind == TaskKind.PHOTO }.map { it.id }
    val sleepTasks = run.tasks.filter { it.kind == TaskKind.SLEEP }.map { it.id }
    if ((photoTasks.isEmpty() || photoDates.isEmpty()) && (sleepTasks.isEmpty() || freeSleepOn == null)) return days
    val out = days.toMutableMap()
    fun add(date: LocalDate, ids: List<String>, mark: String) {
        val d = out[date] ?: DayRecord()
        val extra = ids.filterNot { d.isDone(it) }.associateWith { mark }
        if (extra.isNotEmpty()) out[date] = d.copy(done = d.done + extra)
    }
    if (photoTasks.isNotEmpty()) photoDates.forEach { add(it, photoTasks, TICK_PHOTO) }
    if (sleepTasks.isNotEmpty() && freeSleepOn != null) add(freeSleepOn, sleepTasks, TICK_FREE)
    return out
}

/**
 * The day whose sleep is free: Day 1, when the challenge was started or restarted on Day 1
 * or later, because the night before wasn't part of it. A challenge set up ahead of time
 * gets no free night. [startedOn] null (set up before 0.2.1) counts as started on Day 1.
 */
fun freeSleepDay(run: Run?, startedOn: LocalDate?): LocalDate? = when {
    run == null || run.tasks.none { it.kind == TaskKind.SLEEP } -> null
    startedOn == null || !startedOn.isBefore(run.startDate) -> run.startDate
    else -> null
}

/** Sleep that counts for the Sleep task. */
const val SLEEP_GOAL_MINS = 7 * 60

/** The minutes in a task's label ("Meditate 10 min" -> 10), or null. */
fun labelMinutes(task: TaskDef): Int? =
    Regex("""(\d+)\s*min""").find(task.label)?.groupValues?.get(1)?.toIntOrNull()
