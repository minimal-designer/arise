package com.minimaldesigner.arise.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 1.2.0: the Food tab reads meals from Health Connect (whatever food app writes there).
// The app layer (data/Health.kt) turns NutritionRecords into [HealthMeal]s; [foodLog] groups
// them into days. Goals live in ARISE, because Health Connect doesn't store them.

/** Daily targets, set in Profile & settings → Body & health (meta `foodGoals`). */
@Serializable
data class FoodGoals(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val waterL: Double? = null,
)

data class Meal(
    val slot: String? = null,
    val time: String? = null,
    val what: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

data class FoodDay(
    val date: String,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val meals: List<Meal> = emptyList(),
) {
    val day: LocalDate get() = LocalDate.parse(date)
}

/** The food the Food tab shows: [days] oldest first. */
data class FoodLog(
    val goals: FoodGoals = FoodGoals(),
    val days: List<FoodDay> = emptyList(),
)

/** One NutritionRecord. [mealType] is Health Connect's MealType (0 unknown, 1 breakfast, 2 lunch, 3 dinner, 4 snack). */
data class HealthMeal(
    val time: LocalDateTime,
    val name: String? = null,
    val mealType: Int = 0,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

fun mealSlot(mealType: Int): String = when (mealType) {
    1 -> "Breakfast"
    2 -> "Lunch"
    3 -> "Dinner"
    4 -> "Snack"
    else -> "Meal"
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Health Connect meals as days, oldest first, each with its meals in time order. A day's
 * totals add up the meals that have a value, and stay null when none does.
 */
fun foodLog(meals: List<HealthMeal>, goals: FoodGoals): FoodLog {
    fun sum(ms: List<HealthMeal>, f: (HealthMeal) -> Double?): Double? =
        ms.mapNotNull(f).takeIf { it.isNotEmpty() }?.sum()
    val days = meals.groupBy { it.time.toLocalDate() }.toSortedMap().map { (date, ms) ->
        val sorted = ms.sortedBy { it.time }
        FoodDay(
            date = date.toString(),
            kcal = sum(sorted) { it.kcal },
            protein = sum(sorted) { it.protein },
            carbs = sum(sorted) { it.carbs },
            fat = sum(sorted) { it.fat },
            meals = sorted.map { m ->
                Meal(
                    slot = mealSlot(m.mealType),
                    time = m.time.format(HM),
                    what = m.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Logged food",
                    kcal = m.kcal, protein = m.protein, carbs = m.carbs, fat = m.fat,
                )
            },
        )
    }
    return FoodLog(goals, days)
}

/** The day the Home card shows: today if it's logged, else the latest logged day (v2 Home). */
fun homeDay(log: FoodLog, today: LocalDate): FoodDay? =
    log.days.firstOrNull { it.date == today.toString() } ?: log.days.lastOrNull()

/** Mean calories over the days that have a figure. */
fun averageKcal(days: List<FoodDay>): Double? =
    days.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.average()
