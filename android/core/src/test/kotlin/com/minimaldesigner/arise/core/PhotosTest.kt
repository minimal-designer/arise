package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PhotosTest {
    private val start = LocalDate.of(2026, 9, 1)
    private val run = Run(ProgramId.HARD, "A", start, 75, true, Programs.hard.tasks)

    private fun p(id: String, day: Int, angle: Angle = Angle.FRONT, at: String = "t$id") =
        PhotoMeta(id, start.plusDays(day - 1L), angle, PhotoSource.CAMERA, at)

    @Test
    fun `day and week numbers follow the challenge start`() {
        assertEquals(1, dayOf(run, start))
        assertEquals(8, dayOf(run, start.plusDays(7)))
        assertEquals(0, dayOf(run, start.minusDays(1)))
        assertEquals(1, dayOf(null, start))
        assertEquals(listOf(1, 0, 0, 1, 2, 2, 11), listOf(1, 0, -20, 7, 8, 14, 75).map(::weekOf))
    }

    @Test
    fun `photos sort by date then by when they were added`() {
        val a = p("a", 3, at = "2")
        val b = p("b", 3, at = "1")
        val c = p("c", 1)
        assertEquals(listOf("c", "b", "a"), sortedPhotos(listOf(a, b, c)).map { it.id })
    }

    private val now = Attempt(start, null, "")

    @Test
    fun `grouping is newest week first, newest photo first inside a week`() {
        val photos = listOf(p("w3", 15), p("w1b", 7, Angle.SIDE), p("w1a", 1), p("w2", 8, Angle.BACK))
        val all = photoWeeks(listOf(now), photos, null)
        assertEquals(listOf(3, 2, 1), all.map { it.week })
        assertEquals(listOf("w1b", "w1a"), all.last().photos.map { it.id })
        assertEquals(start to start.plusDays(6), all.last().from to all.last().to)
        val side = photoWeeks(listOf(now), photos, Angle.SIDE)
        assertEquals(listOf(1 to listOf("w1b")), side.map { it.week to it.photos.map { m -> m.id } })
    }

    @Test
    fun `a restart keeps older photos in their own attempt's weeks`() {
        // Attempt 1 ran from Sep 1 and was restarted on day 10; attempt 2 started that day.
        val restart = start.plusDays(9)
        val first = Attempt(start, restart, "75 Hard · attempt 1")
        val second = Attempt(restart, null, "")
        val photos = listOf(p("a1d2", 2), p("a1d9", 9), p("a2d1", 10), p("a2d8", 17), p("old", -20))
        val weeks = photoWeeks(listOf(first, second), photos, null)
        assertEquals(listOf(second to 2, second to 1, first to 2, first to 1, null to 0), weeks.map { it.attempt to it.week })
        // The restart day's photo is Day 1 of the new attempt, not day 10 of the old one.
        assertEquals(1, photoDay(listOf(first, second), restart))
        assertEquals(9, photoDay(listOf(first, second), start.plusDays(8)))
        assertNull(photoDay(listOf(first, second), start.minusDays(20)))
        // Attempt 1's second week is cut short by the restart.
        assertEquals(start.plusDays(7) to restart, weeks[2].from to weeks[2].to)
        // Outside any attempt, photos group by calendar week, Monday first.
        val loose = weeks.last()
        assertEquals(java.time.DayOfWeek.MONDAY, loose.from.dayOfWeek)
        assertEquals(loose.from.plusDays(6), loose.to)
    }

    @Test
    fun `compare uses first and last of the angle, all means front`() {
        val photos = listOf(p("f2", 20), p("s1", 1, Angle.SIDE), p("f1", 1), p("f3", 40), p("s2", 30, Angle.SIDE))
        assertEquals("f1" to "f3", comparePair(photos, null)?.let { it.first.id to it.second.id })
        assertEquals("s1" to "s2", comparePair(photos, Angle.SIDE)?.let { it.first.id to it.second.id })
        assertNull(comparePair(photos, Angle.BACK))
        assertNull(comparePair(listOf(p("x", 1)), null))
    }

    @Test
    fun `unknown keys fall back safely`() {
        assertEquals(Angle.FRONT, Angle.of("diagonal"))
        assertEquals(Angle.BACK, Angle.of("back"))
        assertEquals(PhotoSource.IMPORT75, PhotoSource.of("import75"))
        assertEquals(PhotoSource.PICKER, PhotoSource.of("?"))
    }
}
