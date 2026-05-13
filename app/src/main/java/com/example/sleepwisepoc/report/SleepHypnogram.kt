package com.example.sleepwisepoc.report

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.ui.theme.SleepStageDeep
import com.example.sleepwisepoc.ui.theme.SleepStageDeepDark
import com.example.sleepwisepoc.ui.theme.SleepStageLight
import com.example.sleepwisepoc.ui.theme.SleepStageLightDark
import com.example.sleepwisepoc.ui.theme.SleepStageREM
import com.example.sleepwisepoc.ui.theme.SleepStageREMDark
import com.example.sleepwisepoc.ui.theme.SleepStageWake
import com.example.sleepwisepoc.ui.theme.SleepStageWakeDark
import com.example.sleepwisepoc.ui.theme.SleepTextSecondary
import com.example.sleepwisepoc.ui.theme.SleepDarkTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Stage enum ───────────────────────────────────────────────────────────────

internal enum class HypnoStage(
    val label: String,
    /** 0 = top of chart (Wake), 1 = bottom (Deep) */
    val yFrac: Float,
    val colorLight: Color,
    val colorDark: Color,
) {
    WAKE( "Wake",  0.125f, SleepStageWake,  SleepStageWakeDark),
    REM(  "REM",   0.375f, SleepStageREM,   SleepStageREMDark),
    LIGHT("Light", 0.625f, SleepStageLight, SleepStageLightDark),
    DEEP( "Deep",  0.875f, SleepStageDeep,  SleepStageDeepDark),
}

internal fun HypnoStage.color(dark: Boolean) = if (dark) colorDark else colorLight

private fun String.toHypnoStage(): HypnoStage = when (trim().lowercase()) {
    "deep"          -> HypnoStage.DEEP
    "rem"           -> HypnoStage.REM
    "wake", "awake" -> HypnoStage.WAKE
    else            -> HypnoStage.LIGHT
}

// ─── Data structures ──────────────────────────────────────────────────────────

private data class RunSegment(
    val stage: HypnoStage,
    val startFrac: Float,
    val endFrac: Float,
)

private data class StageTransition(
    val from: HypnoStage,
    val to: HypnoStage,
    /** Fraction of chart width where the boundary occurs */
    val boundaryFrac: Float,
)

private data class HourMark(val label: String, val frac: Float)

private data class HypnoData(
    val runs: List<RunSegment>,
    val transitions: List<StageTransition>,
    val hourMarks: List<HourMark>,
    /** Proportion 0..1 of total session time per stage */
    val stageFracs: Map<HypnoStage, Float>,
)

// ─── Parsing ──────────────────────────────────────────────────────────────────

