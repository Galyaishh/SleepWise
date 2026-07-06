package com.example.sleepwisepoc.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.SessionRecord
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.WeeklyReport
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ─── Formatters ───────────────────────────────────────────────────────────────

private val DateHeaderFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
private val DayNameFmt    = DateTimeFormatter.ofPattern("EEE")
private val DayNumFmt     = DateTimeFormatter.ofPattern("d")
private val TimeFmt12     = DateTimeFormatter.ofPattern("h:mm a")
private val ShortTimeFmt  = DateTimeFormatter.ofPattern("h:mm")

// ─── Stage colors ─────────────────────────────────────────────────────────────

private val StageColorAwake = Color(0xFFE8B06A)
private val StageColorREM   = Color(0xFFB09ADE)
private val StageColorLight = Color(0xFF68BDE0)
private val StageColorDeep  = Color(0xFF7B8FE8)

// ─── Domain models ────────────────────────────────────────────────────────────

private data class StageDurations(
    val awakeMins: Long,
    val remMins: Long,
    val lightMins: Long,
    val deepMins: Long,
) {
    val totalMins    get() = awakeMins + remMins + lightMins + deepMins
    val sleepingMins get() = remMins + lightMins + deepMins
}

private data class Insight(
    val emoji: String,
    val color: Color,
    val title: String,
    val subtitle: String,
)

private data class WeekCard(
    val dayName: String,
    val dayNum: String,
    val durationMins: Long,
    val stageFracs: Map<String, Float>,
    val isCurrent: Boolean,
    val hasData: Boolean,
    val score: Int = 0,
)

// ─── Domain helpers ───────────────────────────────────────────────────────────

private fun computeStageDurations(ticks: List<StageTick>): StageDurations {
    if (ticks.size < 2) return StageDurations(0, 0, 0, 0)
    val instants = ticks.map { runCatching { Instant.parse(it.t) }.getOrNull() }
    val valid    = instants.filterNotNull()
    val avgMs    = if (valid.size >= 2)
        (valid.last().toEpochMilli() - valid.first().toEpochMilli()) / (valid.size - 1).toLong()
    else 300_000L
    var awake = 0L; var rem = 0L; var light = 0L; var deep = 0L
    ticks.forEachIndexed { i, tick ->
        val t0   = instants[i] ?: return@forEachIndexed
        val t1ms = instants.getOrNull(i + 1)?.toEpochMilli() ?: (t0.toEpochMilli() + avgMs)
        val mins = ((t1ms - t0.toEpochMilli()) / 60_000L).coerceAtLeast(0L)
        when (tick.stage.trim().lowercase()) {
            "wake", "awake" -> awake += mins
            "rem"           -> rem   += mins
            "deep"          -> deep  += mins
            else            -> light += mins
        }
    }
    return StageDurations(awake, rem, light, deep)
}

private fun stageFracsOf(d: StageDurations): Map<String, Float> {
    val t = d.totalMins.toFloat().coerceAtLeast(1f)
    return mapOf(
        "awake" to d.awakeMins / t,
        "rem"   to d.remMins   / t,
        "light" to d.lightMins / t,
        "deep"  to d.deepMins  / t,
    )
}

private fun formatDuration(mins: Long): String {
    val h = mins / 60; val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}


/**
 * 0–100 sleep quality score.
 *
 * Component weights:
 *   Total sleep duration  – up to 30 pts  (7–9h ideal)
 *   Deep sleep %          – up to 25 pts  (≥20% ideal)
 *   REM sleep %           – up to 20 pts  (≥20% ideal)
 *   Wake bouts            – up to 15 pts  (0 bouts ideal)
 *   Smart-wake outcome    – up to 10 pts  (favorable)
 */
