package com.minimaldesigner.arise.core

/** The baked program data from arise-v2's `DATA` block. */
object Programs {
    val hard = Program(
        id = ProgramId.HARD, name = "75 Hard", level = "Hardest", length = 75, resetOnMiss = true,
        blurb = "Two workouts, strict diet, a gallon of water. No days off.",
        rule = "Miss a task and you restart at Day 1",
        tasks = listOf(
            TaskDef("w1", "Workout · 45 min", "Any training", TaskKind.WORKOUT),
            TaskDef("w2", "Outdoor workout · 45 min", "Rain or shine", TaskKind.WORKOUT, outdoor = true),
            TaskDef("diet", "Follow your diet", "No cheat meals, no alcohol", TaskKind.DIET),
            TaskDef("water", "Drink 3.8 L of water", "One gallon", TaskKind.WATER),
            TaskDef("read", "Read 10 pages", "Non-fiction", TaskKind.READ),
            TaskDef("photo", "Progress photo", "Front, side or back", TaskKind.PHOTO),
        ),
    )

    val soft = Program(
        id = ProgramId.SOFT, name = "75 Soft", level = "Balanced", length = 75, resetOnMiss = false,
        blurb = "One workout a day, eat well, one rest day a week.",
        rule = "A missed day breaks your streak, nothing more",
        tasks = listOf(
            TaskDef("w1", "Workout · 45 min", "Active recovery 1 day a week", TaskKind.WORKOUT),
            TaskDef("diet", "Eat well", "Drinks only on social occasions", TaskKind.DIET),
            TaskDef("water", "Drink 3 L of water", "", TaskKind.WATER),
            TaskDef("read", "Read 10 pages", "Any book", TaskKind.READ),
        ),
    )

    val custom = Program(
        id = ProgramId.CUSTOM, name = "Custom", level = "Your rules", length = 75, resetOnMiss = false,
        blurb = "Choose your own tasks, length and what a missed day means.",
        rule = "You decide",
        tasks = emptyList(),
    )

    val all = listOf(hard, soft, custom)

    fun of(id: ProgramId) = all.first { it.id == id }

    /** The 12 tasks the custom builder offers. */
    val presets = listOf(
        TaskDef("w1", "Workout · 45 min", kind = TaskKind.WORKOUT),
        TaskDef("w2", "Outdoor workout · 45 min", kind = TaskKind.WORKOUT, outdoor = true),
        TaskDef("diet", "Follow your diet", kind = TaskKind.DIET),
        TaskDef("water", "Drink 3 L of water", kind = TaskKind.WATER),
        TaskDef("read", "Read 10 pages", kind = TaskKind.READ),
        TaskDef("photo", "Progress photo", kind = TaskKind.PHOTO),
        TaskDef("steps", "10,000 steps", kind = TaskKind.WALK),
        TaskDef("protein", "Hit 190 g protein", kind = TaskKind.DIET),
        TaskDef("sleep", "Sleep 7+ hours", kind = TaskKind.SLEEP),
        TaskDef("meditate", "Meditate 10 min", kind = TaskKind.MIND),
        TaskDef("nosugar", "No added sugar", kind = TaskKind.DIET),
        TaskDef("cold", "Cold shower", kind = TaskKind.WATER),
    )

    /** Default custom picks, as in arise-v2's onboarding. */
    val defaultCustom = listOf("w1", "diet", "water", "read")

    val customLengths = listOf(30, 45, 75, 100)

    val workoutTypes = listOf("Strength", "Walk", "Run", "Cycle", "HIIT", "Yoga", "Swim", "Sport")

    /** Name and fraction of the challenge length. */
    val milestones = listOf(
        "Week 1" to 7.0 / 75, "Week 2" to 14.0 / 75, "Halfway" to 0.5, "Final stretch" to 0.8, "Done" to 1.0,
    )
}
