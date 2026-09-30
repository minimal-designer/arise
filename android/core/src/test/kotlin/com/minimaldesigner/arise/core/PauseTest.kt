package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PauseTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val hard = Run(ProgramId.HARD, "A", today.minusDays(10), 75, true, Programs.hard.tasks)

    @Test fun `a break runs until the day back`() {
        val p = (checkPause("  Bali trip ", 3, today) as PauseCheck.Ok).pause
        assertEquals(Pause("Bali trip", "2026-09-30", "2026-10-03"), p)
        assertEquals(PauseState.Active(p, 3), pauseState(p, today))
        assertEquals(PauseState.Active(p, 1), pauseState(p, today.plusDays(2)))
        assertEquals(PauseState.Over(p), pauseState(p, today.plusDays(3)))
        assertEquals(PauseState.Over(p), pauseState(p, today.plusDays(9)))
        assertNull(pauseState(null, today))
        assertTrue(p.covers(today) && p.covers(today.plusDays(2)))
        assertTrue(!p.covers(today.minusDays(1)) && !p.covers(today.plusDays(3)))
    }

    @Test fun `the pause sheet needs a name and 1 to 60 days`() {
        assertEquals(PauseCheck.NoName, checkPause("   ", 7, today))
        assertEquals(PauseCheck.BadLength, checkPause("Holiday", 0, today))
        assertEquals(PauseCheck.BadLength, checkPause("Holiday", 61, today))
        assertTrue(checkPause("Holiday", 60, today) is PauseCheck.Ok)
    }

    @Test fun `preset names read lower case in a sentence, typed ones as typed`() {
        assertEquals("holiday", Pause("Holiday", "2026-09-30", "2026-10-03").phrase)
        assertEquals("Bali trip", Pause("Bali trip", "2026-09-30", "2026-10-03").phrase)
    }

    @Test fun `stored pause survives a round trip and a bad value reads as none`() {
        val p = Pause("Trip", "2026-09-30", "2026-10-07")
        assertEquals(p, decodePause(encodePause(p)))
        assertNull(decodePause(null))
        assertNull(decodePause("not json"))
        assertNull(decodePause("""{"reason":"Trip","from":"x","until":"y"}"""))
    }

    @Test fun `no missed-day sheet and no evening nudge during a break`() {
        val p = compute(hard, emptyMap(), today)
        assertEquals(hard.startDate, pendingMiss(hard, p))
        assertNull(pendingMiss(hard, p, paused = true))
        val soft = hard.copy(program = ProgramId.SOFT, resetOnMiss = false, tasks = Programs.soft.tasks)
        assertTrue(eveningNudge(soft, emptyMap(), today) != null)
        assertNull(eveningNudge(soft, emptyMap(), today, paused = true))
    }

    @Test fun `free passes count this attempt's backfilled days`() {
        assertEquals(FREE_PASSES, passesLeft(hard, emptyMap(), today))
        val filled = backfill(hard, DayRecord(mapOf("w1" to "t")))
        val days = mapOf(
            hard.startDate.minusDays(3) to filled, // an earlier attempt: doesn't count
            hard.startDate to filled,
            hard.startDate.plusDays(1) to DayRecord(hard.tasks.associate { it.id to "t" }),
            hard.startDate.plusDays(2) to filled,
        )
        assertEquals(2, passesUsed(hard, days, today))
        assertEquals(1, passesLeft(hard, days, today))
        val more = days + (hard.startDate.plusDays(3) to filled) + (hard.startDate.plusDays(4) to filled)
        assertEquals(0, passesLeft(hard, more, today))
        // A restart starts the count again.
        assertEquals(FREE_PASSES, passesLeft(restart(hard, today), more, today))
    }

    @Test fun `week grid leaves the days of a break out`() {
        val sunday = LocalDate.of(2026, 9, 27)
        val run = hard.copy(startDate = LocalDate.of(2026, 9, 14))
        val pause = Pause("Trip", "2026-09-24", "2026-10-01")
        val g = weekGrid(run, emptyMap(), sunday, 0, pause)
        assertEquals(listOf(Cell.MISS, Cell.MISS, Cell.MISS, Cell.OFF, Cell.OFF, Cell.OFF, Cell.OFF), g.rows.first().cells)
        assertEquals(0 to 3 * run.tasks.size, g.done to g.possible)
    }
}
