package com.sinura.personaltrainer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "gyms", indices = [Index("sortOrder")])
data class GymEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val badge: String,
    val sortOrder: Int,
    val archivedAtMs: Long?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
