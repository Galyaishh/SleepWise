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

        var epochIndex = 0
        val totalEpochs = (Duration.between(start, end).toMinutes()).coerceAtLeast(10).toInt()

        while (scope.isActive && !alarmFired) {
            val now = System.currentTimeMillis()
            val insideWindow = now in startEpoch..endEpoch
            val pastWindow = now > endEpoch

            if (pastWindow) {
                SessionLog.log(this, "PAST_WINDOW now=${java.util.Date(now)} > endEpoch — firing fallback alarm")
                AlarmScheduler.scheduleAt(this, now + 500)
                alarmFired = true
                update("Wake-up window ended — alarm firing")
                withContext(NonCancellable + Dispatchers.IO) {
                    uploadSession(firedReason = "fallback", firedAt = Instant.now())
                }
                break
            }

            // Build & feed an epoch into the predictor
            val pred = predictor?.let { p ->
                val epoch = acquireEpoch(p, epochIndex, totalEpochs)
                p.addEpoch(epoch)
                if (p.canPredict()) p.predict() else null
            }
            epochIndex++

            if (pred != null) {
                tickHistory += StageTick(
                    t = Instant.now().toString(),
                    stage = pred.sleepStage,
                    conf = pred.confidence,
                    stable = pred.isStable,
                )
                SessionLog.log(
                    this,
                    "tick #$epochIndex now=${java.util.Date(now)} stage=${pred.sleepStage} " +
                            "conf=${"%.2f".format(pred.confidence)} stable=${pred.isStable} " +
                            "insideWindow=$insideWindow"
                )

                val favorable = insideWindow &&
                        pred.sleepStage.equals("Light", ignoreCase = true) &&
                        pred.isStable

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
            } else {
                SessionLog.log(this, "tick #$epochIndex buffer=${predictor?.getBufferSize()} (warming up)")
            }

            delay(tickMs)
        }

        SessionLog.log(this, "loop finished (alarmFired=$alarmFired) — stopping service")
        stopSelf()
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

    // ─── Epoch acquisition ────────────────────────────────────────────────────

    /**
     * Returns the best available epoch for the current tick.
     *
     * Priority:
     *   1. Real HR data from Samsung Health (last 1 h, latest complete epoch)
     *      — requires Samsung Health app + permissions + watch connected
     *   2. Mock epoch — guaranteed fallback so the alarm always works
     */
    private suspend fun acquireEpoch(
        predictor: TFLiteSleepPredictor,
        epochIndex: Int,
        totalEpochs: Int,
    ): TFLiteSleepPredictor.EpochFeatures {
        // Priority 1: real-time HR from the wear companion. If we have ≥3 HR
        // samples in the last 60s and the freshest sample is <2 min old, build
        // an epoch from those samples instead of touching Samsung Health.
        val wearLagMs = WearHrSource.lagMillis()
        if (wearLagMs in 0..120_000) {
            val recent = WearHrSource.recentHr(minutesBack = 2)
                .filter { it.timestamp >= System.currentTimeMillis() - 60_000 }
            if (recent.size >= MIN_HR_SAMPLES) {
                val features = featuresFromWearSamples(recent)
                SessionLog.log(this, "acquireEpoch WEAR_LIVE: " +
                        "hrMean=${features.hrMean.toInt()}bpm hrSamples=${recent.size} " +
                        "lag=${wearLagMs / 1000}s")
                return features
            }
        }

        val manager = healthManager
        if (manager != null) {
            try {
                val epochs = manager.processDataIntoEpochs(hoursBack = 1)
                val latest = epochs.lastOrNull()
                // Probe all three sensor streams in parallel so we can tell if they
                // share a single overnight batch or sync on different schedules.
                val hr1h = try { manager.readHeartRate(hoursBack = 1) } catch (_: Throwable) { emptyList() }
                val temp1h = try { manager.readSkinTemperature(hoursBack = 1) } catch (_: Throwable) { emptyList() }
                val spo21h = try { manager.readBloodOxygen(hoursBack = 1) } catch (_: Throwable) { emptyList() }
                val latestHrTs = hr1h.maxOfOrNull { it.timestamp } ?: 0L
                val latestTempTs = temp1h.maxOfOrNull { it.timestamp } ?: 0L
                val latestSpo2Ts = spo21h.maxOfOrNull { it.timestamp } ?: 0L
                val nowMs = System.currentTimeMillis()
                fun lag(ts: Long) = if (ts > 0) (nowMs - ts) / 60000 else -1L
                SessionLog.log(this, "DATA_SNAPSHOT last_1h: " +
                        "HR=${hr1h.size}(latest=${if (latestHrTs > 0) java.util.Date(latestHrTs) else "—"} lag=${lag(latestHrTs)}min) " +
                        "TEMP=${temp1h.size}(latest=${if (latestTempTs > 0) java.util.Date(latestTempTs) else "—"} lag=${lag(latestTempTs)}min) " +
                        "SPO2=${spo21h.size}(latest=${if (latestSpo2Ts > 0) java.util.Date(latestSpo2Ts) else "—"} lag=${lag(latestSpo2Ts)}min)")
                if (latest != null && latest.hrSampleCount >= MIN_HR_SAMPLES) {
                    SessionLog.log(this, "acquireEpoch REAL: " +
                            "epoch=${latest.timeString} hrMean=${latest.hrMean.toInt()}bpm " +
                            "hrSamples=${latest.hrSampleCount} tempSamples=${latest.tempSampleCount}")
                    return manager.epochToFeatures(latest)
                }
                SessionLog.log(this, "acquireEpoch MOCK_FALLBACK: real data insufficient " +
                        "(${latest?.hrSampleCount ?: 0} samples in latest epoch)")
            } catch (e: Exception) {
                SessionLog.log(this, "acquireEpoch MOCK_FALLBACK: health read threw (${e.message})")
            }
        }
        SessionLog.log(this, "acquireEpoch MOCK: epoch #$epochIndex (no health manager)")
        return predictor.createMockEpoch("light", epochIndex, totalEpochs)
    }

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
