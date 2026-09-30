package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TicksTest {
    private val start = LocalDate.of(2026, 9, 20)
    private val hard = Run(ProgramId.HARD, "A", start, 75, true, Programs.hard.tasks)
    private val sleep = Programs.presets.first { it.id == "sleep" }
    private val custom = Run(ProgramId.CUSTOM, "A", start, 30, false, listOf(Programs.presets.first { it.id == "w1" }, sleep))

    @Test fun `a photo on a day ticks the photo task`() {
        val days = mapOf(start to DayRecord(mapOf("diet" to "t")))
        val out = withDerivedTicks(hard, days, setOf(start, start.plusDays(1)), null)
        assertEquals(TICK_PHOTO, out[start]?.done?.get("photo"))
        assertEquals("t", out[start]?.done?.get("diet"))
        assertTrue(out[start.plusDays(1)]!!.isDone("photo"))
        // A stored tick is left alone.
        val stored = mapOf(start to DayRecord(mapOf("photo" to "2026-09-20T08:00")))
        assertEquals("2026-09-20T08:00", withDerivedTicks(hard, stored, setOf(start), null)[start]?.done?.get("photo"))
        // No photo task, no run: nothing changes.
        assertEquals(days, withDerivedTicks(custom, days, setOf(start), null))
        assertEquals(days, withDerivedTicks(null, days, setOf(start), start))
    }

    @Test fun `derived ticks are never stored`() {
        val d = DayRecord(mapOf("photo" to TICK_PHOTO, "sleep" to TICK_FREE, "diet" to "t", "w1" to "backfilled"))
        assertEquals(mapOf("diet" to "t", "w1" to "backfilled"), d.stored().done)
        assertTrue(isDerived(TICK_FREE))
        assertFalse(isDerived("backfilled"))
        assertFalse(isDerived(null))
    }

    @Test fun `a restart keeps today's photo counted`() {
        // A photo was taken today, then the challenge restarted with Day 1 today.
        val today = start.plusDays(12)
        val next = restart(hard, today)
        val days = withDerivedTicks(next, emptyMap(), setOf(today), null)
        assertEquals(1, compute(next, days, today).doneToday)
    }

    @Test fun `day 1 sleep is free unless the challenge was set up ahead`() {
        assertEquals(start, freeSleepDay(custom, start))
        assertEquals(start, freeSleepDay(custom, start.plusDays(3)))
        assertEquals(start, freeSleepDay(custom, null))
        assertNull(freeSleepDay(custom, start.minusDays(1)))
        assertNull(freeSleepDay(hard, start))
        val days = withDerivedTicks(custom, emptyMap(), emptySet(), start)
        assertEquals(TICK_FREE, days[start]?.done?.get("sleep"))
    }

    @Test fun `minutes come from the task label`() {
        assertEquals(10, labelMinutes(Programs.presets.first { it.id == "meditate" }))
        assertEquals(45, labelMinutes(Programs.hard.tasks[0]))
        assertNull(labelMinutes(Programs.presets.first { it.id == "cold" }))
    }

    @Test fun `mindfulness minutes add up per day`() {
        val t = { h: Int, m: Int -> LocalDateTime.of(2026, 9, 27, h, m) }
        val list = listOf(HealthMindful(t(7, 0), t(7, 12)), HealthMindful(t(21, 0), t(21, 5)), HealthMindful(t(7, 0).minusDays(1), t(7, 30).minusDays(1)))
        assertEquals(17, mindfulMinutes(list, LocalDate.of(2026, 9, 27)))
        assertEquals(2, mindfulOn(list, LocalDate.of(2026, 9, 27)).size)
        assertEquals(0, mindfulMinutes(emptyList(), LocalDate.of(2026, 9, 27)))
    }
}
