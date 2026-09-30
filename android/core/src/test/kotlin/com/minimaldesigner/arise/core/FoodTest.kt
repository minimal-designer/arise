package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FoodTest {
    // Shaped like arise-food's /v1/food, including a key the app doesn't know yet.
    private val body = """
        {"sha":"abc1234","pulledAt":"2026-09-27T01:32:26+00:00","somethingNew":1,
         "goals":{"kcal":2300,"protein":190,"carbs":230,"fat":70,"waterL":3.5},
         "carbsFatSince":"2026-09-24",
         "weighIns":[
           {"date":"2026-09-08","kg":82.0,"scale":"home","note":"starting weight"},
           {"date":"2026-09-23","kg":78.4,"scale":"gym"},
           {"date":"2026-09-25","kg":80.9,"scale":"home"}],
         "days":[
           {"date":"2026-09-25","kcal":1950,"protein":104,"carbs":259,"fat":55,"weight":null,
            "meals":[{"slot":"Breakfast","time":null,"what":"Oats","kcal":585,"protein":36,"carbs":69,"fat":18,"est":false}]},
           {"date":"2026-09-08","kcal":1716,"protein":86,"carbs":null,"fat":null,"overall":"Full day",
            "meals":[{"slot":"Lunch","what":"Sandwich","kcal":586.5,"protein":37,"est":true}]}]}
    """.trimIndent()

    private val log = FoodJson.parse(body)
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun parsesAndSortsDaysOldestFirst() {
        assertEquals(listOf("2026-09-08", "2026-09-25"), log.days.map { it.date })
        assertEquals(2300.0, log.goals.kcal)
        assertNull(log.days[0].carbs)
        assertEquals(586.5, log.days[0].meals[0].kcal)
        assertEquals(true, log.days[0].meals[0].est)
        assertEquals(false, log.days[1].meals[0].est)
        assertEquals("gym", log.weighIns[1].scale)
    }

    @Test fun emptyObjectParses() {
        val empty = FoodJson.parse("{}")
        assertEquals(0, empty.days.size)
        assertNull(homeDay(empty, d("2026-09-25")))
    }

    @Test fun homeDayIsTodayElseLatest() {
        assertEquals("2026-09-25", homeDay(log, d("2026-09-25"))?.date)
        assertEquals("2026-09-25", homeDay(log, d("2026-09-27"))?.date)
        assertEquals("2026-09-08", homeDay(log, d("2026-09-08"))?.date)
    }

    @Test fun weightUsesTheFirstScaleOnly() {
        assertNull(weightOn(log, d("2026-09-07")))
        assertEquals(82.0, weightOn(log, d("2026-09-08"))!!, 1e-9)
        // The gym 78.4 on the 23rd is a different scale, so it's ignored.
        assertEquals(82.0, weightOn(log, d("2026-09-24"))!!, 1e-9)
        assertEquals(80.9, weightOn(log, d("2026-09-26"))!!, 1e-9)
        assertEquals(-1.1, weightChange(log, d("2026-09-08"), d("2026-09-26"))!!, 1e-9)
        assertNull(weightChange(log, d("2026-09-01"), d("2026-09-26")))
    }

    @Test fun averageSkipsMissingDays() {
        assertEquals((1716 + 1950) / 2.0, averageKcal(log.days)!!, 1e-9)
        assertNull(averageKcal(emptyList()))
    }
}
