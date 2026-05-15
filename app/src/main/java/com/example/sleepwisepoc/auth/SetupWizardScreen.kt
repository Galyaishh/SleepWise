package com.example.sleepwisepoc.auth

import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepwisepoc.schedule.SleepTimePickerDialog
import com.example.sleepwisepoc.ui.theme.NightBg
import com.example.sleepwisepoc.ui.theme.NightPrimary
import com.example.sleepwisepoc.ui.theme.NightPrimaryEnd
import com.example.sleepwisepoc.ui.theme.NightSurface
import com.example.sleepwisepoc.ui.theme.NightSurface2
import com.example.sleepwisepoc.ui.theme.NightTextAccent
import com.example.sleepwisepoc.ui.theme.NightTextPrimary
import com.example.sleepwisepoc.ui.theme.NightTextSecondary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFmt = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
fun SetupWizardScreen(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(52.dp))

            // Step indicator
            StepIndicator(current = step, total = 3)

            Spacer(Modifier.height(40.dp))

            when (step) {
                0 -> ConnectWatchStep()
                1 -> ScheduleStep()
                2 -> WakeExperienceStep()
            }

            Spacer(Modifier.weight(1f))

            // CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(NightPrimary, NightPrimaryEnd)))
                    .clickable { if (step < 2) step++ else onComplete() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (step < 2) "Continue" else "Start using SleepWise",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NightTextPrimary,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (step < 2) {
                Text(
                    text = "Skip for now",
                    fontSize = 14.sp,
                    color = NightTextSecondary,
                    modifier = Modifier
                        .clickable { step++ }
                        .padding(8.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

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

@Composable
private fun ConnectWatchStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⌚", fontSize = 52.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Connect your\nsmartwatch",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = NightTextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "SleepWise uses your Galaxy Watch to track heart rate and sleep stages throughout the night.",
            fontSize = 15.sp,
            color = NightTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(32.dp))
        DeviceStatusCard()
    }
}

@Composable
private fun DeviceStatusCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NightSurface)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NightSurface2),
                contentAlignment = Alignment.Center,
            ) {
                Text("⌚", fontSize = 22.sp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Galaxy Watch 6", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = NightTextPrimary)
                Text("Connected", fontSize = 13.sp, color = Color(0xFF4ADE80))
            }
            Text("78%", fontSize = 14.sp, color = NightTextSecondary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ScheduleStep() {
    var weekdayTime by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var weekendTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var showWeekdayPicker by remember { mutableStateOf(false) }
    var showWeekendPicker by remember { mutableStateOf(false) }

    if (showWeekdayPicker) {
        SleepTimePickerDialog(
            initialTime = weekdayTime,
            onConfirm = { weekdayTime = it; showWeekdayPicker = false },
            onDismiss = { showWeekdayPicker = false },
        )
    }
    if (showWeekendPicker) {
        SleepTimePickerDialog(
            initialTime = weekendTime,
            onConfirm = { weekendTime = it; showWeekendPicker = false },
            onDismiss = { showWeekendPicker = false },
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🗓️", fontSize = 52.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Set your sleep\nschedule",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = NightTextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "We'll automatically prepare your smart alarm each night based on your schedule.",
            fontSize = 15.sp,
            color = NightTextSecondary,
            textAlign = TextAlign.Center,
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
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF252B4A)))
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = NightTextPrimary)
        Text(value, fontSize = 15.sp, color = NightTextAccent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WakeExperienceStep() {
    val sounds = listOf("☀️  Sunrise Chimes", "🌧️  Soft Rain", "🌊  Ocean Waves", "🎹  Piano Morning", "🔕  Vibration Only")
    var selected by remember { mutableIntStateOf(0) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎵", fontSize = 52.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Choose your\nwake experience",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = NightTextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "How do you want to wake up each morning?",
            fontSize = 15.sp,
            color = NightTextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NightSurface),
        ) {
            sounds.forEachIndexed { i, sound ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = i }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sound, fontSize = 15.sp, color = NightTextPrimary)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (i == selected) NightPrimary else NightSurface2)
                            .then(if (i != selected) Modifier.background(NightSurface2) else Modifier),
                    )
                }
                if (i < sounds.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF252B4A)))
                }
            }
        }
    }
}
