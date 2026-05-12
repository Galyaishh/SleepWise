package com.example.sleepwisepoc.report

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.ApiClient
import com.example.sleepwisepoc.DeviceStore
import com.example.sleepwisepoc.WeeklyReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReportUiState {
    data object Loading : ReportUiState
    data class Empty(val userId: String) : ReportUiState
    data class Loaded(val report: WeeklyReport, val isDemoData: Boolean = false) : ReportUiState
    data class Error(val message: String) : ReportUiState
}

class SleepReportViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ReportUiState>(ReportUiState.Loading)
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { ReportUiState.Loading }
        viewModelScope.launch {
            try {
                val userId = DeviceStore(getApplication()).load()?.first
                if (userId == null) {
                    Log.w(TAG, "no device registered yet — showing demo data")
                    _state.update { ReportUiState.Loaded(SleepMockData.createReport(), isDemoData = true) }
                    return@launch
                }
                Log.d(TAG, "fetching weekly for user_id=$userId")
                val report = ApiClient.api.weeklyReport(userId)
                _state.update {
                    if (report.sessions.isEmpty()) ReportUiState.Empty(userId)
                    else ReportUiState.Loaded(report)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetch failed: ${t.message} — falling back to demo data")
                _state.update { ReportUiState.Loaded(SleepMockData.createReport(), isDemoData = true) }
            }
        }
    }

    companion object {
        private const val TAG = "SleepReportVM"
    }
}
