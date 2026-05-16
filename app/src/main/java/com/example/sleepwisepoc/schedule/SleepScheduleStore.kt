package com.example.sleepwisepoc.schedule

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) {
    val windowStart: LocalTime get() = wakeTime.minusMinutes(windowMinutes.toLong())
}

data class SleepSchedule(
    val weekday: DaySchedule = DaySchedule(LocalTime.of(7, 0), 30),
    val weekend: DaySchedule = DaySchedule(LocalTime.of(9, 0), 45),
)

class SleepScheduleStore(private val context: Context) {

    private val KEY_WD_HOUR   = intPreferencesKey("wd_wake_hour")
    private val KEY_WD_MIN    = intPreferencesKey("wd_wake_min")
    private val KEY_WD_WIN    = intPreferencesKey("wd_window")
    private val KEY_WD_SMART  = booleanPreferencesKey("wd_smart_alarm")
    private val KEY_WD_SOUND  = stringPreferencesKey("wd_alarm_sound")
    private val KEY_WD_SNOOZE = intPreferencesKey("wd_snooze")
    private val KEY_WE_HOUR   = intPreferencesKey("we_wake_hour")
    private val KEY_WE_MIN    = intPreferencesKey("we_wake_min")
    private val KEY_WE_WIN    = intPreferencesKey("we_window")
    private val KEY_WE_SMART  = booleanPreferencesKey("we_smart_alarm")
    private val KEY_WE_SOUND  = stringPreferencesKey("we_alarm_sound")
    private val KEY_WE_SNOOZE = intPreferencesKey("we_snooze")

    val schedule: Flow<SleepSchedule> = context.scheduleDataStore.data.map { prefs ->
        SleepSchedule(
            weekday = DaySchedule(
                wakeTime      = LocalTime.of(prefs[KEY_WD_HOUR] ?: 7, prefs[KEY_WD_MIN] ?: 0),
                windowMinutes = prefs[KEY_WD_WIN] ?: 30,
                smartAlarm    = prefs[KEY_WD_SMART] ?: true,
                alarmSound    = prefs[KEY_WD_SOUND] ?: "Sunrise Chimes",
                snoozeMinutes = prefs[KEY_WD_SNOOZE] ?: 9,
            ),
            weekend = DaySchedule(
                wakeTime      = LocalTime.of(prefs[KEY_WE_HOUR] ?: 9, prefs[KEY_WE_MIN] ?: 0),
                windowMinutes = prefs[KEY_WE_WIN] ?: 45,
                smartAlarm    = prefs[KEY_WE_SMART] ?: true,
                alarmSound    = prefs[KEY_WE_SOUND] ?: "Sunrise Chimes",
                snoozeMinutes = prefs[KEY_WE_SNOOZE] ?: 9,
            ),
        )
    }

    suspend fun saveWeekday(day: DaySchedule) {
        context.scheduleDataStore.edit { prefs ->
            prefs[KEY_WD_HOUR]   = day.wakeTime.hour
            prefs[KEY_WD_MIN]    = day.wakeTime.minute
            prefs[KEY_WD_WIN]    = day.windowMinutes
            prefs[KEY_WD_SMART]  = day.smartAlarm
            prefs[KEY_WD_SOUND]  = day.alarmSound
            prefs[KEY_WD_SNOOZE] = day.snoozeMinutes
        }
    }

    suspend fun saveWeekend(day: DaySchedule) {
        context.scheduleDataStore.edit { prefs ->
            prefs[KEY_WE_HOUR]   = day.wakeTime.hour
            prefs[KEY_WE_MIN]    = day.wakeTime.minute
            prefs[KEY_WE_WIN]    = day.windowMinutes
            prefs[KEY_WE_SMART]  = day.smartAlarm
            prefs[KEY_WE_SOUND]  = day.alarmSound
            prefs[KEY_WE_SNOOZE] = day.snoozeMinutes
        }
    }
}
