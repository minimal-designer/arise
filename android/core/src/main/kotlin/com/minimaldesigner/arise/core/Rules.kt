package com.minimaldesigner.arise.core

import java.time.LocalDate

/** What onboarding collects. Port of arise-v2's `pick` + the Start button. */
data class StartChoice(
    val program: ProgramId,
    val name: String,
    val startDate: LocalDate,
    val customIds: List<String> = Programs.defaultCustom,
    val extra: List<TaskDef> = emptyList(),
    val customLength: Int = 75,
    val customReset: Boolean = false,
)

sealed interface StartResult {
    data class Ok(val run: Run) : StartResult
    data object NoTasks : StartResult
}

fun startRun(c: StartChoice): StartResult {
    val p = Programs.of(c.program)
    val custom = c.program == ProgramId.CUSTOM
    val tasks = if (custom) {
        val all = Programs.presets + c.extra
        c.customIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
    } else p.tasks
    if (tasks.isEmpty()) return StartResult.NoTasks
    return StartResult.Ok(
        Run(
            program = c.program,
            name = c.name.trim().ifEmpty { "there" },
            startDate = c.startDate,
            lengthDays = if (custom) c.customLength else p.length,
            resetOnMiss = if (custom) c.customReset else p.resetOnMiss,
            tasks = tasks,
        ),
    )
}

/** A user-typed custom task, id'd like arise-v2 ("c" + base-36 time). */
fun customTask(label: String, nowMillis: Long): TaskDef =
    TaskDef("c" + nowMillis.toString(36), label.trim().take(60), "", TaskKind.OTHER)

sealed interface WorkoutCheck {
    data class Ok(val workout: Workout) : WorkoutCheck
    data object NeedsOutdoors : WorkoutCheck
    data object TooShort : WorkoutCheck
}

/** The workout sheet's validation: minutes clamp to 5..300, outdoor tasks must be outdoors, 45 min minimum. */
fun checkWorkout(task: TaskDef, type: String, minutes: Int, outdoor: Boolean): WorkoutCheck {
    val mins = minutes.coerceIn(5, 300)
    if (task.outdoor && !outdoor) return WorkoutCheck.NeedsOutdoors
    if (mins < 45) return WorkoutCheck.TooShort
    return WorkoutCheck.Ok(Workout(task.id, type, mins, outdoor))
}

fun defaultWorkoutType(task: TaskDef) = if (task.outdoor) "Walk" else "Strength"

/** Ticks a task (with its workout or reading, if any) or unticks it. Returns the new day. */
fun toggle(day: DayRecord, task: TaskDef, doneAt: String, workout: Workout? = null, read: ReadLog? = null): DayRecord =
    if (day.isDone(task.id)) {
        DayRecord(day.done - task.id, day.workouts.filterNot { it.taskId == task.id }, if (task.kind == TaskKind.READ) null else day.read)
    } else {
        DayRecord(
            day.done + (task.id to doneAt),
            if (workout != null) day.workouts.filterNot { it.taskId == task.id } + workout else day.workouts,
            read ?: day.read,
        )
    }

/** 75 Hard: the first missed day the user still has to answer, or null. None while [paused]: the break ends in a restart. */
fun pendingMiss(run: Run, p: Progress, paused: Boolean = false): LocalDate? =
    if (!run.resetOnMiss || p.finished || paused) null else p.missed.firstOrNull()

/** Tasks left unticked on `date` (for the missed-day sheet). */
fun leftOn(run: Run, days: Days, date: LocalDate): List<TaskDef> =
    run.tasks.filterNot { days[date]?.isDone(it.id) == true }

/** "I did finish it": tick everything still open on that day. */
fun backfill(run: Run, day: DayRecord?): DayRecord {
    val d = day ?: DayRecord()
    return d.copy(done = d.done + run.tasks.filterNot { d.isDone(it.id) }.associate { it.id to BACKFILLED })
}

/** "Restart from Day 1": same run, new start date, next attempt. */
fun restart(run: Run, today: LocalDate): Run = run.copy(startDate = today, attempt = run.attempt + 1)

data class RecentWorkout(val date: LocalDate, val workout: Workout)

fun recentWorkouts(days: Days, limit: Int = 3): List<RecentWorkout> =
    days.entries.sortedByDescending { it.key }
        .flatMap { (k, d) -> d.workouts.map { RecentWorkout(k, it) } }
        .take(limit)

data class DayMinutes(val date: LocalDate, val indoor: Int, val outdoor: Int)

fun weekMinutes(days: Days, week: WeekStats): List<DayMinutes> = week.cols.map { c ->
    val ws = days[c.date]?.workouts.orEmpty()
    DayMinutes(c.date, ws.filterNot { it.outdoor }.sumOf { it.mins }, ws.filter { it.outdoor }.sumOf { it.mins })
}
