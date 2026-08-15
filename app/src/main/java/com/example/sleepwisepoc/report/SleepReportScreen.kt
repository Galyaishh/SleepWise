package com.example.sleepwisepoc.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.SessionRecord
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.WeeklyReport
import com.example.sleepwisepoc.ui.theme.*
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

// ─── Formatters ───────────────────────────────────────────────────────────────

private val AxisFmt      = DateTimeFormatter.ofPattern("HH:mm")   // 23:12 · 01:00
private val ClockFmt     = DateTimeFormatter.ofPattern("H:mm")    // 6:42
private val DayShortFmt  = DateTimeFormatter.ofPattern("EEE d")   // Wed 12
private val DateLongFmt  = DateTimeFormatter.ofPattern("EEE d MMM") // Thu 13 Aug
private val WeekEndFmt    = DateTimeFormatter.ofPattern("d MMM")
private val DayNumFmt     = DateTimeFormatter.ofPattern("d")
private val WeekDayAbbrev = DateTimeFormatter.ofPattern("EEE")

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

private enum class InsightTone { GOOD, DEEP, REM, AWAKE }

private data class Insight(
    val tone: InsightTone,
    val title: String,
    val subtitle: String,
)

/** Merged hypnogram data mapped into the Night Ribbon's model. */
private data class RibbonData(
    val segments: List<HypSegment>,
    val totalMin: Int,
    val originMs: Long,
    val windowStartMin: Int?,
    val windowEndMin: Int?,
    val wakeMarkerMin: Int?,
)

// ─── Stage mapping ────────────────────────────────────────────────────────────

private fun String.toSleepStage(): SleepStage = when (trim().lowercase()) {
    "deep"          -> SleepStage.DEEP
    "rem"           -> SleepStage.REM
    "wake", "awake" -> SleepStage.AWAKE
    else            -> SleepStage.LIGHT
}

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

