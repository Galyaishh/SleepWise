package com.example.sleepwisepoc.tonight

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepwisepoc.ApiClient
import com.example.sleepwisepoc.HealthConnectManager
import com.example.sleepwisepoc.SamsungHealthManager
import com.example.sleepwisepoc.SessionUpload
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.TFLiteSleepPredictor
import com.example.sleepwisepoc.db.SleepWiseDatabase
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
import java.time.LocalDate
import java.time.LocalTime

enum class WatchStatus { Checking, Connected, NoRecentData, Disconnected, Emulator }

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

    /** True after the heavy one-shot diagnostic has been run this VM lifetime. */
    private var deepDiagnosticRun = false

    init {
        // First pass on app launch: light status check + heavy diagnostic
        // (retrospective + Samsung comparison + good-run upload). Subsequent
        // refreshes (on Tonight tab re-entry, during tracking) skip the heavy
        // path so we're not doing 500-epoch inference every minute.
        refreshWatchStatus()
        viewModelScope.launch(Dispatchers.IO) {
            if (!deepDiagnosticRun) {
                deepDiagnosticRun = true
                runOneShotDiagnostic()
            }
        }
    }

    val state = combine(store.schedule, _isTracking, _watchStatus, _isAlarmOnly) { schedule, tracking, watch, alarmOnly ->
        // "Tonight's" wake-up. In the small hours (before noon) the next alarm is
        // THIS morning, not tomorrow — otherwise at e.g. 00:50 the card would show
        // the day *after* the coming morning.
        val target = if (LocalTime.now().hour < 12) LocalDate.now() else LocalDate.now().plusDays(1)
        TonightUiState(
            greeting    = greeting(),
            schedule    = schedule.forDate(target),
            isTracking  = tracking,
            isAlarmOnly = alarmOnly,
            watchStatus = watch,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TonightUiState())

    /**
     * Watch-readiness logic:
     *   1. Bluetooth bonded-devices: if no watch-class / "Galaxy" / "Watch" device
     *      is paired, status = Disconnected. ("No watch paired")
     *   2. Samsung Health Data SDK probe (HR + temp + SpO2 in last 1h):
     *        any samples  → Connected      ("Connected")
     *        zero samples → NoRecentData   ("Paired, not syncing")
     *   3. SDK init failure → Disconnected as well (treat as not usable).
     */
    fun refreshWatchStatus() {
        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true)
        if (isEmulator) {
            _watchStatus.value = WatchStatus.Emulator
            return
        }
        // Step 1: BT pairing — definitive "no watch" check, independent of Samsung Health.
        if (!hasPairedWatch(getApplication())) {
            Log.d(TAG, "watch readiness: no bonded watch device → Disconnected")
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
                // Light readiness check: "Connected" = any HR/temp/SpO2 sample
                // synced in the last hour. Cheap; safe to call every minute.
                val hr = mgr.readHeartRate(hoursBack = 1)
                val temp = mgr.readSkinTemperature(hoursBack = 1)
                val spo2 = mgr.readBloodOxygen(hoursBack = 1)
                val recentTotal = hr.size + temp.size + spo2.size
                _watchStatus.value =
                    if (recentTotal > 0) WatchStatus.Connected else WatchStatus.NoRecentData
                val latest = hr.maxOfOrNull { it.timestamp } ?: 0L
                val lagMin = if (latest > 0)
                    (System.currentTimeMillis() - latest) / 60000 else -1
                val latestStr = if (latest > 0) java.text.SimpleDateFormat(
                    "HH:mm:ss", java.util.Locale.getDefault()
                ).format(java.util.Date(latest)) else "—"
                Log.d(TAG, "watch readiness last 1h: HR=${hr.size} temp=${temp.size} " +
                        "spo2=${spo2.size} | latestHR=$latestStr lag=${lagMin}min")
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

    /**
     * One-shot heavy diagnostic that runs once per ViewModel lifetime:
     * full Samsung Health dump, retrospective TFLite inference, comparison
     * against Samsung's stages, and a synthetic "good run" session upload.
     * Kept separate from refreshWatchStatus so the periodic light refresh
     * (every minute during tracking) doesn't re-run a 500-epoch inference.
     */
    private suspend fun runOneShotDiagnostic() {
        // Probe Health Connect alongside Samsung Health Data SDK — Samsung Health
        // writes to Health Connect too, and HC's read path may surface data
        // sooner. If HC's "latest HR" is consistently fresher than SDK's, we
        // should switch the live tracking path to HC.
        runCatching { probeHealthConnectLag() }

        val mgr = SamsungHealthManager(getApplication())
        if (!mgr.initialize()) {
            Log.w(TAG, "DIAG: Samsung Health init failed")
            return
        }
        try {
            runCatching { dumpHourlyHrTimeline(mgr) }
            runCatching { Log.d(TAG, "\n" + mgr.getFormattedHealthData()) }
            runCatching {
                val predictions = runRetrospectiveInference(mgr)
                compareWithSamsungStages(mgr, predictions)
                // NOTE: uploadRetrospectiveAsGoodRun(mgr, predictions) intentionally
                // DISABLED — it injected a synthetic session into the backend on
                // every app open (with a malformed afternoon window → negative
                // "time in bed"). Real sessions come only from a completed
                // monitoring run. Keep the retrospective LOGGING for debugging.
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DIAG failed: ${t.message}", t)
        }
    }

    /**
     * Dump HR sample counts bucketed by clock hour for the last 48h, plus the
     * gap between the earliest sample and the next non-empty bucket. Lets us
     * see whether overnight data arrived as a single batch (one hour fat,
     * others zero) or as continuous trickle (every hour populated).
     *
     * For each hour bucket we log:
     *   [HH:00–HH:59]  count=N  first=tt:tt  last=tt:tt  gapToPrev=Xmin
     */
    private suspend fun dumpHourlyHrTimeline(mgr: SamsungHealthManager) {
        val hoursBack = 168  // 7 days, so historical nights are visible
        val hr = mgr.readHeartRate(hoursBack = hoursBack).map { it.timestamp }
        val temp = mgr.readSkinTemperature(hoursBack = hoursBack).map { it.timestamp }
        val spo2 = mgr.readBloodOxygen(hoursBack = hoursBack).map { it.timestamp }
        val bodyTemp = runCatching { mgr.readBodyTemperature(hoursBack = hoursBack).map { it.timestamp } }
            .getOrDefault(emptyList())

        dumpTimeline("HR", hr, hoursBack)
        dumpTimeline("SKIN_TEMP", temp, hoursBack)
        dumpTimeline("SPO2", spo2, hoursBack)
        dumpTimeline("BODY_TEMP", bodyTemp, hoursBack)
    }

    /**
     * Bucket a list of sample timestamps (ms epoch) by clock-hour and log
     * count/first/last/gap for each non-empty hour.
     */
    private fun dumpTimeline(label: String, tsList: List<Long>, hoursBack: Int) {
        if (tsList.isEmpty()) {
            Log.d(TAG, "${label}_TIMELINE: no samples in last ${hoursBack}h")
            return
        }
        val zone = java.time.ZoneId.systemDefault()
        val byHour = tsList.groupBy { ts ->
            java.time.Instant.ofEpochMilli(ts)
                .atZone(zone)
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        }.toSortedMap()
        val hourFmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:00")
        val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        Log.d(
            TAG,
            "${label}_TIMELINE: ${tsList.size} samples across ${byHour.size} hour-buckets (${hoursBack}h window)"
        )
        var prevHourLast: java.time.ZonedDateTime? = null
        byHour.forEach { (hourStart, samples) ->
            val first = java.time.Instant.ofEpochMilli(samples.min()).atZone(zone)
            val last = java.time.Instant.ofEpochMilli(samples.max()).atZone(zone)
            val gapToPrevMin = prevHourLast?.let {
                java.time.Duration.between(it, first).toMinutes()
            } ?: -1
            Log.d(
                TAG,
                "${label}_TIMELINE [${hourStart.format(hourFmt)}] count=${samples.size} " +
                        "first=${first.format(timeFmt)} last=${last.format(timeFmt)} " +
                        "gapToPrev=${gapToPrevMin}min"
            )
            prevHourLast = last
        }
    }

    /** Head-to-head latency probe: log the latest HR sample timestamps from
     *  Samsung Health Data SDK and Health Connect, plus the wall-clock lag for each. */
    private suspend fun probeHealthConnectLag() {
        val ctx = getApplication<Application>()
        if (!HealthConnectManager.isAvailable(ctx)) {
            Log.d(TAG, "HC: not available on this device")
            return
        }
        val hc = HealthConnectManager(ctx)
        val granted = try { hc.hasAllPermissions() } catch (t: Throwable) {
            Log.w(TAG, "HC: permission check failed: ${t.message}"); false
        }
        if (!granted) {
            Log.d(TAG, "HC: permissions NOT granted — cannot compare lag yet")
            return
        }
        try {
            val hcHr = hc.readHeartRate(hoursBack = 24)
            val hcLatest = hcHr.maxOfOrNull { it.timestamp.toEpochMilli() } ?: 0L
            val hcLagMin = if (hcLatest > 0)
                (System.currentTimeMillis() - hcLatest) / 60000 else -1
            // For comparison, Samsung Health SDK side:
            val sdkMgr = SamsungHealthManager(ctx)
            sdkMgr.initialize()
            val sdkHr = sdkMgr.readHeartRate(hoursBack = 24)
            val sdkLatest = sdkHr.maxOfOrNull { it.timestamp } ?: 0L
            val sdkLagMin = if (sdkLatest > 0)
                (System.currentTimeMillis() - sdkLatest) / 60000 else -1
            Log.d(
                TAG,
                "HC vs SDK lag: HC=${hcLagMin}min (${hcHr.size} samples) | " +
                        "SDK=${sdkLagMin}min (${sdkHr.size} samples) | " +
                        "winner=${if (hcLagMin in 0..sdkLagMin) "HC" else "SDK"}"
            )
            // Bonus: HRV via Health Connect, if Samsung Health forwards it.
            runCatching {
                val hrv = hc.readHRV(hoursBack = 24)
                Log.d(TAG, "HC HRV samples (24h): ${hrv.size}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "HC: probe failed: ${t.message}")
        }
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

    /**
     * True iff a watch-class Bluetooth device is currently bonded to this phone.
     * Uses bondedDevices (not a scan) so it's instant and doesn't need
     * BLUETOOTH_SCAN. Requires BLUETOOTH_CONNECT on API 31+.
     *
     * Detection: bonded device whose major class is WEARABLE, OR whose name
     * contains "Galaxy" / "Watch" (covers Samsung's wearables that sometimes
     * advertise as PHONE class with a Galaxy Watch name).
     */
    private fun hasPairedWatch(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            // No permission yet — fall through to data probe rather than block.
            Log.d(TAG, "hasPairedWatch: BLUETOOTH_CONNECT not granted, assuming paired")
            return true
        }
        val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return true
        val adapter = mgr.adapter ?: return false
        if (!adapter.isEnabled) {
            Log.d(TAG, "hasPairedWatch: bluetooth adapter disabled")
            return false
        }
        return try {
            val bonded = adapter.bondedDevices ?: emptySet()
            val match = bonded.firstOrNull { device ->
                val majorClass = device.bluetoothClass?.majorDeviceClass
                val name = device.name.orEmpty()
                majorClass == BluetoothClass.Device.Major.WEARABLE ||
                        name.contains("Galaxy", ignoreCase = true) ||
                        name.contains("Watch", ignoreCase = true)
            }
            if (match != null) {
                Log.d(TAG, "hasPairedWatch: matched ${match.name}")
                true
            } else {
                Log.d(TAG, "hasPairedWatch: ${bonded.size} bonded devices, no watch")
                false
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "hasPairedWatch: SecurityException, assuming paired", e)
            true
        }
    }

    companion object {
        private const val TAG = "TonightViewModel"
    }
}
