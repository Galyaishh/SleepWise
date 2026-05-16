package com.example.sleepwisepoc.tonight

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.ui.theme.NightBg
import com.example.sleepwisepoc.ui.theme.NightBorder
import com.example.sleepwisepoc.ui.theme.NightPrimary
import com.example.sleepwisepoc.ui.theme.NightPrimaryEnd
import com.example.sleepwisepoc.ui.theme.NightSuccess
import com.example.sleepwisepoc.ui.theme.NightSurface
import com.example.sleepwisepoc.ui.theme.NightSurface2
import com.example.sleepwisepoc.ui.theme.NightTextAccent
import com.example.sleepwisepoc.ui.theme.NightTextPrimary
import com.example.sleepwisepoc.ui.theme.NightTextSecondary
import java.time.format.DateTimeFormatter

private val TimeFmt = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
fun TonightScreen(
    modifier: Modifier = Modifier,
    viewModel: TonightViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showNoWatchDialog by remember { mutableStateOf(false) }

    val watchMissing = state.watchStatus == WatchStatus.Disconnected ||
            state.watchStatus == WatchStatus.NoRecentData

    val wakeTimeFormatted = state.schedule.wakeTime.format(TimeFmt)

    Box(modifier = modifier.fillMaxSize()) {

        // ── Main screen ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF0D0F22),
                            0.4f to NightBg,
                            1.0f to NightBg,
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(16.dp))

                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SleepWise",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NightTextPrimary,
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NightSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = NightTextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "${state.greeting} 🌙",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = NightTextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SleepWise is ready for tonight.",
                    fontSize = 14.sp,
                    color = NightTextSecondary,
                )

                Spacer(Modifier.height(24.dp))

                WakeUpCard(state = state)

                Spacer(Modifier.height(16.dp))

                ReadinessCard(state.watchStatus)

                Spacer(Modifier.height(24.dp))

                StartButton(
                    isTracking   = state.isTracking,
                    isAlarmOnly  = state.isAlarmOnly,
                    watchMissing = watchMissing,
                    onStart = {
                        if (watchMissing) showNoWatchDialog = true
                        else viewModel.startTracking()
                    },
                    onStop = viewModel::stopTracking,
                )

                Spacer(Modifier.height(32.dp))
            }
        }

        // ── No-watch bottom sheet overlay ─────────────────────────────────────
        AnimatedVisibility(
            visible = showNoWatchDialog,
            enter = fadeIn(tween(300)),
            exit  = fadeOut(tween(250)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF07080F).copy(alpha = 0.82f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AnimatedVisibility(
                    visible = showNoWatchDialog,
                    enter = slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it },
                    exit  = slideOutVertically(tween(280)) { it },
                ) {
                    NoWatchSheet(
                        wakeTime = wakeTimeFormatted,
                        onDismiss = { showNoWatchDialog = false },
                        onOpenBluetooth = {
                            showNoWatchDialog = false
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        onStartAlarmOnly = {
                            showNoWatchDialog = false
                            viewModel.startTrackingAlarmOnly()
                        },
                    )
                }
            }
        }
    }
}

// ─── No-watch bottom sheet ────────────────────────────────────────────────────

