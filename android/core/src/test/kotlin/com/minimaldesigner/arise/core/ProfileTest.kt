package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProfileTest {
    private val day = LocalDate.of(2026, 9, 27)

    @Test fun `read sheet works out today's pages`() {
        val r = Reading("Book", 124, 320, 1)
        val ok = checkRead(r, " Book ", 134, 320, day) as ReadCheck.Ok
        assertEquals(ReadLog("Book", 134, 320, 10), ok.log)
        assertEquals(Reading("Book", 134, 320, 1), ok.reading)
        assertFalse(ok.finishedBook)
        // A new book counts every page up to where you are.
        assertEquals(20, (checkRead(r, "Other", 20, 0, day) as ReadCheck.Ok).log.pages)
        // Going back a page never reads as negative.
        assertEquals(0, (checkRead(r, "Book", 120, 320, day) as ReadCheck.Ok).log.pages)
    }

    @Test fun `the last page finishes the book`() {
        val ok = checkRead(Reading("Book", 310, 320, 1), "Book", 320, 320, day) as ReadCheck.Ok
        assertTrue(ok.finishedBook)
        assertEquals(Reading(finished = 2, books = listOf(Book("Book", 320, "2026-09-27"))), ok.reading)
        assertEquals(Reading(finished = 3, books = listOf(Book("X", 9, "2026-09-27"))), finishBook(Reading("X", 5, 9, 2), day))
        // Finishing with no book open just counts it.
        assertEquals(Reading(finished = 1), finishBook(Reading(), day))
    }

    @Test fun `read sheet rejects bad input`() {
        val r = Reading()
        assertEquals(ReadCheck.NoTitle, checkRead(r, "  ", 5, 10, day))
        assertEquals(ReadCheck.NoPage, checkRead(r, "Book", 0, 10, day))
        assertEquals(ReadCheck.PastEnd, checkRead(r, "Book", 11, 10, day))
    }

    @Test fun `meta json falls back safely`() {
        assertEquals(Profile(), MetaJson.decode(Profile.serializer(), null, Profile()))
        assertEquals(Profile(), MetaJson.decode(Profile.serializer(), "not json", Profile()))
        // A 0.2.0 reading value (no books) still reads.
        assertEquals(Reading("B", 3, 9, 1), MetaJson.decode(Reading.serializer(), """{"title":"B","page":3,"total":9,"finished":1}""", Reading()))
        val pins = PhotoPins("a", null)
        assertEquals(pins, MetaJson.decode(PhotoPins.serializer(), MetaJson.encode(PhotoPins.serializer(), pins), PhotoPins()))
    }

    private val start = LocalDate.of(2026, 9, 18)
    private val run = Run(ProgramId.SOFT, "A", start, 75, false, Programs.soft.tasks)
    private fun p(id: String, date: LocalDate, a: Angle = Angle.FRONT) = PhotoMeta(id, date, a, PhotoSource.CAMERA, "t$id")
    private val photos = listOf(
        p("old", start.minusDays(20)), p("d1side", start, Angle.SIDE), p("d1", start), p("d8", start.plusDays(7)), p("d15", start.plusDays(14)),
    )

    @Test fun `before defaults to the first front photo of this attempt, after to the latest`() {
        val pair = photoPair(photos, run, PhotoPins())
        assertEquals("d1", pair.before?.id)
        assertEquals("d15", pair.after?.id)
        assertFalse(pair.pinnedBefore)
    }

    @Test fun `pins win, and a missing pin falls back`() {
        val pair = photoPair(photos, run, PhotoPins(before = "old", after = "d8"))
        assertEquals("old", pair.before?.id)
        assertEquals("d8", pair.after?.id)
        assertTrue(pair.pinnedBefore && pair.pinnedAfter)
        // A restart moves the start date; the pinned before stays.
        assertEquals("old", photoPair(photos, run.copy(startDate = start.plusDays(10)), PhotoPins(before = "old")).before?.id)
        assertEquals("d1", photoPair(photos, run, PhotoPins(before = "deleted")).before?.id)
        assertNull(photoPair(listOf(photos[0]), run, PhotoPins()).after)
    }

    @Test fun `week grid scores every task`() {
        val today = LocalDate.of(2026, 9, 27) // a Sunday
        val w1 = Workout("w1", "Run", 60, false)
        val days = mapOf(
            LocalDate.of(2026, 9, 21) to DayRecord(mapOf("w1" to "t", "diet" to "t"), listOf(w1)),
            LocalDate.of(2026, 9, 22) to DayRecord(mapOf("w1" to "t", "read" to "t"), listOf(w1.copy(mins = 93)), ReadLog("B", 30, 0, 12)),
            today to DayRecord(mapOf("diet" to "t")),
        )
        val g = weekGrid(run, days, today, 0)
        val workout = g.rows.first { it.task.id == "w1" }
        assertEquals(listOf(Cell.DONE, Cell.DONE, Cell.MISS, Cell.MISS, Cell.MISS, Cell.MISS, Cell.TODAY), workout.cells)
        assertEquals(2 to 6, workout.done to workout.count)
        assertEquals(listOf(60, 93, 0, 0, 0, 0, 0), workout.values)
        val diet = g.rows.first { it.task.id == "diet" }
        assertEquals(Cell.DONE, diet.cells.last())
        assertEquals(2 to 7, diet.done to diet.count)
        assertEquals(listOf(0, 12, 0, 0, 0, 0, 0), g.rows.first { it.task.id == "read" }.values)
        assertNull(diet.values)
        assertEquals(5 to 25, g.done to g.possible)
        // The week before Day 1 has nothing to count.
        assertNull(weekGrid(run, days, today, -2).pct)
    }

    @Test fun `best streak survives a miss`() {
        val clear = DayRecord(run.tasks.associate { it.id to "t" })
        val days = (0..2).associate { start.plusDays(it.toLong()) to clear } + (4..5).associate { start.plusDays(it.toLong()) to clear }
        val p = compute(run, days, start.plusDays(5))
        assertEquals(3, p.best)
        assertEquals(2, p.streak)
    }

    @Test fun `book log lists the current book, finished ones and older logged titles`() {
        val d = { n: Long -> LocalDate.of(2026, 9, 1).plusDays(n) }
        val days = mapOf(
            d(0) to DayRecord(read = ReadLog("Old one", 100, 100, 100)),
            d(1) to DayRecord(read = ReadLog("Deep Work", 20, 300, 20)),
            d(2) to DayRecord(read = ReadLog("Deep Work", 300, 300, 280)),
            d(3) to DayRecord(read = ReadLog("Atomic Habits", 15, 320, 15)),
            d(4) to DayRecord(read = ReadLog("atomic habits ", 30, 320, 15)),
        )
        val reading = Reading("Atomic Habits", 30, 320, 1, listOf(Book("Deep Work", 300, d(2).toString()), Book("Never logged", 0, d(3).toString())))
        val log = bookLog(reading, days)
        assertEquals(listOf("Atomic Habits", "Never logged", "Deep Work", "Old one"), log.map { it.title })
        val now = log[0]
        assertTrue(now.current && !now.finished)
        assertEquals(30 to 320, now.page to now.total)
        assertEquals(d(3) to d(4), now.from to now.to)
        val deep = log.first { it.title == "Deep Work" }
        assertTrue(deep.finished)
        assertEquals(300, deep.pages)
        // Reached its last page before books were recorded, so it still counts as finished.
        assertTrue(log.last().finished)
        assertNull(log.first { it.title == "Never logged" }.from)
        assertEquals(emptyList<BookEntry>(), bookLog(Reading(), emptyMap()))
    }
}
