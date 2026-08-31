package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AuxiliaryPack
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@Composable
fun AuxiliaryPackList(
    usedPackIds: Set<String>,
    onPick: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = PlanDayCopy.PICK_AUX,
) {
    val warmups = AuxiliaryPacks.warmups.filter { it.id !in usedPackIds }
    val mobility = AuxiliaryPacks.mobility.filter { it.id !in usedPackIds }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(title, modifier = Modifier.weight(1f))
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
                onClick = { onPick(pack.id) },
            )
        }
    }
}

object AuxiliaryPackTags {
    fun row(packId: String): String = "aux-pack-$packId"
}
