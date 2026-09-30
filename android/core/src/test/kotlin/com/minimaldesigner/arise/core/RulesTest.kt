package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    private val today = LocalDate.of(2026, 9, 26)

    @Test
    fun `hard and soft use their fixed rules`() {
        val hard = (startRun(StartChoice(ProgramId.HARD, "  ", today)) as StartResult.Ok).run
        assertEquals("there", hard.name)
        assertEquals(6, hard.tasks.size)
        assertTrue(hard.resetOnMiss)
        val soft = (startRun(StartChoice(ProgramId.SOFT, "Alex", today, customLength = 30)) as StartResult.Ok).run
        assertEquals(75, soft.lengthDays)
        assertEquals(false, soft.resetOnMiss)
    }

    @Test
    fun `custom keeps pick order, adds own tasks and needs at least one`() {
        val own = customTask("  20 push-ups  ", 1_700_000_000_000)
        assertEquals("c" + 1_700_000_000_000.toString(36), own.id)
        assertEquals("20 push-ups", own.label)
        val c = StartChoice(ProgramId.CUSTOM, "A", today, listOf("read", own.id, "w1"), listOf(own), 45, true)
        val run = (startRun(c) as StartResult.Ok).run
        assertEquals(listOf("read", own.id, "w1"), run.tasks.map { it.id })
        assertEquals(45, run.lengthDays)
        assertTrue(run.resetOnMiss)
        assertEquals(StartResult.NoTasks, startRun(c.copy(customIds = emptyList())))
    }

    @Test
    fun `workout sheet validation`() {
        val w1 = Programs.hard.tasks[0]
        val w2 = Programs.hard.tasks[1]
        assertEquals(WorkoutCheck.NeedsOutdoors, checkWorkout(w2, "Walk", 60, false))
        assertEquals(WorkoutCheck.TooShort, checkWorkout(w1, "Strength", 30, false))
        assertEquals(WorkoutCheck.Ok(Workout("w1", "Run", 300, true)), checkWorkout(w1, "Run", 999, true))
        assertEquals("Walk", defaultWorkoutType(w2))
        assertEquals("Strength", defaultWorkoutType(w1))
    }

    @Test
    fun `toggle ticks with a workout and unticks both`() {
        val w1 = Programs.hard.tasks[0]
        val wk = Workout("w1", "Run", 50, false)
        val on = toggle(DayRecord(), w1, "2026-09-26T08:00", wk)
        assertEquals(setOf("w1"), on.done.keys)
        assertEquals(listOf(wk), on.workouts)
        val off = toggle(on, w1, "ignored")
        assertEquals(DayRecord(), off)
    }

    @Test
    fun `missed day - only 75 hard asks, backfill fills the gaps, restart bumps the attempt`() {
        val run = Run(ProgramId.HARD, "A", today.minusDays(2), 75, true, Programs.hard.tasks)
        val days = mapOf(today.minusDays(2) to DayRecord(mapOf("w1" to "t", "diet" to "t")))
        val p = compute(run, days, today)
        assertEquals(today.minusDays(2), pendingMiss(run, p))
        assertNull(pendingMiss(run.copy(resetOnMiss = false), p))
        assertEquals(listOf("w2", "water", "read", "photo"), leftOn(run, days, today.minusDays(2)).map { it.id })

        val fixed = backfill(run, days[today.minusDays(2)])
        assertEquals("t", fixed.done["w1"])
        assertEquals("backfilled", fixed.done["photo"])
        assertEquals(6, fixed.done.size)

        val again = restart(run, today)
        assertEquals(today, again.startDate)
        assertEquals(2, again.attempt)
    }

    @Test
    fun `recent workouts are newest first`() {
        val days = Sample.days(today)
        val r = recentWorkouts(days)
        assertEquals(3, r.size)
        assertEquals(today, r[0].date)
        assertEquals(today.minusDays(1), r[1].date)
        assertEquals("w1", r[1].workout.taskId)
        assertEquals("w2", r[2].workout.taskId)
    }
}
