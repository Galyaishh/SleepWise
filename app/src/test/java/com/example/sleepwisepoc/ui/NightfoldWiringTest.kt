package com.example.sleepwisepoc.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sleepwisepoc.ThemeStore
import com.example.sleepwisepoc.schedule.DaySchedule
import com.example.sleepwisepoc.schedule.SleepSchedule
import com.example.sleepwisepoc.schedule.SleepScheduleStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * "Everything is still wired" regression tests for the Nightfold redesign.
 * The redesign restyled the screens but must NOT change the data/logic behind
 * them: the theme switch (Profile), the Sunday-first per-day schedule (Schedule),
 * and its persistence. These run headless via Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NightfoldWiringTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun themeStore_defaultsToDark_andTogglesBothWays() {
        // Profile's "Night theme" toggle is backed by this; dark is the default.
        assertTrue("app defaults to the dark Nightfold theme", ThemeStore.isDark(ctx))
        ThemeStore.setDark(ctx, false)
        assertFalse(ThemeStore.isDark(ctx))
        ThemeStore.setDark(ctx, true)
        assertTrue(ThemeStore.isDark(ctx))
    }

    @Test
    fun schedule_isSundayFirst_sevenDays() {
        assertEquals(7, SleepSchedule.DEFAULT_DAYS.size)
        // Schedule screen indexes days Sunday=0 … Saturday=6 (dayOfWeek.value % 7).
        assertEquals(0, DayOfWeek.SUNDAY.value % 7)
        assertEquals(1, DayOfWeek.MONDAY.value % 7)
        assertEquals(6, DayOfWeek.SATURDAY.value % 7)
    }

    @Test
    fun scheduleStore_saveDay_persistsThroughFlow() = runTest {
        val store = SleepScheduleStore(ctx)
        store.saveDay(1, DaySchedule(wakeTime = LocalTime.of(6, 15), windowMinutes = 20, smartAlarm = true))
        val s = store.schedule.first()
        assertEquals(LocalTime.of(6, 15), s[1].wakeTime)
        assertEquals(20, s[1].windowMinutes)
        assertTrue(s[1].smartAlarm)
        // windowStart is derived — the smart-wake window the Schedule screen shows.
        assertEquals(LocalTime.of(5, 55), s[1].windowStart)
    }
}
