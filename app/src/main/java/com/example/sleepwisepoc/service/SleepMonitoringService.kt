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
import com.example.sleepwisepoc.wear.WearAccelSource
import com.example.sleepwisepoc.wear.WearCommand
import com.example.sleepwisepoc.wear.WearHrSource
import com.google.firebase.auth.FirebaseAuth
import com.example.sleepwisepoc.SessionUpload
import com.example.sleepwisepoc.StageTick
import com.example.sleepwisepoc.TFLiteSleepPredictor
import com.example.sleepwisepoc.alarm.AlarmScheduler
import com.example.sleepwisepoc.alarm.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var predictor: TFLiteSleepPredictor? = null
    private var healthManager: SamsungHealthManager? = null
    @Volatile private var alarmFired = false

    // Prediction is now EVENT-DRIVEN, not timer-driven. A coroutine delay() loop
    // is frozen by Doze overnight (proven on the 2026-05-24 run: service alive
    // 12h31m but zero predictions). Instead, a prediction pass runs whenever:
    //   1. a wear HR batch arrives (WearMessageListener → onWearDataArrived) —
    //      OS-pushed delivery pierces Doze, and
    //   2. a Doze-proof AlarmManager TICK fires (setExactAndAllowWhileIdle).
    // Passes serialize through this mutex so concurrent triggers don't race the
    // (non-thread-safe) predictor or the lastProcessedEpochEndMs watermark.
    private val passMutex = Mutex()
    @Volatile private var windowStartEpochMs = 0L
    @Volatile private var windowEndEpochMs = 0L
    @Volatile private var lastProcessedEpochEndMs = 0L
    private var passCount = 0

    private val tickHistory = mutableListOf<StageTick>()
    private var sessionStartedAt: Instant? = null
    private var sessionWindowStart: LocalTime? = null
    private var sessionWindowEnd: LocalTime? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        instance = this
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
        // Two entry points: a fresh session start (carries window extras) or a
        // Doze-proof backstop TICK (action ACTION_TICK, no extras).
        if (intent?.action == AlarmReceiver.ACTION_TICK) {
            SessionLog.log(this, "onStartCommand TICK (backstop)")
            scope.launch { runPredictionPass(source = "tick") }
            scheduleBackstopTick()
            return START_STICKY
        }

        val startMin = intent?.getIntExtra(EXTRA_START_MIN, -1) ?: -1
        val endMin = intent?.getIntExtra(EXTRA_END_MIN, -1) ?: -1
        if (startMin < 0 || endMin < 0) {
            // Likely a START_STICKY restart with a null intent — no session to resume.
            SessionLog.log(this, "onStartCommand without window extras (restart?) — idling")
            return START_STICKY
        }
        val start = LocalTime.of(startMin / 60, startMin % 60)
        val end = LocalTime.of(endMin / 60, endMin % 60)
        SessionLog.reset(this)
        SessionLog.log(this, "onStartCommand window=$start..$end")

        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("sdk", ignoreCase = true)
        if (isEmulator) healthManager = null  // Samsung Health not present on emulator

        // Resolve the window's absolute epoch range. Handles cross-midnight
        // windows (e.g. 23:45→00:15) and rolls the whole window forward to the
        // next night if it's already fully in the past.
        val zone = ZoneId.systemDefault()
        val nowDt = LocalDateTime.now()
        val today = LocalDate.now()
        var startDt = today.atTime(start)
        var endDt = today.atTime(end)
        if (!end.isAfter(start)) endDt = endDt.plusDays(1)  // window crosses midnight
        while (endDt.isBefore(nowDt)) {                      // whole window already past → next night
            startDt = startDt.plusDays(1)
            endDt = endDt.plusDays(1)
        }
        windowStartEpochMs = startDt.atZone(zone).toEpochSecond() * 1000
        windowEndEpochMs = endDt.atZone(zone).toEpochSecond() * 1000
        lastProcessedEpochEndMs = 0L
        passCount = 0
        alarmFired = false
        sessionStartedAt = Instant.now()
        sessionWindowStart = start
        sessionWindowEnd = end
        tickHistory.clear()
        SessionLog.log(this, "window resolved: $start..$end " +
                "(startEpoch=${java.util.Date(windowStartEpochMs)} endEpoch=${java.util.Date(windowEndEpochMs)}) " +
                "isEmulator=$isEmulator")

        // Hard fallback alarm at window end (Doze-proof, independent of this
        // service). A favorable detection re-schedules it earlier.
        if (System.currentTimeMillis() < windowEndEpochMs) {
            AlarmScheduler.scheduleAt(this, windowEndEpochMs)
            SessionLog.log(this, "fallback alarm pre-scheduled for ${java.util.Date(windowEndEpochMs)}")
        } else {
            SessionLog.log(this, "WARN: endEpoch already in the past — NOT pre-scheduling fallback alarm")
        }

        update("Watching for the perfect moment to wake you")

        // Tell the watch companion to start streaming real-time HR.
        scope.launch {
            val ok = WearCommand.startStreaming(this@SleepMonitoringService)
            SessionLog.log(this@SleepMonitoringService, "WEAR_START sent=$ok")
        }

        // First pass now (consume any buffered data) + start the Doze-proof
        // backstop tick chain.
        scope.launch { runPredictionPass(source = "initial") }
        scheduleBackstopTick()
        return START_REDELIVER_INTENT
    }

    /** Called by WearMessageListener whenever a fresh HR batch lands. */
    fun onWearDataArrived(label: String) {
        scope.launch { runPredictionPass(source = "wear:$label") }
    }

    private fun scheduleBackstopTick() {
        if (alarmFired) return
        val next = System.currentTimeMillis() + BACKSTOP_TICK_MS
        AlarmScheduler.scheduleTickAt(this, next)
        SessionLog.log(this, "backstop tick scheduled for ${java.util.Date(next)}")
    }

    /**
     * Run ONE prediction pass. Idempotent and mutex-guarded — safe to call
     * concurrently from the wear push path and the AlarmManager tick path.
     * Consumes all real epochs accumulated since the last watermark, walks the
     * predictor through them, fires the alarm on a fresh in-window stable-Light.
     */
    private suspend fun runPredictionPass(source: String) = passMutex.withLock {
        if (alarmFired) return@withLock
        if (windowEndEpochMs == 0L) {
            SessionLog.log(this, "pass[$source]: no active session — ignoring")
            return@withLock
        }
        passCount++
        val now = System.currentTimeMillis()

        if (now > windowEndEpochMs) {
            // The hard fallback alarm was pre-scheduled at windowEndEpochMs and
            // has ALREADY rung by the time a post-window pass runs — so we must
            // NOT re-fire here (that caused the double-ring at 09:20). Just clean
            // up: stop the tick chain, upload the session, stop the service.
            SessionLog.log(this, "pass[$source] #$passCount PAST_WINDOW now=${java.util.Date(now)} — " +
                    "fallback already fired at window end; cleaning up")
            alarmFired = true
            AlarmScheduler.cancelTick(this)
            withContext(NonCancellable + Dispatchers.IO) {
                uploadSession(firedReason = "fallback", firedAt = Instant.ofEpochMilli(windowEndEpochMs))
            }
            stopSelf()
            return@withLock
        }

        val newEpochs = buildNewEpochsFromRealData(lastProcessedEpochEndMs)
        if (newEpochs.isEmpty()) {
            SessionLog.log(this, "pass[$source] #$passCount no new real epochs since " +
                    "${if (lastProcessedEpochEndMs > 0) java.util.Date(lastProcessedEpochEndMs) else "service-start"} " +
                    "(wearBuf=${WearHrSource.bufferSize()} wearLagMin=${WearHrSource.lagMillis() / 60_000})")
            return@withLock
        }

        var lastPred: TFLiteSleepPredictor.SleepPrediction? = null
        val epochFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
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
                    // Per-epoch line so backfilled minutes are all visible in the
                    // log (a morning burst can backfill 60+ epochs — the pass
                    // summary alone would hide the whole trajectory).
                    SessionLog.log(this, "  epoch ${epochFmt.format(java.util.Date(epoch.endTimeMs))} " +
                            "hr=${epoch.hrSampleCount}smp stage=${lastPred!!.sleepStage} " +
                            "conf=${"%.2f".format(lastPred!!.confidence)} stable=${lastPred!!.isStable}")
                }
            }
            lastProcessedEpochEndMs = epoch.endTimeMs
        }

        if (lastPred == null) {
            SessionLog.log(this, "pass[$source] #$passCount processed ${newEpochs.size} epochs, " +
                    "predictor warming up (buffer=${predictor?.getBufferSize()})")
            return@withLock
        }

        val latestEpochAgeMs = now - lastProcessedEpochEndMs
        val insideWindow = lastProcessedEpochEndMs in windowStartEpochMs..windowEndEpochMs
        val fresh = latestEpochAgeMs <= MAX_DATA_AGE_FOR_DECISION_MS
        val moves1m = WearAccelSource.movementEventsInWindow(60_000)
        val moves5m = WearAccelSource.movementEventsInWindow(5 * 60_000)
        SessionLog.log(
            this,
            "pass[$source] #$passCount processed=${newEpochs.size} latestEpoch=${java.util.Date(lastProcessedEpochEndMs)} " +
                    "age=${latestEpochAgeMs / 60000}min stage=${lastPred!!.sleepStage} " +
                    "conf=${"%.2f".format(lastPred!!.confidence)} stable=${lastPred!!.isStable} " +
                    "insideWindow=$insideWindow fresh=$fresh moves1m=$moves1m moves5m=$moves5m"
        )

        val favorable = insideWindow && fresh &&
                lastPred!!.sleepStage.equals("Light", ignoreCase = true) &&
                lastPred!!.isStable
        if (favorable) {
            SessionLog.log(this, "FAVORABLE moment detected — firing alarm now")
            AlarmScheduler.scheduleAt(this, now + 500)
            alarmFired = true
            AlarmScheduler.cancelTick(this)
            update("Light sleep detected — gently waking you")
            withContext(NonCancellable + Dispatchers.IO) {
                uploadSession(firedReason = "favorable", firedAt = Instant.now())
            }
            stopSelf()
        }
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
                val (accStd, accMoveRatio) = WearAccelSource.accFeaturesForWindow(epochStart, epochEnd)
                out.add(
                    RealEpoch(
                        startTimeMs = epochStart,
                        endTimeMs = epochEnd,
                        features = featuresFromWearSamples(epochSamples, accStd, accMoveRatio),
                        hrSampleCount = epochSamples.size,
                    )
                )
            }
            epochStart = epochEnd
        }
        return out
    }

    override fun onDestroy() {
        SessionLog.log(this, "onDestroy (alarmFired=$alarmFired, passes=$passCount)")
        instance = null
        AlarmScheduler.cancelTick(this)
        // Tell the watch companion to stop streaming — runs synchronously on a
        // throwaway scope so it actually completes before the process winds down.
        runCatching {
            kotlinx.coroutines.runBlocking {
                WearCommand.stopStreaming(this@SleepMonitoringService)
            }
        }
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
        accStd: Float = 0f,
        accMoveRatio: Float = 0f,
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
            accStd = accStd,
            accMoveRatio = accMoveRatio,
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
        private const val MIN_HR_SAMPLES = 3   // minimum HR samples in an epoch to trust it
        // How old the latest real epoch can be while still firing the alarm.
        // Sleep cycles change on the ~10-20 min scale, so a Light prediction
        // built from data <10 min old is still acting on a defensible read of
        // the user's current state.
        private const val MAX_DATA_AGE_FOR_DECISION_MS = 10L * 60 * 1000
        // Doze-proof backstop tick cadence. The OS rate-limits
        // setExactAndAllowWhileIdle to ~once per 9 min in deep Doze, so 5 min is
        // a request that realistically lands every ~5-9 min — fine as a safety
        // net behind the wear data-push path (which fires ~every 8 min anyway).
        private const val BACKSTOP_TICK_MS = 5L * 60 * 1000

        // Live instance so the same-process WearMessageListener can poke a
        // prediction pass the instant a batch arrives. Null when not running.
        @Volatile var instance: SleepMonitoringService? = null
            private set

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

        /**
         * Drive a Doze-proof backstop tick. If the service process is alive we
         * poke it directly; otherwise restart it via startForegroundService (the
         * alarm broadcast gets a temporary background-FGS-start allowance).
         */
        fun triggerTick(context: Context) {
            val live = instance
            if (live != null) {
                live.onWearDataArrived("backstop-tick")
                live.scheduleBackstopTick()
            } else {
                val intent = Intent(context, SleepMonitoringService::class.java).apply {
                    action = AlarmReceiver.ACTION_TICK
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
