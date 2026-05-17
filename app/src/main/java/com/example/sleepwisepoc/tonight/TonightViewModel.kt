package com.example.sleepwisepoc.tonight

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.ApiClient
import com.example.sleepwisepoc.SamsungHealthManager
import com.example.sleepwisepoc.SessionUpload
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.TFLiteSleepPredictor
import com.example.sleepwisepoc.schedule.DaySchedule
import java.time.Instant
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
                // First, snapshot the freshest HR timestamp we currently see —
                // then fire the sync-broadcast hack, wait a bit, and re-snapshot
                // so we can measure whether the broadcasts actually reduced lag.
                val beforeHr = mgr.readHeartRate(hoursBack = 24)
                val beforeLatest = beforeHr.maxOfOrNull { it.timestamp } ?: 0L
                val beforeLagMin = if (beforeLatest > 0)
                    (System.currentTimeMillis() - beforeLatest) / 60000 else -1
                Log.d(TAG, "LAG before sync-broadcast: ${beforeLagMin}min (latest=${java.util.Date(beforeLatest)})")
                mgr.triggerSamsungHealthSync()
                kotlinx.coroutines.delay(3000)
                val afterHr = mgr.readHeartRate(hoursBack = 24)
                val afterLatest = afterHr.maxOfOrNull { it.timestamp } ?: 0L
                val afterLagMin = if (afterLatest > 0)
                    (System.currentTimeMillis() - afterLatest) / 60000 else -1
                Log.d(TAG, "LAG after  sync-broadcast: ${afterLagMin}min (latest=${java.util.Date(afterLatest)})")
                Log.d(TAG, "LAG delta: ${beforeLagMin - afterLagMin}min " +
                        "(new samples=${afterHr.size - beforeHr.size})")

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
                // Retrospective: feed last 24h of real epochs through the TFLite
                // model so we can see what stage trajectory it would have predicted,
                // then compare against Samsung's own stages and upload a synthetic
                // "good run" session to the backend.
                runCatching {
                    val predictions = runRetrospectiveInference(mgr)
                    compareWithSamsungStages(mgr, predictions)
                    uploadRetrospectiveAsGoodRun(mgr, predictions)
                }
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

    /** A single model prediction tagged with its epoch start time (ms). */
    private data class TimedPrediction(
        val timestamp: Long,
        val stage: String,
        val confidence: Float,
        val stable: Boolean,
    )

    /**
     * Feed the last 24h of real epochs through a fresh TFLitePredictor and
     * return the resulting trajectory. Used both for diagnostics (logged here)
     * and for the Samsung comparison + good-run upload that follow.
     */
    private suspend fun runRetrospectiveInference(mgr: SamsungHealthManager): List<TimedPrediction> {
        val predictor = TFLiteSleepPredictor(getApplication())
        if (!predictor.initialize()) {
            Log.w(TAG, "RETRO: predictor init failed")
            return emptyList()
        }
        val out = mutableListOf<TimedPrediction>()
        try {
            val epochs = mgr.processDataIntoEpochs(hoursBack = 24)
            Log.d(TAG, "RETRO: processing ${epochs.size} epochs through model")
            epochs.forEach { epoch ->
                val features = mgr.epochToFeatures(epoch)
                predictor.addEpoch(features)
                if (predictor.canPredict()) {
                    val pred = predictor.predict()
                    if (pred != null) {
                        out.add(
                            TimedPrediction(
                                timestamp = epoch.timestamp,
                                stage = pred.sleepStage,
                                confidence = pred.confidence,
                                stable = pred.isStable,
                            )
                        )
                    }
                }
            }
            val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val rendered = out.map { p ->
                val flag = if (p.stable) "*" else " "
                "${fmt.format(java.util.Date(p.timestamp))} $flag${p.stage.first()}${(p.confidence * 100).toInt()}"
            }
            Log.d(TAG, "RETRO: produced ${out.size} predictions (* = stable)")
            rendered.chunked(15).forEachIndexed { idx, chunk ->
                Log.d(TAG, "RETRO[${idx * 15}]: ${chunk.joinToString(" | ")}")
            }
            val deep = out.count { it.stage.equals("Deep", true) }
            val light = out.count { it.stage.equals("Light", true) }
            val stable = out.count { it.stable }
            Log.d(TAG, "RETRO summary: Deep=$deep | Light=$light | Stable=$stable / ${out.size}")
        } catch (t: Throwable) {
            Log.w(TAG, "RETRO failed: ${t.message}", t)
        } finally {
            runCatching { predictor.close() }
        }
        return out
    }

    /**
     * For each minute the model produced a prediction during Samsung's detected
     * sleep period, find what Samsung's own stage classifier said for that minute
     * and report per-minute agreement.
     *
     * Samsung's 4-way enum (AWAKE/LIGHT/DEEP/REM) is collapsed to our binary
     * (Light = AWAKE+LIGHT+REM, Deep = DEEP) since that's how the model was trained.
     */
    private suspend fun compareWithSamsungStages(
        mgr: SamsungHealthManager,
        predictions: List<TimedPrediction>,
    ) {
        if (predictions.isEmpty()) return
        val sessions = mgr.readSleep(daysBack = 2).filter { it.stages.isNotEmpty() }
        if (sessions.isEmpty()) {
            Log.d(TAG, "CMP: no Samsung sleep sessions with stages — skipping comparison")
            return
        }
        val session = sessions.first()
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        Log.d(
            TAG,
            "CMP: Samsung sleep ${fmt.format(java.util.Date(session.startTime))}—" +
                    "${fmt.format(java.util.Date(session.endTime))} with ${session.stages.size} stage segments"
        )
        // Stage breakdown from Samsung
        val breakdown = session.stages.groupBy { it.stage }
            .mapValues { (_, list) -> list.sumOf { (it.endTime - it.startTime) / 60000L } }
        Log.d(TAG, "CMP: Samsung stage minutes: $breakdown")

        // For each model prediction inside the sleep session, find Samsung's stage.
        fun samsungStageAt(ts: Long): String? =
            session.stages.firstOrNull { ts in it.startTime until it.endTime }?.stage

        fun ourBinary(stage: String): String = if (stage.equals("Deep", true)) "DEEP" else "LIGHT"
        fun samsungBinary(stage: String): String = if (stage.uppercase() == "DEEP") "DEEP" else "LIGHT"

        var agree = 0; var disagree = 0
        var samsungDeep = 0; var samsungLight = 0
        val confMatrix = mutableMapOf<String, Int>().withDefault { 0 }
        predictions.forEach { p ->
            val s = samsungStageAt(p.timestamp) ?: return@forEach
            val ours = ourBinary(p.stage)
            val theirs = samsungBinary(s)
            if (ours == theirs) agree++ else disagree++
            if (theirs == "DEEP") samsungDeep++ else samsungLight++
            val key = "samsung=$theirs ours=$ours"
            confMatrix[key] = confMatrix.getValue(key) + 1
        }
        val total = agree + disagree
        if (total == 0) {
            Log.d(TAG, "CMP: no overlap between model predictions and Samsung's sleep window")
            return
        }
        val pct = 100.0 * agree / total
        Log.d(TAG, "CMP: agreement ${"%.1f".format(pct)}% ($agree/$total) | samsungDeep=$samsungDeep samsungLight=$samsungLight")
        Log.d(TAG, "CMP: confusion: $confMatrix")
    }

    /**
     * Build a synthetic "good run" session from the retrospective trajectory
     * and POST it to the backend, so the Sleep tab has a real-looking record
     * to show. We pick the first stable Light moment within a hypothetical
     * 06:00–07:00 wake window as the favorable fire time.
     */
    private suspend fun uploadRetrospectiveAsGoodRun(
        mgr: SamsungHealthManager,
        predictions: List<TimedPrediction>,
    ) {
        if (predictions.isEmpty()) return
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "UPLOAD: no Firebase user — skipping")
            return
        }
        // Hypothetical window: 06:00–07:00 today.
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.now()
        val winStart = today.atTime(6, 0).atZone(zone).toInstant()
        val winEnd = today.atTime(7, 0).atZone(zone).toInstant()
        // First favorable moment = first stable Light in window.
        val favorable = predictions
            .firstOrNull { it.stable && it.stage.equals("Light", true) &&
                    it.timestamp in winStart.toEpochMilli() until winEnd.toEpochMilli() }
        val firedReason: String
        val firedAt: Instant
        if (favorable != null) {
            firedReason = "favorable"
            firedAt = Instant.ofEpochMilli(favorable.timestamp)
        } else {
            firedReason = "fallback"
            firedAt = winEnd
        }
        // Trim trajectory to the sleep period for the upload.
        val startedAt = predictions.first().timestamp
        val ticks = predictions.map { p ->
            StageTick(
                t = Instant.ofEpochMilli(p.timestamp).toString(),
                stage = p.stage,
                conf = p.confidence,
                stable = p.stable,
            )
        }
        val payload = SessionUpload(
            user_id = uid,
            window_start = winStart.toString(),
            window_end = winEnd.toString(),
            started_at = Instant.ofEpochMilli(startedAt).toString(),
            ended_at = firedAt.toString(),
            fired_at = firedAt.toString(),
            fired_reason = firedReason,
            stages = ticks,
        )
        try {
            val saved = ApiClient.api.uploadSession(payload)
            Log.d(
                TAG,
                "UPLOAD: session id=${saved.id} reason=$firedReason " +
                        "ticks=${ticks.size} firedAt=${firedAt}"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "UPLOAD failed: ${t.message}")
        }
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
