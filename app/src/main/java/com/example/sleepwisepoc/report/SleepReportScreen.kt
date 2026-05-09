package com.example.sleepwisepoc.report

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.SessionRecord
import com.example.sleepwisepoc.WeeklyReport
import com.example.sleepwisepoc.ui.theme.SleepAccentEnd
import com.example.sleepwisepoc.ui.theme.SleepAccentStart
import com.example.sleepwisepoc.ui.theme.SleepInsightBg
import com.example.sleepwisepoc.ui.theme.SleepInsightText
import com.example.sleepwisepoc.ui.theme.SleepSuccess
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SleepReportScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    viewModel: SleepReportViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {

            TopBar(onBack = onBack, onRefresh = viewModel::refresh)
            Spacer(Modifier.height(20.dp))
            Header()
            Spacer(Modifier.height(28.dp))

            when (val s = state) {
                ReportUiState.Loading -> LoadingView()
                is ReportUiState.Empty -> EmptyView(onRefresh = viewModel::refresh)
                is ReportUiState.Error -> ErrorView(message = s.message, onRetry = viewModel::refresh)
                is ReportUiState.Loaded -> LoadedView(report = s.report)
            }
        }
    }
}

// ─── Top bar + header ─────────────────────────────────────────────────────────

@Composable
private fun TopBar(onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text(
            text = "Your Sleep",
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "How your wake-ups have gone this week",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── States ───────────────────────────────────────────────────────────────────

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyView(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.WbTwilight,
            contentDescription = null,
            tint = SleepAccentEnd,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No nights yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Set a wake-up window and your sleep history will appear here.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRefresh, shape = RoundedCornerShape(14.dp)) { Text("Refresh") }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Couldn't reach the server",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("Retry") }
    }
}

@Composable
private fun LoadedView(report: WeeklyReport) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { WeeklySummaryCard(report = report) }
        item {
            Text(
                text = "Recent nights",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        items(report.sessions, key = { it.id }) { session ->
            SessionCard(session = session)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─── Cards ────────────────────────────────────────────────────────────────────

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatTile(value = "${report.fired_count}", label = "alarms")
                StatTile(value = "${report.favorable_count}", label = "favorable", accent = SleepSuccess)
                StatTile(value = "${report.fallback_count}", label = "fallback", accent = SleepAccentEnd)
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
            fontSize = 28.sp,
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

@Composable
private fun SessionCard(session: SessionRecord) {
    val zone = ZoneId.systemDefault()
    val started = parseInstant(session.started_at)
    val winStart = parseInstant(session.window_start)
    val winEnd = parseInstant(session.window_end)
    val firedAt = session.fired_at?.let(::parseInstant)

    val dateText = started?.atZone(zone)?.format(DateFmt) ?: "—"
    val rangeText = if (winStart != null && winEnd != null)
        "${winStart.atZone(zone).format(TimeFmt)} → ${winEnd.atZone(zone).format(TimeFmt)}"
    else "—"
    val firedText = firedAt?.atZone(zone)?.format(TimeFmt)
    val isFavorable = session.fired_reason == "favorable"
    val chipColor = if (isFavorable) SleepSuccess else SleepAccentEnd
    val chipLabel = if (isFavorable) "favorable" else "fallback"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                if (session.fired_reason != null) {
                    ReasonChip(label = chipLabel, color = chipColor)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Window  $rangeText",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (firedText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Woke at  $firedText",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.stages.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                StageSparkline(
                    confidences = session.stages.map { it.conf },
                    accent = if (isFavorable) SleepAccentStart else SleepAccentEnd,
                )
            }
        }
    }
}

@Composable
private fun ReasonChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(6.dp).clip(CircleShape).background(color),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}

@Composable
private fun StageSparkline(confidences: List<Float>, accent: Color) {
    if (confidences.size < 2) {
        Text(
            text = "${confidences.size} prediction tick" + if (confidences.size == 1) "" else "s",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(40.dp),
    ) {
        val w = size.width
        val h = size.height
        val n = confidences.size
        val stepX = w / (n - 1)
        val points = confidences.mapIndexed { i, c ->
            val y = h - (c.coerceIn(0f, 1f) * h)
            Offset(i * stepX, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = accent,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 6f,
            )
        }
        points.forEach { p ->
            drawCircle(color = accent, radius = 5f, center = p)
        }
    }
}

private fun parseInstant(s: String?): Instant? = try {
    if (s == null) null else Instant.parse(s)
} catch (_: Throwable) {
    null
}
