package com.example.sleepwisepoc.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════════════════
//  NIGHTFOLD component library — the shared primitives every screen builds from.
//  Read the design handoff for exact metrics; these match it.
// ══════════════════════════════════════════════════════════════════════════════

// ─── Sleep stages + hypnogram data ────────────────────────────────────────────
enum class SleepStage { AWAKE, REM, LIGHT, DEEP }   // row order 0..3 (top→bottom)

data class HypSegment(val startMin: Int, val durationMin: Int, val stage: SleepStage)

enum class RibbonMode { FULL, MONO, GHOST }

fun AppColors.stageColor(s: SleepStage): Color = when (s) {
    SleepStage.AWAKE -> stageAwake
    SleepStage.REM -> stageRem
    SleepStage.LIGHT -> stageLight
    SleepStage.DEEP -> stageDeep
}

/**
 * The Night Ribbon — the brand mark. Four-row band chart at any scale.
 * @param mode FULL = stage colors; MONO = text tone (awake in accent); GHOST = dim @50%.
 * @param windowStartMin/windowEndMin optional smart-wake window tint (accentSoft).
 * @param wakeMarkerMin optional 2px accent vertical rule (the chosen wake moment).
 */
@Composable
fun NightRibbon(
    segments: List<HypSegment>,
    totalMinutes: Int,
    modifier: Modifier = Modifier,
    bandHeight: androidx.compose.ui.unit.Dp = 24.dp,
    mode: RibbonMode = RibbonMode.FULL,
    windowStartMin: Int? = null,
    windowEndMin: Int? = null,
    wakeMarkerMin: Int? = null,
) {
    val c = LocalSleepColors.current
    Canvas(modifier = modifier.fillMaxWidth()) {
        val w = size.width
        val h = size.height
        val rowH = h / 4f
        val bandPx = bandHeight.toPx().coerceAtMost(rowH)
        fun xOf(min: Int) = (min.toFloat() / totalMinutes) * w
        // window tint (z0)
        if (windowStartMin != null && windowEndMin != null) {
            drawRect(color = c.accentSoft, topLeft = Offset(xOf(windowStartMin), 0f),
                size = Size(xOf(windowEndMin) - xOf(windowStartMin), h))
        }
        // gridlines at each row center
        for (r in 0 until 4) {
            val y = r * rowH + rowH / 2f
            drawRect(color = c.line, topLeft = Offset(0f, y - 0.5f), size = Size(w, 1f))
        }
        // bands (z2)
        segments.forEach { seg ->
            val row = seg.stage.ordinal
            val y = row * rowH + (rowH - bandPx) / 2f
            val x = xOf(seg.startMin)
            val bw = (seg.durationMin.toFloat() / totalMinutes) * w
            val color = when (mode) {
                RibbonMode.FULL -> c.stageColor(seg.stage)
                RibbonMode.MONO -> if (seg.stage == SleepStage.AWAKE) c.accent else c.text.copy(alpha = 0.85f)
                RibbonMode.GHOST -> c.dim.copy(alpha = 0.5f)
            }
            drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(bw.coerceAtLeast(1.5f), bandPx),
                cornerRadius = CornerRadius(4f, 4f))
        }
        // wake marker (z3)
        if (wakeMarkerMin != null) {
            val x = xOf(wakeMarkerMin)
            drawRect(color = c.accent, topLeft = Offset(x - 1f, -8f), size = Size(2f, h + 16f))
        }
    }
}

// ─── Score ring ───────────────────────────────────────────────────────────────
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 124.dp) {
    val c = LocalSleepColors.current
    val sweep by animateFloatAsState(targetValue = score.coerceIn(0, 100) * 3.6f,
        animationSpec = tween(900), label = "ring")
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 9.dp.toPx()
            drawArc(color = c.surface2, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            drawArc(color = c.accent, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontFamily = InstrumentSerif, fontSize = 46.sp, color = c.text)
            Text("SCORE", fontFamily = PlexMono, fontSize = 9.sp, letterSpacing = 1.6.sp, color = c.faint)
        }
    }
}

