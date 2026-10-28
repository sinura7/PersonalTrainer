package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.sinura.personaltrainer.domain.EffortSource
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.workout.WorkoutClockCheckpoint
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutEntryCheckpoint
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import com.sinura.personaltrainer.workout.WorkoutUndoCheckpoint
import com.sinura.personaltrainer.workout.WorkoutUndoToken

internal object WorkoutCheckpointCodec : DraftCodec<WorkoutEntryCheckpoint, Unit> {
    override val format = "workout-entry"
    override val version = 1
    override fun encodeDraft(value: WorkoutEntryCheckpoint): JsonElement = JsonObject().apply {
        addProperty("session", value.sessionId); addProperty("start", value.startedAt)
        addProperty("authoredRevision", value.authoredRevision)
        addNullable("selected", value.selectedExerciseId); addNullable("notes", value.notes)
        addNullable("notesBase", value.notesBase); addNullable("acknowledged", value.acknowledgedCommand)
        add("editing", value.editingOriginal?.let(WorkoutSetCheckpointCodec::encodeDraft) ?: JsonNull.INSTANCE)
        add("lifts", JsonArray().apply {
            value.lifts.toSortedMap().forEach { (id, draft) -> add(JsonObject().apply {
                addProperty("exercise", id); add("values", encodeValues(WorkoutSetValues(
                    weightKg = draft.weightKg, reps = draft.reps, rpe = draft.rpe,
                    isWarmup = draft.isWarmup, durationSeconds = draft.durationSeconds,
                )))
                addProperty("dirty", draft.dirty); addProperty("extra", draft.extraSetRequested)
                addProperty("effortSource", draft.effortSource.name)
            }) }
        })
        add("numbers", JsonArray().apply { value.numbers.forEach { add(encodeWorkoutNumber(it)) } })
        add("clocks", value.clocks?.let(::encodeClocks) ?: JsonNull.INSTANCE)
        add("undo", JsonArray().apply { value.undo.forEach { add(encodeUndo(it)) } })
        addProperty("undoDwellMs", value.undoDwellMs)
    }

    override fun decodeDraft(value: JsonElement): WorkoutEntryCheckpoint {
        val item = value.asJsonObject
        val session = item.text("session").also { require(it.isNotBlank()) }
        val notes = item.nullableText("notes")
        val entries = item.getAsJsonArray("lifts").map { raw ->
            val lift = raw.asJsonObject
            val id = lift.text("exercise").also { require(it.isNotBlank()) }
            val fields = decodeValues(lift.get("values"))
            id to WorkoutDraft(
                sessionId = session, exerciseId = id, weightKg = fields.weightKg,
                reps = fields.reps, rpe = fields.rpe, isWarmup = fields.isWarmup,
                notes = notes.orEmpty(), durationSeconds = fields.durationSeconds,
                dirty = lift.flag("dirty"), extraSetRequested = lift.flag("extra"),
                effortSource = lift.nullableText("effortSource")?.let { name ->
                    runCatching { EffortSource.valueOf(name) }.getOrDefault(EffortSource.NONE)
                } ?: EffortSource.NONE,
            )
        }
        require(entries.map { it.first }.distinct().size == entries.size)
        val editing = item.get("editing").notNull()?.let(WorkoutSetCheckpointCodec::decodeDraft)
        require(editing == null || editing.sessionId == session)
        val numbers = item.getAsJsonArray("numbers").map(::decodeWorkoutNumber)
        require(numbers.map { it.exerciseId to it.field }.distinct().size == numbers.size && numbers.count { it.open } <= 1)
        return WorkoutEntryCheckpoint(
            sessionId = session, startedAt = item.integer("start").also { require(it >= 0) },
            authoredRevision = item.integer("authoredRevision").also { require(it >= 0) },
            lifts = entries.toMap(), selectedExerciseId = item.nullableText("selected"),
            notes = notes, notesBase = item.nullableText("notesBase"), editingOriginal = editing,
            acknowledgedCommand = item.nullableText("acknowledged"),
            numbers = numbers,
            clocks = item.get("clocks").notNull()?.let(::decodeClocks),
            undo = item.getAsJsonArray("undo")?.map(::decodeUndo).orEmpty(),
            undoDwellMs = item.get("undoDwellMs")?.strictLong()?.also { require(it >= 0) } ?: 0,
        )
    }
    override fun encodeCommand(value: Unit): JsonElement = error("Entry files cannot hold a business command")
    override fun decodeCommand(value: JsonElement): Unit = error("Entry files cannot hold a business command")
}