private fun formatDuration(mins: Long): String {
    val h = mins / 60; val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/**
 * Merge the session's StageTicks into the Night Ribbon's [HypSegment] model —
 * consecutive same-stage ticks collapse into one band. Minutes are measured from
 * the first tick (the ribbon origin); the smart-wake window and the fired-at
 * marker are expressed in the same origin-relative minute basis.
 */
private fun buildRibbon(session: SessionRecord): RibbonData {
    val ticks = session.stages
    val instants = ticks.map { runCatching { Instant.parse(it.t) }.getOrNull() }
    val valid = instants.filterNotNull()
    if (valid.size < 2) return RibbonData(emptyList(), 1, 0L, null, null, null)

    val originMs = valid.first().toEpochMilli()
    val avgMs = (valid.last().toEpochMilli() - originMs) / (valid.size - 1).toLong()
    val endMs = valid.last().toEpochMilli() + avgMs
    val totalMin = ((endMs - originMs) / 60_000L).toInt().coerceAtLeast(1)

    fun relMin(ms: Long) = ((ms - originMs) / 60_000L).toInt()

    // Raw per-tick spans → [stage, startMin, endMin]
    data class Raw(val stage: SleepStage, val start: Int, val end: Int)
    val raw = ticks.mapIndexedNotNull { i, tick ->
        val t0 = instants[i] ?: return@mapIndexedNotNull null
        val t1 = instants.getOrNull(i + 1)?.toEpochMilli() ?: endMs
        Raw(tick.stage.toSleepStage(), relMin(t0.toEpochMilli()), relMin(t1))
    }

    // Merge consecutive same-stage runs
    val segs = mutableListOf<HypSegment>()
    var k = 0
    while (k < raw.size) {
        val s = raw[k]
        var j = k + 1
        while (j < raw.size && raw[j].stage == s.stage) j++
        val startMin = s.start
        val endMin   = raw[j - 1].end
        segs.add(HypSegment(startMin, (endMin - startMin).coerceAtLeast(1), s.stage))
        k = j
    }

    fun isoRel(iso: String?): Int? = iso
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?.let { relMin(it.toEpochMilli()) }

    return RibbonData(
        segments       = segs,
        totalMin       = totalMin,
        originMs       = originMs,
        windowStartMin = isoRel(session.window_start),
        windowEndMin   = isoRel(session.window_end),
        wakeMarkerMin  = isoRel(session.fired_at),
    )
}

private fun computeSleepScore(d: StageDurations, wakeBouts: Int, firedReason: String?): Int {
    val sleepMins = d.sleepingMins.coerceAtLeast(1L)
    val total     = d.totalMins.toFloat().coerceAtLeast(1f)
    val deepPct   = d.deepMins / total
    val remPct    = d.remMins  / total

    val durPts = when {
        sleepMins in 420..540 -> 30
        sleepMins in 360..419 -> 22
        sleepMins in 541..600 -> 22
        sleepMins in 300..359 -> 12
        sleepMins > 600       -> 12
        else                  -> 4
    }
    val deepPts = when {
        deepPct >= 0.22f -> 25
        deepPct >= 0.17f -> 18
        deepPct >= 0.12f -> 10
        deepPct >= 0.07f -> 4
        else             -> 2
    }
    val remPts = when {
        remPct >= 0.22f -> 20
        remPct >= 0.17f -> 14
        remPct >= 0.11f -> 7
        else            -> 2
    }
    val wakePts = when {
        wakeBouts == 0 -> 15
        wakeBouts <= 1 -> 11
        wakeBouts <= 2 -> 7
        wakeBouts <= 4 -> 3
        else           -> 0
    }
    val alarmPts = if (firedReason == "favorable") 10 else 0

    val isBinary = d.remMins == 0L && d.awakeMins == 0L
    return if (isBinary) {
        ((durPts + deepPts + alarmPts) * 100f / 65f).toInt().coerceIn(0, 98)
    } else {
        (durPts + deepPts + remPts + wakePts + alarmPts).coerceIn(0, 98)
    }
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

private fun verdictFor(score: Int): Pair<String, String> = when {
    score >= 85 -> "A steady night." to
        "You spent almost all of it asleep and woke up clear."
    score >= 70 -> "A solid night." to
        "A good night overall, with a little room for more rest."
    score >= 50 -> "A lighter night." to
        "You slept, but it broke up more than usual."
    else -> "A restless night." to
        "Sleep came in short stretches. Tonight is a fresh start."
}

private fun generateInsights(d: StageDurations, firedReason: String?, wakeBouts: Int): List<Insight> {
    val list = mutableListOf<Insight>()
    val t = d.totalMins.toFloat().coerceAtLeast(1f)
    val deepPct = d.deepMins / t
    val remPct  = d.remMins  / t
    val isBinary = d.remMins == 0L && d.awakeMins == 0L

    when (firedReason) {
        "favorable" -> list += Insight(
            InsightTone.GOOD,
            "You woke during light sleep",
            "That is the easy exit — the first hour of your morning tends to feel clearer.")
        "fallback"  -> list += Insight(
            InsightTone.AWAKE,
            "We woke you at the end of the window",
            "No light-sleep moment showed up in time, so we rang right on schedule.")
    }
    when {
        deepPct >= 0.20f -> list += Insight(
            InsightTone.DEEP,
            "Deep sleep landed early",
            "${formatDuration(d.deepMins)} of deep sleep, most of it in the first half — the pattern of a good recovery night.")
        deepPct < 0.10f -> list += Insight(
            InsightTone.DEEP,
            "Deep sleep ran a little short",
            "An earlier, steadier bedtime tonight usually brings it back.")
    }
    if (!isBinary) when {
        remPct >= 0.22f -> list += Insight(
            InsightTone.REM,
            "Plenty of dreaming sleep",
            "Strong REM supports memory and mood — a good sign.")
        wakeBouts in 1..3 -> list += Insight(
            InsightTone.AWAKE,
            "One restless stretch",
            "A few minutes awake, then straight back down. Nothing to worry about.")
        wakeBouts >= 4 -> list += Insight(
            InsightTone.AWAKE,
            "You stirred a few times",
            "A calmer, darker room can help the night hold together.")
    }
    return list.take(3)
}

// ─── Entry point (signature preserved for MainActivity) ─────────────────────────

@Composable
fun SleepReportScreen(
    modifier: Modifier = Modifier,
    viewModel: SleepReportViewModel = viewModel(),
) {
    val c = LocalSleepColors.current
    val state by viewModel.state.collectAsState()
    Box(modifier = modifier.fillMaxSize().background(c.bg)) {
        when (val s = state) {
            ReportUiState.Loading   -> MessageView("Loading your nights.")
            is ReportUiState.Empty  -> MessageView("No nights recorded yet.")
            is ReportUiState.Error  -> MessageView("No nights recorded yet.")
            is ReportUiState.Loaded -> NightPager(report = s.report, onRefresh = viewModel::refresh)
        }
    }
}

// ─── Night pager — header + navigation between nights ──────────────────────────

@Composable
private fun NightPager(report: WeeklyReport, onRefresh: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val c = LocalSleepColors.current
    val sessions = report.sessions
    if (sessions.isEmpty()) { MessageView("No nights recorded yet."); return }

    // File each session under the calendar date you WOKE UP on.
    val byDate = remember(sessions) {
        sessions.mapNotNull { s ->
            runCatching { Instant.parse(s.ended_at).atZone(zone).toLocalDate() }.getOrNull()?.let { it to s }
        }.toMap()
    }
    val today  = LocalDate.now(zone)
    val oldest = byDate.keys.minOrNull() ?: today
    val days = remember(byDate) {
        val span = ChronoUnit.DAYS.between(oldest, today).toInt().coerceAtLeast(0)
        (0..span).map { today.minusDays(it.toLong()) }   // index 0 = today (newest)
    }
    val newestDataIdx = remember(days) { days.indexOfFirst { byDate.containsKey(it) }.coerceAtLeast(0) }
    var currentPage by remember(days) { mutableStateOf(newestDataIdx) }

    val day     = days[currentPage]
    val session = byDate[day]

    // Header eyebrow + date line.
    val bedInstant  = session?.let { runCatching { Instant.parse(it.started_at) }.getOrNull() }
    val wakeInstant = session?.let { runCatching { Instant.parse(it.ended_at) }.getOrNull() }
    val bedDate  = bedInstant?.atZone(zone)?.toLocalDate() ?: day.minusDays(1)
    val wakeDate = wakeInstant?.atZone(zone)?.toLocalDate() ?: day

    val eyebrow = if (session != null && currentPage == newestDataIdx) "LAST NIGHT"
    else bedDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        .uppercase(Locale.getDefault()) + " NIGHT"
    val dateLine = if (session != null)
        "${bedDate.format(DayShortFmt)} — ${wakeDate.format(DateLongFmt)}"
    else day.format(DateLongFmt)

    fun navigateTo(date: LocalDate) {
        val idx = days.indexOf(date)
        if (idx >= 0) currentPage = idx
    }
    fun jumpWeek(dir: Int) {
        // dir: +1 = older, -1 = newer. Step a whole week, clamped.
        currentPage = (currentPage + dir * 7).coerceIn(0, days.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ReportHeader(
            eyebrow    = eyebrow,
            dateLine   = dateLine,
            canOlder   = currentPage < days.lastIndex,
            canNewer   = currentPage > 0,
            onOlder    = { if (currentPage < days.lastIndex) currentPage++ },
            onNewer    = { if (currentPage > 0) currentPage-- },
        )

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        ) {
            if (session != null) {
                NightContent(session = session)
            } else {
                EmptyNightBlock(onBackToTonight = { currentPage = newestDataIdx; onRefresh() })
            }

            WeekStrip(
                sessions   = sessions,
                anchorDate = day,
                onSelect   = ::navigateTo,
                onJumpWeek = ::jumpWeek,
            )

            Spacer(Modifier.height(34.dp))
        }
    }
}

// ─── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun ReportHeader(
    eyebrow: String,
    dateLine: String,
    canOlder: Boolean,
    canNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    val c = LocalSleepColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton("‹", enabled = canOlder, onClick = onOlder)   // ‹
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(4.dp))
            Text(dateLine, fontFamily = PlexSans, fontSize = 13.sp, color = c.dim)
        }
        ArrowButton("›", enabled = canNewer, onClick = onNewer)   // ›
    }
}

