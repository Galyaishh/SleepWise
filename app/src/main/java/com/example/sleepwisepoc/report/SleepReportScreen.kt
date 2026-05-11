package com.example.sleepwisepoc.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.SessionRecord
import com.example.sleepwisepoc.WeeklyReport
import com.example.sleepwisepoc.ui.theme.SleepAccentEnd
import com.example.sleepwisepoc.ui.theme.SleepAccentStart
import com.example.sleepwisepoc.ui.theme.SleepSuccess
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ─── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun SleepReportScreen(
    modifier: Modifier = Modifier,
    viewModel: SleepReportViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(onRefresh = viewModel::refresh)
            when (val s = state) {
                ReportUiState.Loading        -> LoadingView()
                is ReportUiState.Empty       -> EmptyView(onRefresh = viewModel::refresh)
                is ReportUiState.Error       -> ErrorView(message = s.message, onRetry = viewModel::refresh)
                is ReportUiState.Loaded      -> LoadedContent(report = s.report, isDemoData = s.isDemoData, onRefresh = viewModel::refresh)
            }
        }
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sleep",
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Your recent nights",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── State views ──────────────────────────────────────────────────────────────

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyView(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.WbTwilight,
            contentDescription = null,
            tint = SleepAccentEnd,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No nights recorded yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Set a wake-up window tonight and\nyour sleep history will appear here.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRefresh, shape = RoundedCornerShape(14.dp)) { Text("Refresh") }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Couldn't load sleep data",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) { Text("Retry") }
    }
}

@Composable
private fun LoadedContent(report: WeeklyReport, isDemoData: Boolean = false, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isDemoData) {
            item { DemoBanner() }
        }
        item { WeeklySummaryCard(report = report) }
        item {
            Text(
                text = "Recent nights",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }
        items(report.sessions, key = { it.id }) { session ->
            SessionCard(session = session)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DemoBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Demo data — connect your backend to see real sleep history",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ─── Weekly summary card ──────────────────────────────────────────────────────

@Composable
private fun WeeklySummaryCard(report: WeeklyReport) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "This week",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatTile(value = "${report.fired_count}",   label = "nights")
                StatTile(value = "${report.favorable_count}", label = "optimal", accent = SleepSuccess)
                StatTile(value = "${report.fallback_count}",  label = "fallback", accent = SleepAccentEnd)
                StatTile(value = "%.0f".format(report.avg_window_minutes), label = "avg min")
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, accent: Color = MaterialTheme.colorScheme.onBackground) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Session card ─────────────────────────────────────────────────────────────

@Composable
private fun SessionCard(session: SessionRecord) {
    val zone     = ZoneId.systemDefault()
    val started  = parseInstant(session.started_at)
    val ended    = parseInstant(session.ended_at)
    val winStart = parseInstant(session.window_start)
    val winEnd   = parseInstant(session.window_end)
    val firedAt  = session.fired_at?.let(::parseInstant)

    val dateText   = started?.atZone(zone)?.format(DateFmt) ?: "—"
    val windowText = if (winStart != null && winEnd != null)
        "${winStart.atZone(zone).format(TimeFmt)} → ${winEnd.atZone(zone).format(TimeFmt)}"
    else "—"
    val firedText  = firedAt?.atZone(zone)?.format(TimeFmt)
    val durationText = sessionDuration(started, ended)

    val isFavorable = session.fired_reason == "favorable"
    val chipColor   = if (isFavorable) SleepSuccess else SleepAccentEnd
    val chipLabel   = if (isFavorable) "optimal" else "fallback"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            // ── Header row ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (session.fired_reason != null) {
                    ReasonChip(label = chipLabel, color = chipColor)
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Meta row (window + duration) ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaLabel(label = "Window", value = windowText)
                if (durationText != null) {
                    Text(
                        text = "·",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MetaLabel(label = "Duration", value = durationText)
                }
            }

            if (firedText != null) {
                Spacer(Modifier.height(2.dp))
                MetaLabel(label = "Woke at", value = firedText)
            }

            // ── Sleep stage data ─────────────────────────────────────────────
            if (session.stages.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Divider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp,
                )
                Spacer(Modifier.height(14.dp))

                StagePillBar(
                    stages = session.stages,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                SleepHypnogram(
                    stages = session.stages,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

@Composable
private fun MetaLabel(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label  ",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ReasonChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun parseInstant(s: String?): Instant? = try {
    if (s == null) null else Instant.parse(s)
} catch (_: Throwable) {
    null
}

private fun sessionDuration(started: Instant?, ended: Instant?): String? {
    if (started == null || ended == null) return null
    val mins = Duration.between(started, ended).toMinutes()
    if (mins <= 0) return null
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}
