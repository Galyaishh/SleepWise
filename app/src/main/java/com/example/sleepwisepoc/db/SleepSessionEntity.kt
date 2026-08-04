package com.example.sleepwisepoc.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sleepwisepoc.SessionUpload
import com.example.sleepwisepoc.StageTick
import java.time.Instant

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val windowStart: String,
    val windowEnd: String,
    val startedAt: String,
    val endedAt: String? = null,
    val firedAt: String? = null,
    val firedReason: String? = null,
    val stages: List<StageTick> = emptyList(),
    val uploadStatus: String = "PENDING",
    val createdAt: String = Instant.now().toString(),
)

fun SleepSessionEntity.toSessionUpload() = SessionUpload(
    user_id = "",
    window_start = windowStart,
    window_end = windowEnd,
    started_at = startedAt,
    ended_at = endedAt,
    fired_at = firedAt,
    fired_reason = firedReason,
    stages = stages,
)
