package com.example.sleepwisepoc.report

import com.example.sleepwisepoc.SessionRecord
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.WeeklyReport
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Generates a realistic 3-night weekly report for demo/offline mode.
 * Each night uses a different sleep-architecture pattern so every visual
 * element of the hypnogram (stage transitions, REM cycles, fragmentation)
 * is exercised.
 */
internal object SleepMockData {

    fun createReport(): WeeklyReport {
        val sessions = listOf(
            buildSession(
                id = 1L,
                daysAgo = 1,
                bedHour = 22, bedMin = 35,
                wakeHour = 6, wakeMin = 47,
                windowStartHour = 6, windowStartMin = 30,
                windowEndHour = 7,   windowEndMin = 0,
                firedReason = "favorable",
                pattern = NIGHT_GOOD,
            ),
            buildSession(
                id = 2L,
                daysAgo = 2,
                bedHour = 23, bedMin = 10,
                wakeHour = 6, wakeMin = 22,
                windowStartHour = 6, windowStartMin = 0,
                windowEndHour = 7,   windowEndMin = 0,
                firedReason = "favorable",
                pattern = NIGHT_AVERAGE,
            ),
            buildSession(
                id = 3L,
                daysAgo = 3,
                bedHour = 22, bedMin = 50,
                wakeHour = 7, wakeMin = 30,
                windowStartHour = 7, windowStartMin = 0,
                windowEndHour = 7,   windowEndMin = 30,
                firedReason = "fallback",
                pattern = NIGHT_FRAGMENTED,
            ),
        )

        return WeeklyReport(
            user_id          = "demo",
            sessions         = sessions,
            fired_count      = sessions.size,
            favorable_count  = sessions.count { it.fired_reason == "favorable" },
            fallback_count   = sessions.count { it.fired_reason == "fallback" },
            avg_window_minutes = 40f,
        )
    }

    // ── Sleep architecture patterns  (stage, duration in minutes) ────────────

    /** Textbook good night: two long deep cycles, three REM cycles */
    private val NIGHT_GOOD = listOf(
        "Wake"  to 5,
        "Light" to 10,
        "Deep"  to 40,
        "Light" to 15,
        "Deep"  to 50,
        "REM"   to 30,
        "Light" to 20,
        "Deep"  to 35,
        "REM"   to 40,
        "Light" to 25,
        "Deep"  to 15,
        "REM"   to 40,
        "Light" to 65,   // light sleep segment where alarm fires favorably
    )

    /** Average night: good structure but shorter deep sleep, one wake micro-bout */
    private val NIGHT_AVERAGE = listOf(
        "Wake"  to 10,
        "Light" to 20,
        "Deep"  to 30,
        "Light" to 15,
        "Deep"  to 45,
        "REM"   to 35,
        "Light" to 20,
        "Wake"  to 5,
        "Light" to 15,
        "Deep"  to 25,
        "REM"   to 35,
        "Light" to 40,
        "REM"   to 30,
        "Light" to 55,   // favorable wake here
    )

    /** Fragmented night: multiple wake bouts, shorter deep, fallback alarm */
    private val NIGHT_FRAGMENTED = listOf(
        "Wake"  to 15,
        "Light" to 20,
        "Wake"  to 5,
        "Light" to 15,
        "Deep"  to 25,
        "Light" to 10,
        "Wake"  to 5,
        "Light" to 20,
        "Deep"  to 25,
        "REM"   to 25,
        "Light" to 25,
        "Wake"  to 5,
        "Deep"  to 20,
        "REM"   to 25,
        "Light" to 30,
        "REM"   to 20,
        "Light" to 35,
        "Deep"  to 15,
        "Light" to 25,
        "Wake"  to 5,    // window ends without light detection → fallback
    )

    // ── Builder ───────────────────────────────────────────────────────────────

    private fun buildSession(
        id: Long,
        daysAgo: Long,
        bedHour: Int, bedMin: Int,
        wakeHour: Int, wakeMin: Int,
        windowStartHour: Int, windowStartMin: Int,
        windowEndHour: Int,   windowEndMin: Int,
        firedReason: String,
        pattern: List<Pair<String, Int>>,
    ): SessionRecord {
        val zone      = ZoneId.systemDefault()
        val sleepDate = LocalDate.now().minusDays(daysAgo)
        val wakeDate  = sleepDate.plusDays(1)   // always wakes after midnight

        val bedInstant      = sleepDate.atTime(bedHour, bedMin).atZone(zone).toInstant()
        val wakeInstant     = wakeDate.atTime(wakeHour, wakeMin).atZone(zone).toInstant()
        val winStartInstant = wakeDate.atTime(windowStartHour, windowStartMin).atZone(zone).toInstant()
        val winEndInstant   = wakeDate.atTime(windowEndHour, windowEndMin).atZone(zone).toInstant()

        return SessionRecord(
            id            = id,
            user_id       = "demo",
            window_start  = winStartInstant.toString(),
            window_end    = winEndInstant.toString(),
            started_at    = bedInstant.toString(),
            ended_at      = wakeInstant.toString(),
            fired_at      = wakeInstant.toString(),
            fired_reason  = firedReason,
            stages        = buildStages(bedInstant, pattern),
            created_at    = bedInstant.toString(),
        )
    }

    /** Converts an architecture pattern into a list of StageTick at 5-minute intervals. */
    private fun buildStages(
        startTime: java.time.Instant,
        pattern: List<Pair<String, Int>>,
    ): List<StageTick> {
        val ticks = mutableListOf<StageTick>()
        var current = startTime
        val intervalSec = 5L * 60L   // 5 minutes per tick

        for ((stage, durationMins) in pattern) {
            val tickCount = (durationMins / 5).coerceAtLeast(1)
            repeat(tickCount) {
                ticks.add(
                    StageTick(
                        t      = current.toString(),
                        stage  = stage,
                        conf   = stageConf(stage),
                        stable = stage != "Wake",
                    )
                )
                current = current.plusSeconds(intervalSec)
            }
        }
        return ticks
    }

    private fun stageConf(stage: String): Float = when (stage) {
        "Deep"  -> 0.82f + Random.nextFloat() * 0.14f
        "REM"   -> 0.62f + Random.nextFloat() * 0.22f
        "Light" -> 0.18f + Random.nextFloat() * 0.32f
        else    -> 0.04f + Random.nextFloat() * 0.12f   // Wake
    }
}
