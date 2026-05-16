package com.example.sleepwisepoc.schedule

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.scheduleDataStore by preferencesDataStore(name = "schedule")

data class DaySchedule(
    val wakeTime: LocalTime = LocalTime.of(7, 0),
    val windowMinutes: Int = 30,
) {
    val windowStart: LocalTime get() = wakeTime.minusMinutes(windowMinutes.toLong())
}

data class SleepSchedule(
    val weekday: DaySchedule = DaySchedule(LocalTime.of(7, 0), 30),
    val weekend: DaySchedule = DaySchedule(LocalTime.of(9, 0), 45),
)

class SleepScheduleStore(private val context: Context) {

    private val KEY_WD_HOUR = intPreferencesKey("wd_wake_hour")
    private val KEY_WD_MIN  = intPreferencesKey("wd_wake_min")
    private val KEY_WD_WIN  = intPreferencesKey("wd_window")
    private val KEY_WE_HOUR = intPreferencesKey("we_wake_hour")
    private val KEY_WE_MIN  = intPreferencesKey("we_wake_min")
    private val KEY_WE_WIN  = intPreferencesKey("we_window")

    val schedule: Flow<SleepSchedule> = context.scheduleDataStore.data.map { prefs ->
        SleepSchedule(
            weekday = DaySchedule(
                wakeTime = LocalTime.of(prefs[KEY_WD_HOUR] ?: 7, prefs[KEY_WD_MIN] ?: 0),
                windowMinutes = prefs[KEY_WD_WIN] ?: 30,
            ),
            weekend = DaySchedule(
                wakeTime = LocalTime.of(prefs[KEY_WE_HOUR] ?: 9, prefs[KEY_WE_MIN] ?: 0),
                windowMinutes = prefs[KEY_WE_WIN] ?: 45,
            ),
        )
    }

    suspend fun saveWeekday(day: DaySchedule) {
        context.scheduleDataStore.edit { prefs ->
            prefs[KEY_WD_HOUR] = day.wakeTime.hour
            prefs[KEY_WD_MIN]  = day.wakeTime.minute
            prefs[KEY_WD_WIN]  = day.windowMinutes
        }
    }

    suspend fun saveWeekend(day: DaySchedule) {
        context.scheduleDataStore.edit { prefs ->
            prefs[KEY_WE_HOUR] = day.wakeTime.hour
            prefs[KEY_WE_MIN]  = day.wakeTime.minute
            prefs[KEY_WE_WIN]  = day.windowMinutes
        }
    }
}
