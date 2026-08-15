package com.example.sleepwisepoc.tonight

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ══════════════════════════════════════════════════════════════════════════════
//  NIGHTFOLD · Home / "Tonight" — hero screen.
//  Confirm tonight's alarm at a glance and start tracking.
// ══════════════════════════════════════════════════════════════════════════════

private val HeroFmt = DateTimeFormatter.ofPattern("H:mm")   // 24-hour
private val ClockFmt = DateTimeFormatter.ofPattern("HH:mm")
private val DateFmt = DateTimeFormatter.ofPattern("EEE d MMM")

/** The three Home variants from the spec, derived from the ViewModel's state. */
private enum class HomeState { IDLE, TRACKING, DISCONNECTED }

/**
 * Ghost forecast of the user's night — the reference shape from the handoff,
 * ghosted at 50% (RibbonMode.GHOST), with the smart-wake window tinted at the
 * right edge. TOTAL = 450 minutes.
 */
private const val RIBBON_TOTAL = 450
private val forecastSegments = listOf(
    HypSegment(0, 14, SleepStage.AWAKE),   HypSegment(14, 28, SleepStage.LIGHT),
    HypSegment(42, 54, SleepStage.DEEP),   HypSegment(96, 22, SleepStage.LIGHT),
    HypSegment(118, 22, SleepStage.REM),   HypSegment(140, 10, SleepStage.LIGHT),
    HypSegment(150, 48, SleepStage.DEEP),  HypSegment(198, 16, SleepStage.LIGHT),
    HypSegment(214, 2, SleepStage.AWAKE),  HypSegment(216, 20, SleepStage.LIGHT),
    HypSegment(236, 36, SleepStage.REM),   HypSegment(272, 24, SleepStage.LIGHT),
    HypSegment(296, 34, SleepStage.DEEP),  HypSegment(330, 18, SleepStage.LIGHT),
    HypSegment(348, 30, SleepStage.REM),   HypSegment(378, 14, SleepStage.LIGHT),
    HypSegment(392, 4, SleepStage.AWAKE),  HypSegment(396, 18, SleepStage.LIGHT),
    HypSegment(414, 30, SleepStage.REM),   HypSegment(444, 6, SleepStage.LIGHT),
)

private fun requestBatteryOptimizationExemption(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        // Some OEMs hide the system intent; user will need to whitelist manually.
    }
}

@Composable
fun TonightScreen(
    modifier: Modifier = Modifier,
    viewModel: TonightViewModel = viewModel(),
) {
    val c = LocalSleepColors.current
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showNoWatchDialog by remember { mutableStateOf(false) }

    // "Watch missing" means strictly "no watch paired to this phone" (or emulator).
    // Paired-but-not-syncing (NoRecentData) is still fine to start tracking.
    val watchMissing = state.watchStatus == WatchStatus.Disconnected ||
            state.watchStatus == WatchStatus.Emulator

    // Refresh watch status on entry so the readiness card reflects fresh data.
    LaunchedEffect(Unit) { viewModel.refreshWatchStatus() }
    // While tracking, refresh every minute as Samsung Health flushes new samples.
    LaunchedEffect(state.isTracking) {
        if (state.isTracking) {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                viewModel.refreshWatchStatus()
            }
        }
    }

    // Derive the tri-state that drives all three Home variants.
    val home = when {
        state.isTracking -> HomeState.TRACKING
        watchMissing     -> HomeState.DISCONNECTED
        else             -> HomeState.IDLE
    }

    val wakeTime = state.schedule.wakeTime
    val windowStart = state.schedule.windowStart
    val heroTime = wakeTime.format(HeroFmt)
    val windowRange = "${windowStart.format(ClockFmt)} – ${wakeTime.format(ClockFmt)}"

    val lede = when (home) {
        HomeState.IDLE         -> "We'll wake you around"
        HomeState.TRACKING     -> if (state.isAlarmOnly) "Alarm set for" else "We're watching. We'll wake you by"
        HomeState.DISCONNECTED -> "Alarm set for"
    }

    Box(modifier = modifier.fillMaxSize().background(c.bg)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(18.dp))

            // ── Eyebrow / date ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow("TONIGHT")
                Text(
                    LocalDate.now().format(DateFmt),
                    fontFamily = PlexSans, fontSize = 13.sp, color = c.dim,
                )
            }

            Spacer(Modifier.height(26.dp))

            // ── Lede + hero wake time + window row ─────────────────────────────
            Text(lede, fontFamily = PlexSans, fontSize = 15.sp, color = c.dim)
            Spacer(Modifier.height(4.dp))
            Text(
                heroTime,
                fontFamily = InstrumentSerif,
                fontSize = 108.sp,
                lineHeight = 97.sp,
                letterSpacing = (-2).sp,
                color = c.text,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(26.dp).height(1.dp).background(c.lineStrong))
                Spacer(Modifier.width(12.dp))
                MonoLabel(windowRange, size = 13.sp, color = c.text)
                Spacer(Modifier.width(8.dp))
                Text("smart-wake window", fontFamily = PlexSans, fontSize = 13.sp, color = c.faint)
            }

            Spacer(Modifier.height(26.dp))

            // ── Tonight's shape (ghost forecast ribbon) ───────────────────────
            TonightShape()

            // Push the status cluster to the bottom (margin-top: auto).
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(26.dp))

            // ── Status card ───────────────────────────────────────────────────
            StatusCard(
                home = home,
                isAlarmOnly = state.isAlarmOnly,
                watchStatus = state.watchStatus,
                wakeTime = heroTime,
                onFix = { showNoWatchDialog = true },
            )

            Spacer(Modifier.height(18.dp))

            // ── Primary action ────────────────────────────────────────────────
            when (home) {
                HomeState.TRACKING -> SecondaryButton(
                    text = "Stop tracking",
                    onClick = viewModel::stopTracking,
                )
                HomeState.IDLE -> PrimaryButton(
                    text = "Start sleep tracking",
                    onClick = {
                        requestBatteryOptimizationExemption(context)
                        viewModel.startTracking()
                    },
                )
                HomeState.DISCONNECTED -> PrimaryButton(
                    text = "Start anyway",
                    onClick = { showNoWatchDialog = true },
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Contextual footer ─────────────────────────────────────────────
            when (home) {
                HomeState.IDLE -> TextLink(
                    text = "Change tonight's alarm",
                    onClick = { /* Schedule tab lives in the bottom nav */ },
                    modifier = Modifier.fillMaxWidth(),
                )
                HomeState.TRACKING -> Text(
                    text = "Screen dims in 20s. Keep your watch on.",
                    fontFamily = PlexSans, fontSize = 13.sp, color = c.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                )
                HomeState.DISCONNECTED -> Spacer(Modifier.height(0.dp))
            }

            Spacer(Modifier.height(26.dp))
        }

        // ── No-watch bottom sheet overlay ─────────────────────────────────────
        AnimatedVisibility(
            visible = showNoWatchDialog,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(250)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AnimatedVisibility(
                    visible = showNoWatchDialog,
                    enter = slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it },
                    exit = slideOutVertically(tween(280)) { it },
                ) {
                    NoWatchSheet(
                        wakeTime = heroTime,
                        onDismiss = { showNoWatchDialog = false },
                        onOpenBluetooth = {
                            showNoWatchDialog = false
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        onStartAlarmOnly = {
                            showNoWatchDialog = false
                            requestBatteryOptimizationExemption(context)
                            viewModel.startTrackingAlarmOnly()
                        },
                    )
                }
            }
        }
    }
}