@Composable
private fun ArrowButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalSleepColors.current
    val alpha = if (enabled) 1f else 0.3f
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, c.lineStrong.copy(alpha = c.lineStrong.alpha * alpha), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontFamily = InstrumentSerif, fontSize = 22.sp, color = c.text.copy(alpha = alpha))
    }
}

// ─── Single night content ──────────────────────────────────────────────────────

@Composable
private fun NightContent(session: SessionRecord) {
    val c = LocalSleepColors.current
    val zone = ZoneId.systemDefault()

    val durations = remember(session.id) { computeStageDurations(session.stages) }
    val wakeBouts = remember(session.id) { countWakeBouts(session.stages) }
    val score     = remember(session.id) { computeSleepScore(durations, wakeBouts, session.fired_reason) }
    val insights  = remember(session.id) { generateInsights(durations, session.fired_reason, wakeBouts) }
    val ribbon    = remember(session.id) { buildRibbon(session) }

    val bedInstant  = runCatching { Instant.parse(session.started_at) }.getOrNull()
    val wakeInstant = runCatching { Instant.parse(session.ended_at) }.getOrNull()
    val winStart    = runCatching { Instant.parse(session.window_start) }.getOrNull()
    val winEnd      = runCatching { Instant.parse(session.window_end) }.getOrNull()

    val timeInBedMins = if (bedInstant != null && wakeInstant != null)
        Duration.between(bedInstant, wakeInstant).toMinutes().coerceAtLeast(0)
    else durations.totalMins
    val asleepMins = minOf(durations.sleepingMins, timeInBedMins)
    val wokeAt = wakeInstant?.atZone(zone)?.format(ClockFmt) ?: "—"

    val gutter = Modifier.padding(horizontal = 26.dp)
    val (verdictTitle, verdictBody) = verdictFor(score)

    Spacer(Modifier.height(10.dp))

    // ── Score ring + verdict ────────────────────────────────────────────────
    Row(
        modifier = gutter.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScoreRing(score = score)
        Column(modifier = Modifier.weight(1f)) {
            Text(verdictTitle, fontFamily = InstrumentSerif, fontSize = 28.sp, lineHeight = 32.sp, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(verdictBody, fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 19.sp, color = c.dim)
        }
    }

    Spacer(Modifier.height(26.dp))

    // ── Metric trio ─────────────────────────────────────────────────────────
    MetricTrio(
        inBed  = formatDuration(timeInBedMins),
        asleep = formatDuration(asleepMins),
        wokeAt = wokeAt,
        modifier = gutter,
    )

    Spacer(Modifier.height(26.dp))

    // ── Your night — hypnogram ──────────────────────────────────────────────
    val winLabel = if (winStart != null && winEnd != null)
        "window ${winStart.atZone(zone).format(ClockFmt)}–${winEnd.atZone(zone).format(ClockFmt)}"
    else null
    Column(modifier = gutter.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Your night", fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text)
            if (winLabel != null) MonoLabel(winLabel, color = c.accent)
        }
        Spacer(Modifier.height(14.dp))
        NightRibbon(
            segments       = ribbon.segments,
            totalMinutes   = ribbon.totalMin,
            modifier       = Modifier.fillMaxWidth().height(152.dp),
            bandHeight     = 24.dp,
            mode           = RibbonMode.FULL,
            windowStartMin = ribbon.windowStartMin,
            windowEndMin   = ribbon.windowEndMin,
            wakeMarkerMin  = ribbon.wakeMarkerMin,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Spacer(Modifier.height(8.dp))
        AxisRow(ribbon)
    }

    Spacer(Modifier.height(26.dp))

    // ── Stage breakdown ─────────────────────────────────────────────────────
    StageBreakdown(durations = durations, modifier = gutter)

    Spacer(Modifier.height(26.dp))

    // ── What we noticed ─────────────────────────────────────────────────────
    if (insights.isNotEmpty()) {
        Column(modifier = gutter.fillMaxWidth()) {
            Text("What we noticed", fontFamily = InstrumentSerif, fontSize = 26.sp, color = c.text)
            Spacer(Modifier.height(14.dp))
            insights.forEachIndexed { i, insight ->
                InsightCard(insight)
                if (i < insights.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

// ─── Axis row under the ribbon ──────────────────────────────────────────────────

@Composable
private fun AxisRow(ribbon: RibbonData) {
    val c = LocalSleepColors.current
    val zone = ZoneId.systemDefault()
    val ticks = if (ribbon.originMs == 0L) emptyList() else listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { f ->
        Instant.ofEpochMilli(ribbon.originMs + (ribbon.totalMin * f * 60_000L).toLong())
            .atZone(zone).format(AxisFmt)
    }
    if (ticks.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ticks.forEach { MonoLabel(it, color = c.faint, size = 10.sp) }
    }
}

// ─── Metric trio ─────────────────────────────────────────────────────────────

@Composable
private fun MetricTrio(inBed: String, asleep: String, wokeAt: String, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(c.line),
    ) {
        MetricCell("IN BED", inBed, Modifier.weight(1f))
        Spacer(Modifier.width(1.dp).fillMaxHeight())
        MetricCell("ASLEEP", asleep, Modifier.weight(1f))
        Spacer(Modifier.width(1.dp).fillMaxHeight())
        MetricCell("WOKE AT", wokeAt, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(caption: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    Column(
        modifier = modifier.fillMaxHeight().background(c.surface).padding(vertical = 16.dp, horizontal = 14.dp),
    ) {
        Text(caption, fontFamily = PlexMono, fontSize = 10.sp, letterSpacing = 1.4.sp, color = c.faint)
        Spacer(Modifier.height(6.dp))
        Text(value, fontFamily = InstrumentSerif, fontSize = 26.sp, color = c.text)
    }
}

// ─── Stage breakdown ───────────────────────────────────────────────────────────

@Composable
private fun StageBreakdown(durations: StageDurations, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    data class Row4(val name: String, val mins: Long, val color: Color)
    val rows = listOf(
        Row4("Deep",  durations.deepMins,  c.stageDeep),
        Row4("REM",   durations.remMins,   c.stageRem),
        Row4("Light", durations.lightMins, c.stageLight),
        Row4("Awake", durations.awakeMins, c.stageAwake),
    )
    val maxMins = rows.maxOf { it.mins }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { r ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(r.color))
                Spacer(Modifier.width(12.dp))
                Text(r.name, fontFamily = PlexSans, fontSize = 15.sp, color = c.text)
                Spacer(Modifier.weight(1f))
                // proportional track
                Box(
                    modifier = Modifier.width(84.dp).height(4.dp)
                        .clip(RoundedCornerShape(999.dp)).background(c.surface2),
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(fraction = (r.mins.toFloat() / maxMins).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(999.dp)).background(r.color),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    formatDuration(r.mins),
                    modifier = Modifier.width(62.dp),
                    fontFamily = PlexMono, fontSize = 13.sp, color = c.dim,
                    textAlign = TextAlign.End,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        }
    }
}

// ─── Insight card ──────────────────────────────────────────────────────────────

@Composable
private fun InsightCard(insight: Insight) {
    val c = LocalSleepColors.current
    val dotColor = when (insight.tone) {
        InsightTone.GOOD  -> c.good
        InsightTone.DEEP  -> c.stageDeep
        InsightTone.REM   -> c.stageRem
        InsightTone.AWAKE -> c.stageAwake
    }
    NfCard(radius = 18.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(dotColor))
            Column(modifier = Modifier.weight(1f)) {
                Text(insight.title, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text)
                Spacer(Modifier.height(3.dp))
                Text(insight.subtitle, fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 19.sp, color = c.dim)
            }
        }
    }
}

// ─── Empty night block ───────────────────────────────────────────────────────

@Composable
private fun EmptyNightBlock(onBackToTonight: () -> Unit) {
    val c = LocalSleepColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 70.dp, horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = c.lineStrong,
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    ),
                )
            }
            Text("—", fontFamily = InstrumentSerif, fontSize = 34.sp, color = c.dim)
        }
        Spacer(Modifier.height(18.dp))
        Text("No sleep recorded", fontFamily = InstrumentSerif, fontSize = 26.sp, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Your watch wasn't worn that night. Nothing to fix — we'll pick things up tonight.",
            modifier = Modifier.widthIn(max = 260.dp),
            fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 21.sp, color = c.dim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        SecondaryButton("Back to tonight", onClick = onBackToTonight, modifier = Modifier.widthIn(max = 220.dp))
    }
}

// ─── Week strip ────────────────────────────────────────────────────────────────

@Composable
private fun WeekStrip(
    sessions: List<SessionRecord>,
    anchorDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onJumpWeek: (Int) -> Unit,
) {
    val c = LocalSleepColors.current
    val zone = ZoneId.systemDefault()

    val weekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val weekDays  = (0..6).map { weekStart.plusDays(it.toLong()) }
    val weekEnd   = weekDays.last()

    // Sessions filed under their wake-up (ended_at) date, matching the pager.
    val byDate = remember(sessions) {
        sessions.mapNotNull { s ->
            runCatching { Instant.parse(s.ended_at).atZone(zone).toLocalDate() }.getOrNull()?.let { it to s }
        }.toMap()
    }
    val today = LocalDate.now(zone)

    data class Cell(
        val date: LocalDate,
        val abbrev: String,
        val hasData: Boolean,
        val score: Int,
        val fracs: List<Pair<Color, Float>>,
        val selected: Boolean,
        val navigable: Boolean,
    )

    val cells = weekDays.map { date ->
        val s = byDate[date]
        if (s != null) {
            val d = computeStageDurations(s.stages)
            val bouts = countWakeBouts(s.stages)
            val t = d.totalMins.toFloat().coerceAtLeast(1f)
            Cell(
                date = date,
                abbrev = date.format(WeekDayAbbrev),
                hasData = true,
                score = computeSleepScore(d, bouts, s.fired_reason),
                fracs = listOf(
                    c.stageDeep  to d.deepMins  / t,
                    c.stageLight to d.lightMins / t,
                    c.stageRem   to d.remMins   / t,
                    c.stageAwake to d.awakeMins / t,
                ).filter { it.second > 0f },
                selected = date == anchorDate,
                navigable = true,
            )
        } else {
            Cell(date, date.format(WeekDayAbbrev), false, 0, emptyList(),
                selected = date == anchorDate, navigable = !date.isAfter(today))
        }
    }

    val scores = cells.filter { it.hasData }.map { it.score }
    val avg = if (scores.isEmpty()) null else scores.average().roundToInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Spacer(Modifier.height(20.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            // pager row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArrowButton("‹", enabled = true, onClick = { onJumpWeek(1) })
                Text(
                    "${weekStart.format(DayNumFmt)}–${weekEnd.format(WeekEndFmt)}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontFamily = PlexSans, fontSize = 13.sp, color = c.dim,
                )
                ArrowButton("›", enabled = true, onClick = { onJumpWeek(-1) })
            }
            Spacer(Modifier.height(14.dp))
            // seven night cells
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cells.forEach { cell -> WeekCell(cell.date, cell.abbrev, cell.hasData, cell.score,
                    cell.fracs, cell.selected, cell.navigable, onSelect, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Week average", fontFamily = PlexSans, fontSize = 13.sp, color = c.dim)
                Text(avg?.toString() ?: "—", fontFamily = PlexMono, fontSize = 13.sp, color = c.text)
            }
        }
    }
}

@Composable
private fun WeekCell(
    date: LocalDate,
    abbrev: String,
    hasData: Boolean,
    score: Int,
    fracs: List<Pair<Color, Float>>,
    selected: Boolean,
    navigable: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalSleepColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.surface2 else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, c.lineStrong, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(enabled = navigable) { onSelect(date) }
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(abbrev, fontFamily = PlexMono, fontSize = 10.sp, letterSpacing = 0.8.sp, color = c.faint)
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasData) score.toString() else "—",
            fontFamily = InstrumentSerif, fontSize = 20.sp,
            color = when {
                !hasData -> c.faint
                selected -> c.accent
                else     -> c.text
            },
        )
        Spacer(Modifier.height(6.dp))
        // 3px mini bar
        Row(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp))) {
            if (fracs.isEmpty()) {
                Box(Modifier.fillMaxSize().background(c.line))
            } else {
                fracs.forEach { (col, f) ->
                    Box(Modifier.fillMaxHeight().weight(f).background(col))
                }
            }
        }
    }
}

// ─── Loading / empty message ─────────────────────────────────────────────────

@Composable
private fun MessageView(message: String) {
    val c = LocalSleepColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow("SLEEP REPORT")
            Spacer(Modifier.height(14.dp))
            Text(message, fontFamily = InstrumentSerif, fontSize = 26.sp, color = c.text, textAlign = TextAlign.Center)
        }
    }
}
