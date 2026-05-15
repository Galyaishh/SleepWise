package com.example.sleepwisepoc.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.example.sleepwisepoc.ui.theme.NightBg
import com.example.sleepwisepoc.ui.theme.NightPrimary
import com.example.sleepwisepoc.ui.theme.NightPrimaryEnd
import com.example.sleepwisepoc.ui.theme.NightSurface
import com.example.sleepwisepoc.ui.theme.NightTextPrimary
import com.example.sleepwisepoc.ui.theme.NightTextSecondary

private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
)

private val pages = listOf(
    OnboardingPage(
        emoji = "⏰",
        title = "Wake up at the\nperfect moment",
        description = "SleepWise analyzes your sleep and wakes you during light sleep so mornings feel easier and more natural.",
    ),
    OnboardingPage(
        emoji = "📊",
        title = "Understand\nyour sleep",
        description = "Track sleep stages, consistency, and nightly recovery using your smartwatch.",
    ),
    OnboardingPage(
        emoji = "🔁",
        title = "Built around\nyour routine",
        description = "Set your schedule once and let SleepWise automatically prepare your smart alarm every night.",
    ),
    OnboardingPage(
        emoji = "🔒",
        title = "Private\n& secure",
        description = "Your sleep data stays protected and is designed with privacy first.",
    ),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF0D0F22),
                        0.5f to NightBg,
                        1f to NightBg,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(60.dp))

            // Page content with slide animation
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(200)))
                },
                label = "page",
                modifier = Modifier.weight(1f),
            ) { idx ->
                val p = pages[idx]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Emoji in glowing circle
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        NightPrimary.copy(alpha = 0.25f),
                                        Color.Transparent,
                                    )
                                )
                            )
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(NightSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(p.emoji, fontSize = 38.sp)
                        }
                    }

                    Spacer(Modifier.height(40.dp))

                    Text(
                        text = p.title,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = NightTextPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = p.description,
                        fontSize = 16.sp,
                        color = NightTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                    )
                }
            }

            // Dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == page) NightPrimary else NightPrimary.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Primary CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(NightPrimary, NightPrimaryEnd)))
                    .clickable {
                        if (page < pages.lastIndex) page++ else onComplete()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (page < pages.lastIndex) "Next" else "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NightTextPrimary,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Skip
            if (page < pages.lastIndex) {
                Text(
                    text = "Skip",
                    fontSize = 14.sp,
                    color = NightTextSecondary,
                    modifier = Modifier
                        .clickable { onComplete() }
                        .padding(8.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
