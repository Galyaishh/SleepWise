package com.example.sleepwisepoc.auth

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepwisepoc.ui.theme.Eyebrow
import com.example.sleepwisepoc.ui.theme.HypSegment
import com.example.sleepwisepoc.ui.theme.InstrumentSerif
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import com.example.sleepwisepoc.ui.theme.NfCard
import com.example.sleepwisepoc.ui.theme.NightRibbon
import com.example.sleepwisepoc.ui.theme.PlexSans
import com.example.sleepwisepoc.ui.theme.PrimaryButton
import com.example.sleepwisepoc.ui.theme.RibbonMode
import com.example.sleepwisepoc.ui.theme.SleepStage
import com.example.sleepwisepoc.ui.theme.TextLink

// ── Onboarding copy (design handoff §5) ───────────────────────────────────────
private data class OnboardingSlide(val title: String, val body: String)

private val slides = listOf(
    OnboardingSlide(
        title = "Sleep moves in waves.",
        body = "You drift between deep, light and dreaming sleep all night. Waking mid-wave is what leaves you groggy.",
    ),
    OnboardingSlide(
        title = "Pick a window, not a minute.",
        body = "Give us thirty flexible minutes and we'll watch for your lightest moment inside them.",
    ),
    OnboardingSlide(
        title = "We'll wake you gently.",
        body = "Your watch does the listening. You just go to bed.",
    ),
)

// Reference hypnogram from the handoff (TOTAL = 450 min, 23:12 → 06:42).
private const val RIBBON_TOTAL = 450
private val ribbonSegments = listOf(
    HypSegment(0, 14, SleepStage.AWAKE), HypSegment(14, 28, SleepStage.LIGHT),
    HypSegment(42, 54, SleepStage.DEEP), HypSegment(96, 22, SleepStage.LIGHT),
    HypSegment(118, 22, SleepStage.REM), HypSegment(140, 10, SleepStage.LIGHT),
    HypSegment(150, 48, SleepStage.DEEP), HypSegment(198, 16, SleepStage.LIGHT),
    HypSegment(214, 2, SleepStage.AWAKE), HypSegment(216, 20, SleepStage.LIGHT),
    HypSegment(236, 36, SleepStage.REM), HypSegment(272, 24, SleepStage.LIGHT),
    HypSegment(296, 34, SleepStage.DEEP), HypSegment(330, 18, SleepStage.LIGHT),
    HypSegment(348, 30, SleepStage.REM), HypSegment(378, 14, SleepStage.LIGHT),
    HypSegment(392, 4, SleepStage.AWAKE), HypSegment(396, 18, SleepStage.LIGHT),
    HypSegment(414, 30, SleepStage.REM), HypSegment(444, 6, SleepStage.LIGHT),
)

/** True when the OS "remove animations" accessibility setting is on. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val c = LocalSleepColors.current
    val reducedMotion = rememberReducedMotion()
    var page by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // Header: dots top-left, Skip top-right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    slides.indices.forEach { i ->
                        val active = i == page
                        Box(
                            modifier = Modifier
                                .size(width = if (active) 22.dp else 6.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (active) c.accent else c.lineStrong),
                        )
                    }
                }
                TextLink(text = "Skip", onClick = onComplete)
            }

            // Slide content — rises 10dp + fades over ~0.5s (unless reduced motion).
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(500)) +
                            slideInVertically(tween(500)) { full -> full / 40 }) togetherWith
                            fadeOut(tween(200))
                    }
                },
                label = "onboardingSlide",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { idx ->
                val slide = slides[idx]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Slide art — 168dp surface card with the full-color Night Ribbon.
                    NfCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                        radius = 22.dp,
                        padding = 18.dp,
                    ) {
                        Eyebrow(text = "FIG. 1 — YOUR NIGHT")
                        Spacer(Modifier.height(14.dp))
                        NightRibbon(
                            segments = ribbonSegments,
                            totalMinutes = RIBBON_TOTAL,
                            mode = RibbonMode.FULL,
                            bandHeight = 18.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }

                    Spacer(Modifier.height(34.dp))

                    Text(
                        text = slide.title,
                        fontFamily = InstrumentSerif,
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        color = c.text,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = slide.body,
                        fontFamily = PlexSans,
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                        color = c.dim,
                        textAlign = TextAlign.Start,
                    )
                }
            }

            // Full-width accent CTA: "Next" ×2 then "Get started".
            PrimaryButton(
                text = if (page < slides.lastIndex) "Next" else "Get started",
                onClick = { if (page < slides.lastIndex) page++ else onComplete() },
            )

            Spacer(Modifier.height(34.dp))
        }
    }
}
