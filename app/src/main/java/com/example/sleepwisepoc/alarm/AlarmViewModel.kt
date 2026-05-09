package com.example.sleepwisepoc.alarm

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.service.SleepMonitoringService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

data class SmartAlarmUiState(
    val start: LocalTime = LocalTime.of(6, 30),
    val end: LocalTime = LocalTime.of(7, 0),
    val errorMessage: String? = null,
    val savedAt: LocalTime? = null,
)

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AlarmWindowStore(application)

    private val _state = MutableStateFlow(SmartAlarmUiState())
    val state: StateFlow<SmartAlarmUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.window.firstOrNull()
            if (saved != null) {
                Log.d(TAG, "loaded window from DataStore: $saved")
                _state.update { it.copy(start = saved.start, end = saved.end) }
            } else {
                Log.d(TAG, "no saved window — using defaults")
            }
        }
    }

    fun setStart(time: LocalTime) {
        Log.d(TAG, "setStart=$time")
        _state.update { current ->
            val updated = current.copy(start = time, savedAt = null)
            updated.copy(errorMessage = validate(updated.start, updated.end))
        }
    }

    fun setEnd(time: LocalTime) {
        Log.d(TAG, "setEnd=$time")
        _state.update { current ->
            val updated = current.copy(end = time, savedAt = null)
            updated.copy(errorMessage = validate(updated.start, updated.end))
        }
    }

    fun save() {
        val current = _state.value
        val error = validate(current.start, current.end)
        if (error != null) {
            Log.w(TAG, "save() rejected: $error")
            _state.update { it.copy(errorMessage = error) }
            return
        }
        Log.d(TAG, "save() ok start=${current.start} end=${current.end}")
        viewModelScope.launch {
            store.save(current.start, current.end)
            _state.update { it.copy(savedAt = LocalTime.now(), errorMessage = null) }
            SleepMonitoringService.start(getApplication(), current.start, current.end)
        }
    }

    private fun validate(start: LocalTime, end: LocalTime): String? {
        if (!start.isBefore(end)) return "End time must be after start time"
        val minutes = java.time.Duration.between(start, end).toMinutes()
        if (minutes < MIN_WINDOW_MINUTES) return "Window must be at least $MIN_WINDOW_MINUTES minutes"
        return null
    }

    companion object {
        private const val TAG = "SmartAlarmVM"
        const val MIN_WINDOW_MINUTES = 5L
    }
}
