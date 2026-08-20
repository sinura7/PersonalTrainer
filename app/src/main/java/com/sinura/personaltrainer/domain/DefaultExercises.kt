package com.sinura.personaltrainer.domain

object DefaultExercises {
    fun catalog(): List<Exercise> = listOf(
        exercise("Barbell Back Squat", "Quads"),
        exercise("Front Squat", "Quads"),
        exercise("Goblet Squat", "Quads"),
        exercise("Bulgarian Split Squat", "Quads"),
        exercise("Walking Lunge", "Quads"),
        exercise("Leg Press", "Quads"),
        exercise("Leg Extension", "Quads"),
        exercise("Conventional Deadlift", "Posterior chain"),
        exercise("Romanian Deadlift", "Hamstrings"),
        exercise("Trap Bar Deadlift", "Posterior chain"),
        exercise("Hip Thrust", "Glutes"),
        exercise("Leg Curl", "Hamstrings"),
        exercise("Standing Calf Raise", "Calves"),
        exercise("Barbell Bench Press", "Chest"),
        exercise("Incline Bench Press", "Chest"),
        exercise("Dumbbell Bench Press", "Chest"),
        exercise("Push-Up", "Chest"),
        exercise("Chest Fly", "Chest"),
        exercise("Overhead Press", "Shoulders"),
        exercise("Seated Dumbbell Press", "Shoulders"),
        exercise("Lateral Raise", "Shoulders"),
        exercise("Face Pull", "Rear delts"),
        exercise("Barbell Row", "Back"),
        exercise("Pendlay Row", "Back"),
        exercise("One-Arm Dumbbell Row", "Back"),
        exercise("Lat Pulldown", "Back"),
        exercise("Pull-Up", "Back"),
        exercise("Chin-Up", "Back"),
        exercise("Seated Cable Row", "Back"),
        exercise("Barbell Curl", "Biceps"),
        exercise("Dumbbell Curl", "Biceps"),
        exercise("Tricep Pushdown", "Triceps"),
        exercise("Skull Crusher", "Triceps"),
        exercise("Close-Grip Bench Press", "Triceps"),
        exercise("Plank", "Core"),
        exercise("Hanging Leg Raise", "Core"),
        exercise("Cable Crunch", "Core"),
    )

    private fun exercise(name: String, muscleGroup: String): Exercise {
        val id = "ex-" + name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return Exercise(
            id = id,
            name = name,
            muscleGroup = muscleGroup,
            notes = "",
            isCustom = false,
        )
    }
}
