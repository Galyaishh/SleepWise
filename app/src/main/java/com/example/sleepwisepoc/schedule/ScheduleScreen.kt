package com.example.sleepwisepoc.schedule

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleepwisepoc.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SleepScheduleStore(application)

    val schedule = store.schedule.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), SleepSchedule()
    )

    /** Persist just the edited day (0=Sun … 6=Sat); other days are untouched. */
    fun saveDay(dayIndex: Int, day: DaySchedule) = viewModelScope.launch { store.saveDay(dayIndex, day) }
}

// ─── Day-of-week constants + formatters ───────────────────────────────────────

private val DAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")
private val DAY_NAMES    = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val WINDOW_OPTIONS = listOf(10, 15, 20, 30, 45, 60)

private val HmFmt    = DateTimeFormatter.ofPattern("HH:mm") // 08:00 — hints
private val ShortFmt = DateTimeFormatter.ofPattern("h:mm")  // 6:45 — cells & range

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = viewModel(),
) {
    val c = LocalSleepColors.current
    val schedule by viewModel.schedule.collectAsState()

    // The schedule is a map of day → {on, hour, minute, window}. Editing is
    // multi-select: `selectedDays` is never empty (Sunday-first, Sunday default);
    // every edit writes to the whole selection and implicitly switches those days on.
    var selectedDays by remember { mutableStateOf(setOf(0)) }
    val sel = selectedDays.ifEmpty { setOf(0) }

    val repDay   = schedule[sel.min()]              // representative values for the pickers
    val allOn    = sel.all { schedule[it].smartAlarm }
    val anyOff   = sel.any { !schedule[it].smartAlarm }
    val sameTime = sel.map { schedule[it].wakeTime }.distinct().size == 1

    fun editAll(transform: (DaySchedule) -> DaySchedule) {
        sel.forEach { d -> viewModel.saveDay(d, transform(schedule[d])) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
    ) {
        Spacer(Modifier.height(26.dp))
        Eyebrow("SCHEDULE")

        Spacer(Modifier.height(14.dp))
        Text(
            "When should we wake you?",
            fontFamily = InstrumentSerif, fontSize = 34.sp, lineHeight = 38.sp, color = c.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the days, then set one time for all of them.",
            fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 20.sp, color = c.dim,
        )

        Spacer(Modifier.height(26.dp))

        // ── Day row (Sunday-first) ─────────────────────────────────────────────
        DayRow(schedule, sel) { d ->
            selectedDays = when {
                d in sel && sel.size > 1 -> sel - d   // deselect (but never the last one)
                d in sel                 -> sel        // last remaining day stays selected
                else                     -> sel + d
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Editing row: selection label + hint, and the on/off toggle ─────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (sel.size == 1) DAY_NAMES[sel.first()] else "Editing ${sel.size} days together",
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        anyOff    -> "Some days are off"
                        !sameTime -> "Different times — pick one to set them all"
                        else      -> "Alarm on at ${repDay.wakeTime.format(HmFmt)}"
                    },
                    fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 18.sp, color = c.dim,
                )
            }
            Spacer(Modifier.width(12.dp))
            NfToggle(checked = allOn, onCheckedChange = { v -> editAll { it.copy(smartAlarm = v) } })
        }

        Spacer(Modifier.height(26.dp))

        // ── Time-picker well ───────────────────────────────────────────────────
        TimePickerWell(
            hour   = repDay.wakeTime.hour,
            minute = repDay.wakeTime.minute,
            onHour = { h ->
                val t = LocalTime.of(h, repDay.wakeTime.minute)
                sel.forEach { viewModel.saveDay(it, schedule[it].copy(wakeTime = t, smartAlarm = true)) }
            },
            onMinute = { m ->
                val t = LocalTime.of(repDay.wakeTime.hour, m)
                sel.forEach { viewModel.saveDay(it, schedule[it].copy(wakeTime = t, smartAlarm = true)) }
            },
        )

        Spacer(Modifier.height(26.dp))

        // ── Flexible window ────────────────────────────────────────────────────
        val win   = repDay.windowMinutes
        val wake  = repDay.wakeTime
        val start = wake.minusMinutes(win.toLong())

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Flexible window", fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text)
            Text(
                "${start.format(ShortFmt)} – ${wake.format(ShortFmt)}",
                fontFamily = PlexMono, fontSize = 13.sp, color = c.accent,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            WINDOW_OPTIONS.forEach { w ->
                val on = w == win
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (on) Modifier.background(c.accentSoft).border(1.dp, c.accent, RoundedCornerShape(16.dp))
                            else Modifier.border(1.dp, c.lineStrong, RoundedCornerShape(16.dp))
                        )
                        .clickable {
                            sel.forEach { viewModel.saveDay(it, schedule[it].copy(windowMinutes = w, smartAlarm = true)) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$w", fontFamily = PlexSans, fontSize = 13.sp, color = if (on) c.accent else c.dim)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "We'll find your lightest moment in the $win minutes before ${wake.format(HmFmt)}. " +
                "If you stay deep, we'll wake you right on time.",
            fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 19.sp, color = c.dim,
        )

        Spacer(Modifier.height(26.dp))

        // Edits already persist as you make them; this commits the current selection.
        PrimaryButton(
            text = "Save schedule",
            onClick = { sel.forEach { viewModel.saveDay(it, schedule[it]) } },
        )

        Spacer(Modifier.height(34.dp))
    }
}

// ─── Day row ──────────────────────────────────────────────────────────────────

@Composable
private fun DayRow(schedule: SleepSchedule, selected: Set<Int>, onTap: (Int) -> Unit) {
    val c = LocalSleepColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        for (i in 0..6) {
            val d      = schedule[i]
            val isSel  = i in selected
            val on     = d.smartAlarm
            val letterColor = when { isSel -> c.onAccent; on -> c.text; else -> c.faint }
            val timeColor   = when { isSel -> c.onAccent.copy(alpha = 0.75f); on -> c.dim; else -> c.faint }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 74.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSel) c.accent else c.surface)
                    .clickable { onTap(i) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(DAY_LETTERS[i], fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = letterColor)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (on) d.wakeTime.format(ShortFmt) else "off",
                    fontFamily = PlexMono, fontSize = 9.sp, color = timeColor,
                )
            }
        }
    }
}