internal object WorkoutSetCheckpointCodec : DraftCodec<WorkoutSetSave, WorkoutSetSave> {
    override val format = "workout-set-command"
    override val version = 1
    override fun encodeDraft(value: WorkoutSetSave): JsonElement = JsonObject().apply {
        addProperty("session", value.sessionId); addProperty("exercise", value.exerciseId)
        addProperty("set", value.setId); addProperty("completed", value.completedAt)
        add("values", encodeValues(value.values))
        add("original", value.original?.let(::encodeValues) ?: JsonNull.INSTANCE)
    }
    override fun decodeDraft(value: JsonElement): WorkoutSetSave = decode(value, historicalValues = false)
    internal fun decodeFinishedDraft(value: JsonElement): WorkoutSetSave = decode(value, historicalValues = true)
    private fun decode(value: JsonElement, historicalValues: Boolean): WorkoutSetSave {
        val item = value.asJsonObject
        return WorkoutSetSave(
            sessionId = item.text("session").also { require(it.isNotBlank()) },
            exerciseId = item.text("exercise").also { require(it.isNotBlank()) },
            setId = item.text("set").also { require(it.isNotBlank()) },
            completedAt = item.integer("completed").also { require(it >= 0) },
            values = decodeValues(value = item.get("values"), original = historicalValues),
            original = item.get("original").notNull()?.let { decodeValues(it, original = true) },
        )
    }
    override fun encodeCommand(value: WorkoutSetSave): JsonElement = encodeDraft(value)
    override fun decodeCommand(value: JsonElement): WorkoutSetSave = decodeDraft(value)
}

internal data class WorkoutNotesCommand(val sessionId: String, val before: String, val intended: String) {
    override fun toString(): String = "WorkoutNotesCommand(redacted)"
}
internal object WorkoutNotesCheckpointCodec : DraftCodec<WorkoutNotesCommand, WorkoutNotesCommand> {
    override val format = "workout-notes-command"
    override val version = 1
    override fun encodeDraft(value: WorkoutNotesCommand): JsonElement = JsonObject().apply {
        addProperty("session", value.sessionId); addProperty("before", value.before)
        addProperty("intended", value.intended)
    }
    override fun decodeDraft(value: JsonElement): WorkoutNotesCommand {
        val item = value.asJsonObject
        return WorkoutNotesCommand(
            sessionId = item.text("session").also { require(it.isNotBlank()) },
            before = item.text("before"), intended = item.text("intended"),
        ).also { require(it.before == it.before.trim() && it.intended == it.intended.trim()) }
    }
    override fun encodeCommand(value: WorkoutNotesCommand): JsonElement = encodeDraft(value)
    override fun decodeCommand(value: JsonElement): WorkoutNotesCommand = decodeDraft(value)
}

