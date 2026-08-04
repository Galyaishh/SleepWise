package com.example.sleepwisepoc.db

import com.example.sleepwisepoc.StageTick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun emptyList_roundtrip() {
        val json = converters.fromTicks(emptyList())
        val result = converters.toTicks(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun singleTick_roundtrip_preservesAllFields() {
        val tick = StageTick(t = "2026-08-04T02:00:00Z", stage = "Light", conf = 0.87f, stable = true)
        val json = converters.fromTicks(listOf(tick))
        val result = converters.toTicks(json)

        assertEquals(1, result.size)
        assertEquals(tick.t, result[0].t)
        assertEquals(tick.stage, result[0].stage)
        assertEquals(tick.conf, result[0].conf, 0.001f)
        assertEquals(tick.stable, result[0].stable)
    }

    @Test
    fun multipleTicks_roundtrip_preservesOrder() {
        val ticks = listOf(
            StageTick(t = "2026-08-04T02:00:00Z", stage = "Deep",  conf = 0.91f, stable = true),
            StageTick(t = "2026-08-04T02:01:00Z", stage = "Light", conf = 0.72f, stable = false),
            StageTick(t = "2026-08-04T02:02:00Z", stage = "Light", conf = 0.85f, stable = true),
        )
        val result = converters.toTicks(converters.fromTicks(ticks))

        assertEquals(3, result.size)
        ticks.forEachIndexed { i, expected ->
            assertEquals(expected.stage, result[i].stage)
            assertEquals(expected.conf, result[i].conf, 0.001f)
        }
    }
}
