package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class HealthTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun t(s: String) = LocalDateTime.parse(s)

    @Test fun bmiAndBands() {
        assertEquals(24.7, bmi(80.0, 180.0))
        assertNull(bmi(80.0, null))
        assertNull(bmi(null, 180.0))
        assertEquals(BmiBand.UNDER, bmiBand(18.4))
        assertEquals(BmiBand.HEALTHY, bmiBand(18.5))
        assertEquals(BmiBand.OVER, bmiBand(25.0))
        assertEquals(BmiBand.OBESE, bmiBand(30.0))
    }

    @Test fun healthWeightsWinOverTheFoodLog() {
        val food = FoodLog(
            weighIns = listOf(
                WeighIn("2026-09-08", 82.0, "home"),
                WeighIn("2026-09-23", 78.4, "gym"),
                WeighIn("2026-09-25", 80.9, "home"),
            ),
        )
        val (fromFood, src) = bodyWeights(HealthData(), food)
        assertEquals(WeightSource.FOOD, src)
        // Only the first weigh-in's scale.
        assertEquals(listOf(82.0, 80.9), fromFood.map { it.kg })

        val health = HealthData(weights = listOf(WeightPoint(d("2026-09-20"), 95.0)))
        assertEquals(WeightSource.HEALTH, bodyWeights(health, food).second)
        assertEquals(WeightSource.NONE, bodyWeights(null, null).second)
    }

    @Test fun startWeightIsTheOneAtDayOne() {
        val pts = listOf(WeightPoint(d("2026-09-01"), 99.0), WeightPoint(d("2026-09-10"), 97.0), WeightPoint(d("2026-09-20"), 95.0))
        assertEquals(97.0, startWeight(pts, d("2026-09-12"))!!.kg, 0.0)
        // Nothing before Day 1: the first one after it.
        assertEquals(99.0, startWeight(pts, d("2026-08-01"))!!.kg, 0.0)
        assertEquals(-2.0, weightDelta(pts, d("2026-09-10"), d("2026-09-25"))!!, 0.001)
        assertNull(weightDelta(pts, d("2026-08-01"), d("2026-09-25")))
    }

    private val strength = TaskDef("w1", "Workout", kind = TaskKind.WORKOUT)
    private val outdoor = TaskDef("w2", "Outdoor workout", kind = TaskKind.WORKOUT, outdoor = true)

    @Test fun suggestsTheLongestUnloggedSessionOfTheDay() {
        val gym = HealthWorkout("a", t("2026-09-27T07:00"), t("2026-09-27T08:05"), "Strength", false)
        val run = HealthWorkout("b", t("2026-09-27T18:00"), t("2026-09-27T18:50"), "Run", true)
        val old = HealthWorkout("c", t("2026-09-26T07:00"), t("2026-09-26T09:00"), "Run", true)
        val all = listOf(run, old, gym)
        val today = d("2026-09-27")

        assertEquals("a", suggestWorkout(strength, all, null, today)!!.id)
        // Outdoor tasks prefer outdoor sessions.
        assertEquals("b", suggestWorkout(outdoor, all, null, today)!!.id)
        // Once the gym session is logged, the run is what's left.
        val day = DayRecord(done = mapOf("w1" to "x"), workouts = listOf(Workout("w1", "Strength", 65, false)))
        assertEquals("b", suggestWorkout(strength, all, day, today)!!.id)
        assertNull(suggestWorkout(strength, all, null, d("2026-09-25")))
        assertEquals(listOf("a", "b"), workoutsOn(all, today).map { it.id })
    }

    @Test fun sleepNightsAndAverages() {
        val nap = HealthSleep(t("2026-09-27T14:00"), t("2026-09-27T14:40"), 40)
        val n1 = HealthSleep(t("2026-09-26T23:30"), t("2026-09-27T06:30"), 400)
        val n2 = HealthSleep(t("2026-09-26T00:30"), t("2026-09-26T07:30"), 410)
        val sleeps = listOf(nap, n1, n2)

        assertEquals(n1, sleepEnding(sleeps, d("2026-09-27")))
        val week = sleepWeek(sleeps, d("2026-09-27"), 3)
        assertEquals(listOf(d("2026-09-25"), d("2026-09-26"), d("2026-09-27")), week.map { it.first })
        assertEquals(listOf(null, n2, n1), week.map { it.second })

        // 23:30 and 00:30 average to midnight, not noon.
        val avg = sleepAverage(listOf(n1, n2))!!
        assertEquals(LocalTime.MIDNIGHT, avg.bed)
        assertEquals(LocalTime.of(7, 0), avg.wake)
        assertEquals(405, avg.asleepMins)
        assertNull(sleepAverage(emptyList()))
        assertEquals("6 h 40 m", fmtSleep(400))
    }
}
