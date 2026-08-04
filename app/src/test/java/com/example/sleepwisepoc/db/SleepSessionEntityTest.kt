package com.example.sleepwisepoc.db

import com.example.sleepwisepoc.StageTick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepSessionEntityTest {

    private fun makeEntity(
        windowStart: String = "2026-08-04T00:30:00Z",
        windowEnd: String   = "2026-08-04T07:00:00Z",
        startedAt: String   = "2026-08-04T00:29:00Z",
        endedAt: String?    = "2026-08-04T06:45:00Z",
        firedAt: String?    = "2026-08-04T06:30:00Z",
        firedReason: String? = "favorable",
        stages: List<StageTick> = listOf(
            StageTick(t = "2026-08-04T01:00:00Z", stage = "Light", conf = 0.80f, stable = true)
        ),
    ) = SleepSessionEntity(
        id = 1L,
        windowStart = windowStart,
        windowEnd   = windowEnd,
        startedAt   = startedAt,
        endedAt     = endedAt,
        firedAt     = firedAt,
        firedReason = firedReason,
        stages      = stages,
    )

    @Test
    fun toSessionUpload_mapsAllFields() {
        val entity = makeEntity()
        val upload = entity.toSessionUpload()

        assertEquals(entity.windowStart,  upload.window_start)
        assertEquals(entity.windowEnd,    upload.window_end)
        assertEquals(entity.startedAt,    upload.started_at)
        assertEquals(entity.endedAt,      upload.ended_at)
        assertEquals(entity.firedAt,      upload.fired_at)
        assertEquals(entity.firedReason,  upload.fired_reason)
        assertEquals(entity.stages,       upload.stages)
    }

    @Test
    fun toSessionUpload_nullOptionalFields_remainNull() {
        val entity = makeEntity(endedAt = null, firedAt = null, firedReason = null)
        val upload = entity.toSessionUpload()

        assertNull(upload.ended_at)
        assertNull(upload.fired_at)
        assertNull(upload.fired_reason)
    }

    @Test
    fun toSessionUpload_emptyStages_mapsToEmptyList() {
        val entity = makeEntity(stages = emptyList())
        assertEquals(emptyList<StageTick>(), entity.toSessionUpload().stages)
    }
}
