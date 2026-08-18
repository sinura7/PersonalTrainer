package com.sinura.personaltrainer.data

object SampleData {
    val squat = Exercise(
        id = "ex-squat",
        name = "Goblet Squat",
        muscleGroup = "Legs",
        equipment = "Dumbbell",
        instructions = listOf(
            "Hold a dumbbell at your chest with elbows tucked.",
            "Sit your hips back and down until thighs are parallel.",
            "Drive through your heels to stand tall.",
        ),
    )

    val pushUp = Exercise(
        id = "ex-pushup",
        name = "Push-Up",
        muscleGroup = "Chest",
        equipment = "Bodyweight",
        instructions = listOf(
            "Set hands slightly wider than shoulders.",
            "Lower your chest until elbows are about 90 degrees.",
            "Press the floor away and lock out without sagging hips.",
        ),
    )

    val row = Exercise(
        id = "ex-row",
        name = "Bent-Over Row",
        muscleGroup = "Back",
        equipment = "Dumbbells",
        instructions = listOf(
            "Hinge at the hips with a flat back.",
            "Pull the dumbbells to your lower ribs.",
            "Lower under control and keep your neck long.",
        ),
    )

    val hinge = Exercise(
        id = "ex-hinge",
        name = "Romanian Deadlift",
        muscleGroup = "Hamstrings",
        equipment = "Dumbbells",
        instructions = listOf(
            "Start standing tall with a soft knee bend.",
            "Push hips back while keeping the weights close to your legs.",
            "Stand up by squeezing your glutes.",
        ),
    )

    val plank = Exercise(
        id = "ex-plank",
        name = "Front Plank",
        muscleGroup = "Core",
        equipment = "Bodyweight",
        instructions = listOf(
            "Set elbows under shoulders and squeeze your glutes.",
            "Keep a straight line from head to heels.",
            "Breathe steadily without dropping your hips.",
        ),
    )

    val lunge = Exercise(
        id = "ex-lunge",
        name = "Reverse Lunge",
        muscleGroup = "Legs",
        equipment = "Dumbbells",
        instructions = listOf(
            "Step one foot back and lower the back knee toward the floor.",
            "Keep the front knee stacked over the mid-foot.",
            "Push through the front heel to stand.",
        ),
    )

    val overheadPress = Exercise(
        id = "ex-press",
        name = "Overhead Press",
        muscleGroup = "Shoulders",
        equipment = "Dumbbells",
        instructions = listOf(
            "Start with dumbbells at shoulder height.",
            "Press overhead without arching your lower back.",
            "Lower with control to the start position.",
        ),
    )

    val jumpJack = Exercise(
        id = "ex-jacks",
        name = "Jumping Jacks",
        muscleGroup = "Full Body",
        equipment = "Bodyweight",
        instructions = listOf(
            "Jump the feet out as the arms reach overhead.",
            "Land softly and keep a light bounce.",
            "Stay tall through the ribcage.",
        ),
    )

    val mountainClimber = Exercise(
        id = "ex-climber",
        name = "Mountain Climbers",
        muscleGroup = "Core",
        equipment = "Bodyweight",
        instructions = listOf(
            "Start in a strong high plank.",
            "Drive one knee toward your chest, then switch.",
            "Keep the hips level and shoulders stacked.",
        ),
    )

    val hipOpener = Exercise(
        id = "ex-hip",
        name = "World's Greatest Stretch",
        muscleGroup = "Hips",
        equipment = "Bodyweight",
        instructions = listOf(
            "Step into a long lunge and plant the same-side hand.",
            "Rotate the free arm toward the ceiling.",
            "Keep the back leg long and breathe into the hip.",
        ),
    )

    val catCow = Exercise(
        id = "ex-catcow",
        name = "Cat-Cow",
        muscleGroup = "Spine",
        equipment = "Bodyweight",
        instructions = listOf(
            "Start on all fours with a neutral spine.",
            "Inhale as you arch and look slightly up.",
            "Exhale as you round and tuck the pelvis.",
        ),
    )

    val workouts: List<Workout> = listOf(
        Workout(
            id = "w-full-body",
            name = "Full Body Strength",
            description = "A balanced session covering squat, hinge, push, pull, and core.",
            durationMinutes = 35,
            difficulty = Difficulty.BEGINNER,
            category = WorkoutCategory.FULL_BODY,
            items = listOf(
                WorkoutSet(squat, sets = 3, reps = "10", restSeconds = 60),
                WorkoutSet(hinge, sets = 3, reps = "8", restSeconds = 60),
                WorkoutSet(pushUp, sets = 3, reps = "8-12", restSeconds = 45),
                WorkoutSet(row, sets = 3, reps = "10", restSeconds = 45),
                WorkoutSet(plank, sets = 3, reps = "30 sec", restSeconds = 30),
            ),
        ),
        Workout(
            id = "w-upper",
            name = "Upper Body Push & Pull",
            description = "Build pressing and rowing strength with simple dumbbell work.",
            durationMinutes = 30,
            difficulty = Difficulty.INTERMEDIATE,
            category = WorkoutCategory.STRENGTH,
            items = listOf(
                WorkoutSet(pushUp, sets = 4, reps = "10", restSeconds = 45),
                WorkoutSet(row, sets = 4, reps = "10", restSeconds = 45),
                WorkoutSet(overheadPress, sets = 3, reps = "8", restSeconds = 60),
                WorkoutSet(plank, sets = 3, reps = "40 sec", restSeconds = 30),
            ),
        ),
        Workout(
            id = "w-lower",
            name = "Lower Body Power",
            description = "Legs and glutes with lunges, squats, and posterior-chain work.",
            durationMinutes = 32,
            difficulty = Difficulty.INTERMEDIATE,
            category = WorkoutCategory.STRENGTH,
            items = listOf(
                WorkoutSet(squat, sets = 4, reps = "8", restSeconds = 75),
                WorkoutSet(lunge, sets = 3, reps = "8/side", restSeconds = 60),
                WorkoutSet(hinge, sets = 3, reps = "10", restSeconds = 60),
                WorkoutSet(plank, sets = 2, reps = "45 sec", restSeconds = 30),
            ),
        ),
        Workout(
            id = "w-hiit",
            name = "HIIT Cardio Finisher",
            description = "Short intervals to raise heart rate without equipment.",
            durationMinutes = 18,
            difficulty = Difficulty.ADVANCED,
            category = WorkoutCategory.CARDIO,
            items = listOf(
                WorkoutSet(jumpJack, sets = 4, reps = "40 sec", restSeconds = 20),
                WorkoutSet(mountainClimber, sets = 4, reps = "30 sec", restSeconds = 20),
                WorkoutSet(squat, sets = 3, reps = "15", restSeconds = 30),
            ),
        ),
        Workout(
            id = "w-mobility",
            name = "Core & Mobility Reset",
            description = "Recover with hip openers, spinal movement, and a steady plank.",
            durationMinutes = 20,
            difficulty = Difficulty.BEGINNER,
            category = WorkoutCategory.MOBILITY,
            items = listOf(
                WorkoutSet(catCow, sets = 2, reps = "8 breaths", restSeconds = 15),
                WorkoutSet(hipOpener, sets = 2, reps = "5/side", restSeconds = 20),
                WorkoutSet(plank, sets = 3, reps = "25 sec", restSeconds = 30),
            ),
        ),
    )

    val defaultProfile = UserProfile(
        displayName = "Allen",
        goal = "Get stronger 3 days a week",
        weeklyWorkoutGoal = 3,
    )
}
