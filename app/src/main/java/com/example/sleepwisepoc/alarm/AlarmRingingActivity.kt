package com.example.sleepwisepoc.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.sleepwisepoc.ui.theme.Eyebrow
import com.example.sleepwisepoc.ui.theme.InstrumentSerif
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import com.example.sleepwisepoc.ui.theme.PlexSans
import com.example.sleepwisepoc.ui.theme.SleepWisePOCTheme
import androidx.compose.ui.text.font.FontWeight
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen alarm screen shown over the lock screen when the smart alarm
 * fires. Launched via the full-screen intent on [AlarmRingService]'s
 * notification.
 *
 * The actual sound + vibration live in AlarmRingService — this activity is just
 * the visual + dismiss/snooze affordance, so dismissing here tells the service
 * to stop. This is the Nightfold "Alarm ringing" redesign (handoff §6), built in
 * Compose over the shared theme tokens; all alarm behavior is preserved.
 */
class AlarmRingingActivity : ComponentActivity() {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn the screen on. (Unchanged from the
        // original View-based screen — these flags must not regress.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        // Keep the screen awake while the alarm is ringing on all API levels.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            SleepWisePOCTheme(darkTheme = true) {
                AlarmRingingScreen(
                    time = LocalTime.now().format(timeFormatter),
                    onDismiss = ::dismiss,
                    onSnooze = ::snooze,
                )
            }
        }
    }

    /** Dismiss → stop the ring (design: on to the Sleep report). */
    private fun dismiss() {
        AlarmRingService.stop(this)
        finish()
    }

    /**
     * Snooze → re-arm the alarm 9 minutes out, stop the current ring (design:
     * back to Home). Uses the existing scheduler so the real alarm fires again.
     */
    private fun snooze() {
        AlarmScheduler.scheduleInMinutes(this, SNOOZE_MINUTES.toLong())
        AlarmRingService.stop(this)
        finish()
    }

    override fun onDestroy() {
        // Belt-and-suspenders: ensure the ring stops if the activity goes away.
        AlarmRingService.stop(this)
        super.onDestroy()
    }

    companion object {
        private const val SNOOZE_MINUTES = 9
    }
}

@Composable
private fun AlarmRingingScreen(
    time: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val c = LocalSleepColors.current

    // Breathing halo — 4.5s ease-in-out loop, opacity .35↔.75, scale 1↔1.06.
    val breathe = rememberInfiniteTransition(label = "breathe")
    val haloAlpha by breathe.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(4500), RepeatMode.Reverse),
        label = "haloAlpha",
    )
    val haloScale by breathe.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(4500), RepeatMode.Reverse),
        label = "haloScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(start = 26.dp, end = 26.dp, top = 60.dp, bottom = 46.dp),
    ) {
        // Centered content: breathing halo behind the eyebrow / time / reason.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 200.dp), // leave room for the bottom actions
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                        alpha = haloAlpha
                    }
                    .clip(CircleShape)
                    .background(c.accentSoft),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Eyebrow("GOOD MORNING", color = c.accent)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = time,
                    fontFamily = InstrumentSerif,
                    fontSize = 132.sp,
                    lineHeight = 132.sp,
                    color = c.text,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "You were in light sleep, so we woke you three minutes early.",
                    fontFamily = PlexSans,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = c.dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }
        }

        // Bottom actions: dismiss target + snooze.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DismissTarget(onClick = onDismiss)
            Spacer(Modifier.height(14.dp))
            SnoozeButton(onClick = onSnooze)
        }
    }
}

/** Full-width dismiss target — min-height 104dp, radius 34, solid accent, with a
 *  4s pulsing ring (box-shadow 0→18px, alpha .30→0). */
@Composable
private fun DismissTarget(onClick: () -> Unit) {
    val c = LocalSleepColors.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 104.dp)
            .drawBehind {
                val spread = ring * 18.dp.toPx()
                val radius = 34.dp.toPx() + spread
                drawRoundRect(
                    color = c.accent.copy(alpha = 0.30f * (1f - ring)),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + spread * 2, size.height + spread * 2),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            .clip(RoundedCornerShape(34.dp))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "I'm awake",
            fontFamily = InstrumentSerif,
            fontSize = 34.sp,
            color = c.onAccent,
        )
    }
}

/** Outlined snooze button — 64dp, line-strong border. */
@Composable
private fun SnoozeButton(onClick: () -> Unit) {
    val c = LocalSleepColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, c.lineStrong, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Snooze 9 minutes",
            fontFamily = PlexSans,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = c.text,
        )
    }
}
