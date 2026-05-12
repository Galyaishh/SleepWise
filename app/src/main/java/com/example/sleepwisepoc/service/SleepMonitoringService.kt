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
import com.example.sleepwisepoc.DeviceStore
import com.example.sleepwisepoc.MainActivity
import com.example.sleepwisepoc.SamsungHealthManager
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
        Log.d(TAG, "onStartCommand window=$start..$end")

        loop?.cancel()
        sessionStartedAt = Instant.now()
        sessionWindowStart = start
        sessionWindowEnd = end
        tickHistory.clear()
        loop = scope.launch { runLoop(start, end) }
        return START_REDELIVER_INTENT
    }

    private suspend fun runLoop(start: LocalTime, end: LocalTime) {
        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("sdk", ignoreCase = true)
        val tickMs = (if (isEmulator) TICK_SEC_DEMO else TICK_SEC_REAL) * 1000L
        Log.d(TAG, "loop tick=${tickMs}ms isEmulator=$isEmulator")

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val startEpoch = today.atTime(start).atZone(zone).toEpochSecond() * 1000
        val endEpoch = today.atTime(end).atZone(zone).toEpochSecond() * 1000

        update("Watching for the perfect moment to wake you")

        var epochIndex = 0
        val totalEpochs = (Duration.between(start, end).toMinutes()).coerceAtLeast(10).toInt()

        while (scope.isActive && !alarmFired) {
            val now = System.currentTimeMillis()
            val insideWindow = now in startEpoch..endEpoch
            val pastWindow = now > endEpoch

            if (pastWindow) {
                Log.d(TAG, "past window end — firing fallback alarm (UC-3 alt 3a)")
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
                Log.d(
                    TAG,
                    "tick #$epochIndex stage=${pred.sleepStage} conf=${"%.2f".format(pred.confidence)} " +
                            "stable=${pred.isStable} insideWindow=$insideWindow"
                )

                val favorable = insideWindow &&
                        pred.sleepStage.equals("Light", ignoreCase = true) &&
                        pred.isStable

                if (favorable) {
                    Log.d(TAG, "favorable moment detected — firing now")
                    AlarmScheduler.scheduleAt(this, now + 500)
                    alarmFired = true
                    update("Light sleep detected — gently waking you")
                    withContext(NonCancellable + Dispatchers.IO) {
                        uploadSession(firedReason = "favorable", firedAt = Instant.now())
                    }
                    break
                }
            } else {
                Log.d(TAG, "tick #$epochIndex buffer=${predictor?.getBufferSize()} (warming up)")
            }

            delay(tickMs)
        }

        Log.d(TAG, "loop finished (alarmFired=$alarmFired) — stopping service")
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
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
        val manager = healthManager
        if (manager != null) {
            try {
                val epochs = manager.processDataIntoEpochs(hoursBack = 1)
                val latest = epochs.lastOrNull()
                if (latest != null && latest.hrSampleCount >= MIN_HR_SAMPLES) {
                    Log.d(TAG, "acquireEpoch: real data — HR=${latest.hrMean.toInt()} bpm " +
                            "(${latest.hrSampleCount} samples @ ${latest.timeString})")
                    return manager.epochToFeatures(latest)
                }
                Log.d(TAG, "acquireEpoch: real data insufficient " +
                        "(${latest?.hrSampleCount ?: 0} samples) → mock")
            } catch (e: Exception) {
                Log.w(TAG, "acquireEpoch: health read failed (${e.message}) → mock")
            }
        }
        Log.d(TAG, "acquireEpoch: using mock epoch #$epochIndex")
        return predictor.createMockEpoch("light", epochIndex, totalEpochs)
    }

    // ─── Upload ───────────────────────────────────────────────────────────────

    private suspend fun uploadSession(firedReason: String, firedAt: Instant) {
        val started = sessionStartedAt ?: return
        val winStart = sessionWindowStart ?: return
        val winEnd = sessionWindowEnd ?: return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val payload = SessionUpload(
            user_id = DeviceStore(this).load()?.first ?: "unknown",
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
        private const val TICK_SEC_DEMO = 3L
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
