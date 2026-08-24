package com.sinura.personaltrainer.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.sinura.personaltrainer.data.local.entity.ActivityBlockEntity
import com.sinura.personaltrainer.data.local.entity.ActivityCardioIntervalEntity
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.entity.ActivityStrengthSetEntity
import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity

data class ActivityBlockGraph(
    @Embedded val block: ActivityBlockEntity,
    @Relation(parentColumn = "id", entityColumn = "blockId")
    val strengthSets: List<ActivityStrengthSetEntity>,
    @Relation(parentColumn = "id", entityColumn = "blockId")
    val cardioIntervals: List<ActivityCardioIntervalEntity>,
)

data class ActivitySessionGraph(
    @Embedded val session: ActivitySessionEntity,
    @Relation(
        entity = ActivityBlockEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val blocks: List<ActivityBlockGraph>,
)

data class ActivityTemplateGraph(
    @Embedded val template: ActivityTemplateEntity,
    @Relation(
        entity = ActivityBlockEntity::class,
        parentColumn = "id",
        entityColumn = "templateId",
    )
    val blocks: List<ActivityBlockGraph>,
)
