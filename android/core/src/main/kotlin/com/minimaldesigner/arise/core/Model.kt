package com.minimaldesigner.arise.core

import java.time.LocalDate

/** Picks the icon a task row shows. Mirrors the `kind` strings in arise-v2. */
enum class TaskKind(val key: String) {
    WORKOUT("workout"), DIET("diet"), WATER("water"), READ("read"), PHOTO("photo"),
    WALK("walk"), SLEEP("sleep"), MIND("mind"), OTHER("other");

    companion object {
        fun of(key: String): TaskKind = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

data class TaskDef(
    val id: String,
    val label: String,
    val sub: String = "",
    val kind: TaskKind,
    val outdoor: Boolean = false,
)

enum class ProgramId(val key: String) {
    HARD("hard"), SOFT("soft"), CUSTOM("custom");

    companion object {
        fun of(key: String): ProgramId = entries.first { it.key == key }
    }
}

data class Program(
    val id: ProgramId,
    val name: String,
    val level: String,
    val length: Int,
    val resetOnMiss: Boolean,
    val blurb: String,
    val rule: String,
    val tasks: List<TaskDef>,
)

/** The challenge in progress (arise-v2 `run/current`). */
data class Run(
    val program: ProgramId,
    val name: String,
    val startDate: LocalDate,
    val lengthDays: Int,
    val resetOnMiss: Boolean,
    val tasks: List<TaskDef>,
    val attempt: Int = 1,
)

data class Workout(
    val taskId: String,
    val type: String,
    val mins: Int,
    val outdoor: Boolean,
)

/** What the Read task logged on a day: the book, the page reached, and pages read that day. */
data class ReadLog(val title: String, val page: Int, val total: Int, val pages: Int)

/** One calendar day: which tasks were ticked (id -> ISO time or "backfilled"), the workouts, and the reading. */
data class DayRecord(
    val done: Map<String, String> = emptyMap(),
    val workouts: List<Workout> = emptyList(),
    val read: ReadLog? = null,
) {
    fun isDone(taskId: String) = done.containsKey(taskId)
}

typealias Days = Map<LocalDate, DayRecord>
