package com.example.sleepwisepoc.alarm

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "smart_alarm")

private val KEY_START_MIN = intPreferencesKey("start_minute_of_day")
private val KEY_END_MIN = intPreferencesKey("end_minute_of_day")

class AlarmWindowStore(private val context: Context) {

    val window: Flow<SmartAlarmWindow?> = context.dataStore.data.map { prefs ->
        val startMin = prefs[KEY_START_MIN]
        val endMin = prefs[KEY_END_MIN]
        if (startMin == null || endMin == null) null
        else SmartAlarmWindow(
            start = LocalTime.of(startMin / 60, startMin % 60),
            end = LocalTime.of(endMin / 60, endMin % 60),
        )
    }

    suspend fun save(start: LocalTime, end: LocalTime) {
        context.dataStore.edit { prefs ->
            prefs[KEY_START_MIN] = start.hour * 60 + start.minute
            prefs[KEY_END_MIN] = end.hour * 60 + end.minute
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}

data class SmartAlarmWindow(
    val start: LocalTime,
    val end: LocalTime,
)
