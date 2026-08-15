package com.example.sleepwisepoc.auth

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepwisepoc.ui.theme.Eyebrow
import com.example.sleepwisepoc.ui.theme.HypSegment
import com.example.sleepwisepoc.ui.theme.InstrumentSerif
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import com.example.sleepwisepoc.ui.theme.NightRibbon
import com.example.sleepwisepoc.ui.theme.RibbonMode
import com.example.sleepwisepoc.ui.theme.SleepStage
import com.example.sleepwisepoc.ui.theme.TextLink

// ── Reference night, TOTAL = 450 min (the handoff's SEGS). Drawn as the logo. ──
private val A = SleepStage.AWAKE
private val L = SleepStage.LIGHT
private val D = SleepStage.DEEP
private val R = SleepStage.REM
private const val SPLASH_TOTAL = 450

private val splashSegments = listOf(
    HypSegment(0, 14, A), HypSegment(14, 28, L), HypSegment(42, 54, D), HypSegment(96, 22, L),
    HypSegment(118, 22, R), HypSegment(140, 10, L), HypSegment(150, 48, D), HypSegment(198, 16, L),
    HypSegment(214, 2, A), HypSegment(216, 20, L), HypSegment(236, 36, R), HypSegment(272, 24, L),
    HypSegment(296, 34, D), HypSegment(330, 18, L), HypSegment(348, 30, R), HypSegment(378, 14, L),
    HypSegment(392, 4, A), HypSegment(396, 18, L), HypSegment(414, 30, R), HypSegment(444, 6, L),
)

/** True when the OS has animations switched off (accessibility / reduced motion). */
@Composable
private fun reduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun SplashScreen() {
    val c = LocalSleepColors.current
    val reduced = reduceMotion()

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    // Left-to-right reveal of the Night Ribbon over ~1.4s.
    val reveal by animateFloatAsState(
        targetValue = if (started || reduced) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduced) 0 else 1400, easing = LinearEasing),
        label = "ribbon_reveal",
    )
    // The wordmark settles in once the ribbon has mostly drawn.
    val textAlpha by animateFloatAsState(
        targetValue = if (started || reduced) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduced) 0 else 600, delayMillis = if (reduced) 0 else 900),
        label = "wordmark_alpha",
    )

    val ribbonWidth = 260.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The brand mark, drawing itself in.
            Box(
                modifier = Modifier
                    .width(ribbonWidth)
                    .height(64.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(ribbonWidth * reveal)
                        .clipToBounds(),
                ) {
                    NightRibbon(
                        segments = splashSegments,
                        totalMinutes = SPLASH_TOTAL,
                        mode = RibbonMode.MONO,
                        bandHeight = 10.dp,
                        modifier = Modifier
                            .requiredWidth(ribbonWidth)
                            .fillMaxHeight(),
                    )
                }
            }

            Spacer(Modifier.height(34.dp))

            Text(
                text = "SleepWise",
                fontFamily = InstrumentSerif,
                fontSize = 44.sp,
                color = c.text,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.alpha(textAlpha),
            )

            Spacer(Modifier.height(12.dp))

            Eyebrow(
                text = "WAKE UP WELL",
                color = c.faint,
                modifier = Modifier.alpha(textAlpha),
            )
        }

        // Quiet nudge, pinned ~46dp from the bottom.
        TextLink(
            text = "Tap to continue",
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 46.dp)
                .alpha(textAlpha),
        )
    }
}
