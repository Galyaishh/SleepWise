package com.example.sleepwisepoc.auth

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import com.example.sleepwisepoc.SamsungHealthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepwisepoc.schedule.ALARM_SOUNDS
import com.example.sleepwisepoc.schedule.DaySchedule
import com.example.sleepwisepoc.schedule.SleepScheduleStore
import com.example.sleepwisepoc.schedule.SleepTimePickerDialog
import com.example.sleepwisepoc.ui.theme.NightBg
import com.example.sleepwisepoc.ui.theme.NightBorder
import com.example.sleepwisepoc.ui.theme.NightPrimary
import com.example.sleepwisepoc.ui.theme.NightPrimaryEnd
import com.example.sleepwisepoc.ui.theme.NightSurface
import com.example.sleepwisepoc.ui.theme.NightSuccess
import com.example.sleepwisepoc.ui.theme.NightSurface2
import com.example.sleepwisepoc.ui.theme.NightTextAccent
import com.example.sleepwisepoc.ui.theme.NightTextPrimary
import com.example.sleepwisepoc.ui.theme.NightTextSecondary
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFmt = DateTimeFormatter.ofPattern("hh:mm a")

private val SOUND_EMOJIS = mapOf(
    "Sunrise Chimes" to "☀️",
    "Forest Morning"  to "🌿",
    "Ocean Waves"     to "🌊",
    "Gentle Bell"     to "🔔",
    "Vibration Only"  to "🔕",
)

private fun isEmulator() =
    Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
    Build.MODEL.contains("emulator", ignoreCase = true) ||
    Build.MODEL.contains("sdk", ignoreCase = true) ||
    Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
    Build.HARDWARE.contains("goldfish", ignoreCase = true)

@Composable
fun SetupWizardScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val store   = remember { SleepScheduleStore(context) }

    var step by remember { mutableIntStateOf(0) }

    // Hoisted state — shared across steps and saved on finish
    var weekdayTime   by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var weekendTime   by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var selectedSound by remember { mutableStateOf(ALARM_SOUNDS.first()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(0f to Color(0xFF0D0F22), 0.5f to NightBg, 1f to NightBg)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(52.dp))

            StepIndicator(current = step, total = 3)

            Spacer(Modifier.height(40.dp))

            when (step) {
                0 -> ConnectWatchStep()
                1 -> ScheduleStep(
                    weekdayTime     = weekdayTime,
                    weekendTime     = weekendTime,
                    onWeekdayChange = { weekdayTime = it },
                    onWeekendChange = { weekendTime = it },
                )
                2 -> WakeExperienceStep(
                    selectedSound = selectedSound,
                    onSoundChange = { selectedSound = it },
                )
            }

            Spacer(Modifier.weight(1f))

            // Primary CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(NightPrimary, NightPrimaryEnd)))
                    .clickable {
                        if (step < 2) {
                            step++
                        } else {
                            scope.launch {
                                store.saveWeekday(
                                    DaySchedule(
                                        wakeTime      = weekdayTime,
                                        windowMinutes = 30,
                                        alarmSound    = selectedSound,
                                    )
                                )
                                store.saveWeekend(
                                    DaySchedule(
                                        wakeTime      = weekendTime,
                                        windowMinutes = 45,
                                        alarmSound    = selectedSound,
                                    )
                                )
                                onComplete()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = if (step < 2) "Continue" else "Start using SleepWise",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = NightTextPrimary,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (step < 2) {
                Text(
                    text     = "Skip for now",
                    fontSize = 14.sp,
                    color    = NightTextSecondary,
                    modifier = Modifier.clickable { step++ }.padding(8.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Step indicator ───────────────────────────────────────────────────────────

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == current) 32.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == current) NightPrimary else NightPrimary.copy(alpha = 0.25f))
            )
        }
    }
}

// ─── Step 1: Connect watch ────────────────────────────────────────────────────

private enum class WatchCheckState { Checking, Connected, Disconnected }

