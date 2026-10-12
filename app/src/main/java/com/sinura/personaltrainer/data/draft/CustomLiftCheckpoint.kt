package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.domain.LoadType

/** Raw fields remain distinct from normalized, immutable business intent. */
internal data class CustomLiftDraft(
    val createId: String,
    val editId: String?,
    val name: String,
    val muscle: String,
    val notes: String,
    val loadType: LoadType,
    val before: CustomLiftRecord?,
) {
    override fun toString(): String = "CustomLiftDraft(redacted)"
}

internal data class CustomLiftRecord(val row: ExerciseEntity, val credits: List<ExerciseMuscleEntity>) {
    override fun toString(): String = "CustomLiftRecord(redacted)"
}
internal data class CustomLiftCommand(val intended: CustomLiftRecord, val before: CustomLiftRecord?) {
    override fun toString(): String = "CustomLiftCommand(redacted)"
}

internal object CustomLiftCodec : DraftCodec<CustomLiftDraft, CustomLiftCommand> {
    override val format = "custom-lift"
    override val version = 1

    override fun encodeDraft(value: CustomLiftDraft): JsonElement = JsonObject().apply {
        addProperty("createId", value.createId)
        add("editId", value.editId?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        addProperty("name", value.name)
        addProperty("muscle", value.muscle)
        addProperty("notes", value.notes)
        addProperty("loadType", value.loadType.name)
        add("before", value.before?.let(::encodeRecord) ?: JsonNull.INSTANCE)
    }

    override fun decodeDraft(value: JsonElement): CustomLiftDraft {
        val item = value.asJsonObject
        val draft = CustomLiftDraft(
            createId = item.text("createId"), editId = item.nullableText("editId"),
            name = item.text("name"), muscle = item.text("muscle"), notes = item.text("notes"),
            loadType = LoadType.valueOf(item.text("loadType")),
            before = item.get("before").takeUnless { it.isJsonNull }?.let(::decodeRecord),
        )
        require(draft.createId.startsWith("ex-custom-"))
        val suffix = draft.createId.removePrefix("ex-custom-")
        require(java.util.UUID.fromString(suffix).toString() == suffix)
        require(draft.editId == draft.before?.row?.id)
        return draft
    }

    override fun encodeCommand(value: CustomLiftCommand): JsonElement = JsonObject().apply {
        add("intended", encodeRecord(value.intended))
        add("before", value.before?.let(::encodeRecord) ?: JsonNull.INSTANCE)
    }

    override fun decodeCommand(value: JsonElement): CustomLiftCommand {
        val item = value.asJsonObject
        val result = CustomLiftCommand(
            intended = decodeRecord(item.get("intended")),
            before = item.get("before").takeUnless { it.isJsonNull }?.let(::decodeRecord),
        )
        require(result.before == null || result.before.row.id == result.intended.row.id)
        require(result.intended.row.name.isNotBlank())
        return result
    }

    private fun encodeRecord(value: CustomLiftRecord): JsonObject = JsonObject().apply {
        val row = value.row
        addProperty("id", row.id); addProperty("name", row.name); addProperty("muscle", row.muscleGroup)
        addProperty("notes", row.notes); addProperty("equipment", row.equipment); addProperty("load", row.loadType)
        add("movement", row.movementKey?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        add("image", row.imageKey?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        addProperty("nameKey", row.nameKey); addProperty("stamp", row.updatedAtMs)
        add("credits", JsonArray().apply {
            value.credits.sortedBy { it.muscleKey }.forEach { credit ->
                add(JsonObject().apply { addProperty("key", credit.muscleKey); addProperty("weight", credit.weight) })
            }
        })
    }

    private fun decodeRecord(value: JsonElement): CustomLiftRecord {
        val item = value.asJsonObject
        val id = item.text("id").also { require(it.isNotBlank()) }
        val row = ExerciseEntity(
            id = id, name = item.text("name"), muscleGroup = item.text("muscle"), notes = item.text("notes"),
            isCustom = true, equipment = item.text("equipment"), loadType = LoadType.valueOf(item.text("load")).name,
            movementKey = item.nullableText("movement"), imageKey = item.nullableText("image"),
            nameKey = item.text("nameKey"), updatedAtMs = item.get("stamp").also {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
            }.asString.toLong(),
        )
        val credits = item.getAsJsonArray("credits").map {
            val credit = it.asJsonObject
            val weight = credit.get("weight").also { number ->
                require(number.isJsonPrimitive && number.asJsonPrimitive.isNumber)
            }.asDouble.also { number -> require(number.isFinite() && number > 0.0 && number <= 1.0) }
            ExerciseMuscleEntity(exerciseId = id, muscleKey = credit.text("key"), weight = weight)
        }.sortedBy { it.muscleKey }
        require(credits.map { it.muscleKey }.distinct().size == credits.size)
        return CustomLiftRecord(row, credits)
    }

    private fun JsonObject.text(key: String): String = get(key).also {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
    }.asString
    private fun JsonObject.nullableText(key: String): String? = get(key).takeUnless { it.isJsonNull }?.let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
        it.asString
    }
}

internal object CustomLiftIndexCodec : DraftCodec<String, String> {
    override val format = "custom-lift-index"
    override val version = 1
    override fun encodeDraft(value: String): JsonElement = com.google.gson.JsonPrimitive(value)
    override fun decodeDraft(value: JsonElement): String = value.also {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
    }.asString.also { require(java.util.UUID.fromString(it).toString() == it) }
    override fun encodeCommand(value: String): JsonElement = encodeDraft(value)
    override fun decodeCommand(value: JsonElement): String = decodeDraft(value)
}
