package com.example.sleepwisepoc.report

import com.example.sleepwisepoc.SessionRecord
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.WeeklyReport
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Generates a realistic 7-night weekly report for demo/sample mode (toggled via
 * [com.example.sleepwisepoc.DemoStore]). Each night uses a different sleep-
 * architecture pattern so every visual element of the hypnogram (stage
 * transitions, REM cycles, fragmentation) is exercised. Every tick carries a
 * 4-stage report_stage (Wake/Light/Deep/REM) plus the binary alarm stage, so the
 * report renders the full 4-stage hypnogram exactly as a real night would.
 */
internal object SleepMockData {

    /** One night's spec: how many days ago, bed/wake/window times, fire reason, architecture. */
    private data class NightSpec(
        val daysAgo: Long,
        val bedHour: Int, val bedMin: Int,
        val wakeHour: Int, val wakeMin: Int,
        val winStartH: Int, val winStartM: Int,
        val winEndH: Int, val winEndM: Int,
        val firedReason: String,
        val pattern: List<Pair<String, Int>>,
    )

    // lazy so the pattern vals below are initialised before this list references them
    private val NIGHTS by lazy {
        listOf(
            NightSpec(1, 22, 40, 6, 43, 6, 30, 7, 0, "favorable", NIGHT_GOOD),
            NightSpec(2, 23, 15, 6, 52, 6, 30, 7, 0, "favorable", NIGHT_AVERAGE),
            NightSpec(3, 0, 5, 7, 12, 6, 45, 7, 15, "favorable", NIGHT_REM_HEAVY),
            NightSpec(4, 22, 20, 6, 20, 6, 0, 6, 30, "favorable", NIGHT_DEEP_HEAVY),
            NightSpec(5, 23, 40, 7, 8, 6, 45, 7, 15, "fallback", NIGHT_FRAGMENTED),
            NightSpec(6, 22, 55, 6, 38, 6, 15, 6, 45, "favorable", NIGHT_SOLID),
            NightSpec(7, 23, 25, 6, 30, 6, 0, 6, 30, "favorable", NIGHT_AVERAGE),
        )
    }

    fun createReport(): WeeklyReport {
        val sessions = NIGHTS.mapIndexed { i, n ->
            buildSession(
                id = (i + 1).toLong(),
                daysAgo = n.daysAgo,
                bedHour = n.bedHour, bedMin = n.bedMin,
                wakeHour = n.wakeHour, wakeMin = n.wakeMin,
                windowStartHour = n.winStartH, windowStartMin = n.winStartM,
                windowEndHour = n.winEndH, windowEndMin = n.winEndM,
                firedReason = n.firedReason,
                pattern = n.pattern,
            )
        }

        return WeeklyReport(
            user_id          = "demo",
            sessions         = sessions,
            fired_count      = sessions.size,
            favorable_count  = sessions.count { it.fired_reason == "favorable" },
            fallback_count   = sessions.count { it.fired_reason == "fallback" },
            avg_window_minutes = 30f,
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

    /** REM-heavy night: longer, more frequent REM cycles toward morning */
    private val NIGHT_REM_HEAVY = listOf(
        "Wake"  to 5,
        "Light" to 15,
        "Deep"  to 35,
        "REM"   to 20,
        "Light" to 20,
        "Deep"  to 30,
        "REM"   to 35,
        "Light" to 15,
        "Deep"  to 20,
        "REM"   to 45,
        "Light" to 20,
        "REM"   to 40,
        "Light" to 50,
    )

    /** Deep-heavy night: dominant slow-wave sleep early, restorative */
    private val NIGHT_DEEP_HEAVY = listOf(
        "Wake"  to 5,
        "Light" to 10,
        "Deep"  to 55,
        "Light" to 10,
        "Deep"  to 50,
        "REM"   to 20,
        "Deep"  to 40,
        "Light" to 15,
        "Deep"  to 25,
        "REM"   to 25,
        "Light" to 45,
    )

    /** Solid, well-balanced night: even cycling of all four stages */
    private val NIGHT_SOLID = listOf(
        "Wake"  to 5,
        "Light" to 15,
        "Deep"  to 40,
        "REM"   to 20,
        "Light" to 20,
        "Deep"  to 40,
        "REM"   to 25,
        "Light" to 20,
        "Deep"  to 25,
        "REM"   to 30,
        "Light" to 50,
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
                        // binary alarm view: only Deep vs everything-else (Light)
                        stage  = if (stage == "Deep") "Deep" else "Light",
                        conf   = stageConf(stage),
                        stable = stage != "Wake",
                        // 4-stage report view (what the report renders)
                        reportStage = stage,
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