// ─── Time-picker well ─────────────────────────────────────────────────────────

@Composable
private fun TimePickerWell(hour: Int, minute: Int, onHour: (Int) -> Unit, onMinute: (Int) -> Unit) {
    val c = LocalSleepColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Static center selection band — a 44px strip inset 10px, hairlines top & bottom.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 10.dp)
                .drawBehind {
                    drawRect(color = c.lineStrong, topLeft = Offset(0f, 0f), size = Size(size.width, 1f))
                    drawRect(color = c.lineStrong, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f))
                },
        )
        // Numerals sit on top of the band and receive the scroll gestures.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrollPickerColumn(count = 24, selectedIndex = hour, onSelected = onHour, modifier = Modifier.width(92.dp))
            Text(":", fontFamily = InstrumentSerif, fontSize = 40.sp, color = c.text)
            ScrollPickerColumn(count = 60, selectedIndex = minute, onSelected = onMinute, modifier = Modifier.width(92.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollPickerColumn(
    count: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalSleepColors.current
    val itemHeightPx = with(LocalDensity.current) { 44.dp.toPx() }
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // Which item sits in the center band right now.
    val centered by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2f) 1 else 0)
                .coerceIn(0, count - 1)
        }
    }

    // Center the current value on mount and whenever the value changes externally
    // (e.g. switching the selected day). Never fights an in-progress user drag.
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress) listState.scrollToItem(selectedIndex)
    }

    // Commit once the scroll settles.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && centered != selectedIndex) onSelected(centered)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = fling,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 62.dp), // 62 + 44 + 62 = 168 → item 0 centers
        modifier = modifier.height(168.dp),
    ) {
        items(count) { i ->
            val isSel = i == centered
            Box(
                modifier = Modifier.height(44.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%02d".format(i),
                    fontFamily = InstrumentSerif,
                    fontSize = if (isSel) 40.sp else 30.sp,
                    color = if (isSel) c.text else c.faint,
                )
            }
        }
    }
}
