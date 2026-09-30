package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeekStatsTest {
    private val today = LocalDate.of(2026, 9, 26) // Saturday
    private val monday = LocalDate.of(2026, 9, 21)

    @Test
    fun `this week runs monday to sunday with the future blank`() {
        val run = Sample.run(today)
        val w = weekStats(run, Sample.days(today), today, 0)
        assertEquals(monday, w.monday)
        assertEquals(listOf(6, 6, 6, 6, 6, 3, null), w.cols.map { it.n })
        assertEquals(true, w.cols.last().future)
        assertEquals((5 + 0.5) / 6, w.pct!!, 1e-9)
    }

    @Test
    fun `offsets move whole weeks and sunday belongs to the week before`() {
        assertEquals(monday.minusWeeks(1), weekStats(null, emptyMap(), today, -1).monday)
        val sunday = LocalDate.of(2026, 9, 27)
        assertEquals(monday, weekStats(null, emptyMap(), sunday, 0).monday)
    }

    @Test
    fun `days before the run starts are not counted`() {
        val run = Run(ProgramId.SOFT, "A", LocalDate.of(2026, 9, 24), 75, false, Programs.soft.tasks)
        val days = mapOf(LocalDate.of(2026, 9, 24) to DayRecord(Programs.soft.tasks.associate { it.id to "t" }))
        val w = weekStats(run, days, today, 0)
        assertEquals(listOf(false, false, false, true, true, true, true), w.cols.map { it.inRun })
        assertEquals(listOf(null, null, null, 4, 0, 0, null), w.cols.map { it.n })
        assertEquals(1.0 / 3, w.pct!!, 1e-9)
    }

    @Test
    fun `no run means no percentage`() {
        assertNull(weekStats(null, emptyMap(), today, 0).pct)
    }

    @Test
    fun `workout minutes split indoor and outdoor`() {
        val run = Sample.run(today)
        val days = Sample.days(today)
        val mins = weekMinutes(days, weekStats(run, days, today, 0))
        val sat = mins[5]
        assertEquals(today, sat.date)
        assertEquals(0, sat.outdoor) // w2 not logged on the sample's last day
        assertEquals(DayMinutes(LocalDate.of(2026, 9, 27), 0, 0), mins[6])
    }
}
