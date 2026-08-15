package com.example.sleepwisepoc.auth

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.sleepwisepoc.schedule.DaySchedule
import com.example.sleepwisepoc.schedule.SleepScheduleStore
import com.example.sleepwisepoc.ui.theme.Eyebrow
import com.example.sleepwisepoc.ui.theme.InstrumentSerif
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import com.example.sleepwisepoc.ui.theme.NfCard
import com.example.sleepwisepoc.ui.theme.PlexMono
import com.example.sleepwisepoc.ui.theme.PlexSans
import com.example.sleepwisepoc.ui.theme.PrimaryButton
import com.example.sleepwisepoc.ui.theme.TextLink
import kotlinx.coroutines.launch
import java.time.LocalTime

// ══════════════════════════════════════════════════════════════════════════════
//  Pair your watch — Nightfold "SETUP — STEP 1 OF 3".
//  Three numbered pairing steps advance through current / completed / upcoming
//  states as `step` moves 0 → 1 → 2; finishing saves the schedule and lands Home.
// ══════════════════════════════════════════════════════════════════════════════

private data class PairStep(val title: String, val body: String)

private val PairSteps = listOf(
    PairStep("Open SleepWise on your watch", "It is in the app list, next to Workout."),
    PairStep("Tap “Connect phone”", "A six-digit code appears."),
    PairStep("Wear it loosely tonight", "A finger-width above the wrist bone reads best."),
)

@Composable
fun SetupWizardScreen(onComplete: () -> Unit) {
    val c = LocalSleepColors.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val store   = remember { SleepScheduleStore(context) }

    var step by remember { mutableIntStateOf(0) }

    // Schedule defaults are written on finish — preserved wiring, no longer edited here.
    val weekdayTime    = LocalTime.of(7, 0)
    val weekendTime    = LocalTime.of(9, 0)
    val selectedSound  = "Default Alarm"
    val selectedSoundUri: String? = null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(52.dp))

            Eyebrow("SETUP — STEP 1 OF 3")

            Spacer(Modifier.height(14.dp))

            Text(
                "Pair your watch",
                fontFamily = InstrumentSerif,
                fontSize   = 38.sp,
                lineHeight = 42.sp,
                color      = c.text,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "It reads your movement and heart rate through the night. Nothing leaves your phone.",
                fontFamily = PlexSans,
                fontSize   = 15.sp,
                lineHeight = 23.sp,
                color      = c.dim,
            )

            Spacer(Modifier.height(28.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PairSteps.forEachIndexed { index, s ->
                    val state = when {
                        index < step  -> PairState.Completed
                        index == step -> PairState.Current
                        else          -> PairState.Upcoming
                    }
                    StepCard(index = index, step = s, state = state)
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(28.dp))

            PrimaryButton(
                text = if (step < 2) "Done — next step" else "Finish setup",
                onClick = {
                    if (step < 2) {
                        step++
                    } else {
                        scope.launch {
                            store.saveWeekdays(
                                DaySchedule(
                                    wakeTime      = weekdayTime,
                                    windowMinutes = 30,
                                    alarmSound    = selectedSound,
                                    alarmSoundUri = selectedSoundUri,
                                )
                            )
                            store.saveWeekends(
                                DaySchedule(
                                    wakeTime      = weekendTime,
                                    windowMinutes = 45,
                                    alarmSound    = selectedSound,
                                    alarmSoundUri = selectedSoundUri,
                                )
                            )
                            onComplete()
                        }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            TextLink(
                text = "I'll do this later",
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Step card ────────────────────────────────────────────────────────────────

private enum class PairState { Current, Completed, Upcoming }

@Composable
private fun StepCard(index: Int, step: PairStep, state: PairState) {
    val c = LocalSleepColors.current
    NfCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (state == PairState.Upcoming) Modifier.alpha(0.55f) else Modifier),
        radius = 20.dp,
        padding = 18.dp,
        fill = if (state == PairState.Current) c.surface2 else c.surface,
        borderColor = if (state == PairState.Current) c.accent else c.line,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepCircle(index = index, state = state)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    step.title,
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 15.sp,
                    color      = c.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    step.body,
                    fontFamily = PlexSans,
                    fontSize   = 13.sp,
                    lineHeight = 19.sp,
                    color      = c.dim,
                )
            }
            Spacer(Modifier.width(12.dp))
            when (state) {
                PairState.Current -> Text(
                    "NOW",
                    fontFamily    = PlexMono,
                    fontSize      = 10.sp,
                    letterSpacing = 1.3.sp,
                    color         = c.accent,
                )
                PairState.Completed -> Text(
                    "✓",
                    fontFamily = PlexSans,
                    fontSize   = 16.sp,
                    color      = c.good,
                )
                PairState.Upcoming -> {}
            }
        }
    }
}

@Composable
private fun StepCircle(index: Int, state: PairState) {
    val c = LocalSleepColors.current
    val base = Modifier.size(26.dp).clip(CircleShape)
    val shaped = when (state) {
        PairState.Current   -> base.background(c.accent)
        PairState.Completed -> base.background(c.good)
        PairState.Upcoming  -> base.border(1.dp, c.lineStrong, CircleShape)
    }
    Box(modifier = shaped, contentAlignment = Alignment.Center) {
        when (state) {
            PairState.Completed -> Text(
                "✓",
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = c.onAccent,
            )
            else -> Text(
                "${index + 1}",
                fontFamily = InstrumentSerif,
                fontSize   = 15.sp,
                color      = if (state == PairState.Current) c.onAccent else c.faint,
            )
        }
    }
}
