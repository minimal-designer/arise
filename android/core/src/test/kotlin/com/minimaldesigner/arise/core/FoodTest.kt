package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FoodTest {
    private fun t(s: String) = LocalDateTime.parse(s)
    private fun d(s: String) = LocalDate.parse(s)

    // Health Connect meals, out of order, across two days; one has no macros, one no name.
    private val meals = listOf(
        HealthMeal(t("2026-09-25T19:30"), "Salmon and rice", 3, kcal = 700.0, protein = 45.0, carbs = 60.0, fat = 25.0),
        HealthMeal(t("2026-09-25T08:00"), "Oats", 1, kcal = 450.0, protein = 20.0, carbs = 70.0, fat = 10.0),
        HealthMeal(t("2026-09-24T13:00"), "  ", 2, kcal = 600.0),
        HealthMeal(t("2026-09-25T16:00"), "Apple", 4, kcal = 80.0, protein = null, carbs = 20.0),
    )
    private val goals = FoodGoals(kcal = 2000.0, protein = 150.0)
    private val log = foodLog(meals, goals)

    @Test fun groupsByDayOldestFirstWithMealsInTimeOrder() {
        assertEquals(listOf("2026-09-24", "2026-09-25"), log.days.map { it.date })
        assertEquals(listOf("08:00", "16:00", "19:30"), log.days[1].meals.map { it.time })
        assertEquals(listOf("Breakfast", "Snack", "Dinner"), log.days[1].meals.map { it.slot })
        assertEquals(goals, log.goals)
    }

    @Test fun dayTotalsAddWhatIsThere() {
        val day = log.days[1]
        assertEquals(1230.0, day.kcal!!, 1e-9)
        assertEquals(65.0, day.protein!!, 1e-9) // the apple has no protein: skipped, not zero
        assertEquals(150.0, day.carbs!!, 1e-9)
        val lunch = log.days[0]
        assertEquals(600.0, lunch.kcal!!, 1e-9)
        assertNull(lunch.protein) // nothing logged a value
    }

    @Test fun namesAndSlotsHaveFallbacks() {
        val m = log.days[0].meals.single()
        assertEquals("Lunch", m.slot)
        assertEquals("Logged food", m.what)
        assertEquals("Meal", mealSlot(0))
        assertEquals("Meal", mealSlot(99))
    }

    @Test fun emptyHasNoDays() {
        val empty = foodLog(emptyList(), FoodGoals())
        assertEquals(0, empty.days.size)
        assertNull(homeDay(empty, d("2026-09-25")))
    }

    @Test fun homeDayIsTodayElseLatest() {
        assertEquals("2026-09-25", homeDay(log, d("2026-09-25"))?.date)
        assertEquals("2026-09-25", homeDay(log, d("2026-09-27"))?.date)
        assertEquals("2026-09-24", homeDay(log, d("2026-09-24"))?.date)
    }

    @Test fun averageSkipsMissingDays() {
        assertEquals((600.0 + 1230.0) / 2, averageKcal(log.days)!!, 1e-9)
        assertNull(averageKcal(emptyList()))
    }
}
