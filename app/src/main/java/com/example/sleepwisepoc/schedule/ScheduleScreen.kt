package com.example.sleepwisepoc.schedule

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SleepScheduleStore(application)

    val schedule = store.schedule.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), SleepSchedule()
    )

    fun saveWeekday(day: DaySchedule) = viewModelScope.launch { store.saveWeekday(day) }
    fun saveWeekend(day: DaySchedule) = viewModelScope.launch { store.saveWeekend(day) }
}

// ─── Day-of-week constants ────────────────────────────────────────────────────

private val DAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")
private val DAY_NAMES   = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

/** 0=Sun,1=Mon…6=Sat. Israeli week: Sun–Thu = weekday; Fri/Sat = weekend. */
private fun isWeekday(i: Int) = i in 0..4

private fun sameAsText(selected: Int): String {
    return if (isWeekday(selected)) {
        (0..4).filter { it != selected }.joinToString(", ") { DAY_NAMES[it].take(3) }
    } else {
        if (selected == 5) "Sat" else "Fri"
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val TimeFmt      = DateTimeFormatter.ofPattern("hh:mm a")
private val ShortTimeFmt = DateTimeFormatter.ofPattern("h:mm")

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = viewModel(),
) {
    val c = LocalSleepColors.current
    val schedule by viewModel.schedule.collectAsState()
    // selectedDay: 0=Sun … 6=Sat; default to Sunday (start of Israeli work week)
    var selectedDay by remember { mutableIntStateOf(0) }
    var showTimePicker  by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    var showSnoozePicker by remember { mutableStateOf(false) }

    val isWd  = isWeekday(selectedDay)
    val day   = if (isWd) schedule.weekday else schedule.weekend
    val onSave: (DaySchedule) -> Unit = if (isWd) viewModel::saveWeekday else viewModel::saveWeekend

    if (showTimePicker) {
        SleepTimePickerDialog(
            initialTime = day.wakeTime,
            onConfirm = { time ->
                onSave(day.copy(wakeTime = time))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showSoundPicker) {
        PickerDialog(
            title = "Alarm Sound",
            options = ALARM_SOUNDS,
            selected = day.alarmSound,
            onSelect = { onSave(day.copy(alarmSound = it)); showSoundPicker = false },
            onDismiss = { showSoundPicker = false },
        )
    }

    if (showSnoozePicker) {
        PickerDialog(
            title = "Snooze Duration",
            options = SNOOZE_OPTIONS.map { "$it min" },
            selected = "${day.snoozeMinutes} min",
            onSelect = { label ->
                val mins = label.removeSuffix(" min").toIntOrNull() ?: day.snoozeMinutes
                onSave(day.copy(snoozeMinutes = mins))
                showSnoozePicker = false
            },
            onDismiss = { showSnoozePicker = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Schedule", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text("Set once, we handle the rest", fontSize = 14.sp, color = c.textSecondary)
        }

        Spacer(Modifier.height(20.dp))

        // ── Day circle picker ──────────────────────────────────────────────────
        DayRowPicker(
            selectedDay  = selectedDay,
            weekdaySched = schedule.weekday,
            weekendSched = schedule.weekend,
            onSelect     = { selectedDay = it },
        )

        Spacer(Modifier.height(10.dp))

        // ── "Editing X · same as Y, Z" caption ────────────────────────────────
        val sameAs = sameAsText(selectedDay)
        Text(
            text = buildAnnotatedString {
                append("Editing ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.textPrimary)) {
                    append(DAY_NAMES[selectedDay])
                }
                append(" · same as ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.textPrimary)) {
                    append(sameAs)
                }
            },
            modifier  = Modifier.padding(horizontal = 24.dp),
            fontSize  = 13.sp,
            color     = c.textSecondary,
        )

        Spacer(Modifier.height(16.dp))

        // Wake time + window + smart alarm
        SettingsCard {
            SettingsRow(
                label = "Wake-up time",
                value = day.wakeTime.format(TimeFmt),
                onClick = { showTimePicker = true },
            )

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Smart window", fontSize = 15.sp, color = c.textPrimary)
                    Text(
                        "${day.windowMinutes} min",
                        fontSize = 15.sp,
                        color = c.textAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wake window: ${day.windowStart.format(shortFmt())} – ${day.wakeTime.format(shortFmt())}",
                    fontSize = 12.sp,
                    color = c.textSecondary,
                )
                Slider(
                    value = day.windowMinutes.toFloat(),
                    onValueChange = { onSave(day.copy(windowMinutes = it.toInt())) },
                    valueRange = 15f..60f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = c.primary,
                        activeTrackColor = c.primary,
                        inactiveTrackColor = c.surface2,
                    ),
                )
            }

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Smart Alarm", fontSize = 15.sp, color = c.textPrimary)
                    Text("Wake me during light sleep", fontSize = 12.sp, color = c.textSecondary)
                }
                Switch(
                    checked = day.smartAlarm,
                    onCheckedChange = { onSave(day.copy(smartAlarm = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.textPrimary,
                        checkedTrackColor = c.primary,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Wake Experience
        SettingsCard {
            Text(
                "WAKE EXPERIENCE",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary,
                letterSpacing = 1.sp,
            )
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
            SettingsRow(
                label = "Alarm Sound",
                value = day.alarmSound,
                valueColor = c.success,
                onClick = { showSoundPicker = true },
            )
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
            SettingsRow(
                label = "Snooze",
                value = "${day.snoozeMinutes} min",
                onClick = { showSnoozePicker = true },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── Day row picker ───────────────────────────────────────────────────────────

@Composable
private fun DayRowPicker(
    selectedDay: Int,
    weekdaySched: DaySchedule,
    weekendSched: DaySchedule,
    onSelect: (Int) -> Unit,
) {
    val c = LocalSleepColors.current
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        DAY_LETTERS.forEachIndexed { i, letter ->
            val isSelected = i == selectedDay
            val sched      = if (isWeekday(i)) weekdaySched else weekendSched
            val timeText   = sched.wakeTime.format(ShortTimeFmt)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { onSelect(i) },
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> c.primary
                                isWeekday(i) == isWeekday(selectedDay) ->
                                    c.surface2.copy(alpha = 0.6f)
                                else -> c.surface
                            }
                        )
                        .then(
                            if (!isSelected && isWeekday(i) == isWeekday(selectedDay))
                                Modifier.border(1.dp, c.primary.copy(0.25f), CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (isSelected) Color.White else c.textSecondary,
                    )
                }
                Text(
                    timeText,
                    fontSize = 10.sp,
                    color    = if (isWeekday(i) == isWeekday(selectedDay))
                        c.textPrimary else c.textSecondary,
                )
            }
        }
    }
}

// ─── Picker dialog ────────────────────────────────────────────────────────────

@Composable
private fun PickerDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalSleepColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = c.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option, fontSize = 15.sp, color = c.textPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.textSecondary)
            }
        },
        containerColor = c.surface,
    )
}

// ─── Reusable composables ─────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSleepColors.current
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalSleepColors.current
    val resolvedValueColor = valueColor ?: c.textAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = c.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 15.sp, color = resolvedValueColor, fontWeight = FontWeight.Medium)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ChevronRight, null,
                    tint = c.textSecondary,
                    modifier = Modifier.height(18.dp).width(18.dp),
                )
            }
        }
    }
}

private fun shortFmt(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
