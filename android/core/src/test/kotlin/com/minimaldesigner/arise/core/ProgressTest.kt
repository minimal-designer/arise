package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val today = LocalDate.of(2026, 9, 26) // a Saturday

    private fun cleared(run: Run) = DayRecord(run.tasks.associate { it.id to "t" })

    @Test
    fun `sample data matches the artifact - day 38, 37 cleared, 37-day streak`() {
        val run = Sample.run(today)
        val p = compute(run, Sample.days(today), today)
        assertEquals(38, p.dayNum)
        assertEquals(37, p.cleared)
        assertEquals(37, p.streak)
        assertEquals(emptyList<LocalDate>(), p.missed)
        assertEquals(3, p.doneToday)
        assertEquals(6, p.total)
        assertTrue(p.started)
        assertFalse(p.finished)
        assertEquals(listOf("Week 1", "Week 2"), p.hit.map { it.name })
        assertEquals(Milestone("Halfway", 38), p.next)
        assertEquals(37.0 / 75, p.progress, 1e-9)
    }

    @Test
    fun `milestones scale with length and round like javascript`() {
        assertEquals(listOf(7, 14, 38, 60, 75), milestonesFor(75).map { it.at })
        assertEquals(listOf(3, 6, 15, 24, 30), milestonesFor(30).map { it.at })
        assertEquals(listOf(9, 19, 50, 80, 100), milestonesFor(100).map { it.at })
        assertEquals(listOf(1, 1, 1, 1, 1), milestonesFor(1).map { it.at })
    }

    @Test
    fun `a missed day resets the streak but today does not count as missed`() {
        val run = Run(ProgramId.SOFT, "A", today.minusDays(5), 75, false, Programs.soft.tasks)
        val days = listOf(5, 4, 2, 1).associate { today.minusDays(it.toLong()) to cleared(run) }
        val p = compute(run, days, today)
        assertEquals(6, p.dayNum)
        assertEquals(4, p.cleared)
        assertEquals(2, p.streak)
        assertEquals(listOf(today.minusDays(3)), p.missed)
    }

    @Test
    fun `partial days are not cleared`() {
        val run = Run(ProgramId.SOFT, "A", today.minusDays(1), 75, false, Programs.soft.tasks)
        val days = mapOf(today.minusDays(1) to DayRecord(mapOf("w1" to "t")))
        val p = compute(run, days, today)
        assertEquals(0, p.cleared)
        assertEquals(listOf(today.minusDays(1)), p.missed)
    }

    @Test
    fun `a run starting tomorrow has not started`() {
        val run = Run(ProgramId.HARD, "A", today.plusDays(1), 75, true, Programs.hard.tasks)
        val p = compute(run, emptyMap(), today)
        assertFalse(p.started)
        assertEquals(1, p.dayNum)
        assertEquals(0, p.cleared)
        assertEquals(Status.NotStarted(today.plusDays(1)), statusOf(run, p, 9))
    }

    @Test
    fun `finishing every day completes the challenge and caps day number`() {
        val run = Run(ProgramId.CUSTOM, "A", today.minusDays(40), 30, false, Programs.soft.tasks)
        val days = (0L until 30L).associate { run.startDate.plusDays(it) to cleared(run) }
        val p = compute(run, days, today)
        assertTrue(p.finished)
        assertEquals(30, p.dayNum)
        assertEquals(1.0, p.progress, 0.0)
        assertNull(p.next)
        assertEquals(Status.Complete, statusOf(run, p, 21))
    }

    @Test
    fun `a run with no tasks never clears a day`() {
        val run = Run(ProgramId.CUSTOM, "A", today.minusDays(2), 30, false, emptyList())
        assertEquals(0, compute(run, mapOf(today.minusDays(1) to DayRecord()), today).cleared)
    }

    @Test
    fun `status follows the artifact order`() {
        val hard = Sample.run(today)
        val p = compute(hard, Sample.days(today), today)
        assertEquals(Status.OnTrack, statusOf(hard, p, 19))
        assertEquals(Status.AtRisk, statusOf(hard, p, 20))
        val allDone = p.copy(doneToday = p.total)
        assertEquals(Status.OnTrack, statusOf(hard, allDone, 22))
        val missed = p.copy(missed = listOf(today.minusDays(3)))
        assertEquals(Status.MissedDay, statusOf(hard, missed, 9))
        val soft = hard.copy(resetOnMiss = false)
        assertEquals(Status.OnTrack, statusOf(soft, missed, 9))
    }
}
