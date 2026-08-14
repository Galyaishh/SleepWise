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
 * Stage-2 wrapper around the Samsung Health Sensor SDK. Runs on the WATCH,
 * streams two extra live signals in parallel with HrStreamService's HR/accel:
 *   • skin temperature  (SKIN_TEMPERATURE_CONTINUOUS)  -> drainTemp()
 *   • inter-beat interval (HEART_RATE_CONTINUOUS.IBI)  -> drainIbi()  (phone computes HRV)
 *
 * Requires: samsung-health-sensor-api .aar in wear/libs/ + watch "developer mode".
 * NOTE: class/ValueKey names below use the SDK 1.x naming — every spot marked
 * `// VERIFY` must be checked against the exact SDK version you download.
 */
class SamsungSensorTracker(private val context: Context) {

    private var service: HealthTrackingService? = null
    private var tempTracker: HealthTracker? = null
    private var hrTracker: HealthTracker? = null

    private val tempBuf = ConcurrentLinkedQueue<Pair<Long, Float>>()   // ts, °C
    private val ibiBuf = ConcurrentLinkedQueue<Pair<Long, Float>>()    // ts, IBI ms (valid only)

    fun start() {
        val listener = object : ConnectionListener {
            override fun onConnectionSuccess() {
                Log.d(TAG, "Samsung HealthTrackingService connected")
                runCatching { startTemp() }.onFailure { Log.w(TAG, "temp start failed: ${it.message}") }
                runCatching { startHr() }.onFailure { Log.w(TAG, "hr/ibi start failed: ${it.message}") }
            }
            override fun onConnectionEnded() { Log.d(TAG, "Samsung service ended") }
            override fun onConnectionFailed(e: HealthTrackerException) {
                Log.w(TAG, "Samsung connect failed: ${e.message} (developer mode enabled on watch?)")
            }
        }
        service = HealthTrackingService(listener, context).also { it.connectService() }
    }

    private fun startTemp() {
        // VERIFY: SKIN_TEMPERATURE_CONTINUOUS exists on GW5+ with updated Health Sensor Service
        tempTracker = service?.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
        tempTracker?.setEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(data: List<DataPoint>) {
                for (dp in data) {
                    // VERIFY: OBJECT_TEMPERATURE = skin (vs AMBIENT_TEMPERATURE); some SDKs expose a status key
                    val t = runCatching { dp.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE) }.getOrNull()
                    if (t != null && t > 20f && t < 45f) tempBuf.add(dp.timestamp to t.toFloat())
                }
            }
            override fun onFlushCompleted() {}
            override fun onError(e: HealthTracker.TrackerError?) { Log.w(TAG, "temp error: $e") }
        })
    }

    private fun startHr() {
        // Continuous HR tracker also carries the per-beat IBI list.
        hrTracker = service?.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
        hrTracker?.setEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(data: List<DataPoint>) {
                for (dp in data) {
                    val ts = dp.timestamp
                    // VERIFY: IBI_LIST + IBI_STATUS_LIST (status 0 == valid) in HeartRateSet
                    val ibis = runCatching { dp.getValue(ValueKey.HeartRateSet.IBI_LIST) }.getOrNull() ?: continue
                    val status = runCatching { dp.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST) }.getOrNull()
                    ibis.forEachIndexed { i, ms ->
                        val ok = status?.getOrNull(i)?.let { it == 0 } ?: true
                        if (ok && ms in 300..2000) ibiBuf.add(ts to ms.toFloat())  // 30–200 bpm plausibility
                    }
                }
            }
            override fun onFlushCompleted() {}
            override fun onError(e: HealthTracker.TrackerError?) { Log.w(TAG, "hr/ibi error: $e") }
        })
    }

    /** Called by HrStreamService's 5-second flush loop. */
    fun drainTemp(): List<Pair<Long, Float>> = drain(tempBuf)
    fun drainIbi(): List<Pair<Long, Float>> = drain(ibiBuf)

    private fun drain(q: ConcurrentLinkedQueue<Pair<Long, Float>>): List<Pair<Long, Float>> {
        val out = ArrayList<Pair<Long, Float>>(q.size)
        while (true) { out.add(q.poll() ?: break) }
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