// ─── Text helpers ─────────────────────────────────────────────────────────────
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = LocalSleepColors.current
    Text(text.uppercase(), modifier = modifier, fontFamily = PlexMono, fontSize = 11.sp,
        letterSpacing = 2.4.sp, color = color ?: c.faint)
}

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color? = null, size: androidx.compose.ui.unit.TextUnit = 13.sp) {
    val c = LocalSleepColors.current
    Text(text, modifier = modifier, fontFamily = PlexMono, fontSize = size, color = color ?: c.dim)
}

// ─── Buttons ──────────────────────────────────────────────────────────────────
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalSleepColors.current
    Box(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = c.onAccent) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    Box(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, c.lineStrong, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = c.text) }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    Text(text, modifier = modifier.clickable(onClick = onClick).padding(6.dp),
        fontFamily = PlexSans, fontSize = 14.sp, color = c.dim, textAlign = TextAlign.Center)
}

/** Small mono pill — filled (INSTALL) or outlined (FIX). */
@Composable
fun SmallPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val c = LocalSleepColors.current
    val base = if (filled) Modifier.background(c.accent) else Modifier.border(1.dp, c.accent, RoundedCornerShape(999.dp))
    Box(
        modifier = modifier.clip(RoundedCornerShape(999.dp)).then(base).clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), fontFamily = PlexMono, fontSize = 11.sp, letterSpacing = 1.3.sp,
            color = if (filled) c.onAccent else c.accent)
    }
}

// ─── Status dot ───────────────────────────────────────────────────────────────
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, halo: Boolean = false) {
    val c = LocalSleepColors.current
    Box(modifier = modifier.size(if (halo) 22.dp else 10.dp), contentAlignment = Alignment.Center) {
        if (halo) Box(Modifier.size(22.dp).clip(CircleShape).background(c.accentSoft))
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
}

// ─── Chip ─────────────────────────────────────────────────────────────────────
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    val m = if (selected) Modifier.background(c.accent) else Modifier.border(1.dp, c.lineStrong, RoundedCornerShape(999.dp))
    Box(
        modifier = modifier.clip(RoundedCornerShape(999.dp)).then(m).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = PlexSans, fontSize = 13.sp, color = if (selected) c.onAccent else c.dim) }
}

// ─── Toggle ───────────────────────────────────────────────────────────────────
@Composable
fun NfToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    val align by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "tg")
    Box(
        modifier = modifier.width(56.dp).height(32.dp).clip(RoundedCornerShape(999.dp))
            .background(if (checked) c.accent else c.raise).clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = (align * 24).dp)
            .size(26.dp).clip(CircleShape).background(if (checked) c.onAccent else c.dim))
    }
}

// ─── Card ─────────────────────────────────────────────────────────────────────
@Composable
fun NfCard(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = 18.dp,
    fill: Color? = null,
    borderColor: Color? = null,
    padding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalSleepColors.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(radius)).background(fill ?: c.surface)
            .border(1.dp, borderColor ?: c.line, RoundedCornerShape(radius)).padding(padding),
        content = content,
    )
}

// ─── Bottom nav ───────────────────────────────────────────────────────────────
data class NavItem(val key: String, val label: String)

@Composable
fun NfBottomNav(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalSleepColors.current
    Column(modifier = modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items.forEach { item ->
                val active = item.key == selected
                Column(
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 58.dp)
                        .clip(RoundedCornerShape(16.dp)).clickable { onSelect(item.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.width(22.dp).height(2.dp).background(if (active) c.accent else Color.Transparent))
                    Spacer(Modifier.height(8.dp))
                    Text(item.label, fontFamily = PlexSans, fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) c.text else c.faint)
                }
            }
        }
    }
}