private fun computeSleepScore(
    d: StageDurations,
    wakeBouts: Int,
    firedReason: String?,
): Int {
    val sleepMins = d.sleepingMins.coerceAtLeast(1L)
    val total     = d.totalMins.toFloat().coerceAtLeast(1f)
    val deepPct   = d.deepMins / total
    val remPct    = d.remMins  / total

    // Duration (30 pts)
    val durPts = when {
        sleepMins >= 420 && sleepMins <= 540 -> 30  // 7–9h
        sleepMins >= 360 && sleepMins < 420  -> 22  // 6–7h
        sleepMins >= 540 && sleepMins <= 600 -> 22  // 9–10h (slightly long)
        sleepMins >= 300 && sleepMins < 360  -> 12  // 5–6h
        sleepMins > 600                      -> 12  // >10h
        else                                 -> 4   // <5h
    }

    // Deep sleep (25 pts)
    val deepPts = when {
        deepPct >= 0.20f -> 25
        deepPct >= 0.15f -> 18
        deepPct >= 0.10f -> 10
        else             -> 3
    }

    // REM sleep (20 pts)
    val remPts = when {
        remPct >= 0.20f -> 20
        remPct >= 0.15f -> 14
        remPct >= 0.10f -> 7
        else            -> 2
    }

    // Wake bouts (15 pts)
    val wakePts = when {
        wakeBouts == 0 -> 15
        wakeBouts <= 1 -> 11
        wakeBouts <= 2 -> 7
        wakeBouts <= 4 -> 3
        else           -> 0
    }

    // Smart-wake outcome (10 pts)
    val alarmPts = if (firedReason == "favorable") 10 else 0

    return (durPts + deepPts + remPts + wakePts + alarmPts).coerceIn(0, 100)
}

private fun scoreColor(score: Int): Color = when {
    score >= 85 -> Color(0xFF4CAF82)   // green
    score >= 70 -> Color(0xFF68BDE0)   // teal/blue
    score >= 50 -> Color(0xFFE8B06A)   // amber
    else        -> Color(0xFFE06868)   // red
}

private fun countWakeBouts(ticks: List<StageTick>): Int {
    var count = 0; var wasWake = false
    for (tick in ticks) {
        val isWake = tick.stage.trim().lowercase().let { it == "wake" || it == "awake" }
        if (isWake && !wasWake) count++
        wasWake = isWake
    }
    return count
}

private fun generateInsights(
    d: StageDurations,
    firedReason: String?,
    wakeBouts: Int,
): List<Insight> {
    val list    = mutableListOf<Insight>()
    val t       = d.totalMins.toFloat().coerceAtLeast(1f)
    val deepPct = d.deepMins / t
    val remPct  = d.remMins  / t

    when (firedReason) {
        "favorable" -> list += Insight("✨", Color(0xFFFFD166),
            "You woke during optimal light sleep.",
            "This helps you feel more refreshed.")
        "fallback"  -> list += Insight("⏰", StageColorAwake,
            "Alarm fired at the end of your window.",
            "No light sleep moment was detected in time.")
    }
    when {
        remPct >= 0.22f -> list += Insight("🧠", StageColorREM,
            "REM sleep was strong last night.",
            "Memory and mood restoration was active.")
        remPct < 0.12f  -> list += Insight("🧠", StageColorREM,
            "REM sleep was shorter than usual.",
            "Consistent sleep timing helps improve this.")
    }
    when {
        deepPct >= 0.20f -> list += Insight("💪", StageColorDeep,
            "Deep sleep supported physical recovery.",
            "Your body got solid restoration overnight.")
        deepPct < 0.10f  -> list += Insight("🛌", StageColorDeep,
            "Deep sleep was lower than ideal.",
            "Try an earlier, consistent bedtime tonight.")
    }
    when {
        wakeBouts == 0 -> list += Insight("🌙", Color(0xFF4ADE80),
            "Your heart rate stayed calm and stable.",
            "A sign of good recovery.")
        wakeBouts >= 4 -> list += Insight("🌙", StageColorAwake,
            "You stirred ${wakeBouts} times during the night.",
            "A calmer sleep environment may help.")
    }
    return list.take(3)
}

