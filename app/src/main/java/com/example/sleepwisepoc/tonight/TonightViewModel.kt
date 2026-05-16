package com.example.sleepwisepoc.tonight

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.SamsungHealthManager
import com.example.sleepwisepoc.schedule.DaySchedule
import com.example.sleepwisepoc.schedule.SleepScheduleStore
import com.example.sleepwisepoc.service.SleepMonitoringService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class WatchStatus { Checking, Connected, NoRecentData, Disconnected }

data class TonightUiState(
    val greeting: String = "Good evening",
    val schedule: DaySchedule = DaySchedule(),
    val isTracking: Boolean = false,
    val isAlarmOnly: Boolean = false,
    val watchStatus: WatchStatus = WatchStatus.Checking,
)

class TonightViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SleepScheduleStore(application)
    private val _isTracking  = MutableStateFlow(false)
    private val _isAlarmOnly = MutableStateFlow(false)
    private val _watchStatus = MutableStateFlow(WatchStatus.Checking)

    init {
        refreshWatchStatus()
    }

    val state = combine(store.schedule, _isTracking, _watchStatus, _isAlarmOnly) { schedule, tracking, watch, alarmOnly ->
        val tomorrow = LocalDate.now().plusDays(1)
        val isWeekend = tomorrow.dayOfWeek == DayOfWeek.SATURDAY ||
                tomorrow.dayOfWeek == DayOfWeek.SUNDAY
        TonightUiState(
            greeting    = greeting(),
            schedule    = if (isWeekend) schedule.weekend else schedule.weekday,
            isTracking  = tracking,
            isAlarmOnly = alarmOnly,
            watchStatus = watch,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TonightUiState())

    /** Probe Samsung Health for recent HR data — that's our proxy for "watch connected". */
    fun refreshWatchStatus() {
        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true)
        if (isEmulator) {
            _watchStatus.value = WatchStatus.Disconnected
            return
        }
        _watchStatus.value = WatchStatus.Checking
        viewModelScope.launch(Dispatchers.IO) {
            val mgr = SamsungHealthManager(getApplication())
            if (!mgr.initialize()) {
                Log.w(TAG, "Samsung Health init failed → Disconnected")
                _watchStatus.value = WatchStatus.Disconnected
                return@launch
            }
            try {
                // "Connected" = any data type synced in the last hour.
                // We probe HR + skin-temp + SpO2 in parallel-ish so we don't
                // miss watches that sync temperature/SpO2 first.
                val hr = mgr.readHeartRate(hoursBack = 1)
                val temp = mgr.readSkinTemperature(hoursBack = 1)
                val spo2 = mgr.readBloodOxygen(hoursBack = 1)
                val recentTotal = hr.size + temp.size + spo2.size
                _watchStatus.value =
                    if (recentTotal > 0) WatchStatus.Connected else WatchStatus.NoRecentData
                Log.d(TAG, "watch readiness last 1h: HR=${hr.size} temp=${temp.size} spo2=${spo2.size}")
                // Full diagnostic dump — see what data Samsung Health actually has.
                // Visible in logcat with `adb logcat -s TonightViewModel:D`.
                runCatching { Log.d(TAG, "\n" + mgr.getFormattedHealthData()) }
            } catch (t: Throwable) {
                Log.w(TAG, "watch readiness check failed: ${t.message}")
                _watchStatus.value = WatchStatus.Disconnected
            }
        }
    }

    fun startTracking() {
        val s = state.value.schedule
        SleepMonitoringService.start(getApplication(), s.windowStart, s.wakeTime)
        _isTracking.update { true }
        _isAlarmOnly.update { false }
    }

    fun startTrackingAlarmOnly() {
        val s = state.value.schedule
        SleepMonitoringService.start(getApplication(), s.windowStart, s.wakeTime)
        _isTracking.update { true }
        _isAlarmOnly.update { true }
    }

    fun stopTracking() {
        SleepMonitoringService.stop(getApplication())
        _isTracking.update { false }
        _isAlarmOnly.update { false }
    }

    private fun greeting(): String {
        val timeOfDay = when (LocalTime.now().hour) {
            in 5..11  -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else      -> "Good night"
        }
        val firstName = FirebaseAuth.getInstance().currentUser
            ?.displayName
            ?.trim()
            ?.split(" ")
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
        return if (firstName != null) "$timeOfDay, $firstName" else timeOfDay
    }

    companion object {
        private const val TAG = "TonightViewModel"
    }
}
