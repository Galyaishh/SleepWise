package com.example.sleepwisepoc.schedule

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

private val Context.scheduleDataStore by preferencesDataStore(name = "schedule")

val ALARM_SOUNDS = listOf("Sunrise Chimes", "Forest Morning", "Ocean Waves", "Gentle Bell", "Vibration Only")
val SNOOZE_OPTIONS = listOf(5, 9, 10, 15)

data class DaySchedule(
    val wakeTime: LocalTime = LocalTime.of(7, 0),
    val windowMinutes: Int = 30,
    val smartAlarm: Boolean = true,
    val alarmSound: String = "Sunrise Chimes",
    val snoozeMinutes: Int = 9,
    val alarmSoundUri: String? = null,
) {
    val windowStart: LocalTime get() = wakeTime.minusMinutes(windowMinutes.toLong())
}

/**
 * Per-day schedule. Index 0 = Sunday … 6 = Saturday — every weekday is configured
 * independently. Defaults follow the Israeli work week (Sun–Thu 07:00 / Fri–Sat 09:00),
 * but each day can be customised separately on the Schedule screen.
 */
data class SleepSchedule(
    val days: List<DaySchedule> = DEFAULT_DAYS,
) {
    operator fun get(dayIndex: Int): DaySchedule = days[dayIndex.coerceIn(0, 6)]

    /** The DaySchedule that applies to a given calendar date (0=Sun … 6=Sat). */
    fun forDate(date: LocalDate): DaySchedule = this[date.dayOfWeek.value % 7]

    companion object {
        val DEFAULT_DAYS: List<DaySchedule> = List(7) { i ->
            if (i in 0..4) DaySchedule(LocalTime.of(7, 0), 30)
            else DaySchedule(LocalTime.of(9, 0), 45)
        }
    }
}

class SleepScheduleStore(private val context: Context) {

    private fun kHour(d: Int)     = intPreferencesKey("d${d}_wake_hour")
    private fun kMin(d: Int)      = intPreferencesKey("d${d}_wake_min")
    private fun kWin(d: Int)      = intPreferencesKey("d${d}_window")
    private fun kSmart(d: Int)    = booleanPreferencesKey("d${d}_smart_alarm")
    private fun kSound(d: Int)    = stringPreferencesKey("d${d}_alarm_sound")
    private fun kSoundUri(d: Int) = stringPreferencesKey("d${d}_alarm_sound_uri")
    private fun kSnooze(d: Int)   = intPreferencesKey("d${d}_snooze")

    val schedule: Flow<SleepSchedule> = context.scheduleDataStore.data.map { prefs ->
        SleepSchedule(List(7) { i ->
            val def = SleepSchedule.DEFAULT_DAYS[i]
            DaySchedule(
                wakeTime      = LocalTime.of(prefs[kHour(i)] ?: def.wakeTime.hour, prefs[kMin(i)] ?: def.wakeTime.minute),
                windowMinutes = prefs[kWin(i)] ?: def.windowMinutes,
                smartAlarm    = prefs[kSmart(i)] ?: def.smartAlarm,
                alarmSound    = prefs[kSound(i)] ?: def.alarmSound,
                snoozeMinutes = prefs[kSnooze(i)] ?: def.snoozeMinutes,
                alarmSoundUri = prefs[kSoundUri(i)],
            )
        })
    }

    /** Persist a single day (0=Sun … 6=Sat) independently of the others. */
    suspend fun saveDay(dayIndex: Int, day: DaySchedule) {
        val d = dayIndex.coerceIn(0, 6)
        context.scheduleDataStore.edit { prefs ->
            prefs[kHour(d)]   = day.wakeTime.hour
            prefs[kMin(d)]    = day.wakeTime.minute
            prefs[kWin(d)]    = day.windowMinutes
            prefs[kSmart(d)]  = day.smartAlarm
            prefs[kSound(d)]  = day.alarmSound
            prefs[kSnooze(d)] = day.snoozeMinutes
            if (day.alarmSoundUri != null) prefs[kSoundUri(d)] = day.alarmSoundUri
        }
    }

    /** Bulk-seed Sun–Thu (used by the first-run setup wizard for sensible defaults). */
    suspend fun saveWeekdays(day: DaySchedule) { (0..4).forEach { saveDay(it, day) } }

    /** Bulk-seed Fri–Sat (used by the first-run setup wizard for sensible defaults). */
    suspend fun saveWeekends(day: DaySchedule) { (5..6).forEach { saveDay(it, day) } }
}