// ─── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun SleepReportScreen(
    modifier: Modifier = Modifier,
    viewModel: SleepReportViewModel = viewModel(),
) {
    val c = LocalSleepColors.current
    val state by viewModel.state.collectAsState()
    Box(modifier = modifier.fillMaxSize().background(c.bg)) {
        when (val s = state) {
            ReportUiState.Loading        -> LoadingView()
            is ReportUiState.Empty       -> EmptyView()
            is ReportUiState.Error       -> EmptyView()
            is ReportUiState.Loaded      -> NightPager(
                report     = s.report,
                isDemoData = s.isDemoData,
                onRefresh  = viewModel::refresh,
            )
        }
    }
}

// ─── Night pager ──────────────────────────────────────────────────────────────

@Composable
private fun NightPager(report: WeeklyReport, isDemoData: Boolean, onRefresh: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val c = LocalSleepColors.current
    val sessions = report.sessions
    if (sessions.isEmpty()) { EmptyView(); return }

    // Map each session to the calendar date you WOKE UP on (ended_at local date),
    // so a night is filed under its morning.
    val byDate = remember(sessions) {
        sessions.mapNotNull { s ->
            runCatching { Instant.parse(s.ended_at).atZone(zone).toLocalDate() }.getOrNull()?.let { it to s }
        }.toMap()
    }
    // Day slots: today back to the oldest recorded night, so gaps AND empty days
    // (incl. today before tonight's run) are navigable.
    val today  = java.time.LocalDate.now(zone)
    val oldest = byDate.keys.minOrNull() ?: today
    val days = remember(byDate) {
        val span = java.time.temporal.ChronoUnit.DAYS.between(oldest, today).toInt().coerceAtLeast(0)
        (0..span).map { today.minusDays(it.toLong()) }   // index 0 = today (newest)
    }
    // Open on the most recent day that actually has data.
    var currentPage by remember(days) {
        mutableStateOf(days.indexOfFirst { byDate.containsKey(it) }.coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ◁ older
            IconButton(
                onClick = { if (currentPage < days.lastIndex) currentPage++ },
                enabled = currentPage < days.lastIndex,
            ) {
                Icon(
                    Icons.Rounded.ChevronLeft, null,
                    tint = if (currentPage < days.lastIndex) c.textPrimary else c.textSecondary.copy(0.3f),
                )
            }

            val day     = days[currentPage]
            val dateStr = if (day == today) "Today" else day.format(DateHeaderFmt)
            Text(
                dateStr,
                modifier   = Modifier.weight(1f),
                textAlign  = TextAlign.Center,
                fontSize   = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color      = c.textPrimary,
            )

            // ▷ newer
            IconButton(
                onClick = { if (currentPage > 0) currentPage-- },
                enabled = currentPage > 0,
            ) {
                Icon(
                    Icons.Rounded.ChevronRight, null,
                    tint = if (currentPage > 0) c.textPrimary else c.textSecondary.copy(0.3f),
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, null, tint = c.textSecondary)
            }
        }

        if (isDemoData) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("✦", fontSize = 10.sp, color = c.primary)
                Text(
                    "Demo data — connect your watch for real nights",
                    fontSize = 11.sp,
                    color    = c.textSecondary,
                )
            }
        }

        // Page content: the day's night, or an empty state if nothing was tracked.
        val session = byDate[days[currentPage]]
        if (session != null) {
            NightPage(session = session, sessions = sessions, modifier = Modifier.weight(1f))
        } else {
            EmptyDay(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptyDay(modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier            = Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("😴", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("No sleep tracked", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Nothing recorded for this night.",
                fontSize = 13.sp, color = c.textSecondary, textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Single night page ────────────────────────────────────────────────────────

@Composable
private fun NightPage(
    session: SessionRecord,
    sessions: List<SessionRecord>,
    modifier: Modifier = Modifier,
) {
    val zone        = ZoneId.systemDefault()
    val durations   = remember(session.id) { computeStageDurations(session.stages) }
    val wakeBouts   = remember(session.id) { countWakeBouts(session.stages) }
    val score       = remember(session.id) { computeSleepScore(durations, wakeBouts, session.fired_reason) }
    val insights    = remember(session.id) { generateInsights(durations, session.fired_reason, wakeBouts) }

    val bedInstant  = runCatching { Instant.parse(session.started_at) }.getOrNull()
    val wakeInstant = runCatching { Instant.parse(session.ended_at)   }.getOrNull()
    val winStart    = runCatching { Instant.parse(session.window_start) }.getOrNull()
    val winEnd      = runCatching { Instant.parse(session.window_end)   }.getOrNull()

    val bedText     = bedInstant?.atZone(zone)?.format(TimeFmt12)      ?: "—"
    val wakeText    = wakeInstant?.atZone(zone)?.format(TimeFmt12)     ?: "—"
    val winStartStr = winStart?.atZone(zone)?.format(ShortTimeFmt)     ?: "—"
    val winEndStr   = winEnd?.atZone(zone)?.format(TimeFmt12)          ?: "—"

    val timeInBedMins = if (bedInstant != null && wakeInstant != null)
        // Clamp: never render negative time-in-bed (a malformed session whose
        // started_at is after ended_at would otherwise show "minus minutes").
        Duration.between(bedInstant, wakeInstant).toMinutes().coerceAtLeast(0)
    else durations.totalMins

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Hero ───────────────────────────────────────────────────────────────
        HeroSection(
            timeInBedMins = timeInBedMins,
            sleepMins     = durations.sleepingMins,
            bedText       = bedText,
            wakeText      = wakeText,
            winStartStr   = winStartStr,
            winEndStr     = winEndStr,
            score         = score,
        )

        // ── Smart wake banner ──────────────────────────────────────────────────
        if (session.fired_reason == "favorable") {
            SmartWakeBanner()
        }

        Spacer(Modifier.height(24.dp))

        // ── Sleep stages + hypnogram ───────────────────────────────────────────
        SleepStagesSection(session = session)

        Spacer(Modifier.height(4.dp))

        // ── Stage summary (inline, no card) ───────────────────────────────────
        StageSummaryRow(durations = durations, modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(28.dp))

        // ── Insights ──────────────────────────────────────────────────────────
        if (insights.isNotEmpty()) {
            InsightsSection(insights = insights, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(28.dp))
        }

        // ── This week ─────────────────────────────────────────────────────────
        ThisWeekSection(
            sessions      = sessions,
            currentId     = session.id,
            sessionDate   = bedInstant?.atZone(zone)?.toLocalDate(),
            modifier      = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(40.dp))
    }
}

// ─── Hero section ─────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(
    timeInBedMins: Long,
    sleepMins: Long,
    bedText: String,
    wakeText: String,
    winStartStr: String,
    winEndStr: String,
    score: Int,
) {
    val c = LocalSleepColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1235), Color(0xFF0A0C18)))),
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.TopCenter)
                .background(Brush.radialGradient(listOf(c.primary.copy(0.10f), Color.Transparent))),
        )

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left — time in bed
                Column(modifier = Modifier.weight(1f)) {
                    Text("Time in bed", fontSize = 11.sp, color = c.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatDuration(timeInBedMins),
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = c.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$bedText – $wakeText",
                        fontSize   = 11.sp,
                        color      = c.textSecondary,
                        lineHeight = 15.sp,
                    )
                }

                // Center — score ring
                val ringColor = scoreColor(score)
                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sw    = 9.dp.toPx()
                        val inset = sw / 2f
                        val rect  = Size(size.width - sw, size.height - sw)
                        // Background track
                        drawArc(
                            color      = c.surface2,
                            startAngle = -90f, sweepAngle = 360f,
                            useCenter  = false,
                            topLeft    = Offset(inset, inset), size = rect,
                            style      = Stroke(sw, cap = StrokeCap.Round),
                        )
                        // Filled arc proportional to score
                        drawArc(
                            color      = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * (score / 100f),
                            useCenter  = false,
                            topLeft    = Offset(inset, inset), size = rect,
                            style      = Stroke(sw, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            score.toString(),
                            fontSize   = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color      = ringColor,
                        )
                        Text(
                            "Score",
                            fontSize   = 12.sp,
                            color      = c.textSecondary,
                        )
                    }
                }

                // Right — total sleep
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("Total sleep", fontSize = 11.sp, color = c.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatDuration(sleepMins),
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = c.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Smart wake", fontSize = 11.sp, color = c.textSecondary)
                    Text(
                        "$winStartStr – $winEndStr",
                        fontSize   = 11.sp,
                        color      = c.textAccent,
                        textAlign  = TextAlign.End,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

// ─── Smart wake banner ────────────────────────────────────────────────────────

@Composable
private fun SmartWakeBanner() {
    val c = LocalSleepColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141829))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.primary.copy(0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✨", fontSize = 16.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "You woke during optimal light sleep.",
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = c.textPrimary,
            )
            Text(
                "Great way to start your day!",
                fontSize = 12.sp,
                color    = c.textSecondary,
            )
        }
    }
}

