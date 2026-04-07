package com.example.nanobot.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "behavior_events")
data class BehaviorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val key: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val metadata: String,
    val timestamp: Long
)
