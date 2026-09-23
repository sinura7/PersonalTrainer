package com.sinura.personaltrainer.ui.workout

import java.io.File

/*
 * The one way the workout tests read production source, and it is for bans only: a line
 * that must never come back. What the floor shows and does is held by rendered and
 * ViewModel tests; a positive `contains` on a source line pins where code happens to sit,
 * and the next packet that moves it (W2a's dead-code sweep, W2d's extraction out of
 * ActiveWorkoutViewModel) fails the pin while the behaviour stays fine.
 *
 * Paths resolve from the repository root and from `app/`, as Gradle may run the tests from
 * either. A missing file fails loudly instead of letting a ban pass on an empty string.
 */

private val OWNED_ROOTS = listOf(
    File("app/src/main/java/com/sinura/personaltrainer"),
    File("../app/src/main/java/com/sinura/personaltrainer"),
)

/**
 * The workout screen's code, relative to the owned roots, each with what its scan must find.
 *
 * `ui/workout` is the screen and its ViewModel; `workout` is where the ViewModel's undo, save
 * and draft pieces were extracted to (FloorUndo, SavedStateFloorUndo, FinishWorkout,
 * WorkoutDraftCache), and where W2d moves more. A ban that reads only the first would pass
 * over whatever moved to the second. The whole app is not read: other screens legitimately
 * say some of these words (Home and History each have their own `onUndoOfferHandled`).
 *
 * The anchor is a declaration each package is known to hold, so a moved or renamed directory
 * cannot leave a package-wide ban passing over nothing: `class ActiveWorkoutViewModel` survives
 * any split of its body into helpers beside it, and `sealed interface FloorUndo` is the undo
 * token the second package was made for.
 */
private val WORKOUT_PACKAGES = listOf(
    "ui/workout" to "class ActiveWorkoutViewModel",
    "workout" to "sealed interface FloorUndo",
)

/** The production file at [relative] (from `com/sinura/personaltrainer/`), as text. */
internal fun ownedSource(relative: String): String {
    val file = OWNED_ROOTS.map { File(it, relative) }.firstOrNull { it.isFile }
        ?: throw AssertionError("no production file $relative under ${OWNED_ROOTS.map { it.path }}")
    return file.readText()
}

/** Whether a production file exists at [relative]; for bans on files that were retired. */
internal fun ownedSourceExists(relative: String): Boolean = OWNED_ROOTS.any { File(it, relative).isFile }

/**
 * Every Kotlin file under the workout packages (`ui/workout` and `workout`), recursively, as
 * (path from `com/sinura/personaltrainer/`, text).
 *
 * A ban whose intent is "nobody on the workout screen does X" reads this rather than one file,
 * so moving code between files, or out of `ui/workout` into `workout` (W2d), cannot silently
 * empty it. Each package must exist, hold Kotlin files and hold its anchor, or the scan fails.
 */
internal fun workoutPackageSourceFiles(): List<Pair<String, String>> = WORKOUT_PACKAGES.flatMap { (pkg, anchor) ->
    val root = OWNED_ROOTS.map { File(it, pkg) }.firstOrNull { it.isDirectory }
        ?: throw AssertionError("no $pkg package under ${OWNED_ROOTS.map { it.path }}")
    val files = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.path }
        .map { "$pkg/${it.relativeTo(root).path}" to it.readText() }
        .toList()
    if (files.isEmpty()) throw AssertionError("the $pkg scan found no Kotlin files under ${root.path}")
    if (files.none { (_, text) -> text.contains(anchor) }) {
        throw AssertionError("the $pkg scan did not find \"$anchor\"; it is reading the wrong place")
    }
    files
}

/** The workout packages as one text, for a ban that does not care which file holds a line. */
internal fun workoutPackageSources(): String = workoutPackageSourceFiles().joinToString(separator = "\n") { it.second }

/** Fails, naming the file, if any file of the workout packages contains [needle]. */
internal fun assertWorkoutPackageLacks(needle: String, reason: String = "the workout packages must not contain \"$needle\"") {
    val offenders = workoutPackageSourceFiles().filter { (_, text) -> text.contains(needle) }.map { it.first }
    if (offenders.isNotEmpty()) throw AssertionError("$reason; found in $offenders")
}

/**
 * The body of `fun [name](` wherever it is declared in the workout packages, braces included.
 * It must be declared exactly once, so a ban on what the body does cannot pass because the
 * function moved, was renamed or was overloaded. It must have a block body: a function that
 * became a one-line `= …` delegate has no braces of its own, and the first `{` after it would
 * be the next declaration's, so the ban would read someone else's code. That fails instead,
 * naming the delegate, so the ban can follow the code it was about.
 */
internal fun workoutFunctionBody(name: String): String {
    val marker = "fun $name("
    val declared = workoutPackageSourceFiles().filter { (_, text) -> text.contains(marker) }
    if (declared.size != 1) {
        throw AssertionError("expected one \"$marker\" in the workout packages, found it in ${declared.map { it.first }}")
    }
    val declaration = declared.single()
    val file = declaration.first
    val text = declaration.second
    val start = text.indexOf(marker)
    if (text.indexOf(marker, start + marker.length) >= 0) throw AssertionError("\"$marker\" is declared twice in $file")
    val params = closingParen(text, start + marker.length - 1)
        ?: throw AssertionError("the parameter list of \"$marker\" in $file never closes")
    val open = text.indexOf('{', params)
    if (open < 0) throw AssertionError("\"$marker\" in $file has no body")
    // Between the parameters and the brace sits at most a return type; an `=` there means an
    // expression body, and the brace belongs to whatever follows it.
    val signatureTail = text.substring(params + 1, open)
    if (signatureTail.contains('=')) {
        throw AssertionError("\"$marker\" in $file has an expression body (`${signatureTail.trim()}`); the ban must read the code it delegates to")
    }
    var depth = 0
    for (index in open until text.length) {
        when (text[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return text.substring(start, index + 1)
            }
        }
    }
    throw AssertionError("the body of \"$marker\" never closes")
}

/** The index of the `)` that closes the `(` at [open] in [text], or null if it never does. */
private fun closingParen(text: String, open: Int): Int? {
    var depth = 0
    for (index in open until text.length) {
        when (text[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }
    return null
}

/** [text] from the first [marker] on; fails if the marker is gone, so the slice is never empty by accident. */
internal fun sourceFrom(text: String, marker: String): String {
    val at = text.indexOf(marker)
    if (at < 0) throw AssertionError("\"$marker\" is gone; the ban that slices from it needs a new anchor")
    return text.substring(at)
}

/** [text] from [start] up to the next [end] after it; fails if either marker is gone. */
internal fun sourceBetween(text: String, start: String, end: String): String {
    val from = text.indexOf(start)
    if (from < 0) throw AssertionError("\"$start\" is gone; the ban that slices from it needs a new anchor")
    val to = text.indexOf(end, from + start.length)
    if (to < 0) throw AssertionError("nothing \"$end\" follows \"$start\"; the ban that slices to it needs a new anchor")
    return text.substring(from, to)
}
