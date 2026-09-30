package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderTest {
    private val start = LocalDate.of(2026, 9, 18)
    private val run = Run(ProgramId.SOFT, "Alex", start, 75, false, Programs.soft.tasks)
    private val today = start.plusDays(9)

    private fun ticked(vararg ids: String): Days = mapOf(today to DayRecord(ids.associateWith { "t" }))

    @Test fun `lists what is still open`() {
        val n = eveningNudge(run, ticked("w1", "diet"), today)!!
        assertEquals("At risk tonight: 2 tasks left", n.title)
        assertEquals("Drink 3 L of water · Read 10 pages", n.text)
        assertEquals("At risk tonight: 1 task left", eveningNudge(run, ticked("w1", "diet", "water"), today)!!.title)
    }

    @Test fun `stays quiet when there is nothing to nag about`() {
        assertNull(eveningNudge(null, emptyMap(), today))
        assertNull(eveningNudge(run, ticked("w1", "diet", "water", "read"), today))
        assertNull(eveningNudge(run, emptyMap(), start.minusDays(1))) // not started
        assertNull(eveningNudge(run, emptyMap(), start.plusDays(75))) // finished
    }

    @Test fun `fires today if the time is still ahead, else tomorrow`() {
        val eight = LocalTime.of(20, 0)
        assertEquals(LocalDateTime.of(2026, 9, 27, 20, 0), nextReminder(LocalDateTime.of(2026, 9, 27, 9, 30), eight))
        assertEquals(LocalDateTime.of(2026, 9, 28, 20, 0), nextReminder(LocalDateTime.of(2026, 9, 27, 20, 0), eight))
        assertEquals(LocalDateTime.of(2026, 9, 28, 20, 0), nextReminder(LocalDateTime.of(2026, 9, 27, 23, 59), eight))
    }
}
