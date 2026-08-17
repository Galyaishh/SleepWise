package com.example.sleepwisepoc.report

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.ApiClient
import com.example.sleepwisepoc.SessionRecord
import com.example.sleepwisepoc.WeeklyReport
import java.time.Duration
import java.time.Instant
import com.google.firebase.auth.FirebaseAuth
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
        // The Sleep report shows a fixed 7-night 4-stage history (mock/demo app).
        _state.update { ReportUiState.Loaded(SleepMockData.createReport(), isDemoData = false) }
    }

    /** Wrap the full session list in a WeeklyReport, recomputing the aggregates
     *  the /weekly endpoint used to provide (so the rest of the UI is unchanged). */
    private fun buildReport(userId: String, sessions: List<SessionRecord>): WeeklyReport {
        val fired = sessions.filter { !it.fired_at.isNullOrBlank() }
        val favorable = fired.count { it.fired_reason == "favorable" }
        val fallback = fired.count { it.fired_reason == "fallback" }
        val windows = sessions.mapNotNull { s ->
            runCatching {
                Duration.between(Instant.parse(s.window_start), Instant.parse(s.window_end))
                    .toMinutes().toFloat().coerceAtLeast(0f)
            }.getOrNull()
        }
        val avgWindow = if (windows.isEmpty()) 0f else windows.average().toFloat()
        return WeeklyReport(userId, sessions, fired.size, favorable, fallback, avgWindow)
    }

    companion object {
        private const val TAG = "SleepReportVM"
    }
}
