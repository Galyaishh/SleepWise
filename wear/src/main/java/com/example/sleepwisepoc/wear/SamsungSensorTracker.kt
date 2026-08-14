package com.example.sleepwisepoc.wear

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Stage-2: live skin-temperature + inter-beat-interval (for HRV) via the Samsung
 * Health Sensor SDK. Runs on the WATCH alongside HrStreamService's HR/accel and
 * buffers samples that the 5-second flush loop drains and sends to the phone.
 *
 * Requires: samsung-health-sensor-api aar in wear/libs/ AND "developer mode" of
 * the Health Sensor Service enabled on the watch (own-device testing; public
 * distribution needs a Samsung Partner Program approval).
 *
 * API names verified against samsung-health-sensor-api 1.4.1.
 */
class SamsungSensorTracker(private val context: Context) {

    private var service: HealthTrackingService? = null
    private var tempTracker: HealthTracker? = null
    private var hrTracker: HealthTracker? = null

    private val tempBuf = ConcurrentLinkedQueue<Pair<Long, Float>>()   // ts, skin °C
    private val ibiBuf = ConcurrentLinkedQueue<Pair<Long, Float>>()    // ts, valid IBI ms

    fun start() {
        val listener = object : ConnectionListener {
            override fun onConnectionSuccess() {
                Log.d(TAG, "Samsung HealthTrackingService connected")
                runCatching { startTemp() }.onFailure { Log.w(TAG, "temp start failed: ${it.message}") }
                runCatching { startHr() }.onFailure { Log.w(TAG, "ibi start failed: ${it.message}") }
            }
            override fun onConnectionEnded() { Log.d(TAG, "Samsung service ended") }
            override fun onConnectionFailed(e: HealthTrackerException) {
                Log.w(TAG, "Samsung connect failed: ${e.message} — is developer mode on the watch enabled?")
            }
        }
        service = HealthTrackingService(listener, context).also { it.connectService() }
    }

    private fun startTemp() {
        tempTracker = service?.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
        tempTracker?.setEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(data: List<DataPoint>) {
                for (dp in data) {
                    val t = runCatching { dp.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE) }.getOrNull()
                    if (t != null && t > 20f && t < 45f) tempBuf.add(dp.timestamp to t)
                }
            }
            override fun onFlushCompleted() {}
            override fun onError(error: HealthTracker.TrackerError) { Log.w(TAG, "temp error: $error") }
        })
    }

    private fun startHr() {
        // The continuous HR tracker also carries the per-beat IBI list + status.
        hrTracker = service?.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
        hrTracker?.setEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(data: List<DataPoint>) {
                for (dp in data) {
                    val ts = dp.timestamp
                    val ibis = runCatching { dp.getValue(ValueKey.HeartRateSet.IBI_LIST) }.getOrNull() ?: continue
                    val status = runCatching { dp.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST) }.getOrNull()
                    ibis.forEachIndexed { i, ms ->
                        val ok = status?.getOrNull(i)?.let { it == 0 } ?: true   // 0 == valid
                        if (ok && ms in 300..2000) ibiBuf.add(ts to ms.toFloat())  // 30–200 bpm plausibility
                    }
                }
            }
            override fun onFlushCompleted() {}
            override fun onError(error: HealthTracker.TrackerError) { Log.w(TAG, "ibi error: $error") }
        })
    }

    /** Drained by HrStreamService's flush loop every 5 s. */
    fun drainTemp(): List<Pair<Long, Float>> = drain(tempBuf)
    fun drainIbi(): List<Pair<Long, Float>> = drain(ibiBuf)

    private fun drain(q: ConcurrentLinkedQueue<Pair<Long, Float>>): List<Pair<Long, Float>> {
        val out = ArrayList<Pair<Long, Float>>(q.size)
        while (true) out.add(q.poll() ?: break)
        return out
    }

    fun stop() {
        runCatching { tempTracker?.unsetEventListener() }
        runCatching { hrTracker?.unsetEventListener() }
        runCatching { service?.disconnectService() }
        tempTracker = null; hrTracker = null; service = null
        tempBuf.clear(); ibiBuf.clear()
    }

    companion object { private const val TAG = "SamsungSensorTracker" }
}