private fun encodeValues(value: WorkoutSetValues): JsonObject = JsonObject().apply {
    addProperty("weight", value.weightKg); addProperty("reps", value.reps)
    add("effort", value.rpe?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    addProperty("warmup", value.isWarmup)
    add("seconds", value.durationSeconds?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
}
private fun decodeValues(value: JsonElement, original: Boolean = false): WorkoutSetValues {
    val item = value.asJsonObject
    return WorkoutSetValues(
        weightKg = item.get("weight").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }
            .asDouble.also { require(it.isFinite() && it >= 0) },
        reps = item.integer("reps").toIntExact().also { require(original || it >= 0) },
        rpe = item.get("effort").notNull()?.strictLong()?.toIntExact()?.also { require(it in 1..10) },
        isWarmup = item.flag("warmup"),
        durationSeconds = item.get("seconds").notNull()?.strictLong()?.toIntExact()?.also { require(it >= 0) },
    )
}

internal fun encodeWorkoutNumber(value: WorkoutNumberDraft): JsonObject = JsonObject().apply {
    addNullable("exercise", value.exerciseId); addProperty("field", value.field.name)
    addProperty("text", value.text); addNullable("unit", value.weightUnit?.name)
    addProperty("selectionStart", value.selectionStart); addProperty("selectionEnd", value.selectionEnd)
    addProperty("open", value.open)
}
internal fun decodeWorkoutNumber(value: JsonElement): WorkoutNumberDraft {
    val item = value.asJsonObject
    val field = WorkoutNumberField.valueOf(item.text("field"))
    val unit = item.nullableText("unit")?.let(WeightUnit::valueOf)
    val text = item.text("text")
    require((field == WorkoutNumberField.WEIGHT) == (unit != null))
    return WorkoutNumberDraft(
        exerciseId = item.nullableText("exercise"), field = field, text = text, weightUnit = unit,
        selectionStart = item.integer("selectionStart").toIntExact().also { require(it in 0..text.length) },
        selectionEnd = item.integer("selectionEnd").toIntExact().also { require(it in 0..text.length) },
        open = item.flag("open"),
    )
}

private fun encodeUndo(value: WorkoutUndoCheckpoint): JsonObject = JsonObject().apply {
    addProperty("key", value.key); addProperty("message", value.message)
    when (val token = value.token) {
        is WorkoutUndoToken.SetReceipt -> {
            addProperty("kind", "set"); addProperty("id", token.id); addProperty("exercise", token.exerciseId)
            addProperty("number", token.number); addProperty("completed", token.completedAt)
            add("values", encodeValues(token.values))
        }
        is WorkoutUndoToken.LiftReceipt -> {
            addProperty("kind", "lift"); addProperty("id", token.id); addProperty("exercise", token.exerciseId)
            addProperty("name", token.name); addProperty("order", token.order)
            addProperty("sets", token.sets); addProperty("reps", token.reps)
            add("weight", token.weightKg?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
            addProperty("rest", token.rest)
            add("seconds", token.seconds?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
            add("secondsMax", token.secondsMax?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        }
    }
}
private fun decodeUndo(value: JsonElement): WorkoutUndoCheckpoint {
    val item = value.asJsonObject
    val id = item.text("id").also { require(it.isNotBlank()) }
    val exercise = item.text("exercise").also { require(it.isNotBlank()) }
    val token = when (item.text("kind")) {
        "set" -> WorkoutUndoToken.SetReceipt(
            id = id, exerciseId = exercise, number = item.integer("number").toIntExact(),
            completedAt = item.integer("completed"), values = decodeValues(item.get("values"), original = true),
        )
        "lift" -> WorkoutUndoToken.LiftReceipt(
            id = id, exerciseId = exercise, name = item.text("name"), order = item.integer("order").toIntExact(),
            sets = item.integer("sets").toIntExact(), reps = item.integer("reps").toIntExact(),
            weightKg = item.get("weight").notNull()?.asDouble?.also { require(it.isFinite()) },
            rest = item.integer("rest").toIntExact(), seconds = item.get("seconds").notNull()?.strictLong()?.toIntExact(),
            secondsMax = item.get("secondsMax").notNull()?.strictLong()?.toIntExact(),
        )
        else -> error("Unknown private undo token")
    }
    return WorkoutUndoCheckpoint(token = token, key = item.text("key"), message = item.text("message"))
}

private fun encodeClocks(value: WorkoutClockCheckpoint): JsonObject = JsonObject().apply {
    add("boot", value.boot?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    addNullable("holdExercise", value.holdExerciseId)
    add("hold", value.hold?.let { hold -> JsonObject().apply {
        addProperty("running", hold.running); addProperty("total", hold.totalSeconds)
        addProperty("start", hold.startElapsedRealtime); addProperty("deadline", hold.deadlineElapsedRealtime)
        addProperty("targetReached", hold.targetReached); addProperty("leadInStart", hold.leadInStartElapsedRealtime)
        addProperty("elapsed", hold.elapsedSeconds)
    } } ?: JsonNull.INSTANCE)
    add("stopwatches", JsonArray().apply { value.stopwatches.toSortedMap().forEach { (id, watch) ->
        add(JsonObject().apply {
            addProperty("exercise", id); addProperty("running", watch.running)
            addProperty("elapsed", watch.elapsedSeconds); addProperty("used", watch.used)
            addProperty("start", watch.startElapsedRealtime); addProperty("frozen", watch.frozenElapsedSeconds)
        })
    } })
}
private fun decodeClocks(value: JsonElement): WorkoutClockCheckpoint {
    val item = value.asJsonObject
    val holdExercise = item.nullableText("holdExercise")
    val hold = item.get("hold").notNull()?.asJsonObject?.let { raw -> HoldTimerUiState(
        running = raw.flag("running"), totalSeconds = raw.integer("total").toIntExact().also { require(it > 0) },
        startElapsedRealtime = raw.integer("start").also { require(it >= 0) },
        deadlineElapsedRealtime = raw.integer("deadline").also { require(it >= 0) },
        targetReached = raw.flag("targetReached"), leadInStartElapsedRealtime = raw.integer("leadInStart").also { require(it >= 0) },
        elapsedSeconds = raw.integer("elapsed").toIntExact().also { require(it >= 0) },
    ) }
    require((hold == null) == (holdExercise == null))
    val watches = item.getAsJsonArray("stopwatches").map { raw ->
        val watch = raw.asJsonObject
        val id = watch.text("exercise").also { require(it.isNotBlank()) }
        id to SetStopwatchUiState(
            running = watch.flag("running"), elapsedSeconds = watch.integer("elapsed").toIntExact().also { require(it >= 0) },
            used = watch.flag("used"), startElapsedRealtime = watch.integer("start").also { require(it >= 0) },
            frozenElapsedSeconds = watch.integer("frozen").toIntExact().also { require(it >= 0) }, exerciseId = id,
        )
    }
    require(watches.map { it.first }.distinct().size == watches.size)
    return WorkoutClockCheckpoint(
        boot = item.get("boot").notNull()?.strictLong(),
        holdExerciseId = holdExercise, hold = hold, stopwatches = watches.toMap(),
    )
}

private fun JsonObject.addNullable(key: String, value: String?) = add(key, value?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
private fun JsonObject.text(key: String): String = get(key).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
private fun JsonObject.nullableText(key: String): String? = get(key).notNull()?.also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }?.asString
private fun JsonElement.notNull(): JsonElement? = takeUnless { it.isJsonNull }
private fun JsonObject.integer(key: String): Long = get(key).strictLong()
private fun JsonElement.strictLong(): Long = also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }.asString.toLong()
private fun Long.toIntExact(): Int = Math.toIntExact(this)
private fun JsonObject.flag(key: String): Boolean = get(key).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
