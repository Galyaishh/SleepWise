package com.example.sleepwisepoc.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await as awaitTask

/**
 * Watch-side foreground service that streams real-time heart rate to the phone.
 *
 * Uses **`ExerciseClient`** (not `MeasureClient`) because:
 * - `MeasureClient` is designed for on-demand spot readings; Wear OS power-
 *   throttles its callback after a long continuous registration. We observed
 *   the watch going completely silent ~1 hour into a streaming session.
 * - `ExerciseClient` is the API for sustained tracking sessions (workouts,
 *   sleep monitoring). It keeps PPG in active mode for the duration of the
 *   exercise and delivers samples continuously without OS-imposed silencing.
 *
 * Lifecycle:
 *   onStartCommand → startExerciseAsync(ExerciseType.WORKOUT, HR only)
 *   → ExerciseUpdateCallback.onExerciseUpdateReceived delivers HR samples
 *   → flush loop sends them to the phone via Wearable Data Layer every 5s
 *   → onDestroy / "/sleepwise/cmd/stop" → endExerciseAsync, clean shutdown
 */
class HrStreamService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var exerciseClient: ExerciseClient? = null
    private var flushJob: Job? = null
    private val pendingSamples = mutableListOf<Pair<Long, Float>>()
    private val pendingLock = Any()
    @Volatile private var exerciseActive = false

    // Accelerometer: gravity-removed magnitude in m/s² per sample. We do the
    // gravity subtraction on the watch (cheap) and just send (timestamp, magnitude).
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private val pendingAccel = mutableListOf<Pair<Long, Float>>()
    private val accelLock = Any()
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // event.values = [x, y, z] in m/s² including gravity.
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = kotlin.math.sqrt(x * x + y * y + z * z) - 9.81f
            // Use absolute value — small negative readings (free-fall-like) are
            // still motion. Anything above MOVEMENT_THRESHOLD_M_S2 will count
            // as a "movement event" on the phone side.
            val tsMs = System.currentTimeMillis()
            synchronized(accelLock) {
                pendingAccel += tsMs to kotlin.math.abs(magnitude)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* unused */ }
    }

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onRegistered() {
            Log.d(TAG, "ExerciseUpdateCallback registered")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "ExerciseUpdateCallback registration failed", throwable)
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val hrSamples = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
            if (hrSamples.isEmpty()) return
            // Each SampleDataPoint carries its own timeDurationFromBoot — i.e., the
            // monotonic boot-time clock value when the PPG sensor produced it.
            // Convert each to wall-clock individually so a 250-sample burst preserves
            // the 1Hz spacing instead of collapsing to one timestamp.
            val nowWallMs = System.currentTimeMillis()
            val nowBootMs = SystemClock.elapsedRealtime()
            hrSamples.forEach { sample ->
                val bpm = sample.value.toFloat()
                if (bpm > 0f) {
                    val sampleBootMs = sample.timeDurationFromBoot.toMillis()
                    val sampleWallMs = nowWallMs - (nowBootMs - sampleBootMs)
                    synchronized(pendingLock) {
                        pendingSamples += sampleWallMs to bpm
                    }
                    Log.d(TAG, "sample bpm=${bpm.toInt()} ts=$sampleWallMs")
                }
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) { /* unused */ }

        override fun onAvailabilityChanged(
            dataType: DataType<*, *>,
            availability: Availability,
        ) {
            val state = (availability as? DataTypeAvailability)?.name ?: availability.toString()
            Log.d(TAG, "HR availability changed: $state")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand exerciseActive=$exerciseActive")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BODY_SENSORS not granted — cannot stream HR")
            stopSelf()
            return START_NOT_STICKY
        }
        // Idempotent: if we already have a running exercise, ignore repeat START commands.
        if (!exerciseActive) {
            startExerciseSession()
        } else {
            Log.d(TAG, "ignoring START — exercise already active")
        }
        return START_STICKY
    }

    private fun startAccelStreaming() {
        if (sensorManager != null) return  // already started
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sm == null) {
            Log.w(TAG, "no SensorManager — accel disabled")
            return
        }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.w(TAG, "no accelerometer on this device — accel disabled")
            return
        }
        // SENSOR_DELAY_NORMAL ≈ 200ms cadence — fine for movement detection
        // and much cheaper than UI/GAME/FASTEST rates.
        val ok = sm.registerListener(accelListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (ok) {
            sensorManager = sm
            accelSensor = sensor
            Log.d(TAG, "accelerometer streaming started")
        } else {
            Log.w(TAG, "registerListener returned false — accel disabled")
        }
    }

    private fun stopAccelStreaming() {
        val sm = sensorManager ?: return
        runCatching { sm.unregisterListener(accelListener) }
        sensorManager = null
        accelSensor = null
        Log.d(TAG, "accelerometer streaming stopped")
    }

    private fun startExerciseSession() {
        val client = HealthServices.getClient(this).exerciseClient
        exerciseClient = client
        // Register the callback synchronously BEFORE starting the exercise so we
        // don't miss the initial onExerciseUpdateReceived events.
        try {
            client.setUpdateCallback(exerciseCallback)
        } catch (t: Throwable) {
            Log.e(TAG, "setUpdateCallback failed", t)
            stopSelf()
            return
        }
        val config = ExerciseConfig.builder(ExerciseType.WORKOUT)
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            // Disable auto-pause so the watch doesn't stop HR delivery when
            // the user lies still (which is *exactly* sleep tracking).
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(false)
            .build()
        scope.launch {
            try {
                client.startExerciseAsync(config).await()
                exerciseActive = true
                Log.d(TAG, "exercise started (WORKOUT type, HR_BPM only)")
            } catch (t: Throwable) {
                Log.e(TAG, "startExerciseAsync failed", t)
                stopSelf()
                return@launch
            }
        }
        // Accelerometer streams independently of ExerciseClient — same listener
        // pattern, just plain Android SensorManager.
        startAccelStreaming()
        // Periodic flush loop — push buffered HR and accel to the phone every 5s.
        flushJob?.cancel()
        flushJob = scope.launch {
            while (isActive) {
                delay(BATCH_INTERVAL_MS)
                flushBatch()
                flushAccelBatch()
            }
        }
    }

    private suspend fun endExerciseSession() {
        val client = exerciseClient ?: return
        try {
            client.endExerciseAsync().await()
            Log.d(TAG, "exercise ended cleanly")
        } catch (t: Throwable) {
            Log.w(TAG, "endExerciseAsync failed (likely already ended): ${t.message}")
        }
        exerciseActive = false
    }

    private fun flushBatch() {
        val batch: List<Pair<Long, Float>>
        synchronized(pendingLock) {
            if (pendingSamples.isEmpty()) return
            batch = pendingSamples.toList()
            pendingSamples.clear()
        }
        scope.launch {
            sendBatchToPhone(WearProtocol.PATH_HR_BATCH, batch, "HR")
        }
    }

    private fun flushAccelBatch() {
        val batch: List<Pair<Long, Float>>
        synchronized(accelLock) {
            if (pendingAccel.isEmpty()) return
            batch = pendingAccel.toList()
            pendingAccel.clear()
        }
        scope.launch {
            sendBatchToPhone(WearProtocol.PATH_ACCEL_BATCH, batch, "ACCEL")
        }
    }

    /** Shared batch-send helper used by both HR and accel flush paths. */
    private suspend fun sendBatchToPhone(
        path: String,
        batch: List<Pair<Long, Float>>,
        label: String,
    ) {
        try {
            val payload = WearProtocol.encodeBatch(batch)
            val nodes = Wearable.getNodeClient(this@HrStreamService).connectedNodes.awaitTask()
            if (nodes.isEmpty()) {
                Log.w(TAG, "no connected phone node — dropping ${batch.size} $label samples")
                return
            }
            val msgClient = Wearable.getMessageClient(this@HrStreamService)
            nodes.forEach { node ->
                msgClient.sendMessage(node.id, path, payload).awaitTask()
            }
            Log.d(TAG, "sent $label batch of ${batch.size} samples to ${nodes.size} node(s)")
        } catch (t: Throwable) {
            Log.w(TAG, "$label batch send failed: ${t.message}")
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SleepWise HR", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("SleepWise tracking")
            .setContentText("Streaming heart rate to phone")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        flushJob?.cancel()
        stopAccelStreaming()
        // Run end + final flush synchronously on a fresh blocking context so they
        // complete before the process is killed.
        runCatching {
            kotlinx.coroutines.runBlocking {
                endExerciseSession()
            }
        }
        flushBatch()
        flushAccelBatch()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "HrStreamService"
        private const val CHANNEL_ID = "sleepwise_hr_stream"
        private const val NOTIF_ID = 4242
        private const val BATCH_INTERVAL_MS = 5_000L
    }
}
