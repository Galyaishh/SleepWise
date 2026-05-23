package com.example.sleepwisepoc.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.SampleDataPoint
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Watch-side foreground service that streams real-time heart rate to the phone.
 *
 * Architecture:
 *   1. Register a MeasureCallback with Health Services for HEART_RATE_BPM.
 *   2. Health Services delivers SampleDataPoints in real time as the PPG
 *      sensor produces them (typically every 1–5 seconds when worn).
 *   3. We batch samples in memory and flush every BATCH_INTERVAL_MS via the
 *      Wearable Data Layer (MessageClient.sendMessage) to every paired node.
 *   4. Phone-side WearMessageListener receives the batches and pushes them
 *      into the sleep-tracking service's HR buffer — no more Samsung Health
 *      lag, no more 6-hour overnight gaps.
 */
class HrStreamService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var measureClient: MeasureClient? = null
    private var flushJob: Job? = null
    private val pendingSamples = mutableListOf<Pair<Long, Float>>()
    private val pendingLock = Any()

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(
            dataType: DeltaDataType<*, *>,
            availability: Availability,
        ) {
            val state = (availability as? DataTypeAvailability)?.name ?: availability.toString()
            Log.d(TAG, "HR availability changed: $state")
        }

        override fun onDataReceived(data: androidx.health.services.client.data.DataPointContainer) {
            val samples = data.getData(DataType.HEART_RATE_BPM)
            samples.forEach { sample ->
                onSample(sample)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BODY_SENSORS not granted — service cannot stream HR")
            stopSelf()
            return START_NOT_STICKY
        }
        startStreaming()
        return START_STICKY
    }

    private fun startStreaming() {
        val client = HealthServices.getClient(this).measureClient
        measureClient = client
        try {
            client.registerMeasureCallback(DataType.HEART_RATE_BPM, hrCallback)
            Log.d(TAG, "registered MeasureCallback for HEART_RATE_BPM")
        } catch (t: Throwable) {
            Log.e(TAG, "registerMeasureCallback failed", t)
            stopSelf()
            return
        }
        // Periodic flush loop — push the buffered batch to the phone.
        flushJob?.cancel()
        flushJob = scope.launch {
            while (isActive) {
                delay(BATCH_INTERVAL_MS)
                flushBatch()
            }
        }
    }

    private fun onSample(sample: SampleDataPoint<Double>) {
        val bpm = sample.value.toFloat()
        if (bpm <= 0f) return  // 0/-1 means "no contact" — skip
        val tsMs = System.currentTimeMillis() // sample.timeDurationFromBoot would be elapsed; we want wall-clock
        synchronized(pendingLock) {
            pendingSamples += tsMs to bpm
        }
        Log.d(TAG, "sample bpm=${bpm.toInt()} ts=$tsMs")
    }

    private fun flushBatch() {
        val batch: List<Pair<Long, Float>>
        synchronized(pendingLock) {
            if (pendingSamples.isEmpty()) return
            batch = pendingSamples.toList()
            pendingSamples.clear()
        }
        scope.launch {
            try {
                val payload = WearProtocol.encodeBatch(batch)
                val nodes = Wearable.getNodeClient(this@HrStreamService).connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w(TAG, "no connected phone node — dropping ${batch.size} samples")
                    return@launch
                }
                val msgClient = Wearable.getMessageClient(this@HrStreamService)
                nodes.forEach { node ->
                    msgClient.sendMessage(node.id, WearProtocol.PATH_HR_BATCH, payload).await()
                }
                Log.d(TAG, "sent batch of ${batch.size} samples to ${nodes.size} node(s)")
            } catch (t: Throwable) {
                Log.w(TAG, "flushBatch failed: ${t.message}")
            }
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
        try {
            measureClient?.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback)
        } catch (_: Throwable) { /* best effort */ }
        // Flush any straggler samples synchronously.
        flushBatch()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "HrStreamService"
        private const val CHANNEL_ID = "sleepwise_hr_stream"
        private const val NOTIF_ID = 4242
        private const val BATCH_INTERVAL_MS = 5_000L  // flush every 5s
    }
}