@Composable
private fun ConnectWatchStep() {
    val context = LocalContext.current
    var watchState by remember { mutableStateOf(WatchCheckState.Checking) }

    LaunchedEffect(Unit) {
        if (isEmulator()) {
            watchState = WatchCheckState.Disconnected
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val mgr = SamsungHealthManager(context)
                if (!mgr.initialize()) {
                    watchState = WatchCheckState.Disconnected
                    return@withContext
                }
                val hr   = mgr.readHeartRate(hoursBack = 1)
                val temp = mgr.readSkinTemperature(hoursBack = 1)
                val spo2 = mgr.readBloodOxygen(hoursBack = 1)
                watchState = if (hr.size + temp.size + spo2.size > 0)
                    WatchCheckState.Connected
                else
                    WatchCheckState.Disconnected
            } catch (t: Throwable) {
                watchState = WatchCheckState.Disconnected
            }
        }
    }

    val statusText  = when (watchState) {
        WatchCheckState.Checking     -> "Checking…"
        WatchCheckState.Connected    -> "Connected"
        WatchCheckState.Disconnected -> "Not connected"
    }
    val statusColor = when (watchState) {
        WatchCheckState.Connected    -> NightSuccess
        WatchCheckState.Checking     -> NightTextSecondary
        WatchCheckState.Disconnected -> NightTextSecondary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⌚", fontSize = 52.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Connect your\nsmartwatch",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = NightTextPrimary,
            textAlign  = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "SleepWise uses your Galaxy Watch to track heart rate and sleep stages throughout the night.",
            fontSize   = 15.sp,
            color      = NightTextSecondary,
            textAlign  = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NightSurface)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(NightSurface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Watch, null, tint = NightTextAccent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Galaxy Watch", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = NightTextPrimary)
                        Text(statusText, fontSize = 13.sp, color = statusColor)
                    }
                }

                if (watchState != WatchCheckState.Connected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(NightPrimary, NightPrimaryEnd)))
                            .clickable { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Bluetooth, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Open Bluetooth Settings", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                    Text(
                        "Already connected? Tap Continue below.",
                        fontSize  = 12.sp,
                        color     = NightTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "Your watch is ready. Tap Continue.",
                        fontSize  = 12.sp,
                        color     = NightSuccess,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ─── Step 2: Schedule ─────────────────────────────────────────────────────────

@Composable
private fun ScheduleStep(
    weekdayTime: LocalTime,
    weekendTime: LocalTime,
    onWeekdayChange: (LocalTime) -> Unit,
    onWeekendChange: (LocalTime) -> Unit,
) {
    var showWeekdayPicker by remember { mutableStateOf(false) }
    var showWeekendPicker by remember { mutableStateOf(false) }

    if (showWeekdayPicker) {
        SleepTimePickerDialog(
            initialTime = weekdayTime,
            onConfirm   = { onWeekdayChange(it); showWeekdayPicker = false },
            onDismiss   = { showWeekdayPicker = false },
        )
    }
    if (showWeekendPicker) {
        SleepTimePickerDialog(
            initialTime = weekendTime,
            onConfirm   = { onWeekendChange(it); showWeekendPicker = false },
            onDismiss   = { showWeekendPicker = false },
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🗓️", fontSize = 52.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Set your sleep\nschedule",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = NightTextPrimary,
            textAlign  = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "We'll automatically prepare your smart alarm each night based on your schedule.",
            fontSize   = 15.sp,
            color      = NightTextSecondary,
            textAlign  = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NightSurface),
        ) {
            TimeRow("Weekdays", weekdayTime.format(TimeFmt)) { showWeekdayPicker = true }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NightBorder))
            TimeRow("Weekends", weekendTime.format(TimeFmt)) { showWeekendPicker = true }
        }
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = NightTextPrimary)
        Text(value, fontSize = 15.sp, color = NightTextAccent, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Step 3: Wake experience ──────────────────────────────────────────────────

@Composable
private fun WakeExperienceStep(
    selectedSound: String,
    onSoundChange: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎵", fontSize = 52.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Choose your\nwake experience",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = NightTextPrimary,
            textAlign  = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "How do you want to wake up each morning?",
            fontSize  = 15.sp,
            color     = NightTextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NightSurface),
        ) {
            ALARM_SOUNDS.forEachIndexed { i, sound ->
                val emoji = SOUND_EMOJIS[sound] ?: "🎵"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSoundChange(sound) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("$emoji  $sound", fontSize = 15.sp, color = NightTextPrimary)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (sound == selectedSound) NightPrimary else NightSurface2),
                    )
                }
                if (i < ALARM_SOUNDS.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NightBorder))
                }
            }
        }
    }
}