@Composable
private fun NoWatchSheet(
    wakeTime: String,
    onDismiss: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onStartAlarmOnly: () -> Unit,
) {
    val pulse = rememberInfiniteTransition("watchGlow")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.12f,
        targetValue  = 0.38f,
        animationSpec = infiniteRepeatable(
            animation   = tween(1800, easing = FastOutSlowInEasing),
            repeatMode  = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f   to Color(0xFF1C2040),
                        0.7f to Color(0xFF111428),
                        1f   to NightBg,
                    )
                )
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NightBorder),
        )

        Spacer(Modifier.height(24.dp))

        // Watch icon — pulsing glow rings + X badge
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer pulsing glow
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(NightPrimary.copy(alpha = glowAlpha)),
            )
            // Mid ring
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(NightPrimary.copy(alpha = 0.18f)),
            )
            // Icon circle
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(NightSurface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Watch,
                    contentDescription = null,
                    tint   = NightTextAccent,
                    modifier = Modifier.size(34.dp),
                )
            }
            // X badge
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(NightPrimary)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text       = "No Galaxy Watch connected 🌙",
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = NightTextPrimary,
            textAlign  = TextAlign.Center,
            lineHeight = 30.sp,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text       = "To track your sleep stages and wake you during light sleep, SleepWise needs your Galaxy Watch.",
            fontSize   = 14.sp,
            color      = NightTextSecondary,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(20.dp))

        // Info card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NightPrimary.copy(alpha = 0.10f))
                .border(1.dp, NightPrimary.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("⏰", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Without your watch, the alarm will ring at",
                    fontSize = 13.sp,
                    color = NightTextSecondary,
                    lineHeight = 18.sp,
                )
                Row {
                    Text(wakeTime, fontSize = 13.sp, color = NightPrimary, fontWeight = FontWeight.SemiBold)
                    Text(" — the end of your wake window.", fontSize = 13.sp, color = NightTextSecondary)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Primary — Bluetooth Settings
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(NightPrimary, NightPrimaryEnd)))
                .clickable { onOpenBluetooth() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Bluetooth, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    "Open Bluetooth Settings",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Secondary — Start Without Watch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, NightBorder, RoundedCornerShape(50))
                .background(NightSurface)
                .clickable { onStartAlarmOnly() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Start Without Watch",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium,
                color      = NightTextPrimary,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Tertiary — Not now
        Text(
            "Not now",
            fontSize = 14.sp,
            color    = NightTextSecondary,
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(vertical = 14.dp),
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Wake-up card ─────────────────────────────────────────────────────────────

@Composable
private fun WakeUpCard(state: TonightUiState) {
    val wakeTime = state.schedule.wakeTime
    val winStart = state.schedule.windowStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF1A1F3A), NightSurface)))
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NightPrimary.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text          = "Tomorrow's wake up",
                fontSize      = 12.sp,
                fontWeight    = FontWeight.Medium,
                color         = NightTextSecondary,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = wakeTime.format(TimeFmt),
                fontSize   = 54.sp,
                fontWeight = FontWeight.Bold,
                color      = NightTextPrimary,
                lineHeight = 58.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Smart window  ", fontSize = 13.sp, color = NightTextSecondary)
                Text(
                    text       = "${winStart.format(shortTime())} – ${wakeTime.format(shortTime())}",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = NightTextAccent,
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = if (state.isAlarmOnly) "Regular alarm — smart wake disabled" else "We will wake you during light sleep",
                    fontSize   = 13.sp,
                    color      = if (state.isAlarmOnly) NightTextSecondary else NightTextAccent,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ─── Readiness card ───────────────────────────────────────────────────────────

@Composable
private fun ReadinessCard(watchStatus: WatchStatus) {
    val (watchValue, watchOk) = when (watchStatus) {
        WatchStatus.Checking     -> "Checking…"      to false
        WatchStatus.Connected    -> "Connected"       to true
        WatchStatus.NoRecentData -> "No recent data"  to false
        WatchStatus.Disconnected -> "Not connected"   to false
    }
    val trackingReady = watchStatus == WatchStatus.Connected

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NightSurface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("READINESS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = NightTextSecondary, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))
            ReadinessRow(icon = Icons.Outlined.Watch, title = "Galaxy Watch", value = watchValue, ok = watchOk)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = NightBorder, thickness = 0.5.dp)
            ReadinessRow(
                icon  = Icons.Outlined.MonitorHeart,
                title = "Sleep tracking",
                value = if (trackingReady) "Ready" else "Waiting for watch",
                ok    = trackingReady,
            )
        }
    }
}

@Composable
private fun ReadinessRow(icon: ImageVector, title: String, value: String, ok: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(NightSurface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = NightTextAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = NightTextPrimary, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 12.sp, color = if (ok) NightSuccess else NightTextSecondary)
        }
        if (ok) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(NightSuccess.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, null, tint = NightSuccess, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─── CTA button ───────────────────────────────────────────────────────────────

@Composable
private fun StartButton(
    isTracking: Boolean,
    isAlarmOnly: Boolean,
    watchMissing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val bgBrush = if (isTracking) {
        Brush.horizontalGradient(listOf(Color(0xFF2A3060), Color(0xFF1E2A44)))
    } else {
        Brush.horizontalGradient(listOf(NightPrimary, NightPrimaryEnd))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(50))
                .background(bgBrush)
                .clickable { if (isTracking) onStop() else onStart() },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Nightlight, null, tint = NightTextPrimary, modifier = Modifier.size(20.dp))
                Text(
                    text = when {
                        isTracking && isAlarmOnly -> "Alarm Only — Tap to Stop"
                        isTracking               -> "Monitoring Active — Tap to Stop"
                        else                     -> "Start Sleep Tracking"
                    },
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = NightTextPrimary,
                )
            }
        }

        if (!isTracking && watchMissing) {
            Spacer(Modifier.height(8.dp))
            Text("⚠ No watch detected — alarm only", fontSize = 12.sp, color = NightTextSecondary)
        }

        if (isTracking && isAlarmOnly) {
            Spacer(Modifier.height(8.dp))
            Text("Regular alarm set — no sleep monitoring", fontSize = 12.sp, color = NightTextSecondary)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun shortTime(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