private fun parseHypnoData(ticks: List<StageTick>): HypnoData? {
    if (ticks.size < 2) return null
    val zone = ZoneId.systemDefault()
    val hourFmt = DateTimeFormatter.ofPattern("H:mm")

    val instants: List<Instant?> = ticks.map { t ->
        try { Instant.parse(t.t) } catch (_: Exception) { null }
    }

    val valid = instants.filterNotNull()
    if (valid.size < 2) return null

    val minMs = valid.minOf { it.toEpochMilli() }
    // Extend past the last tick by the average interval so the last block has width
    val avgIntervalMs = (valid.last().toEpochMilli() - valid.first().toEpochMilli())
        .toFloat() / (valid.size - 1).coerceAtLeast(1)
    val maxMs = valid.last().toEpochMilli() + avgIntervalMs.toLong()
    val spanMs = (maxMs - minMs).toFloat().coerceAtLeast(1f)

    fun frac(ms: Long) = ((ms - minMs) / spanMs).coerceIn(0f, 1f)

    // Each tick covers its timestamp → next tick's timestamp (last extends by avgInterval)
    data class RawSeg(val stage: HypnoStage, val sf: Float, val ef: Float)

    val rawSegs = ticks.mapIndexedNotNull { i, tick ->
        val t0 = instants[i] ?: return@mapIndexedNotNull null
        val t1ms = instants.getOrNull(i + 1)?.toEpochMilli() ?: maxMs
        RawSeg(tick.stage.toHypnoStage(), frac(t0.toEpochMilli()), frac(t1ms))
    }

    // Merge consecutive same-stage raw segments into runs
    val merged = mutableListOf<RunSegment>()
    var k = 0
    while (k < rawSegs.size) {
        val seg = rawSegs[k]
        var j = k + 1
        while (j < rawSegs.size && rawSegs[j].stage == seg.stage) j++
        merged.add(RunSegment(seg.stage, seg.sf, rawSegs[j - 1].ef))
        k = j
    }

    // Shrink each run by half-gap at boundaries where the adjacent stage differs,
    // so the bezier connector has room to draw between blocks.
    val GAP = 0.012f
    val runs = merged.mapIndexed { i, run ->
        val gapL = i > 0 && merged[i - 1].stage != run.stage
        val gapR = i < merged.size - 1 && merged[i + 1].stage != run.stage
        run.copy(
            startFrac = if (gapL) (run.startFrac + GAP / 2f).coerceAtMost(run.endFrac) else run.startFrac,
            endFrac   = if (gapR) (run.endFrac   - GAP / 2f).coerceAtLeast(run.startFrac) else run.endFrac,
        )
    }

    val transitions = merged.zipWithNext().mapNotNull { (a, b) ->
        if (a.stage != b.stage) StageTransition(a.stage, b.stage, a.endFrac) else null
    }

    // Hour marks
    val startZdt = Instant.ofEpochMilli(minMs).atZone(zone)
    val endZdt   = Instant.ofEpochMilli(maxMs).atZone(zone)
    var cursor = startZdt.toLocalDateTime().withMinute(0).withSecond(0).withNano(0).plusHours(1)
    val hourMarks = mutableListOf<HourMark>()
    while (!cursor.atZone(zone).toInstant().isAfter(endZdt.toInstant())) {
        val f = frac(cursor.atZone(zone).toInstant().toEpochMilli())
        if (f in 0.02f..0.97f) hourMarks.add(HourMark(cursor.format(hourFmt), f))
        cursor = cursor.plusHours(1)
    }

    // Stage proportions for pill bar
    val stageFracs = HypnoStage.entries.associateWith { stage ->
        merged.filter { it.stage == stage }.sumOf { (it.endFrac - it.startFrac).toDouble() }.toFloat()
    }

    return HypnoData(runs, transitions, hourMarks, stageFracs)
}

// ─── Stage Pill Bar ───────────────────────────────────────────────────────────

