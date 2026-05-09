package com.example.sleepwisepoc.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sleepwisepoc.service.SleepMonitoringService
import com.example.sleepwisepoc.ui.theme.SleepAccentEnd
import com.example.sleepwisepoc.ui.theme.SleepAccentStart
import com.example.sleepwisepoc.ui.theme.SleepInsightBg
import com.example.sleepwisepoc.ui.theme.SleepInsightText
import com.example.sleepwisepoc.ui.theme.SleepSuccess
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HourFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private enum class TimeField { Start, End }

@Composable
fun SmartAlarmScreen(
    modifier: Modifier = Modifier,
    viewModel: AlarmViewModel,
    onDevMode: () -> Unit,
    onHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var pickingField by remember { mutableStateOf<TimeField?>(null) }
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            TopBarLinks(
                onDevMode = onDevMode,
                onHistory = onHistory,
                onTest5Min = {
                    Log.d("SmartAlarmScreen", "Test 5m (Run now) tapped")
                    val now = LocalTime.now().withSecond(0).withNano(0)
                    val end = now.plusMinutes(3)
                    SleepMonitoringService.start(context, now, end)
                    Toast.makeText(context, "Monitoring now → fires within 3 min", Toast.LENGTH_SHORT).show()
                },
            )

            Spacer(Modifier.height(28.dp))

            Header()

            Spacer(Modifier.height(36.dp))

            TimeRangeSection(
                start = state.start,
                end = state.end,
                onStartClick = { pickingField = TimeField.Start },
                onEndClick = { pickingField = TimeField.End },
            )

            Spacer(Modifier.height(24.dp))

            WindowSummaryCard(start = state.start, end = state.end)

            Spacer(Modifier.height(16.dp))

            InsightCard(end = state.end)

            AnimatedVisibility(visible = state.errorMessage != null) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    ErrorPill(message = state.errorMessage.orEmpty())
                }
            }

            Spacer(Modifier.height(36.dp))

            SaveButton(
                isSaved = state.savedAt != null,
                enabled = state.errorMessage == null,
                onClick = viewModel::save,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    pickingField?.let { field ->
        val initial = if (field == TimeField.Start) state.start else state.end
        TimePickerDialog(
            initial = initial,
            title = if (field == TimeField.Start) "Earliest wake time" else "Latest wake time",
            onConfirm = { picked ->
                if (field == TimeField.Start) viewModel.setStart(picked) else viewModel.setEnd(picked)
                pickingField = null
            },
            onDismiss = { pickingField = null },
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBarLinks(
    onDevMode: () -> Unit,
    onHistory: () -> Unit,
    onTest5Min: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onHistory) {
            Text(
                "History",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTest5Min) {
                Text(
                    "Test 5m",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onDevMode) {
                Text(
                    "Dev",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text(
            text = "Smart Alarm",
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Wake up at the perfect moment",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Time range cards ─────────────────────────────────────────────────────────

@Composable
private fun TimeRangeSection(
    start: LocalTime,
    end: LocalTime,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TimeCard(
            modifier = Modifier.weight(1f),
            label = "Earliest",
            time = start,
            accent = SleepAccentStart,
            icon = Icons.Outlined.Bedtime,
            onClick = onStartClick,
        )
        TimeCard(
            modifier = Modifier.weight(1f),
            label = "Latest",
            time = end,
            accent = SleepAccentEnd,
            icon = Icons.Outlined.WbTwilight,
            onClick = onEndClick,
        )
    }
}

@Composable
private fun TimeCard(
    modifier: Modifier = Modifier,
    label: String,
    time: LocalTime,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(accent.copy(alpha = 0.35f), label = "border")
    Surface(
        modifier = modifier
            .heightIn(min = 150.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = time.format(HourFormatter),
                fontSize = 38.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Tap to change",
                fontSize = 11.sp,
                color = accent,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─── Summary + insight ────────────────────────────────────────────────────────

@Composable
private fun WindowSummaryCard(start: LocalTime, end: LocalTime) {
    val minutes = Duration.between(start, end).toMinutes().coerceAtLeast(0)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Your wake-up window",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${start.format(HourFormatter)}  →  ${end.format(HourFormatter)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$minutes min",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InsightCard(end: LocalTime) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SleepInsightBg,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = SleepInsightText,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Column {
                Text(
                    text = "We will wake you up during light sleep within this window.",
                    fontSize = 14.sp,
                    color = SleepInsightText,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "If no light sleep is detected, the alarm will ring at ${end.format(HourFormatter)}.",
                    fontSize = 13.sp,
                    color = SleepInsightText.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun ErrorPill(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─── Save button ──────────────────────────────────────────────────────────────

@Composable
private fun SaveButton(isSaved: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isSaved) 0.98f else 1f, label = "savedScale")
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSaved) SleepSuccess else MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = isSaved, enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = if (isSaved) "Saved" else "Save Alarm",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─── Time picker dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    title: String,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .width(340.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(20.dp))
                TimePicker(
                    state = pickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) },
                    ) { Text("OK", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
