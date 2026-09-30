package com.minimaldesigner.arise.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

// The food log as arise-food (github.com/minimal-designer/arise-food) serves it on GET /v1/food.
// Every field is optional so a new or missing key on the server never breaks the app.

@Serializable
data class FoodGoals(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val waterL: Double? = null,
)

@Serializable
data class Meal(
    val slot: String? = null,
    val time: String? = null,
    val what: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val est: Boolean = false,
    val note: String? = null,
)

@Serializable
data class FoodDay(
    val date: String,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val weight: Double? = null,
    val overall: String? = null,
    val meals: List<Meal> = emptyList(),
) {
    val day: LocalDate get() = LocalDate.parse(date)
}

@Serializable
data class WeighIn(
    val date: String,
    val kg: Double? = null,
    val scale: String? = null,
    val note: String? = null,
)

@Serializable
data class FoodLog(
    val sha: String? = null,
    val pulledAt: String? = null,
    val goals: FoodGoals = FoodGoals(),
    val carbsFatSince: String? = null,
    val weighIns: List<WeighIn> = emptyList(),
    val days: List<FoodDay> = emptyList(),
)

object FoodJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** Parses a /v1/food body; days come back oldest first. Throws on malformed JSON. */
    fun parse(body: String): FoodLog = json.decodeFromString<FoodLog>(body).let { log ->
        log.copy(days = log.days.sortedBy { it.date })
    }
}

/** The day the Home card shows: today if it's logged, else the latest logged day (v2 Home). */
fun homeDay(log: FoodLog, today: LocalDate): FoodDay? =
    log.days.firstOrNull { it.date == today.toString() } ?: log.days.lastOrNull()

/**
 * Weight on [date]: the latest weigh-in on or before it, taken only from the scale of
 * the first weigh-in, because different scales disagree by kilos (v2 `weightOn`).
 */
fun weightOn(log: FoodLog, date: LocalDate): Double? {
    val scale = log.weighIns.firstOrNull()?.scale
    return log.weighIns
        .filter { it.scale == scale && it.kg != null && it.date <= date.toString() }
        .maxByOrNull { it.date }
        ?.kg
}

/** Weight change from [from] to [to] on the same scale, or null if either end is unknown. */
fun weightChange(log: FoodLog, from: LocalDate, to: LocalDate): Double? {
    val a = weightOn(log, from) ?: return null
    val b = weightOn(log, to) ?: return null
    return b - a
}

/** Mean calories over the days that have a figure. */
fun averageKcal(days: List<FoodDay>): Double? =
    days.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.average()