// ─── Sleep stages section ─────────────────────────────────────────────────────

@Composable
private fun SleepStagesSection(session: SessionRecord) {
    val c = LocalSleepColors.current
    if (session.stages.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Sleep stages",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = c.textPrimary,
            )
            Icon(Icons.Outlined.Info, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(12.dp))
        SleepHypnogram(
            stages   = session.stages,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

// ─── Stage summary row ────────────────────────────────────────────────────────

@Composable
private fun StageSummaryRow(durations: StageDurations, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    data class Item(val label: String, val mins: Long, val color: Color)
    val total = durations.totalMins.toFloat().coerceAtLeast(1f)
    val items = listOf(
        Item("Awake", durations.awakeMins, StageColorAwake),
        Item("REM",   durations.remMins,   StageColorREM),
        Item("Light", durations.lightMins, StageColorLight),
        Item("Deep",  durations.deepMins,  StageColorDeep),
    )
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { item ->
            Column(
                modifier            = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(item.color),
                    )
                    Text(item.label, fontSize = 11.sp, color = item.color, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    formatDuration(item.mins),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = c.textPrimary,
                    textAlign  = TextAlign.Center,
                )
                Text(
                    "${((item.mins / total) * 100).roundToInt()}%",
                    fontSize  = 11.sp,
                    color     = c.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─── Insights section ─────────────────────────────────────────────────────────

@Composable
private fun InsightsSection(insights: List<Insight>, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Sleep insights",
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = c.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.surface),
        ) {
            insights.forEachIndexed { index, insight ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier         = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(insight.color.copy(0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(insight.emoji, fontSize = 18.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            insight.title,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = c.textPrimary,
                            lineHeight = 19.sp,
                        )
                        Text(
                            insight.subtitle,
                            fontSize   = 12.sp,
                            color      = c.textSecondary,
                            lineHeight = 17.sp,
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight, null,
                        tint     = c.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (index < insights.lastIndex) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(start = 72.dp),
                        color     = c.border,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

// ─── This week section ────────────────────────────────────────────────────────

@Composable
private fun ThisWeekSection(
    sessions: List<SessionRecord>,
    currentId: Long,
    sessionDate: LocalDate?,
    modifier: Modifier = Modifier,
) {
    val c = LocalSleepColors.current
    val zone = ZoneId.systemDefault()

    // Build Mon–Sun week around the current session date
    val anchor  = sessionDate ?: LocalDate.now()
    val monday  = anchor.with(DayOfWeek.MONDAY)
    val weekDays = (0..6).map { monday.plusDays(it.toLong()) }

    // Index sessions by their bed date
    val sessionByDate = sessions.associateBy { s ->
        runCatching { Instant.parse(s.started_at) }.getOrNull()
            ?.atZone(zone)?.toLocalDate()
    }

    val cards = weekDays.map { date ->
        val s = sessionByDate[date]
        if (s != null) {
            val d     = computeStageDurations(s.stages)
            val bouts = countWakeBouts(s.stages)
            WeekCard(
                dayName      = date.format(DayNameFmt),
                dayNum       = date.dayOfMonth.toString(),
                durationMins = d.sleepingMins,
                stageFracs   = stageFracsOf(d),
                isCurrent    = s.id == currentId,
                hasData      = true,
                score        = computeSleepScore(d, bouts, s.fired_reason),
            )
        } else {
            WeekCard(
                dayName      = date.format(DayNameFmt),
                dayNum       = date.dayOfMonth.toString(),
                durationMins = 0,
                stageFracs   = emptyMap(),
                isCurrent    = false,
                hasData      = false,
            )
        }
    }

    val avgMins = cards.filter { it.hasData }
        .map { it.durationMins }.average()
        .let { if (it.isNaN()) 0.0 else it }
    val avgText = if (avgMins > 0) "Avg ${formatDuration(avgMins.toLong())}" else ""

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("This week", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            if (avgText.isNotEmpty()) {
                Text(avgText, fontSize = 13.sp, color = c.textAccent, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(end = 4.dp),
        ) {
            items(cards) { card ->
                WeekMiniCard(card)
            }
        }
    }
}

@Composable
private fun WeekMiniCard(card: WeekCard) {
    val c = LocalSleepColors.current
    Column(
        modifier = Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (card.isCurrent)
                    Brush.verticalGradient(listOf(c.primary.copy(0.20f), c.surface))
                else
                    Brush.verticalGradient(listOf(c.surface, c.surface)),
            )
            .then(
                if (card.isCurrent)
                    Modifier.border(1.dp, c.primary.copy(0.30f), RoundedCornerShape(18.dp))
                else Modifier
            )
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            card.dayName,
            fontSize   = 10.sp,
            color      = if (card.isCurrent) c.primary.copy(0.9f) else c.textSecondary,
            fontWeight = FontWeight.Medium,
        )
        Text(
            card.dayNum,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = if (card.isCurrent) c.primary else c.textPrimary,
        )
        Spacer(Modifier.height(4.dp))

        // Score ring
        val miniRingColor = if (card.hasData) scoreColor(card.score) else c.surface2
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sw    = 3.5.dp.toPx()
                val inset = sw / 2f
                val rect  = Size(size.width - sw, size.height - sw)
                drawArc(
                    color      = c.surface2,
                    startAngle = -90f, sweepAngle = 360f,
                    useCenter  = false,
                    topLeft    = Offset(inset, inset), size = rect,
                    style      = Stroke(sw, cap = StrokeCap.Round),
                )
                if (card.hasData) {
                    drawArc(
                        color      = miniRingColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (card.score / 100f),
                        useCenter  = false,
                        topLeft    = Offset(inset, inset), size = rect,
                        style      = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                if (card.hasData) card.score.toString() else "–",
                fontSize   = 14.sp,
                fontWeight = if (card.hasData) FontWeight.Bold else FontWeight.Normal,
                color      = if (card.hasData) miniRingColor else c.textSecondary,
            )
        }

        // Tiny stage dots
        if (card.hasData) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(
                    StageColorAwake to (card.stageFracs["awake"] ?: 0f),
                    StageColorDeep  to (card.stageFracs["deep"]  ?: 0f),
                    StageColorLight to (card.stageFracs["light"] ?: 0f),
                    StageColorREM   to (card.stageFracs["rem"]   ?: 0f),
                ).filter { it.second > 0.03f }.take(3).forEach { (col, _) ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(col),
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            if (card.hasData) formatDuration(card.durationMins) else "—",
            fontSize  = 10.sp,
            color     = c.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Empty / loading ──────────────────────────────────────────────────────────

@Composable
private fun LoadingView() {
    val c = LocalSleepColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌙", fontSize = 40.sp)
            Spacer(Modifier.height(16.dp))
            Text("Loading your nights…", fontSize = 14.sp, color = c.textSecondary)
        }
    }
}

@Composable
private fun EmptyView() {
    val c = LocalSleepColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier            = Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🌙", fontSize = 52.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                "No nights recorded yet",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = c.textPrimary,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Set a wake-up window tonight and your sleep history will appear here.",
                fontSize   = 14.sp,
                color      = c.textSecondary,
                textAlign  = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}
