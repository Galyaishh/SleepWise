package com.example.sleepwisepoc.tonight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.schedule.DaySchedule
import com.example.sleepwisepoc.schedule.SleepScheduleStore
import com.example.sleepwisepoc.service.SleepMonitoringService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class TonightUiState(
    val greeting: String = "Good evening",
    val schedule: DaySchedule = DaySchedule(),
    val isTracking: Boolean = false,
)

class TonightViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SleepScheduleStore(application)
    private val _isTracking = MutableStateFlow(false)

    val state = combine(store.schedule, _isTracking) { schedule, tracking ->
        val tomorrow = LocalDate.now().plusDays(1)
        val isWeekend = tomorrow.dayOfWeek == DayOfWeek.SATURDAY ||
                tomorrow.dayOfWeek == DayOfWeek.SUNDAY
        TonightUiState(
            greeting = greeting(),
            schedule = if (isWeekend) schedule.weekend else schedule.weekday,
            isTracking = tracking,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TonightUiState())

    fun startTracking() {
        val s = state.value.schedule
        SleepMonitoringService.start(getApplication(), s.windowStart, s.wakeTime)
        _isTracking.update { true }
    }

    fun stopTracking() {
        SleepMonitoringService.stop(getApplication())
        _isTracking.update { false }
    }

    private fun greeting(): String = when (LocalTime.now().hour) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else      -> "Good night"
    }
}