@Composable
fun StagePillBar(
    stages: List<StageTick>,
    modifier: Modifier = Modifier,
) {
    if (stages.isEmpty()) return
    val isDark = isSystemInDarkTheme()
    val data = remember(stages) { parseHypnoData(stages) } ?: return

    // Ordered so Deep appears first (most prominent)
    val order = listOf(HypnoStage.DEEP, HypnoStage.LIGHT, HypnoStage.REM, HypnoStage.WAKE)
    val segments = order.mapNotNull { stage ->
        val frac = data.stageFracs[stage] ?: 0f
        if (frac > 0f) stage to frac else null
    }

    Column(modifier = modifier) {
        // Pill bar
        val pillHeight = 7.dp
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeight),
        ) {
            val radius = size.height / 2f
            val pillPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f, top = 0f, right = size.width, bottom = size.height,
                        cornerRadius = CornerRadius(radius),
                    )
                )
            }
            clipPath(pillPath) {
                var x = 0f
                segments.forEach { (stage, frac) ->
                    val w = frac * size.width
                    drawRect(
                        color = stage.color(isDark),
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                    )
                    x += w
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            segments.forEach { (stage, frac) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(stage.color(isDark))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${stage.label} ${(frac * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ─── Sleep Hypnogram Canvas ───────────────────────────────────────────────────

@Composable
fun SleepHypnogram(
    stages: List<StageTick>,
    modifier: Modifier = Modifier,
) {
    if (stages.size < 2) return

    val isDark = isSystemInDarkTheme()
    val data = remember(stages) { parseHypnoData(stages) } ?: return

    // Left-to-right reveal animation
    var triggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { triggered = true }
    val progress by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "hypno_reveal",
    )

    val textMeasurer = rememberTextMeasurer()

    // Colours captured before entering DrawScope (can't call @Composable inside Canvas)
    val labelColor  = if (isDark) SleepDarkTextSecondary else SleepTextSecondary
    val bandTint    = if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.025f)
    val dividerTint = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f)
    val connColor   = if (isDark) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.20f)
    val tickColor   = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Medium)
    val hourStyle  = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier = modifier) {
        val labelW  = 38.dp.toPx()
        val hourH   = 18.dp.toPx()
        val chartW  = size.width - labelW
        val chartH  = size.height - hourH

        // Helper: canvas X / Y for a fraction
        fun cx(f: Float) = labelW + f * chartW
        fun cy(yFrac: Float) = yFrac * chartH

        // ── 1. Stage zone background bands & Y-axis labels (always visible) ──
        HypnoStage.entries.forEach { stage ->
            val bandTop = cy(stage.yFrac - 0.125f)
            val bandBot = cy(stage.yFrac + 0.125f)

            // Alternating faint tint for readability
            drawRect(
                color = bandTint,
                topLeft = Offset(labelW, bandTop),
                size = Size(chartW, bandBot - bandTop),
            )

            // Y-axis label
            val measured = textMeasurer.measure(stage.label, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (labelW - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                    y = cy(stage.yFrac) - measured.size.height / 2f,
                ),
            )
        }

        // Horizontal divider lines between stage bands
        listOf(0.25f, 0.50f, 0.75f).forEach { y ->
            drawLine(
                color = dividerTint,
                start = Offset(labelW, cy(y)),
                end   = Offset(labelW + chartW, cy(y)),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        // ── 2. Animated chart content (clipped to progress) ──────────────────
        clipRect(
            left   = labelW,
            top    = 0f,
            right  = labelW + chartW * progress,
            bottom = size.height,
        ) {
            val blockHalf  = chartH * 0.065f   // block = 13% of chartH
            val cornerR    = CornerRadius(5.dp.toPx())
            val glowR1     = CornerRadius(8.dp.toPx())
            val glowR2     = CornerRadius(10.dp.toPx())
            val glowExpand1 = 2.5.dp.toPx()
            val glowExpand2 = 5.dp.toPx()

            // ── Stage blocks ─────────────────────────────────────────────────
            data.runs.forEach { run ->
                val stageColor = run.stage.color(isDark)
                val left   = cx(run.startFrac)
                val right  = cx(run.endFrac)
                val yC     = cy(run.stage.yFrac)
                val top    = yC - blockHalf
                val bottom = yC + blockHalf
                val w      = (right - left).coerceAtLeast(1f)
                val h      = blockHalf * 2f

                // Outer glow
                drawRoundRect(
                    color = stageColor.copy(alpha = 0.10f),
                    topLeft = Offset(left - glowExpand2, top - glowExpand2),
                    size = Size(w + glowExpand2 * 2, h + glowExpand2 * 2),
                    cornerRadius = glowR2,
                )
                // Inner glow
                drawRoundRect(
                    color = stageColor.copy(alpha = 0.18f),
                    topLeft = Offset(left - glowExpand1, top - glowExpand1),
                    size = Size(w + glowExpand1 * 2, h + glowExpand1 * 2),
                    cornerRadius = glowR1,
                )
                // Main block — vertical gradient (lighter on top → solid on bottom)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(stageColor.copy(alpha = 0.72f), stageColor),
                        startY = top,
                        endY   = bottom,
                    ),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = cornerR,
                )
                // Subtle inner highlight on top edge
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        startY = top,
                        endY   = top + h * 0.45f,
                    ),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = cornerR,
                )
            }

            // ── Bezier connectors between stage transitions ───────────────────
            data.transitions.forEach { t ->
                val x1 = cx(t.boundaryFrac - 0.006f)
                val y1 = cy(t.from.yFrac)
                val x2 = cx(t.boundaryFrac + 0.006f)
                val y2 = cy(t.to.yFrac)
                val mx = (x1 + x2) / 2f

                val path = Path().apply {
                    moveTo(x1, y1)
                    cubicTo(mx, y1, mx, y2, x2, y2)
                }
                drawPath(
                    path = path,
                    color = connColor,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // ── Hour marks & labels ───────────────────────────────────────────
            data.hourMarks.forEach { mark ->
                val x = cx(mark.frac)
                // Tick
                drawLine(
                    color = tickColor,
                    start = Offset(x, chartH),
                    end   = Offset(x, chartH + 3.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
                // Label
                val measured = textMeasurer.measure(mark.label, hourStyle)
                val lx = (x - measured.size.width / 2f).coerceIn(labelW, labelW + chartW - measured.size.width)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(lx, chartH + 4.dp.toPx()),
                )
            }
        }
    }
}
