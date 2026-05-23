package com.example.sleepwisepoc.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sleepwisepoc.ApiClient
import com.example.sleepwisepoc.MainActivity
import com.example.sleepwisepoc.SamsungHealthManager
import com.example.sleepwisepoc.wear.WearCommand
import com.example.sleepwisepoc.wear.WearHrSource
import com.google.firebase.auth.FirebaseAuth
import com.example.sleepwisepoc.SessionUpload
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.TFLiteSleepPredictor
import com.example.sleepwisepoc.alarm.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Foreground service that monitors sleep stages during the user's wake-up window
 * and fires the smart alarm at the most favorable moment (Light sleep) within it.
 *
 * On emulator / debug: drives the predictor with mock epochs from
 * [TFLiteSleepPredictor.createMockEpoch] and ticks every [TICK_SEC_DEMO] seconds
 * so a window can play out in under a minute.
 *
 * Real-device path (HealthConnect → 30-feature epoch) is a TODO — the abstraction
 * boundary is [acquireEpoch].
 */
class SleepMonitoringService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loop: Job? = null
    private var predictor: TFLiteSleepPredictor? = null
    private var healthManager: SamsungHealthManager? = null
    private var alarmFired = false

    private val tickHistory = mutableListOf<StageTick>()
    private var sessionStartedAt: Instant? = null
    private var sessionWindowStart: LocalTime? = null
    private var sessionWindowEnd: LocalTime? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FGS_ID,
                buildNotification("Preparing to monitor your sleep…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(FGS_ID, buildNotification("Preparing to monitor your sleep…"))
        }
        predictor = TFLiteSleepPredictor(this).also {
            val ok = it.initialize()
            Log.d(TAG, "TFLite predictor initialized=$ok")
        }
        healthManager = SamsungHealthManager(this).also {
            val ok = it.initialize()
            Log.d(TAG, "SamsungHealthManager initialized=$ok")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startMin = intent?.getIntExtra(EXTRA_START_MIN, -1) ?: -1
        val endMin = intent?.getIntExtra(EXTRA_END_MIN, -1) ?: -1
        if (startMin < 0 || endMin < 0) {
            Log.w(TAG, "missing window extras — stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        val start = LocalTime.of(startMin / 60, startMin % 60)
        val end = LocalTime.of(endMin / 60, endMin % 60)
        SessionLog.reset(this)
        SessionLog.log(this, "onStartCommand window=$start..$end")

        loop?.cancel()
        sessionStartedAt = Instant.now()
        sessionWindowStart = start
        sessionWindowEnd = end
        tickHistory.clear()
        // Tell the watch companion to start streaming real-time HR.
        scope.launch {
            val ok = WearCommand.startStreaming(this@SleepMonitoringService)
            SessionLog.log(this@SleepMonitoringService, "WEAR_START sent=$ok")
        }
        loop = scope.launch { runLoop(start, end) }
        return START_REDELIVER_INTENT
    }

    private suspend fun runLoop(start: LocalTime, end: LocalTime) {
        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("sdk", ignoreCase = true)
        val tickMs = (if (isEmulator) TICK_SEC_DEMO else TICK_SEC_REAL) * 1000L
        SessionLog.log(this, "loop tick=${tickMs}ms isEmulator=$isEmulator")
        if (isEmulator) healthManager = null  // Samsung Health not present on emulator

        // Pick the calendar date the window belongs to. If the start time is
        // already in the past for today (e.g. user starts tracking at 22:30 for
        // a 06:30 wake window), the window is on tomorrow's date.
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val windowDate = if (start.isBefore(LocalTime.now())) today.plusDays(1) else today
        val startEpoch = windowDate.atTime(start).atZone(zone).toEpochSecond() * 1000
        val endEpoch = windowDate.atTime(end).atZone(zone).toEpochSecond() * 1000
        SessionLog.log(this, "window resolved: $windowDate $start..$end " +
                "(startEpoch=${java.util.Date(startEpoch)} endEpoch=${java.util.Date(endEpoch)})")

        // Pre-schedule a hard fallback at window end via AlarmManager. This
        // alarm survives the service being killed by the OS overnight (Doze,
        // Samsung battery optimization, OOM). If the loop later detects a
        // favorable Light moment, it re-schedules the same PendingIntent for
        // earlier — AlarmManager replaces the old one.
        if (System.currentTimeMillis() < endEpoch) {
            AlarmScheduler.scheduleAt(this, endEpoch)
            SessionLog.log(this, "fallback alarm pre-scheduled for ${java.util.Date(endEpoch)}")
        } else {
            SessionLog.log(this, "WARN: endEpoch already in the past — NOT pre-scheduling fallback alarm")
        }

        update("Watching for the perfect moment to wake you")

        // We never produce a prediction from synthetic data — only from real
        // HR samples. The predictor's state advances monotonically with the
        // sample timestamps we feed in, not with wall-clock ticks. When a
        // burst arrives covering N minutes, we build N sequential 1-min
        // epochs from it and let the predictor walk through them in order.
        var lastProcessedEpochEndMs = 0L
        var tickNum = 0

        while (scope.isActive && !alarmFired) {
            tickNum++
            val now = System.currentTimeMillis()
            if (now > endEpoch) {
                SessionLog.log(this, "PAST_WINDOW now=${java.util.Date(now)} > endEpoch — firing fallback alarm")
                AlarmScheduler.scheduleAt(this, now + 500)
                alarmFired = true
                update("Wake-up window ended — alarm firing")
                withContext(NonCancellable + Dispatchers.IO) {
                    uploadSession(firedReason = "fallback", firedAt = Instant.now())
                }
                break
            }

            val newEpochs = buildNewEpochsFromRealData(lastProcessedEpochEndMs)
            if (newEpochs.isEmpty()) {
                SessionLog.log(this, "tick #$tickNum no new real epochs since " +
                        "${if (lastProcessedEpochEndMs > 0) java.util.Date(lastProcessedEpochEndMs) else "service-start"} — skipping")
                delay(tickMs)
                continue
            }

            // Feed each new epoch to the predictor in chronological order so the
            // EMA + hysteresis + 3-consecutive-stable smoothing walks through
            // the trajectory naturally. Last prediction is what we act on.
            var lastPred: TFLiteSleepPredictor.SleepPrediction? = null
            newEpochs.forEach { epoch ->
                predictor?.addEpoch(epoch.features)
                if (predictor?.canPredict() == true) {
                    lastPred = predictor?.predict()
                    if (lastPred != null) {
                        tickHistory += StageTick(
                            t = Instant.ofEpochMilli(epoch.endTimeMs).toString(),
                            stage = lastPred!!.sleepStage,
                            conf = lastPred!!.confidence,
                            stable = lastPred!!.isStable,
                        )
                    }
                }
                lastProcessedEpochEndMs = epoch.endTimeMs
            }

            if (lastPred == null) {
                SessionLog.log(this, "tick #$tickNum processed ${newEpochs.size} epochs, " +
                        "predictor still warming up (buffer=${predictor?.getBufferSize()})")
                delay(tickMs)
                continue
            }

            val latestEpochAgeMs = now - lastProcessedEpochEndMs
            val insideWindow = lastProcessedEpochEndMs in startEpoch..endEpoch
            val fresh = latestEpochAgeMs <= MAX_DATA_AGE_FOR_DECISION_MS
            SessionLog.log(
                this,
                "tick #$tickNum processed=${newEpochs.size} latestEpoch=${java.util.Date(lastProcessedEpochEndMs)} " +
                        "age=${latestEpochAgeMs / 60000}min stage=${lastPred!!.sleepStage} " +
                        "conf=${"%.2f".format(lastPred!!.confidence)} stable=${lastPred!!.isStable} " +
                        "insideWindow=$insideWindow fresh=$fresh"
            )

            val favorable = insideWindow && fresh &&
                    lastPred!!.sleepStage.equals("Light", ignoreCase = true) &&
                    lastPred!!.isStable
            if (favorable) {
                SessionLog.log(this, "FAVORABLE moment detected — firing alarm now")
                AlarmScheduler.scheduleAt(this, now + 500)
                alarmFired = true
                update("Light sleep detected — gently waking you")
                withContext(NonCancellable + Dispatchers.IO) {
                    uploadSession(firedReason = "favorable", firedAt = Instant.now())
                }
                break
            }

            delay(tickMs)
        }

        SessionLog.log(this, "loop finished (alarmFired=$alarmFired) — stopping service")
        stopSelf()
    }

    /** Cohort of HR samples bucketed into a 1-minute epoch, with predictor-ready features. */
    private data class RealEpoch(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val features: TFLiteSleepPredictor.EpochFeatures,
        val hrSampleCount: Int,
    )

    /**
     * Pull fresh HR samples from every available source (wear stream is the
     * primary; Samsung Health is the fallback for nights where the wear
     * companion isn't installed), bucket them into 1-minute epochs strictly
     * AFTER [sinceMs], compute features, return chronologically.
     */
    private suspend fun buildNewEpochsFromRealData(sinceMs: Long): List<RealEpoch> {
        // Pull a generous backlog so a 5-6 min burst is fully covered.
        val wearSamples = WearHrSource.recentHr(minutesBack = 15)
        val healthSamples = try {
            healthManager?.readHeartRate(hoursBack = 1) ?: emptyList()
        } catch (_: Throwable) { emptyList() }
        // Dedup by second-truncated timestamp; wear samples come first so
        // they win ties (they're the real-time source).
        val merged = (wearSamples + healthSamples)
            .filter { it.timestamp > sinceMs && it.bpm > 0 }
            .distinctBy { it.timestamp / 1000 }
            .sortedBy { it.timestamp }
        if (merged.isEmpty()) return emptyList()

        val out = mutableListOf<RealEpoch>()
        val epochSizeMs = SamsungHealthManager.EPOCH_DURATION_MS
        var epochStart = (merged.first().timestamp / epochSizeMs) * epochSizeMs
        while (epochStart <= merged.last().timestamp) {
            val epochEnd = epochStart + epochSizeMs
            val epochSamples = merged.filter { it.timestamp in epochStart until epochEnd }
            if (epochSamples.size >= MIN_HR_SAMPLES && epochEnd > sinceMs) {
                out.add(
                    RealEpoch(
                        startTimeMs = epochStart,
                        endTimeMs = epochEnd,
                        features = featuresFromWearSamples(epochSamples),
                        hrSampleCount = epochSamples.size,
                    )
                )
            }
            epochStart = epochEnd
        }
        return out
    }

    override fun onDestroy() {
        SessionLog.log(this, "onDestroy (alarmFired=$alarmFired)")
        // Tell the watch companion to stop streaming — runs synchronously on a
        // throwaway scope so it actually completes before the process winds down.
        runCatching {
            kotlinx.coroutines.runBlocking {
                WearCommand.stopStreaming(this@SleepMonitoringService)
            }
        }
        loop?.cancel()
        scope.cancel()
        predictor?.close()
        super.onDestroy()
    }

    // ─── Epoch feature computation ────────────────────────────────────────────

    /**
     * Build the 12 epoch features (9 HR stats + 3 temp stats) directly from a
     * list of live HR samples received from the wear companion. Skin temp isn't
     * available in real time from Wear OS Health Services, so we use the same
     * 34 °C constant fallback as SamsungHealthManager.processDataIntoEpochs.
     */
    private fun featuresFromWearSamples(
        samples: List<SamsungHealthManager.HeartRateSample>,
    ): TFLiteSleepPredictor.EpochFeatures {
        val bpm = samples.map { it.bpm.toFloat() }.sorted()
        val n = bpm.size
        val mean = bpm.average().toFloat()
        val variance = bpm.map { val d = it - mean; d * d }.average().toFloat()
        val std = kotlin.math.sqrt(variance.toDouble()).toFloat()
        val min = bpm.first()
        val max = bpm.last()
        val median = if (n % 2 == 1) bpm[n / 2] else (bpm[n / 2 - 1] + bpm[n / 2]) / 2f
        val q1 = bpm[(n * 0.25).toInt().coerceAtMost(n - 1)]
        val q3 = bpm[(n * 0.75).toInt().coerceAtMost(n - 1)]
        val iqr = q3 - q1
        val skew = if (std > 0f) {
            val m3 = bpm.map {
                val d = (it - mean) / std
                d * d * d
            }.average().toFloat()
            m3
        } else 0f
        val cv = if (mean > 0f) std / mean * 100f else 0f
        return TFLiteSleepPredictor.EpochFeatures(
            hrMean = mean,
            hrStd = std,
            hrMin = min,
            hrMax = max,
            hrRange = max - min,
            hrCv = cv,
            hrMedian = median,
            hrIqr = iqr,
            hrSkew = skew,
            tempMean = 34.0f,       // skin temp not available in real time
            tempStd = 0.0f,
            tempTrend = 0.0f,
        )
    }

    // ─── Upload ───────────────────────────────────────────────────────────────

    private suspend fun uploadSession(firedReason: String, firedAt: Instant) {
        val started = sessionStartedAt ?: return
        val winStart = sessionWindowStart ?: return
        val winEnd = sessionWindowEnd ?: return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val payload = SessionUpload(
            user_id = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown",
            window_start = today.atTime(winStart).atZone(zone).toInstant().toString(),
            window_end = today.atTime(winEnd).atZone(zone).toInstant().toString(),
            started_at = started.toString(),
            ended_at = Instant.now().toString(),
            fired_at = firedAt.toString(),
            fired_reason = firedReason,
            stages = tickHistory.toList(),
        )

        try {
            val saved = ApiClient.api.uploadSession(payload)
            Log.d(TAG, "session uploaded id=${saved.id} ticks=${tickHistory.size} reason=$firedReason")
        } catch (t: Throwable) {
            Log.w(TAG, "session upload failed: ${t.message}")
        }
    }

    // ─── Notification plumbing ────────────────────────────────────────────────

    private fun update(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FGS_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): android.app.Notification {
        val tap = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("SleepWise")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(tap)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Sleep Monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent notification while monitoring your sleep stage"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val TAG = "SleepMonitoring"
        private const val CHANNEL_ID = "sleep_monitoring"
        private const val FGS_ID = 2001
        private const val EXTRA_START_MIN = "extra_start_min"
        private const val EXTRA_END_MIN = "extra_end_min"
        private const val TICK_SEC_REAL = 60L
        private const val TICK_SEC_DEMO = 10L
        private const val MIN_HR_SAMPLES = 3   // minimum HR samples in an epoch to trust it
        // How old the latest real epoch can be while still firing the alarm.
        // Sleep cycles change on the ~10-20 min scale, so a Light prediction
        // built from data <10 min old is still acting on a defensible read of
        // the user's current state.
        private const val MAX_DATA_AGE_FOR_DECISION_MS = 10L * 60 * 1000

        fun start(context: Context, start: LocalTime, end: LocalTime) {
            val intent = Intent(context, SleepMonitoringService::class.java).apply {
                putExtra(EXTRA_START_MIN, start.hour * 60 + start.minute)
                putExtra(EXTRA_END_MIN, end.hour * 60 + end.minute)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SleepMonitoringService::class.java))
        }
    }
}
