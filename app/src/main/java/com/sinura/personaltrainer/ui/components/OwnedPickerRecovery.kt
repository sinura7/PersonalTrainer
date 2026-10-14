package com.sinura.personaltrainer.ui.components

import androidx.lifecycle.SavedStateHandle
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftVersion
import com.sinura.personaltrainer.data.draft.PickerContextCodec
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.PickerContextSnapshot
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleGroups
import kotlinx.coroutines.CoroutineScope

internal class OwnedPickerRecovery(
    scope: CoroutineScope,
    contextDrafts: PickerContextDrafts,
    customDrafts: CustomExerciseDrafts,
    repository: ExerciseRepository,
    savedState: SavedStateHandle,
    owner: suspend () -> DraftOwner,
    stillOwned: suspend (DraftOwner) -> Boolean,
    private val onQuery: (String) -> Unit,
    private val selectCreated: suspend (Exercise) -> Boolean,
    private val onSelected: () -> Unit = {},
    ownedAtCommit: suspend (DraftOwner) -> Boolean,
    private val onReset: () -> Unit = {},
) {
    private val key = "picker.framework.v1"
    val view = PickerViewRecovery(
        scope = scope, drafts = contextDrafts, owner = owner, stillOwned = stillOwned,
        initial = { PickerContextSnapshot() },
        framework = { requested -> readSeed(savedState.get<String>(key), requested) },
        cache = { savedState[key] = encodeSeed(it) }, restored = { onQuery(it.query) },
    )
    val create = PickerCreateRecovery(scope = scope, drafts = customDrafts,
        repository = repository, stillOwned = stillOwned, ownedAtCommit = ownedAtCommit)

    fun openCreate() {
        if (!view.editable || create.status.value.busy) return
        val requested = view.activeOwner ?: return
        val fields = view.fields.value
        create.open(requested = requested, name = fields.query.trim(),
            muscle = MuscleGroups.forNewDraft(fields.muscle), onReady = ::createdDraftReady)
    }

    fun change(value: PickerContextSnapshot) { view.changed(value); onQuery(view.fields.value.query) }
    fun backToList() {
        create.keep { view.changed(view.fields.value.copy(creating = false)) }
    }
    fun saveCreate() { create.save { useSaved() } }
    fun useSaved() {
        create.useSaved(select = selectCreated, onComplete = ::selectionComplete)
    }
    private fun createdDraftReady(id: String) { view.changed(view.fields.value.copy(creating = true, customDraftId = id)) }
    private fun selectionComplete() {
        view.changed(view.fields.value.copy(creating = false, customDraftId = null))
        view.leave(onSelected)
    }
    fun leave(onKept: () -> Unit) {
        if (!canLeave()) return
        create.keep { view.leave(onKept) }
    }
    fun canLeave(): Boolean = !view.status.value.busy && !view.status.value.changedElsewhere && !create.status.value.busy
    fun discardPrivate() {
        if (view.status.value.busy || create.status.value.busy) return
        val requested = view.activeOwner ?: return
        create.discard(requested) { view.reset(onReset) }
    }
}

private fun readSeed(text: String?, requested: DraftOwner): PickerFrameworkSeed? = try {
    text?.let { source ->
        val item = JsonParser.parseString(source).asJsonObject
        val owner = DraftOwner(DraftForm.valueOf(item.get("form").asString), item.get("owner").asString)
        if (owner != requested) null else {
            val version = item.get("generation").takeUnless { it.isJsonNull }?.asString?.let {
                DraftVersion(owner = DraftOwner(DraftForm.LIBRARY, item.get("basisOwner").asString),
                    generation = it, revision = item.get("revision").asLong)
            }
            PickerFrameworkSeed(owner = owner, version = version,
                fields = PickerContextCodec.decodeDraft(item.get("fields")), unkept = item.get("unkept").asBoolean)
        }
    }
} catch (_: Exception) { null }

private fun encodeSeed(seed: PickerFrameworkSeed): String = JsonObject().apply {
    addProperty("form", seed.owner.form.name)
    addProperty("owner", seed.owner.identity)
    addProperty("generation", seed.version?.generation)
    addProperty("basisOwner", seed.version?.owner?.identity)
    addProperty("revision", seed.version?.revision)
    addProperty("unkept", seed.unkept)
    add("fields", PickerContextCodec.encodeDraft(seed.fields))
}.toString()
