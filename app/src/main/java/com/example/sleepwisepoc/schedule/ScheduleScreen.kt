package com.example.sleepwisepoc.schedule

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

    /** Persist just the edited day (0=Sun … 6=Sat); other days are untouched. */
    fun saveDay(dayIndex: Int, day: DaySchedule) = viewModelScope.launch { store.saveDay(dayIndex, day) }
}

// ─── Day-of-week constants ────────────────────────────────────────────────────

private val DAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")
private val DAY_NAMES   = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

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
    var selectedDay      by remember { mutableIntStateOf(0) }
    var showTimePicker   by remember { mutableStateOf(false) }
    var showSoundPicker  by remember { mutableStateOf(false) }
    var showSnoozePicker by remember { mutableStateOf(false) }
    var isMultiSelect    by remember { mutableStateOf(false) }
    var multiDays        by remember { mutableStateOf(emptySet<Int>()) }

    val day   = schedule[selectedDay]
    val onSave: (DaySchedule) -> Unit = { viewModel.saveDay(selectedDay, it) }

    val pickerInitialTime = if (isMultiSelect && multiDays.isNotEmpty())
        schedule[multiDays.min()].wakeTime
    else
        day.wakeTime

    if (showTimePicker) {
        SleepTimePickerDialog(
            initialTime = pickerInitialTime,
            onConfirm = { time ->
                if (isMultiSelect) {
                    multiDays.forEach { d -> viewModel.saveDay(d, schedule[d].copy(wakeTime = time)) }
                    isMultiSelect = false
                    multiDays = emptySet()
                } else {
                    onSave(day.copy(wakeTime = time))
                }
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
            selectedDay   = selectedDay,
            schedule      = schedule,
            isMultiSelect = isMultiSelect,
            multiDays     = multiDays,
            onSelect      = { selectedDay = it },
            onToggle      = { d ->
                val next = if (d in multiDays) multiDays - d else multiDays + d
                multiDays = next
                if (next.isEmpty()) isMultiSelect = false
            },
            onLongPress   = { d ->
                isMultiSelect = true
                multiDays = multiDays + d
            },
        )

        Spacer(Modifier.height(8.dp))

        if (isMultiSelect) {
            // ── Multi-select action bar ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    if (multiDays.isEmpty()) "Tap days to select"
                    else "${multiDays.size} day${if (multiDays.size != 1) "s" else ""} selected",
                    fontSize = 13.sp,
                    color    = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick  = { if (multiDays.isNotEmpty()) showTimePicker = true },
                    enabled  = multiDays.isNotEmpty(),
                ) {
                    Text("Set time", color = if (multiDays.isNotEmpty()) c.primary else c.textSecondary)
                }
                TextButton(onClick = { isMultiSelect = false; multiDays = emptySet() }) {
                    Text("Cancel", color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Text(
                "Hold a day to select multiple",
                modifier = Modifier.padding(horizontal = 24.dp),
                fontSize = 11.sp,
                color    = c.textSecondary.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            // ── "Editing X" caption ────────────────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    append("Editing ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.textPrimary)) {
                        append(DAY_NAMES[selectedDay])
                    }
                },
                modifier = Modifier.padding(horizontal = 24.dp),
                fontSize = 13.sp,
                color    = c.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Single-day settings (hidden during multi-select) ──────────────────
        if (!isMultiSelect) SettingsCard {
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
        if (!isMultiSelect) SettingsCard {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayRowPicker(
    selectedDay: Int,
    schedule: SleepSchedule,
    isMultiSelect: Boolean,
    multiDays: Set<Int>,
    onSelect: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    val c = LocalSleepColors.current
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        DAY_LETTERS.forEachIndexed { i, letter ->
            val isSelected  = !isMultiSelect && i == selectedDay
            val isChecked   = isMultiSelect && i in multiDays
            val timeText    = schedule[i].wakeTime.format(ShortTimeFmt)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // Gal's multi-select (long-press to pick multiple days) + no
                // rectangular ripple (selection is shown by the circle turning purple).
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick     = { if (isMultiSelect) onToggle(i) else onSelect(i) },
                    onLongClick = { onLongPress(i) },
                ),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isChecked  -> c.primary
                                isSelected -> c.primary
                                isMultiSelect -> c.surface2
                                else       -> c.surface
                            }
                        )
                        .then(
                            if (isMultiSelect && !isChecked)
                                Modifier.border(1.5.dp, c.border, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = when {
                            isChecked || isSelected -> Color.White
                            else -> c.textSecondary
                        },
                    )
                }
                Text(
                    timeText,
                    fontSize = 10.sp,
                    color    = when {
                        isChecked  -> c.primary
                        isSelected -> c.textPrimary
                        else       -> c.textSecondary
                    },
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