// ─── Tonight's shape (ghost ribbon forecast) ──────────────────────────────────

@Composable
private fun TonightShape() {
    val c = LocalSleepColors.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "TONIGHT'S SHAPE",
                    fontFamily = PlexMono, fontSize = 10.sp, letterSpacing = 1.6.sp, color = c.faint,
                )
                Text("based on 14 nights", fontFamily = PlexSans, fontSize = 12.sp, color = c.faint)
            }
            Spacer(Modifier.height(14.dp))
            NightRibbon(
                segments = forecastSegments,
                totalMinutes = RIBBON_TOTAL,
                modifier = Modifier.fillMaxWidth().height(92.dp),
                bandHeight = 14.dp,
                mode = RibbonMode.GHOST,
                windowStartMin = 420,
                windowEndMin = RIBBON_TOTAL,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("23:15", "02:00", "04:30", "06:42").forEach {
                    Text(it, fontFamily = PlexMono, fontSize = 9.sp, letterSpacing = 1.2.sp, color = c.faint)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

// ─── Status card (three states) ───────────────────────────────────────────────

@Composable
private fun StatusCard(
    home: HomeState,
    isAlarmOnly: Boolean,
    watchStatus: WatchStatus,
    wakeTime: String,
    onFix: () -> Unit,
) {
    val c = LocalSleepColors.current

    val dotColor: Color
    val halo: Boolean
    val title: String
    val body: String
    when (home) {
        HomeState.TRACKING -> {
            dotColor = c.accent
            halo = true
            if (isAlarmOnly) {
                title = "Alarm only"
                body = "Regular alarm — smart wake disabled."
            } else {
                title = "Monitoring your sleep"
                body = "Tracking now · listening for light sleep."
            }
        }
        HomeState.IDLE -> {
            dotColor = c.good
            halo = false
            title = "Watch connected"
            body = when (watchStatus) {
                WatchStatus.NoRecentData -> "Pulse Watch · paired, syncing overnight."
                WatchStatus.Checking     -> "Pulse Watch · checking connection…"
                else                     -> "Pulse Watch · synced and ready."
            }
        }
        HomeState.DISCONNECTED -> {
            dotColor = c.faint
            halo = false
            title = "Watch not connected"
            body = "We can still ring at $wakeTime — we just can't pick the gentle moment."
        }
    }

    NfCard(radius = 18.dp, padding = 18.dp) {
        Row(verticalAlignment = Alignment.Top) {
            StatusDot(color = dotColor, halo = halo, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(body, fontFamily = PlexSans, fontSize = 13.sp, color = c.dim, lineHeight = 18.sp)
            }
            if (home == HomeState.DISCONNECTED) {
                Spacer(Modifier.width(12.dp))
                SmallPill(text = "FIX", onClick = onFix, filled = false)
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
    val c = LocalSleepColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(c.surface)
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 18.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.lineStrong),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = c.faint)
            Spacer(Modifier.width(14.dp))
            Text(
                "Watch not connected",
                fontFamily = InstrumentSerif, fontSize = 26.sp, color = c.text,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "To watch your sleep stages and wake you at the gentle moment, SleepWise needs your watch. Without it, we'll still ring at $wakeTime — the end of your window.",
            fontFamily = PlexSans, fontSize = 15.sp, color = c.dim, lineHeight = 23.sp,
        )

        Spacer(Modifier.height(26.dp))

        PrimaryButton(text = "Open Bluetooth settings", onClick = onOpenBluetooth)

        Spacer(Modifier.height(12.dp))

        SecondaryButton(text = "Start anyway", onClick = onStartAlarmOnly)

        TextLink(text = "Not now", onClick = onDismiss, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(18.dp))
    }
}
