package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.backup.BackupActivity
import com.sinura.personaltrainer.data.backup.BackupActivityTemplate
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.mapper.toBackup
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityTemplate
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.StrengthBlock

/** Export/import the foundation activity tables. Caller owns the transaction. */
object ActivityBackupIo {
    suspend fun snapshot(dao: ActivityDao): Pair<List<BackupActivity>, List<BackupActivityTemplate>> {
        val activities = dao.getAllGraphs()
            .map { it.toDomain() }
            .filter { it.status != ActivityStatus.ACTIVE }
            .map { it.toBackup() }
        val templates = dao.getAllTemplateGraphs().map { it.toDomain().toBackup() }
        return activities to templates
    }

    suspend fun replace(dao: ActivityDao, document: BackupDocument) {
        dao.deleteAllSessions()
        dao.deleteAllTemplates()
        document.activityTemplates.forEach { insertTemplate(dao, it.toDomain(), updatedAtMs = 0L) }
        document.activities
            .filter { it.status != "ACTIVE" }
            .forEach { insertSession(dao, it.toDomain()) }
    }

    suspend fun insertSession(dao: ActivityDao, session: ActivitySession) {
        dao.insertSession(session.toEntity())
        insertBlocks(dao, session.blocks, sessionId = session.id, templateId = null)
    }

    suspend fun insertTemplate(dao: ActivityDao, template: ActivityTemplate, updatedAtMs: Long = 0L) {
        dao.upsertTemplate(template.toEntity(updatedAtMs))
        insertBlocks(dao, template.blocks, sessionId = null, templateId = template.id)
    }

    private suspend fun insertBlocks(
        dao: ActivityDao,
        blocks: List<ActivityBlock>,
        sessionId: String?,
        templateId: String?,
    ) {
        blocks.forEach { block ->
            dao.insertBlock(block.toEntity(sessionId, templateId))
            when (block) {
                is StrengthBlock ->
                    if (block.sets.isNotEmpty()) {
                        dao.insertStrengthSets(block.sets.map { it.toEntity(block.id) })
                    }
                is CardioBlock ->
                    if (block.intervals.isNotEmpty()) {
                        dao.insertCardioIntervals(block.intervals.map { it.toEntity(block.id) })
                    }
            }
        }
    }
}
