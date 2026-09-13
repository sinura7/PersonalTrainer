package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AuxiliaryKind
import com.sinura.personaltrainer.domain.AuxiliaryPack
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.ExtraEquipment
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PickerStill
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.components.extraPackArtwork
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

@Composable
fun AuxiliaryPackList(
    usedPackIds: Set<String>,
    onPick: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = PlanDayCopy.PICK_AUX,
    suggestedKit: ExtraEquipment = ExtraEquipment.MIXED,
) {
    var kitName by rememberSaveable { mutableStateOf<String?>(null) }
    val kit = kitName?.let { ExtraEquipment.fromStorage(it) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        if (kit == null) {
            ExtraEquipmentList(
                title = ExtraEquipment.PICK,
                suggestedKit = suggestedKit,
                onPick = { kitName = it.name },
                onCancel = onCancel,
            )
        } else {
            val visible = AuxiliaryPacks.visibleFor(kit, usedPackIds)
            val warmups = visible.filter { it.kind == AuxiliaryKind.WARMUP }
            val mobility = visible.filter { it.kind == AuxiliaryKind.MOBILITY }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker(text = title, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { kitName = null },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = Metrics.touchMin),
                ) {
                    Text(
                        PlanDayCopy.CANCEL,
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                    )
                }
            }
            if (warmups.isEmpty() && mobility.isEmpty()) {
                Text(
                    PlanDayCopy.AUX_ALREADY,
                    style = InstrumentType.body,
                    color = TextPrimary,
                )
            } else {
                if (warmups.isNotEmpty()) {
                    Kicker(PlanDayCopy.WARM_UP)
                    PackGroup(warmups, onPick)
                }
                if (mobility.isNotEmpty()) {
                    Kicker(PlanDayCopy.MOBILITY)
                    PackGroup(mobility, onPick)
                }
            }
        }
    }
}

@Composable
private fun ExtraEquipmentList(
    title: String,
    suggestedKit: ExtraEquipment,
    onPick: (ExtraEquipment) -> Unit,
    onCancel: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Kicker(text = title, modifier = Modifier.weight(1f))
        TextButton(
            onClick = onCancel,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.heightIn(min = Metrics.touchMin),
        ) {
            Text(
                PlanDayCopy.CANCEL,
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
            )
        }
    }
    GroupedList(
        modifier = Modifier
            .testTag(ExtraEquipmentTags.PAGE)
            .selectableGroup(),
    ) {
        ExtraEquipment.entries.forEachIndexed { index, kit ->
            if (index > 0) HairlineDivider()
            val usual = kit == suggestedKit
            InstrumentRow(
                title = kit.label,
                subtitle = if (usual) ExtraEquipment.USUAL else kit.caption,
                selected = usual,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ExtraEquipmentTags.choice(kit))
                    .semantics { contentDescription = kit.label },
                onClick = { onPick(kit) },
                trailing = extraKitCheck(usual),
            )
        }
    }
}

@Composable
private fun PackGroup(
    packs: List<AuxiliaryPack>,
    onPick: (String) -> Unit,
) {
    GroupedList {
        packs.forEachIndexed { index, pack ->
            if (index > 0) HairlineDivider()
            InstrumentRow(
                title = pack.title,
                subtitle = pack.caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AuxiliaryPackTags.row(pack.id))
                    .semantics { contentDescription = pack.title },
                leading = {
                    PickerStill(
                        art = extraPackArtwork(pack.id),
                        size = ThumbSize.picker,
                    )
                },
                onClick = { onPick(pack.id) },
            )
        }
    }
}

private fun extraKitCheck(selected: Boolean): @Composable RowScope.() -> Unit = {
    if (selected) {
        Icon(
            imageVector = TemperIcons.Check,
            contentDescription = null,
            tint = Volt,
            modifier = Modifier.size(Metrics.icon),
        )
    }
}

object AuxiliaryPackTags {
    fun row(packId: String): String = "aux-pack-$packId"
}

object ExtraEquipmentTags {
    const val PAGE = "extra-equipment-page"

    fun choice(kit: ExtraEquipment): String = "extra-kit-${kit.name.lowercase()}"
}
