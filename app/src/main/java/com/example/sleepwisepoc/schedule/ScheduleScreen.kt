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
import androidx.compose.runtime.key
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

private val HmFmt    = DateTimeFormatter.ofPattern("HH:mm") // 08:00 — hints (24h)
private val ShortFmt = DateTimeFormatter.ofPattern("H:mm")  // 6:45 / 18:45 — cells & range (24h)

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

    // NOTHING is persisted until "Save schedule". The picker edits a local DRAFT
    // (time + window + on/off). The draft loads from a day when you focus a single
    // day; with several days selected it stays put, and Save writes it to ALL of
    // them at once. Day cells keep showing their SAVED values until you press Save.
    var draftHour by remember { mutableStateOf(7) }
    var draftMinute by remember { mutableStateOf(0) }
    var draftWindow by remember { mutableStateOf(30) }
    var draftOn by remember { mutableStateOf(true) }
    var pickerKey by remember { mutableStateOf(0) }
    var edited by remember { mutableStateOf(false) }

    val draftTime = LocalTime.of(draftHour, draftMinute)

    fun saveDraftTo(target: Set<Int>) {
        val t = LocalTime.of(draftHour, draftMinute)
        target.forEach { viewModel.saveDay(it, schedule[it].copy(wakeTime = t, windowMinutes = draftWindow, smartAlarm = draftOn)) }
    }

    // Load the focused single day into the draft (until the user edits it). Covers
    // the initial async load and switching to a different single day.
    LaunchedEffect(sel, schedule) {
        if (sel.size == 1 && !edited) {
            val d = schedule[sel.first()]
            if (d.wakeTime.hour != draftHour || d.wakeTime.minute != draftMinute ||
                d.windowMinutes != draftWindow || d.smartAlarm != draftOn) {
                draftHour = d.wakeTime.hour; draftMinute = d.wakeTime.minute
                draftWindow = d.windowMinutes; draftOn = d.smartAlarm
                pickerKey++
            }
        }
    }

    // The draft differs from a selected day's saved value → there are unsaved changes.
    val dirty = sel.any {
        val d = schedule[it]
        d.wakeTime.hour != draftHour || d.wakeTime.minute != draftMinute ||
            d.windowMinutes != draftWindow || d.smartAlarm != draftOn
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
            val newSel = when {
                d in sel && sel.size > 1 -> sel - d   // deselect (but never the last one)
                d in sel                 -> sel        // last remaining day stays selected
                else                     -> sel + d    // add a day
            }
            selectedDays = newSel
            if (newSel.size == 1) edited = false       // focus one day → load its saved time
            // multi-select keeps the current draft; nothing is written until Save
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
                    if (sel.size == 1) DAY_NAMES[sel.first()] else "${sel.size} days selected",
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (!draftOn) "Alarm off" else "Wake at ${draftTime.format(HmFmt)}${if (dirty) " · unsaved" else ""}",
                    fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 18.sp, color = c.dim,
                )
            }
            Spacer(Modifier.width(12.dp))
            NfToggle(checked = draftOn, onCheckedChange = { v -> draftOn = v; edited = true })
        }

        Spacer(Modifier.height(26.dp))

        // ── Time-picker well ───────────────────────────────────────────────────
        // key(pickerKey) re-seeds the columns only when a new day is loaded — NOT
        // when the time value changes — so scrolling the hour never resets the
        // minute column. Every scroll updates the draft and applies it to all days.
        key(pickerKey) {
            TimePickerWell(
                hour   = draftHour,
                minute = draftMinute,
                onHour = { h -> draftHour = h; edited = true },
                onMinute = { m -> draftMinute = m; edited = true },
            )
        }

        Spacer(Modifier.height(26.dp))

        // ── Flexible window ────────────────────────────────────────────────────
        val win   = draftWindow
        val wake  = draftTime
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
                        .clickable { draftWindow = w; edited = true },
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

        // Writes the draft (time + window + on/off) to EVERY selected day — nothing
        // was persisted before this.
        PrimaryButton(
            text = if (dirty) "Save schedule" else "Saved",
            onClick = { saveDraftTo(sel); edited = false },
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
    // Seed the scroll position to the current value ONCE (this composable is
    // key()'d on the day selection upstream, so it re-seeds when the day changes).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // The centered item = the one whose center is nearest the viewport center,
    // read precisely from layoutInfo. (The old firstVisibleIndex+offset heuristic
    // could land one off after a snap, so the SAVED value didn't match what you saw.)
    val centered by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) selectedIndex
            else {
                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - mid) }!!.index
            }
        }
    }

    // Commit once the scroll settles (and only when it actually changed).
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
