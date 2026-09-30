package com.minimaldesigner.arise.core

import java.time.LocalDate

/** arise-v2's "Try it with sample data": 75 Hard, Day 38, today half done. */
object Sample {
    private val indoor = listOf("Strength", "HIIT", "Strength", "Cycle", "Strength", "Yoga", "Strength")
    private val outdoor = listOf("Walk", "Run", "Walk", "Cycle", "Run", "Sport", "Walk")

    fun run(today: LocalDate) = Run(
        program = ProgramId.HARD, name = "Miranda", startDate = today.minusDays(37),
        lengthDays = 75, resetOnMiss = true, tasks = Programs.hard.tasks,
    )

    fun days(today: LocalDate): Days {
        val start = today.minusDays(37)
        return (0 until 38).associate { i ->
            val done = Programs.hard.tasks.associate { it.id to "sample" }.toMutableMap()
            val workouts = mutableListOf(
                Workout("w1", indoor[i % 7], 45 + (i % 3) * 15, false),
                Workout("w2", outdoor[i % 7], 45 + (i % 2) * 20, true),
            )
            if (i == 37) {
                done -= listOf("w2", "read", "photo"); workouts.removeAt(1)
            }
            start.plusDays(i.toLong()) to DayRecord(done, workouts)
        }
    }
}
