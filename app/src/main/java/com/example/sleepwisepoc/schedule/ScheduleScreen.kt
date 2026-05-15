package com.example.sleepwisepoc.schedule

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.ui.theme.NightBg
import com.example.sleepwisepoc.ui.theme.NightBorder
import com.example.sleepwisepoc.ui.theme.NightPrimary
import com.example.sleepwisepoc.ui.theme.NightSuccess
import com.example.sleepwisepoc.ui.theme.NightSurface
import com.example.sleepwisepoc.ui.theme.NightSurface2
import com.example.sleepwisepoc.ui.theme.NightTextAccent
import com.example.sleepwisepoc.ui.theme.NightTextPrimary
import com.example.sleepwisepoc.ui.theme.NightTextSecondary
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
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

// ─── Screen ───────────────────────────────────────────────────────────────────

private val TimeFmt = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = viewModel(),
) {
    val schedule by viewModel.schedule.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NightBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        // Header
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Schedule", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = NightTextPrimary)
            Spacer(Modifier.height(3.dp))
            Text("Set once, we handle the rest", fontSize = 14.sp, color = NightTextSecondary)
        }

        Spacer(Modifier.height(20.dp))

        // Weekday / Weekend tabs
        TabRow(
            selectedTabIndex = tab,
            modifier = Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)),
            containerColor = NightSurface,
            contentColor = NightTextPrimary,
            indicator = { tabPositions ->
                Box(
                    Modifier
                        .tabIndicatorOffset(tabPositions[tab])
                        .height(40.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NightSurface2)
                )
            },
            divider = {},
        ) {
            listOf("Weekdays", "Weekends").forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (tab == i) NightTextPrimary else NightTextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val day = if (tab == 0) schedule.weekday else schedule.weekend
        val onSave: (DaySchedule) -> Unit = if (tab == 0) viewModel::saveWeekday else viewModel::saveWeekend

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

        // Wake time row
        SettingsCard {
            SettingsRow(
                label = "Wake-up time",
                value = day.wakeTime.format(TimeFmt),
                onClick = { showTimePicker = true },
            )

            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)

            // Window duration slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Smart window", fontSize = 15.sp, color = NightTextPrimary)
                    Text(
                        "${day.windowMinutes} min",
                        fontSize = 15.sp,
                        color = NightTextAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wake window: ${day.windowStart.format(shortFmt())} – ${day.wakeTime.format(shortFmt())}",
                    fontSize = 12.sp,
                    color = NightTextSecondary,
                )
                Slider(
                    value = day.windowMinutes.toFloat(),
                    onValueChange = { onSave(day.copy(windowMinutes = it.toInt())) },
                    valueRange = 15f..60f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = NightPrimary,
                        activeTrackColor = NightPrimary,
                        inactiveTrackColor = NightSurface2,
                    ),
                )
            }

            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)

            // Smart alarm toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Smart Alarm", fontSize = 15.sp, color = NightTextPrimary)
                    Text("Wake me during light sleep", fontSize = 12.sp, color = NightTextSecondary)
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NightTextPrimary,
                        checkedTrackColor = NightPrimary,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Wake Experience section
        SettingsCard {
            Text(
                "WAKE EXPERIENCE",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = NightTextSecondary,
                letterSpacing = 1.sp,
            )
            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
            SettingsRow(label = "☀️  Sunrise Chimes", value = "Selected", valueColor = NightSuccess)
            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
            SettingsRow(label = "Snooze", value = "9 min")
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── Reusable composables ─────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NightSurface),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    valueColor: Color = NightTextAccent,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = NightTextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 15.sp, color = valueColor, fontWeight = FontWeight.Medium)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ChevronRight, null,
                    tint = NightTextSecondary,
                    modifier = Modifier.height(18.dp).width(18.dp),
                )
            }
        }
    }
}

private fun shortFmt(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
